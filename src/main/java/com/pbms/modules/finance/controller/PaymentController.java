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

@RestController
@RequestMapping("/api/v1/finance/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PayPalStrategy payPalStrategy;
    private final PayOsStrategy payOsStrategy;
    private final com.pbms.modules.finance.service.PaymentValidatorService paymentValidatorService;
    private final com.pbms.modules.finance.repository.PaymentOrderRepository paymentOrderRepository;

    /**
     * POST /api/v1/payments/initialize
     * BƯỚC 1: Khởi tạo thanh toán.
     * Hàm này nhận thông tin yêu cầu thanh toán (ví dụ: số tiền, loại ví, hành động) từ Frontend.
     * Tùy thuộc vào gateway (PAYOS, PAYPAL), nó sẽ gọi các Strategy tương ứng để tạo URL thanh toán.
     */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initializePayment(
            @RequestBody com.pbms.modules.finance.dto.PaymentActionRequest request) {
        try {
            String currentUserEmail = null;
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                currentUserEmail = auth.getName();
            }

            Double amount = request.getAmount() != null ? request.getAmount() : 0.0;
            String gateway = request.getGateway(); // VNPAY, PAYOS, PAYPAL
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
                paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=" + orderId;
            }

            // Lưu lại thông tin đơn hàng này vào Database (trạng thái PENDING) để sau này đối chiếu 
            // khi có callback từ cổng thanh toán trả về.
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
     * BƯỚC 2 (PayPal): Xử lý phản hồi (Callback/Webhook) từ PayPal sau khi user đã chuyển tiền.
     * Gọi API của PayPal để capture đơn hàng và đổi trạng thái thành PAID trong DB.
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
     * BƯỚC 2 (PayOS): Frontend gọi API này sau khi user thanh toán PayOS thành công
     * (hoặc webhook từ PayOS bắn về). Xác nhận đơn hàng có đúng đã trả tiền chưa.
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
     * BƯỚC 3: Thực thi logic nghiệp vụ.
     * Khi tiền đã được xác nhận (PAID), hàm này sẽ chạy logic thực sự (ví dụ: Nạp tiền vào ví, mua gói đỗ xe).
     * Nếu xảy ra lỗi nghiệp vụ (hệ thống lỗi, data sai), nó sẽ tự động hoàn tiền (Refund) lại cho user để đảm bảo an toàn (Transaction rollback).
     */
    @PostMapping("/execute-action")
    public ResponseEntity<ApiResponse<com.pbms.modules.finance.dto.PaymentExecutionResponse>> executeAction(@RequestBody Map<String, String> requestBody) {
        try {
            String token = requestBody.get("token");
            if (token == null || token.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Token required"));
            
            com.pbms.modules.finance.dto.PaymentExecutionResponse executionResponse = paymentValidatorService.executeAction(token);
            
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
                    com.pbms.modules.finance.dto.PaymentExecutionResponse refundResponse = paymentValidatorService.processRefundForFailedAction(token, errorMessage, currentUserEmail);
                    return ResponseEntity.badRequest().body(ApiResponse.error(400, refundResponse.getMessage()));
                } catch (Exception refundEx) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(500, "Execution Failed & Refund Failed: " + refundEx.getMessage()));
                }
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Execution Failed: " + e.getMessage()));
        }
    }
}

