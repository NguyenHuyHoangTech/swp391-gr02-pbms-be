/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: JPA Repository cho entity RfidCard.
 * @Dependencies:
 * - RfidCard (Local)
 */
package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.RfidCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface RfidCardRepository extends JpaRepository<RfidCard, Long> {
    Optional<RfidCard> findByCardId(String cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RfidCard> findByCardCode(String cardCode);

    List<RfidCard> findByStatus(String status);
}
