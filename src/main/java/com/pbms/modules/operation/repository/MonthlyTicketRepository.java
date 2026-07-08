package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.MonthlyTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonthlyTicketRepository extends JpaRepository<MonthlyTicket, Long> {
}
