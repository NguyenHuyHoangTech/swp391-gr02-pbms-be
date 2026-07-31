// Author: Võ Trung Hiếu
package com.pbms.modules.finance.service;

import com.pbms.modules.finance.dto.RevenueRecordDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
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

    private static final String BASE_SQL = """
        SELECT 
            CONVERT(VARCHAR(10), ps.time_out, 120) AS date_str, 
            COALESCE(vt.type_name, 'Unclear') AS vehicleType,
            COALESCE(g.gate_name, 'N/A') AS gateName, 
            v.revenueSource, 
            COALESCE(t.payment_method, 'CASH') AS paymentMethod, 
            SUM(v.revenueAmount) AS totalRevenue, 
            COUNT(ps.id) AS totalTransactions 
        FROM parking_sessions ps 
        LEFT JOIN vehicle_types vt ON ps.vehicle_type_id = vt.id 
        LEFT JOIN gates g ON ps.gate_out_id = g.id 
        LEFT JOIN monthly_tickets mt ON ps.plate = mt.plate_number AND mt.status != 'CANCELLED' AND ps.time_in BETWEEN mt.valid_from AND mt.valid_until
        LEFT JOIN reservations r ON ps.reservation_id = r.id
        OUTER APPLY (
            SELECT TOP 1 payment_method 
            FROM transactions trx 
            WHERE trx.parking_session_id = ps.id AND trx.status = 'SUCCESS' 
            ORDER BY trx.created_at DESC
        ) t 
        CROSS APPLY (
            VALUES 
                (CASE 
                    WHEN ps.reservation_id IS NOT NULL THEN 'Reservation' 
                    WHEN mt.id IS NOT NULL THEN 'Monthly Ticket' 
                    ELSE 'Standard Ticket' 
                 END, COALESCE(ps.parking_fee, 0) + COALESCE(r.reservation_fee, 0)),
                ('Overtime Surcharge', ps.overtime_fee),
                ('Penalty', ps.penalty_fee)
        ) AS v(revenueSource, revenueAmount)
        WHERE ps.status = 'COMPLETED' 
          AND v.revenueAmount > 0
          AND CAST(ps.time_out AS DATE) >= :startDate 
          AND CAST(ps.time_out AS DATE) <= :endDate 
        GROUP BY 
            CONVERT(VARCHAR(10), ps.time_out, 120), 
            COALESCE(vt.type_name, 'Unclear'),
            COALESCE(g.gate_name, 'N/A'), 
            v.revenueSource, 
            COALESCE(t.payment_method, 'CASH')
        UNION ALL 
        SELECT 
            CONVERT(VARCHAR(10), t.created_at, 120) AS date_str, 
            'Unclear' AS vehicleType,
            N'N/A' AS gateName, 
            'Cancel Penalty' AS revenueSource, 
            t.payment_method AS paymentMethod, 
            SUM(t.amount) AS totalRevenue, 
            COUNT(t.id) AS totalTransactions 
        FROM transactions t 
        WHERE t.status = 'SUCCESS' 
          AND t.transaction_reference LIKE 'PENALTY-RES-%' 
          AND CAST(t.created_at AS DATE) >= :startDate 
          AND CAST(t.created_at AS DATE) <= :endDate 
        GROUP BY 
            CONVERT(VARCHAR(10), t.created_at, 120), 
            t.payment_method
        UNION ALL 
        SELECT 
            CONVERT(VARCHAR(10), t.created_at, 120) AS date_str, 
            COALESCE(vt.type_name, 'Unclear') AS vehicleType,
            N'N/A' AS gateName, 
            'Monthly Ticket Sales' AS revenueSource, 
            t.payment_method AS paymentMethod, 
            SUM(t.amount) AS totalRevenue, 
            COUNT(t.id) AS totalTransactions 
        FROM transactions t 
        INNER JOIN monthly_tickets mt ON t.monthly_ticket_id = mt.id
        LEFT JOIN vehicle_types vt ON mt.vehicle_type_id = vt.id
        WHERE t.status = 'SUCCESS' 
          AND t.transaction_reference LIKE 'TXN-MT-%' 
          AND CAST(t.created_at AS DATE) >= :startDate 
          AND CAST(t.created_at AS DATE) <= :endDate 
        GROUP BY 
            CONVERT(VARCHAR(10), t.created_at, 120), 
            COALESCE(vt.type_name, 'Unclear'),
            t.payment_method
        """;

    private static final String TABLE_SQL = """
        SELECT 
            CONVERT(VARCHAR(19), ps.time_out, 120) AS checkoutTime, 
            ps.plate,
            COALESCE(vt.type_name, 'Unclear') AS vehicleType,
            COALESCE(g.gate_name, 'N/A') AS gateName, 
            COALESCE(r.reservation_fee, 0) AS reservationFee,
            COALESCE(ps.parking_fee, 0) AS baseFee,
            COALESCE(ps.overtime_fee, 0) AS overtimeFee,
            COALESCE(ps.penalty_fee, 0) AS penaltyFee,
            (COALESCE(r.reservation_fee, 0) + COALESCE(ps.parking_fee, 0) + COALESCE(ps.overtime_fee, 0) + COALESCE(ps.penalty_fee, 0)) AS totalFee,
            COALESCE(t.payment_method, 'CASH') AS paymentMethod
        FROM parking_sessions ps 
        LEFT JOIN vehicle_types vt ON ps.vehicle_type_id = vt.id 
        LEFT JOIN gates g ON ps.gate_out_id = g.id 
        LEFT JOIN reservations r ON ps.reservation_id = r.id
        OUTER APPLY (
            SELECT TOP 1 payment_method 
            FROM transactions trx 
            WHERE trx.parking_session_id = ps.id AND trx.status = 'SUCCESS' 
            ORDER BY trx.created_at DESC
        ) t
        WHERE ps.status = 'COMPLETED' 
          AND (COALESCE(r.reservation_fee, 0) + COALESCE(ps.parking_fee, 0) + COALESCE(ps.overtime_fee, 0) + COALESCE(ps.penalty_fee, 0)) > 0
          AND CAST(ps.time_out AS DATE) >= :startDate 
          AND CAST(ps.time_out AS DATE) <= :endDate 
        UNION ALL
        SELECT 
            CONVERT(VARCHAR(19), t.created_at, 120) AS checkoutTime, 
            N'N/A' AS plate,
            'Unclear' AS vehicleType,
            N'N/A' AS gateName, 
            0 AS reservationFee,
            0 AS baseFee,
            0 AS overtimeFee,
            t.amount AS penaltyFee,
            t.amount AS totalFee,
            t.payment_method AS paymentMethod
        FROM transactions t 
        WHERE t.status = 'SUCCESS' 
          AND t.transaction_reference LIKE 'PENALTY-RES-%' 
          AND CAST(t.created_at AS DATE) >= :startDate 
          AND CAST(t.created_at AS DATE) <= :endDate 
        UNION ALL
        SELECT 
            CONVERT(VARCHAR(19), t.created_at, 120) AS checkoutTime, 
            mt.plate_number AS plate,
            COALESCE(vt.type_name, 'Unclear') AS vehicleType,
            N'N/A' AS gateName, 
            0 AS reservationFee,
            t.amount AS baseFee,
            0 AS overtimeFee,
            0 AS penaltyFee,
            t.amount AS totalFee,
            t.payment_method AS paymentMethod
        FROM transactions t 
        INNER JOIN monthly_tickets mt ON t.monthly_ticket_id = mt.id
        LEFT JOIN vehicle_types vt ON mt.vehicle_type_id = vt.id
        WHERE t.status = 'SUCCESS' 
          AND t.transaction_reference LIKE 'TXN-MT-%' 
          AND CAST(t.created_at AS DATE) >= :startDate 
          AND CAST(t.created_at AS DATE) <= :endDate 
        """;

    /**
     * [1] Fetch Dashboard Aggregated Data
     * Retrieves all revenue grouped by date, vehicle type, gate, and payment method for the given date range.
     * Used mainly for constructing Charts and KPIs on the UI.
     * @param startDate The start date of the reporting period
     * @param endDate The end date of the reporting period
     * @return List of aggregated revenue records
     */
    @Transactional(readOnly = true)
    public List<RevenueRecordDTO> getRevenueDashboardData(LocalDate startDate, LocalDate endDate) {
        Query query = entityManager.createNativeQuery(BASE_SQL);
        query.setParameter("startDate", startDate.toString());
        query.setParameter("endDate", endDate.toString());

        @SuppressWarnings("unchecked")
        List<Object[]> rawList = query.getResultList();
        return mapToDTOList(rawList);
    }

    /**
     * [2] Fetch Paginated Data for Data Table
     * Retrieves the same aggregated data but with Server-side Pagination.
     * Essential for handling large date ranges without crashing the browser.
     * @param startDate The start date
     * @param endDate The end date
     * @param page Current page number (1-indexed)
     * @param size Number of items per page
     * @return Page object containing the data slice and total count
     */
    @Transactional(readOnly = true)
    public Page<com.pbms.modules.finance.dto.RevenueTransactionDTO> getRevenueTableData(LocalDate startDate, LocalDate endDate, int page, int size) {
        // 1. Get count
        String countSql = "SELECT COUNT(*) FROM (" + TABLE_SQL + ") AS raw_data";
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("startDate", startDate.toString());
        countQuery.setParameter("endDate", endDate.toString());
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        // 2. Get paginated data
        String paginatedSql = "SELECT * FROM (" + TABLE_SQL + ") AS raw_data ORDER BY checkoutTime DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";
        Query query = entityManager.createNativeQuery(paginatedSql);
        query.setParameter("startDate", startDate.toString());
        query.setParameter("endDate", endDate.toString());
        query.setParameter("offset", (page - 1) * size);
        query.setParameter("limit", size);

        @SuppressWarnings("unchecked")
        List<Object[]> rawList = query.getResultList();
        List<com.pbms.modules.finance.dto.RevenueTransactionDTO> dtoList = mapToTransactionDTOList(rawList);

        return new PageImpl<>(dtoList, PageRequest.of(page - 1, size), totalElements);
    }

    /**
     * [3] Export Revenue Data to CSV
     * Streams the entire dataset for the date range directly to the HTTP response.
     * Uses UTF-8 BOM encoding so it opens correctly in MS Excel without garbled Vietnamese text.
     */
    @Transactional(readOnly = true)
    public StreamingResponseBody exportRevenueCsv(LocalDate startDate, LocalDate endDate) {
        return outputStream -> {
            try {
                // Write BOM for Excel UTF-8 support
                outputStream.write(0xEF);
                outputStream.write(0xBB);
                outputStream.write(0xBF);

                try (PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8))) {
                    writer.println("Ngày giờ ra;Biển số;Loại xe;Cổng ra;Tiền đặt chỗ;Tiền vé;Tiền lố giờ;Tiền phạt;Tổng thu;Thanh toán");

                    Query query = entityManager.createNativeQuery("SELECT * FROM (" + TABLE_SQL + ") AS raw_data ORDER BY checkoutTime DESC");
                    query.setParameter("startDate", startDate.toString());
                    query.setParameter("endDate", endDate.toString());

                    // Unwrap Hibernate query and set fetch size to stream data without OOM
                    @SuppressWarnings("unchecked")
                    org.hibernate.query.Query<Object[]> hibernateQuery = query.unwrap(org.hibernate.query.Query.class);
                    hibernateQuery.setFetchSize(500);

                    java.util.stream.Stream<Object[]> stream = hibernateQuery.stream();

                    stream.forEach(row -> {
                        String checkoutTime = (String) row[0];
                        String plate = (String) row[1];
                        String vehicleType = (String) row[2];
                        String gateName = (String) row[3];
                        BigDecimal reservationFee = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                        BigDecimal baseFee = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
                        BigDecimal overtimeFee = row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO;
                        BigDecimal penaltyFee = row[7] != null ? new BigDecimal(row[7].toString()) : BigDecimal.ZERO;
                        BigDecimal totalFee = row[8] != null ? new BigDecimal(row[8].toString()) : BigDecimal.ZERO;
                        String paymentMethod = (String) row[9];

                        writer.printf("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s\n",
                                checkoutTime, plate, vehicleType, gateName, reservationFee, baseFee, overtimeFee, penaltyFee, totalFee, paymentMethod);
                    });
                }
            } catch (Exception e) {
                log.error("Error exporting CSV: ", e);
                throw new RuntimeException("Failed to export CSV", e);
            }
        };
    }

    private List<com.pbms.modules.finance.dto.RevenueTransactionDTO> mapToTransactionDTOList(List<Object[]> results) {
        List<com.pbms.modules.finance.dto.RevenueTransactionDTO> dtoList = new ArrayList<>();
        for (Object[] row : results) {
            dtoList.add(com.pbms.modules.finance.dto.RevenueTransactionDTO.builder()
                    .checkoutTime((String) row[0])
                    .plate((String) row[1])
                    .vehicleType((String) row[2])
                    .gateName((String) row[3])
                    .reservationFee(row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO)
                    .baseFee(row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO)
                    .overtimeFee(row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO)
                    .penaltyFee(row[7] != null ? new BigDecimal(row[7].toString()) : BigDecimal.ZERO)
                    .totalFee(row[8] != null ? new BigDecimal(row[8].toString()) : BigDecimal.ZERO)
                    .paymentMethod((String) row[9])
                    .build());
        }
        return dtoList;
    }

    private List<RevenueRecordDTO> mapToDTOList(List<Object[]> results) {
        List<RevenueRecordDTO> dtoList = new ArrayList<>();
        for (Object[] row : results) {
            dtoList.add(RevenueRecordDTO.builder()
                    .date((String) row[0])
                    .vehicleType((String) row[1])
                    .gateName((String) row[2])
                    .revenueSource((String) row[3])
                    .paymentMethod((String) row[4])
                    .totalRevenue(row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO)
                    .totalTransactions(row[6] != null ? ((Number) row[6]).longValue() : 0L)
                    .build());
        }
        return dtoList;
    }
}
