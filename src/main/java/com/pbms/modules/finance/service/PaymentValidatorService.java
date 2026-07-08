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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * BƯỚC 1: Khởi tạo đơn hàng thanh toán (PaymentOrder).
     * Hàm này kiểm tra tính hợp lệ của dữ liệu đầu vào (Ví dụ: vé tháng có tồn tại không, biển số đúng không)
     * trước khi sinh ra URL thanh toán để tránh người dùng thanh toán xong nhưng lỗi dữ liệu.
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
     * BƯỚC 3: Thực thi logic nghiệp vụ sau khi đã thanh toán thành công (PAID).
     * Dựa vào actionType (CREATE_RESERVATION, CREATE_MONTHLY_TICKET, CHECKOUT),
     * hệ thống sẽ gọi các service tương ứng để thực hiện thao tác (tạo vé, mở cổng...).
     * Nếu lỗi xảy ra, transaction sẽ tự rollback.
     */
    @Transactional
    public PaymentExecutionResponse executeAction(String orderCode) {
        PaymentOrder order = paymentOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment Order not found"));

        if (!"PAID".equals(order.getStatus())) {
            return new PaymentExecutionResponse(false, "FAILED",
                    "Order is not in PAID status (current: " + order.getStatus() + ")", null);
        }

        try {
            // Re-parse payload
            Map<String, Object> payload = objectMapper.readValue(order.getPayload(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object resultData = null;

            if ("CREATE_RESERVATION".equals(order.getActionType())) {
                CreateReservationRequest createReq = objectMapper.convertValue(payload, CreateReservationRequest.class);
                resultData = reservationService.createReservation(createReq);
            } else if ("CREATE_MONTHLY_TICKET".equals(order.getActionType())) {
                resultData = monthlyTicketService.createTicket(payload);
            } else if ("RENEW_MONTHLY_TICKET".equals(order.getActionType())) {
                Long ticketId = Long.valueOf(payload.get("id").toString());
                int duration = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString())
                        : 1;
                resultData = monthlyTicketService.renewTicket(ticketId, duration);
            } else if ("CHECKOUT".equals(order.getActionType())) {
                // Typically Check-out updates the session
                com.pbms.modules.operation.dto.CheckOutRequestDTO checkoutReq = objectMapper.convertValue(payload,
                        com.pbms.modules.operation.dto.CheckOutRequestDTO.class);
                checkoutReq.setPaymentMethod(order.getPaymentMethod()); // ensure it reflects the gateway
                resultData = gateOperationService.processCheckOut(checkoutReq);
            } else {
                throw new UnsupportedOperationException("Unknown action type: " + order.getActionType());
            }

            // Success -> Mark as COMPLETED
            order.setStatus("COMPLETED");
            paymentOrderRepository.save(order);
            return new PaymentExecutionResponse(true, "COMPLETED", "Action executed successfully", resultData);

        } catch (Exception e) {
            // Rethrow to let the transaction roll back
            throw new RuntimeException(e);
        }
    }

    /**
     * Xử lý Hoàn Tiền Tự Động (Auto-Refund) khi thanh toán thành công nhưng lỗi hệ thống ở bước Thực thi.
     * Hàm này tạo một yêu cầu hoàn tiền (RefundRequest) để Admin có thể trả lại tiền cho người dùng.
     */
    @Transactional
    public PaymentExecutionResponse processRefundForFailedAction(String orderCode, String errorMessage, String currentUserEmail) {
        PaymentOrder order = paymentOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment Order not found"));

        if (!"PAID".equals(order.getStatus()) && !"FAILED".equals(order.getStatus())) {
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
                .cancelTime(com.pbms.common.utils.TimeProvider.now())
                .rejectReason("System Error during execution: " + errorMessage)
                .build();

        refundRequestRepository.save(refundRequest);

        return new PaymentExecutionResponse(false, "REFUND_INITIATED",
                "System could not complete the action. Your payment of " + order.getAmount()
                        + " has been queued for a full refund. Error: " + errorMessage,
                null);
    }
}
