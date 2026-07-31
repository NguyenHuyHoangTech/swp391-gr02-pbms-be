package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    List<RefundRequest> findByUserId(Long userId);
    List<RefundRequest> findByStatus(String status);
    java.util.Optional<RefundRequest> findByReferenceTypeAndReferenceId(String type, String id);
}

