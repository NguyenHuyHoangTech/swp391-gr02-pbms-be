// Author: Võ Trung Hiếu
package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.pbms.modules.finance.strategy.PayPalStrategy;
import com.pbms.modules.finance.strategy.PayOsStrategy;
import com.pbms.modules.finance.service.PaymentValidatorService;
import com.pbms.modules.finance.repository.PaymentOrderRepository;
import com.pbms.modules.finance.dto.PaymentActionRequest;
import com.pbms.modules.finance.dto.PaymentExecutionResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@RestController
@RequestMapping("/api/v1/finance/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PayPalStrategy payPalStrategy;
    private final PayOsStrategy payOsStrategy;
    private final PaymentValidatorService paymentValidatorService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * POST /api/v1/payments/initialize
     * Validates business payload and generates payment link
     */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initializePayment(
            @RequestBody PaymentActionRequest request) {
        try {
            String currentUserEmail = null;
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                currentUserEmail = auth.getName();
            }

            Double requestedAmount = request.getAmount() != null ? request.getAmount() : 0.0;
            Double amount = paymentValidatorService.calculateRequiredAmount(request);
            
            if (amount <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Số tiền thanh toán phải lớn hơn 0 để sử dụng cổng thanh toán."));
            }
            
            if (Math.abs(amount - requestedAmount) > 1.0) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Phí thanh toán thực tế đã thay đổi thành " + String.format("%,d", amount.longValue()) + " VNĐ. Vui lòng làm mới trang (refresh) để xem báo giá mới nhất."));
            }
            
            request.setAmount(amount);
            String gateway = request.getGateway(); // PAYOS, PAYPAL
            String orderId = UUID.randomUUID().toString();
            String paymentUrl = "";
            String qrCode = null;

            if ("PAYOS".equalsIgnoreCase(gateway)) {
                Map<String, String> payosData = payOsStrategy.generatePayOsLink(amount, orderId);
                paymentUrl = payosData.get("checkoutUrl");
                qrCode = payosData.get("qrCode");
                orderId = payosData.get("orderCode"); 
            } else if ("PAYPAL".equalsIgnoreCase(gateway)) {
                orderId = "order_" + com.pbms.common.utils.TimeProvider.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
                paymentUrl = payPalStrategy.generatePaymentUrl(amount, orderId);
                if (paymentUrl != null && paymentUrl.contains("token=")) {
                    orderId = paymentUrl.substring(paymentUrl.indexOf("token=") + 6);
                }
            } else {
                throw new IllegalArgumentException("Unsupported payment gateway: " + gateway);
            }

            // Save Pre-validated Order
            paymentValidatorService.initializePaymentOrder(request, orderId, currentUserEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("paymentUrl", paymentUrl);
            response.put("amount", amount);
            response.put("gateway", gateway);
            response.put("status", "PENDING");
            response.put("orderId", orderId);
            if (qrCode != null) response.put("qrCode", qrCode);

            return ResponseEntity.ok(ApiResponse.success(response, "Payment initialized successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Error: " + e.getMessage()));
        }
    }


    /**
     * POST /api/v1/payments/paypal/capture
     * Capture PayPal order
     */
    @PostMapping("/paypal/capture")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capturePayPalOrder(@RequestBody Map<String, String> requestBody) {
        try {
            String token = requestBody.get("token");
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Token is required"));
            }
            boolean success = payPalStrategy.captureOrder(token);
            if (success) {
                paymentOrderRepository.findByOrderCode(token).ifPresent(order -> {
                    order.setStatus("PAID");
                    paymentOrderRepository.save(order);
                });
                return ResponseEntity.ok(ApiResponse.success(Map.of("status", "COMPLETED"), "Payment captured successfully"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Payment capture failed"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
    /**
     * POST /api/v1/payments/payos/capture
     * Capture PayOS order
     */
    @PostMapping("/payos/capture")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capturePayOsOrder(@RequestBody Map<String, String> requestBody) {
        try {
            String token = requestBody.get("token"); // orderCode
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Order code token is required"));
            }
            boolean success = payOsStrategy.captureOrder(token);
            if (success) {
                paymentOrderRepository.findByOrderCode(token).ifPresent(order -> {
                    order.setStatus("PAID");
                    paymentOrderRepository.save(order);
                });
                return ResponseEntity.ok(ApiResponse.success(Map.of("status", "COMPLETED"), "Payment captured successfully"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Payment capture failed or still pending"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/v1/payments/execute-action
     * Finalize transaction and run business logic
     */
    @PostMapping("/execute-action")
    public ResponseEntity<ApiResponse<PaymentExecutionResponse>> executeAction(@RequestBody Map<String, String> requestBody) {
        try {
            String token = requestBody.get("token");
            if (token == null || token.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Token required"));
            
            PaymentExecutionResponse executionResponse = paymentValidatorService.executeAction(token);
            
            if (executionResponse.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(executionResponse, "Action executed successfully"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, executionResponse.getMessage()));
            }
        } catch (Exception e) {
            String token = requestBody.get("token");
            if (token != null && !token.isEmpty()) {
                String errorMessage = e.getMessage();
                if (e instanceof org.springframework.transaction.UnexpectedRollbackException && e.getCause() != null) {
                    errorMessage = e.getCause().getMessage();
                } else if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                    errorMessage = e.getCause().getMessage();
                } else if (e.getCause() != null) {
                    errorMessage = e.getCause().getMessage();
                }
                
                try {
                    String currentUserEmail = null;
                    org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                        currentUserEmail = auth.getName();
                    }
                    PaymentExecutionResponse refundResponse = paymentValidatorService.processRefundForFailedAction(token, errorMessage, currentUserEmail);
                    return ResponseEntity.badRequest().body(ApiResponse.error(400, refundResponse.getMessage()));
                } catch (Exception refundEx) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(500, "Execution Failed & Refund Failed: " + refundEx.getMessage()));
                }
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Execution Failed: " + e.getMessage()));
        }
    }

    /**
     * POST /api/v1/finance/payments/webhook/payos
     * Webhook endpoint for PayOS
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/webhook/payos")
    public ResponseEntity<ApiResponse<String>> handlePayOsWebhook(@RequestBody Map<String, Object> payload) {
        try {
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) payload.get("data");
            if (data == null) return ResponseEntity.ok(ApiResponse.success("Ignored", "No data"));
            
            String orderCode = String.valueOf(data.get("orderCode"));
            return processWebhookPayment(orderCode);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * POST /api/v1/finance/payments/webhook/paypal
     * Webhook endpoint for PayPal
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/webhook/paypal")
    public ResponseEntity<ApiResponse<String>> handlePayPalWebhook(@RequestBody Map<String, Object> payload) {
        try {
            java.util.Map<String, Object> resource = (java.util.Map<String, Object>) payload.get("resource");
            if (resource == null) return ResponseEntity.ok(ApiResponse.success("Ignored", "No resource"));
            
            String orderId = (String) resource.get("id");
            return processWebhookPayment(orderId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse<String>> processWebhookPayment(String token) {
        if (token == null || token.isEmpty()) return ResponseEntity.ok(ApiResponse.success("Ignored", "No token"));
        
        java.util.Optional<com.pbms.modules.finance.domain.PaymentOrder> orderOpt = paymentOrderRepository.findByOrderCode(token);
        if (orderOpt.isEmpty()) return ResponseEntity.ok(ApiResponse.success("Ignored", "Order not found"));
        
        com.pbms.modules.finance.domain.PaymentOrder order = orderOpt.get();
        if (!"PENDING".equals(order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success("Ignored", "Order is not PENDING"));
        }
        
        order.setStatus("PAID");
        paymentOrderRepository.save(order);
        
        try {
            PaymentExecutionResponse execRes = paymentValidatorService.executeAction(token);
            if (!execRes.isSuccess()) {
                paymentValidatorService.processRefundForFailedAction(token, execRes.getMessage(), "SYSTEM_WEBHOOK");
                messagingTemplate.convertAndSend("/topic/payments/" + token, "{\"status\":\"FAILED\", \"message\":\"" + execRes.getMessage() + "\"}");
            } else {
                messagingTemplate.convertAndSend("/topic/payments/" + token, "{\"status\":\"SUCCESS\"}");
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (e instanceof org.springframework.transaction.UnexpectedRollbackException && e.getCause() != null) {
                errorMessage = e.getCause().getMessage();
            } else if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                errorMessage = e.getCause().getMessage();
            } else if (e.getCause() != null) {
                errorMessage = e.getCause().getMessage();
            }
            try {
                paymentValidatorService.processRefundForFailedAction(token, errorMessage, "SYSTEM_WEBHOOK");
                messagingTemplate.convertAndSend("/topic/payments/" + token, "{\"status\":\"FAILED\", \"message\":\"" + errorMessage + "\"}");
            } catch (Exception ignored) {}
        }
        
        return ResponseEntity.ok(ApiResponse.success("Processed", "Webhook processed successfully"));
    }
}

