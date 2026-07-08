package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    @Modifying
    @Query(value = "UPDATE transactions SET created_at = :createdAt WHERE id = :id", nativeQuery = true)
    void updateCreatedAtNative(@Param("id") Long id, @Param("createdAt") java.time.LocalDateTime createdAt);
}

