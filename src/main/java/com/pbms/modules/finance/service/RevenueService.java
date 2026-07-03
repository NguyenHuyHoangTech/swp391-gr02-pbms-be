package com.pbms.modules.finance.service;

import com.pbms.modules.finance.dto.RevenueRecordDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RevenueService {

    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<RevenueRecordDTO> getRevenueDashboardData(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT " +
                "    CONVERT(VARCHAR(10), ps.time_out, 120) AS date_str, " +
                "    COALESCE(vt.type_name, 'Unclear') AS vehicleType," +
                "    COALESCE(g.gate_name, 'N/A') AS gateName, " +
                "    CASE " +
                "        WHEN ps.penalty_fee > 0 THEN 'Penalty' " +
                "        WHEN ps.reservation_id IS NOT NULL THEN 'Reservation' " +
                "        WHEN mt.id IS NOT NULL THEN 'Monthly Ticket' " +
                "        ELSE 'Standard Ticket' " +
                "    END AS revenueSource, " +
                "    COALESCE(t.payment_method, 'CASH') AS paymentMethod, " +
                "    SUM(ps.total_fee) AS totalRevenue, " +
                "    COUNT(ps.id) AS totalTransactions " +
                "FROM parking_sessions ps " +
                "LEFT JOIN vehicle_types vt ON ps.vehicle_type_id = vt.id " +
                "LEFT JOIN gates g ON ps.gate_out_id = g.id " +
                "LEFT JOIN monthly_tickets mt ON ps.plate = mt.plate AND mt.status = 'ACTIVE' " +
                "LEFT JOIN transactions t ON ps.id = t.parking_session_id AND t.status = 'SUCCESS' " +
                "WHERE ps.status = 'COMPLETED' " +
                "  AND CAST(ps.time_out AS DATE) >= :startDate " +
                "  AND CAST(ps.time_out AS DATE) <= :endDate " +
                "GROUP BY " +
                "    CONVERT(VARCHAR(10), ps.time_out, 120), " +
                "    COALESCE(vt.type_name, 'Unclear')," +
                "    COALESCE(g.gate_name, 'N/A'), " +
                "    CASE " +
                "        WHEN ps.penalty_fee > 0 THEN 'Penalty' " +
                "        WHEN ps.reservation_id IS NOT NULL THEN 'Reservation' " +
                "        WHEN mt.id IS NOT NULL THEN 'Monthly Ticket' " +
                "        ELSE 'Standard Ticket' " +
                "    END, " +
                "    COALESCE(t.payment_method, 'CASH')" +
                " UNION ALL " +
                "SELECT " +
                "    CONVERT(VARCHAR(10), t.created_at, 120) AS date_str, " +
                "    'Unclear' AS vehicleType," +
                "    N'N/A' AS gateName, " +
                "    'Reservation Cancellation Penalty' AS revenueSource, " +
                "    t.payment_method AS paymentMethod, " +
                "    SUM(t.amount) AS totalRevenue, " +
                "    COUNT(t.id) AS totalTransactions " +
                "FROM transactions t " +
                "WHERE t.status = 'SUCCESS' " +
                "  AND t.transaction_reference LIKE 'PENALTY-RES-%' " +
                "  AND CAST(t.created_at AS DATE) >= :startDate " +
                "  AND CAST(t.created_at AS DATE) <= :endDate " +
                "GROUP BY " +
                "    CONVERT(VARCHAR(10), t.created_at, 120), " +
                "    t.payment_method";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();
        List<RevenueRecordDTO> dtoList = new ArrayList<>();

        for (Object[] row : results) {
            String dateStr = (String) row[0];
            String vehicleType = (String) row[1];
            String gateName = (String) row[2];
            String revenueSource = (String) row[3];
            String paymentMethod = (String) row[4];
            BigDecimal totalRevenue = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
            Long totalTransactions = row[6] != null ? ((Number) row[6]).longValue() : 0L;

            dtoList.add(RevenueRecordDTO.builder()
                    .date(dateStr)
                    .vehicleType(vehicleType)
                    .gateName(gateName)
                    .revenueSource(revenueSource)
                    .paymentMethod(paymentMethod)
                    .totalRevenue(totalRevenue)
                    .totalTransactions(totalTransactions)
                    .build());
        }

        return dtoList;
    }
}

