package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.RfidCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RfidCardRepository extends JpaRepository<RfidCard, Long> {
    Optional<RfidCard> findByCardCode(String cardCode);
    List<RfidCard> findByStatus(String status);
}

