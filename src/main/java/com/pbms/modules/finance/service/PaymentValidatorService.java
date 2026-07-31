// Author: Võ Trung Hiếu
package com.pbms.modules.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbms.modules.finance.domain.PaymentOrder;
import com.pbms.modules.finance.domain.RefundRequest;
import com.pbms.modules.finance.dto.PaymentActionRequest;
import com.pbms.modules.finance.dto.PaymentExecutionResponse;
import com.pbms.modules.finance.repository.PaymentOrderRepository;
import com.pbms.modules.finance.repository.RefundRequestRepository;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.operation.dto.CreateReservationRequest;
import com.pbms.modules.finance.domain.PricingPolicy;
import com.pbms.modules.finance.repository.PricingPolicyRepository;
import com.pbms.modules.operation.domain.MonthlyTicket;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.service.GateOperationService;
import com.pbms.modules.operation.service.MonthlyTicketService;
import com.pbms.modules.operation.service.ReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentValidatorService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final UserRepository userRepository;

    private final ReservationService reservationService;
    private final MonthlyTicketService monthlyTicketService;
    private final GateOperationService gateOperationService;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final MonthlyTicketRepository monthlyTicketRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final com.pbms.common.security.JwtProvider jwtProvider;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final org.springframework.context.ApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    /**
     * Calculates monthly ticket total with duration-based discount from system config.
     * Mirrors the same logic used on the frontend.
     */
    private BigDecimal calculateMonthlyTicketFee(PricingPolicy policy, int durationMonths) {
        BigDecimal rate = policy.getMonthlyRate() != null ? policy.getMonthlyRate() : BigDecimal.ZERO;
        BigDecimal baseFee = rate.multiply(BigDecimal.valueOf(durationMonths));
        double discountRate = 0.0;
        try {
            String key = "MONTHLY_DISCOUNT_" + durationMonths;
            com.pbms.modules.system.domain.SystemConfig config = systemConfigService.getConfigByKey(key);
            if (config != null && config.getConfigValue() != null) {
                discountRate = Double.parseDouble(config.getConfigValue());
            }
        } catch (Exception e) {
            // Default discount fallback
            if (durationMonths == 3) discountRate = 0.05;
            else if (durationMonths == 6) discountRate = 0.10;
            else if (durationMonths == 12) discountRate = 0.15;
        }
        BigDecimal discount = baseFee.multiply(BigDecimal.valueOf(discountRate));
        return baseFee.subtract(discount);
    }

    /**
     * Calculates the exact required payment amount based on the action type and payload.
     * Prevents the frontend from manipulating the requested amount.
     */
    @Transactional(readOnly = true)
    public Double calculateRequiredAmount(PaymentActionRequest request) {
        String actionType = request.getActionType();
        Map<String, Object> payload = request.getPayload();
        
        try {
            if ("CREATE_RESERVATION".equals(actionType)) {
                CreateReservationRequest createReq = objectMapper.convertValue(payload, CreateReservationRequest.class);
                return reservationService.previewPrice(createReq.getVehicleTypeId(), createReq.getExpectedEntryTime(), createReq.getExpectedDurationMinutes()).doubleValue();
                
            } else if ("CREATE_MONTHLY_TICKET".equals(actionType)) {
                Long vehicleTypeId = payload.get("vehicleTypeId") != null ? Long.parseLong(payload.get("vehicleTypeId").toString()) : null;
                int duration = 1;
                if (payload.get("durationMonths") != null) {
                    duration = Integer.parseInt(payload.get("durationMonths").toString());
                } else if (payload.get("duration") != null) {
                    duration = Integer.parseInt(payload.get("duration").toString());
                }
                
                PricingPolicy policy = pricingPolicyRepository.findByVehicleTypeIdAndStatus(vehicleTypeId, "ACTIVE")
                        .orElseThrow(() -> new IllegalArgumentException("No active pricing policy for vehicle type: " + vehicleTypeId));
                return calculateMonthlyTicketFee(policy, duration).doubleValue();
                
            } else if ("RENEW_MONTHLY_TICKET".equals(actionType)) {
                Long ticketId = Long.valueOf(payload.get("id").toString());
                int duration = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString()) : 1;
                
                MonthlyTicket ticket = monthlyTicketRepository.findById(ticketId)
                        .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
                PricingPolicy policy = pricingPolicyRepository.findByVehicleTypeIdAndStatus(ticket.getVehicleType().getId(), "ACTIVE")
                        .orElseThrow(() -> new IllegalArgumentException("No active pricing policy found"));
                return calculateMonthlyTicketFee(policy, duration).doubleValue();
                
            } else if ("CHECKOUT".equals(actionType)) {
                String plateNumber = payload.get("plateNumber") != null ? payload.get("plateNumber").toString() : null;
                String rfid = payload.get("rfid") != null ? payload.get("rfid").toString() : null;
                
                ParkingSession session = null;
                if (rfid != null && !rfid.isEmpty()) {
                    session = parkingSessionRepository.findByRfidCard_CardCodeAndStatus(rfid, "ACTIVE").stream().findFirst().orElse(null);
                }
                if (session == null && plateNumber != null && !plateNumber.isEmpty()) {
                    session = parkingSessionRepository.findByPlateAndStatus(plateNumber, "ACTIVE").stream().findFirst().orElse(null);
                }
                if (session == null) {
                    throw new IllegalArgumentException("No active parking session found for Checkout");
                }
                
                if (request.getCheckoutToken() != null && !request.getCheckoutToken().isEmpty()) {
                    try {
                        io.jsonwebtoken.Claims claims = jwtProvider.getCheckoutClaims(request.getCheckoutToken());
                        if (!String.valueOf(session.getId()).equals(claims.get("sessionId", String.class))) {
                            throw new IllegalArgumentException("Token không khớp với phiên đỗ xe hiện tại.");
                        }
                        Double expectedFeeDouble = claims.get("expectedFee", Double.class);
                        if (expectedFeeDouble != null) {
                            return expectedFeeDouble;
                        }
                    } catch (io.jsonwebtoken.ExpiredJwtException e) {
                        throw new IllegalArgumentException("Báo giá đã hết hạn. Vui lòng làm mới trang (refresh) để xem báo giá mới nhất.");
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Token báo giá không hợp lệ: " + e.getMessage());
                    }
                }
                
                // Fallback (should ideally be rejected if no token is provided, but keep for legacy testing)
                com.pbms.modules.operation.dto.CheckOutSessionInfoDTO info = gateOperationService.getCheckOutSessionInfo(session, com.pbms.common.utils.TimeProvider.now());
                return gateOperationService.calculateTotalAmount(info).doubleValue();
            } else if ("RESOLVE_INCIDENT".equals(actionType)) {
                Long incidentId = Long.valueOf(payload.get("incidentId").toString());
                com.pbms.modules.incident.domain.IncidentTicket ticket = applicationContext.getBean(com.pbms.modules.incident.repository.IncidentTicketRepository.class).findById(incidentId)
                        .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
                
                Double expectedPenalty = ticket.getFineAmount() != null ? ticket.getFineAmount().doubleValue() : 0.0;
                Double expectedParking = 0.0;
                
                String checkoutToken = (String) payload.get("checkoutToken");
                if (checkoutToken != null && !checkoutToken.isEmpty()) {
                    try {
                        io.jsonwebtoken.Claims claims = jwtProvider.getCheckoutClaims(checkoutToken);
                        if (ticket.getSession() != null && !String.valueOf(ticket.getSession().getId()).equals(claims.get("sessionId", String.class))) {
                            throw new IllegalArgumentException("Mã xác thực không khớp với phiên đỗ xe hiện tại.");
                        }
                        expectedParking = claims.get("expectedFee", Double.class);
                    } catch (io.jsonwebtoken.ExpiredJwtException e) {
                        throw new IllegalArgumentException("Báo giá đã hết hạn. Vui lòng làm mới trang (refresh) để xem báo giá mới nhất.");
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Token báo giá không hợp lệ: " + e.getMessage());
                    }
                } else if (ticket.getSession() != null && ("ACTIVE".equals(ticket.getSession().getStatus()) || "LOCKED".equals(ticket.getSession().getStatus()))) {
                    com.pbms.modules.operation.dto.CheckOutSessionInfoDTO info = gateOperationService.getCheckOutSessionInfo(ticket.getSession(), com.pbms.common.utils.TimeProvider.now());
                    expectedParking = gateOperationService.calculateTotalAmount(info).doubleValue();
                }
                
                Double discount = payload.get("discountAmount") != null ? Double.parseDouble(payload.get("discountAmount").toString()) : 0.0;
                Double total = expectedParking + expectedPenalty - discount;
                return total > 0 ? total : 0.0;
            }
        } catch (Exception e) {
            log.error("Failed to calculate required amount for action: " + actionType, e);
            throw new IllegalArgumentException("Lỗi tính toán số tiền: " + e.getMessage());
        }
        
        throw new IllegalArgumentException("Unsupported action type for calculating amount: " + actionType);
    }

    /**
     * Pre-Validate the payload before generating payment link.
     */
    @Transactional
    public PaymentOrder initializePaymentOrder(PaymentActionRequest request, String orderCode, String currentUserEmail) {
        // 1. Validation Logic
        String actionType = request.getActionType();
        Map<String, Object> payload = request.getPayload();

        try {
            if ("CREATE_RESERVATION".equals(actionType)) {
                CreateReservationRequest createReq = objectMapper.convertValue(payload, CreateReservationRequest.class);
                reservationService.validateCreateReservation(createReq);
            } else if ("CREATE_MONTHLY_TICKET".equals(actionType)) {
                monthlyTicketService.validateCreateTicket(payload);
            } else if ("RENEW_MONTHLY_TICKET".equals(actionType)) {
                Long ticketId = Long.valueOf(payload.get("id").toString());
                int duration = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString()) : 1;
                monthlyTicketService.validateRenewTicket(ticketId, duration);
            } else if ("CHECKOUT".equals(actionType)) {
                if (payload.get("rfid") == null && payload.get("plateNumber") == null)
                    throw new IllegalArgumentException("RFID or Plate Number is required for Checkout");
            } else if ("RESOLVE_INCIDENT".equals(actionType)) {
                if (payload.get("incidentId") == null) {
                    throw new IllegalArgumentException("Incident ID is required for resolving incident");
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Validation failed before payment: " + e.getMessage());
        }

        // 2. Save PaymentOrder
        String payloadJson = "{}";
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
        }

        Long currentUserId = null;
        if (currentUserEmail != null) {
            User u = userRepository.findByEmail(currentUserEmail).orElse(null);
            if (u != null) {
                currentUserId = u.getId();
            }
        }

        PaymentOrder order = PaymentOrder.builder()
                .orderCode(orderCode)
                .amount(BigDecimal.valueOf(request.getAmount()))
                .status("PENDING")
                .paymentMethod(request.getGateway())
                .actionType(actionType)
                .payload(payloadJson)
                .userId(currentUserId)
                .build();

        return paymentOrderRepository.save(order);
    }

    /**
     * Execute business logic AFTER payment is successful.
     * If execution fails, create a RefundRequest.
     */
    @Transactional
    public PaymentExecutionResponse executeAction(String orderCode) {
        int updated = paymentOrderRepository.updateStatusIfMatch(orderCode, "PAID", "PROCESSING");
        if (updated == 0) {
            PaymentOrder order = paymentOrderRepository.findByOrderCode(orderCode).orElse(null);
            if (order != null && "COMPLETED".equals(order.getStatus())) {
                return new PaymentExecutionResponse(true, "COMPLETED", "Action executed successfully (cached)", null);
            }
            if (order != null && "PROCESSING".equals(order.getStatus())) {
                return new PaymentExecutionResponse(true, "PROCESSING", "Action is currently being executed by another request", null);
            }
            return new PaymentExecutionResponse(false, "FAILED", "Order is not in PAID status (current: " + (order != null ? order.getStatus() : "null") + ")", null);
        }

        PaymentOrder order = paymentOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment Order not found"));

        try {
            // Re-parse payload
            Map<String, Object> payload = objectMapper.readValue(order.getPayload(),
                    new TypeReference<Map<String, Object>>() {
                    });
            
            // Inject payment details into payload for subsequent services to record Transactions
            payload.put("paymentOrderId", order.getId());
            payload.put("paymentMethod", order.getPaymentMethod());
            
            Object resultData = null;

            org.springframework.security.core.context.SecurityContext originalContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
            try {
                if (order.getUserId() != null) {
                    com.pbms.modules.identity.domain.User u = userRepository.findById(order.getUserId()).orElse(null);
                    if (u != null) {
                        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = java.util.Collections.<org.springframework.security.core.GrantedAuthority>singletonList(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + u.getRole()));
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
                                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(u.getEmail(), null, authorities);
                        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }

                if ("CREATE_RESERVATION".equals(order.getActionType())) {
                CreateReservationRequest createReq = objectMapper.convertValue(payload, CreateReservationRequest.class);
                java.time.LocalDateTime entry = createReq.getExpectedEntryTime();
                if (entry.isBefore(com.pbms.common.utils.TimeProvider.now())) {
                    throw new IllegalArgumentException("Thời gian bắt đầu đặt chỗ đã trôi qua do thanh toán quá trễ. Giao dịch sẽ bị hủy và số tiền thanh toán sẽ được tự động hoàn lại.");
                }
                BigDecimal currentFee = reservationService.previewPrice(createReq.getVehicleTypeId(), entry, createReq.getExpectedDurationMinutes());
                if (currentFee.compareTo(order.getAmount()) > 0) {
                    throw new IllegalArgumentException("Phí dịch vụ đặt chỗ đã tăng so với lúc tạo yêu cầu. Số tiền thanh toán sẽ được tự động hoàn lại.");
                }
                resultData = reservationService.createReservation(createReq);
            } else if ("CREATE_MONTHLY_TICKET".equals(order.getActionType())) {
                Long vehicleTypeId = payload.get("vehicleTypeId") != null ? Long.parseLong(payload.get("vehicleTypeId").toString()) : null;
                int duration = 1;
                if (payload.get("durationMonths") != null) {
                    duration = Integer.parseInt(payload.get("durationMonths").toString());
                } else if (payload.get("duration") != null) {
                    duration = Integer.parseInt(payload.get("duration").toString());
                }
                PricingPolicy policy = pricingPolicyRepository.findByVehicleTypeIdAndStatus(vehicleTypeId, "ACTIVE")
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chính sách giá"));
                BigDecimal currentTotal = calculateMonthlyTicketFee(policy, duration);
                if (currentTotal.compareTo(order.getAmount()) > 0) {
                    throw new IllegalArgumentException("Giá vé tháng đã tăng so với lúc tạo yêu cầu. Số tiền thanh toán sẽ được tự động hoàn lại.");
                }
                resultData = monthlyTicketService.createTicket(payload);
            } else if ("RENEW_MONTHLY_TICKET".equals(order.getActionType())) {
                Long ticketId = Long.valueOf(payload.get("id").toString());
                int duration = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString()) : 1;
                MonthlyTicket ticket = monthlyTicketRepository.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
                PricingPolicy policy = pricingPolicyRepository.findByVehicleTypeIdAndStatus(ticket.getVehicleType().getId(), "ACTIVE")
                        .orElseThrow(() -> new IllegalArgumentException("No active pricing policy found"));
                BigDecimal currentTotal = calculateMonthlyTicketFee(policy, duration);
                if (currentTotal.compareTo(order.getAmount()) > 0) {
                    throw new IllegalArgumentException("Giá vé tháng đã tăng so với lúc tạo yêu cầu. Số tiền thanh toán sẽ được tự động hoàn lại.");
                }
                resultData = monthlyTicketService.renewTicket(ticketId, duration, order.getPaymentMethod(), order.getId());
            } else if ("CHECKOUT".equals(order.getActionType())) {
                // Typically Check-out updates the session
                com.pbms.modules.operation.dto.CheckOutRequestDTO checkoutReq = objectMapper.convertValue(payload,
                        com.pbms.modules.operation.dto.CheckOutRequestDTO.class);
                checkoutReq.setPaymentMethod(order.getPaymentMethod()); // ensure it reflects the gateway
                resultData = gateOperationService.processCheckOut(checkoutReq);
            } else if ("RESOLVE_INCIDENT".equals(order.getActionType())) {
                Long incidentId = Long.valueOf(payload.get("incidentId").toString());
                String resolutionNotes = payload.get("resolutionNotes") != null ? payload.get("resolutionNotes").toString() : null;
                String resolutionImageUrl = payload.get("resolutionImageUrl") != null ? payload.get("resolutionImageUrl").toString() : null;
                String uploadedPicOutUrl = payload.get("uploadedPicOutUrl") != null ? payload.get("uploadedPicOutUrl").toString() : null;
                BigDecimal parkingFee = payload.get("parkingFee") != null ? new BigDecimal(payload.get("parkingFee").toString()) : null;
                BigDecimal penaltyFee = payload.get("penaltyFee") != null ? new BigDecimal(payload.get("penaltyFee").toString()) : null;
                BigDecimal discountAmount = payload.get("discountAmount") != null ? new BigDecimal(payload.get("discountAmount").toString()) : null;
                
                String checkoutToken = payload.get("checkoutToken") != null ? payload.get("checkoutToken").toString() : null;
                com.pbms.modules.incident.service.IncidentService incidentService = applicationContext.getBean(com.pbms.modules.incident.service.IncidentService.class);
                resultData = incidentService.resolveIncident(incidentId, resolutionNotes, resolutionImageUrl, uploadedPicOutUrl, parkingFee, penaltyFee, discountAmount, order.getPaymentMethod(), order.getId(), checkoutToken);
            } else {
                throw new UnsupportedOperationException("Unknown action type: " + order.getActionType());
            }

            // Success -> Mark as COMPLETED
            order.setStatus("COMPLETED");
            paymentOrderRepository.save(order);
            return new PaymentExecutionResponse(true, "COMPLETED", "Action executed successfully", resultData);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.setContext(originalContext);
            }

        } catch (Exception e) {
            // Rethrow to let the transaction roll back
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public PaymentExecutionResponse processRefundForFailedAction(String orderCode, String errorMessage, String currentUserEmail) {
        PaymentOrder order = paymentOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment Order not found"));

        if (!"PAID".equals(order.getStatus()) && !"FAILED".equals(order.getStatus()) && !"PROCESSING".equals(order.getStatus())) {
            return new PaymentExecutionResponse(false, "FAILED",
                    "Cannot refund order in status: " + order.getStatus(), null);
        }

        if ("FAILED".equals(order.getStatus())) {
            return new PaymentExecutionResponse(false, "REFUND_INITIATED", "Already refunded", null);
        }

        // System Failure -> Fallback to Refund
        order.setStatus("FAILED");
        paymentOrderRepository.save(order);

        User user = null;
        if (order.getUserId() != null) {
            user = userRepository.findById(order.getUserId()).orElse(null);
        }
        if (user == null && currentUserEmail != null && !currentUserEmail.equals("anonymousUser")) {
            user = userRepository.findByEmail(currentUserEmail).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByEmail("systemadministratorweb@gmail.com").orElseThrow();
        }

        RefundRequest refundRequest = RefundRequest.builder()
                .user(user)
                .referenceType("FAILED_TRANSACTION")
                .referenceId(order.getOrderCode())
                .paidAmount(order.getAmount())
                .penaltyFee(BigDecimal.ZERO)
                .refundAmount(order.getAmount())
                .status("PENDING")
                .rejectReason("System Error during execution: " + errorMessage)
                .build();

        refundRequestRepository.save(refundRequest);

        return new PaymentExecutionResponse(false, "REFUND_INITIATED",
                "System could not complete the action. Your payment of " + order.getAmount()
                        + " has been queued for a full refund. Error: " + errorMessage,
                null);
    }
}
