package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    // Tìm đơn hàng theo mã orderCode (dùng để xác nhận khi cổng thanh toán gọi về)
    Optional<PaymentOrder> findByOrderCode(String orderCode);
}

