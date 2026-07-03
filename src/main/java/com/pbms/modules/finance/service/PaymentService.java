package com.pbms.modules.finance.service;

import com.pbms.modules.finance.domain.PaymentOrder;
import com.pbms.modules.finance.dto.PaymentActionRequest;
import com.pbms.modules.finance.dto.PaymentExecutionResponse;
import com.pbms.modules.finance.factory.PaymentFactory;
import com.pbms.modules.finance.repository.PaymentOrderRepository;
import com.pbms.modules.finance.strategy.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // [PBMS-76] Inject Factory và Repository vào đây thay vì để ở Controller
    private final PaymentFactory paymentFactory;
    private final PaymentOrderRepository paymentOrderRepository;

    @Transactional
    public PaymentExecutionResponse processPayment(PaymentActionRequest request) {
        // 1. Lấy Strategy tương ứng dựa vào gateway client chọn (VNPAY, PAYOS, PAYPAL)
        PaymentStrategy strategy = paymentFactory.getStrategy(request.getGateway());

        // 2. Sinh mã đơn hàng ngẫu nhiên & duy nhất
        String orderCode = "ORDER_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if ("PAYPAL".equalsIgnoreCase(request.getGateway())) {
            orderCode = "order_" + System.currentTimeMillis(); // Đặc thù của PayPal như code bạn viết
        }

        // 3. Gọi cổng thanh toán để sinh link (gọi xuống class Strategy)
        String paymentUrl = strategy.generatePaymentUrl(request.getAmount().doubleValue(), orderCode);

        // 4. Lưu đơn hàng nháp xuống Database với trạng thái PENDING
        PaymentOrder order = PaymentOrder.builder()
                .orderCode(orderCode)
                .amount(request.getAmount())
                .status("PENDING")
                .paymentMethod(request.getGateway())
                // TODO: Dựa vào request.getReferenceType() để set thuộc tính reservation hoặc monthlyTicket tương ứng
                .build();
        paymentOrderRepository.save(order);

        // 5. Đóng gói kết quả trả về cho Controller
        return PaymentExecutionResponse.builder()
                .paymentUrl(paymentUrl)
                .orderCode(orderCode)
                .amount(request.getAmount())
                .gateway(request.getGateway())
                .status("PENDING")
                .referenceId(request.getReferenceId())
                .referenceType(request.getReferenceType())
                .build();
    }
    @Transactional
    public boolean capturePayPalOrder(String token) {
        // 1. Lấy Strategy của riêng PayPal thông qua Factory
        PaymentStrategy strategy = paymentFactory.getStrategy("PAYPAL");

        // 2. Gọi API của PayPal để capture tiền về tài khoản
        boolean success = strategy.captureOrder(token);

        if (success) {
            log.info("Successfully captured PayPal order for token: {}", token);

            // 3. Cập nhật trạng thái đơn hàng trong Database
            // Lưu ý: Thông thường token của PayPal sẽ gắn liền với orderCode bạn đã lưu lúc đầu.
            // Nếu token chính là orderCode, bạn dùng hàm dưới đây:
            paymentOrderRepository.findByOrderCode(token)
                    .ifPresent(order -> {
                        order.setStatus("PAID");
                        paymentOrderRepository.save(order);
                        log.info("Updated order status {} to PAID", order.getOrderCode());

                        // [PBMS-76] Bổ sung: Gọi sang ReservationService hoặc MonthlyTicketService để kích hoạt vé cho khách
                    });
        } else {
            log.warn("Failed to capture PayPal order for token: {}", token);
        }

        return success;
    }
}