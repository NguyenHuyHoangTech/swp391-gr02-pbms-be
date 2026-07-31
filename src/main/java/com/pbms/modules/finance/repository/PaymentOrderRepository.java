// Author: Võ Trung Hiếu
package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderCode(String orderCode);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE PaymentOrder p SET p.status = :newStatus WHERE p.orderCode = :orderCode AND p.status = :oldStatus")
    int updateStatusIfMatch(@org.springframework.data.repository.query.Param("orderCode") String orderCode, @org.springframework.data.repository.query.Param("oldStatus") String oldStatus, @org.springframework.data.repository.query.Param("newStatus") String newStatus);

    long countByStatus(String status);
}
