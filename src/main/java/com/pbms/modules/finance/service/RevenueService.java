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

    /**
     * [SQL_BASE_001] Câu lệnh SQL truy vấn tổng hợp doanh thu theo ngày, cổng, loại xe, nguồn thu và phương thức thanh toán.
     * Cấu trúc câu lệnh gồm 2 phần liên kết bằng UNION ALL:
     * 
     * Phân đoạn 1 (Truy vấn doanh thu từ phiên đỗ xe):
     * - Bảng tham gia: parking_sessions, vehicle_types, gates, monthly_tickets, transactions.
     * - Chức năng: 
     *   + Select các trường và quy đổi ngày giờ check-out sang định dạng YYYY-MM-DD.
     *   + Sử dụng CROSS APPLY (VALUES ...) để tách doanh thu từ 3 nguồn: 
     *     * Vé lượt/vé tháng/đặt trước (từ ps.total_fee).
     *     * Phụ phí quá giờ (từ ps.overtime_fee).
     *     * Phí phạt phát sinh (từ ps.penalty_fee).
     *   + Điều kiện: ps.status = 'COMPLETED', doanh thu > 0, thời gian ra nằm trong khoảng lọc.
     *   + Group by để tính tổng tiền (SUM) và số giao dịch (COUNT).
     * 
     * Phân đoạn 2 (Truy vấn phạt hủy đặt chỗ):
     * - Bảng tham gia: transactions.
     * - Chức năng: Thống kê số tiền phạt hủy chỗ (các giao dịch thành công có reference bắt đầu bằng 'PENALTY-RES-%') theo ngày và phương thức thanh toán.
     */
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
        LEFT JOIN monthly_tickets mt ON ps.plate = mt.plate AND mt.status = 'ACTIVE' 
        LEFT JOIN transactions t ON ps.id = t.parking_session_id AND t.status = 'SUCCESS' 
        CROSS APPLY (
            VALUES 
                (CASE 
                    WHEN ps.reservation_id IS NOT NULL THEN 'Reservation' 
                    WHEN mt.id IS NOT NULL THEN 'Monthly Ticket' 
                    ELSE 'Standard Ticket' 
                 END, ps.total_fee),
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
        """;

    /**
     * [SQL_TABLE_002] Câu lệnh SQL truy vấn danh sách giao dịch doanh thu chi tiết (General Data Table).
     * - Bảng tham gia: parking_sessions, vehicle_types, gates, transactions.
     * - Chức năng: 
     *   + Lấy thời gian ra (check-out), biển số xe, loại xe, cổng ra.
     *   + Trích xuất các thành phần phí: phí gốc (baseFee), phí quá giờ (overtimeFee), phí phạt (penaltyFee).
     *   + Cộng dồn các phí thành tổng số tiền thu (totalFee).
     *   + Lấy phương thức thanh toán tương ứng.
     * - Điều kiện: Trạng thái phiên đỗ xe là 'COMPLETED' và tổng tiền thu lớn hơn 0, trong khoảng thời gian lọc.
     */
    private static final String TABLE_SQL = """
        SELECT 
            CONVERT(VARCHAR(19), ps.time_out, 120) AS checkoutTime, 
            ps.plate,
            COALESCE(vt.type_name, 'Unclear') AS vehicleType,
            COALESCE(g.gate_name, 'N/A') AS gateName, 
            COALESCE(ps.total_fee, 0) AS baseFee,
            COALESCE(ps.overtime_fee, 0) AS overtimeFee,
            COALESCE(ps.penalty_fee, 0) AS penaltyFee,
            (COALESCE(ps.total_fee, 0) + COALESCE(ps.overtime_fee, 0) + COALESCE(ps.penalty_fee, 0)) AS totalFee,
            COALESCE(t.payment_method, 'CASH') AS paymentMethod
        FROM parking_sessions ps 
        LEFT JOIN vehicle_types vt ON ps.vehicle_type_id = vt.id 
        LEFT JOIN gates g ON ps.gate_out_id = g.id 
        LEFT JOIN transactions t ON ps.id = t.parking_session_id AND t.status = 'SUCCESS' 
        WHERE ps.status = 'COMPLETED' 
          AND (COALESCE(ps.total_fee, 0) + COALESCE(ps.overtime_fee, 0) + COALESCE(ps.penalty_fee, 0)) > 0
          AND CAST(ps.time_out AS DATE) >= :startDate 
          AND CAST(ps.time_out AS DATE) <= :endDate 
        """;

    /**
     * [BE_FN_001] Lấy dữ liệu tổng hợp Doanh thu để hiển thị biểu đồ trên Dashboard (Master Dataset).
     * - API Endpoint: GET /api/v1/finance/revenue/dashboard
     * - Chức năng: Truy vấn toàn bộ dữ liệu trong khoảng thời gian (không phân trang), nhóm theo ngày, loại xe, cổng, nguồn thu.
     *
     * @param startDate Ngày bắt đầu báo cáo.
     * @param endDate Ngày kết thúc báo cáo.
     * @return Danh sách dữ liệu doanh thu dạng phẳng (Flat Dataset).
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
     * [BE_FN_002] Lấy dữ liệu Doanh thu phân trang (Server-side Pagination).
     * - API Endpoint: GET /api/v1/finance/revenue/table
     * - Chức năng: Tối ưu hóa hiệu năng khi xem dữ liệu trên Bảng (Data Table) bằng cách chỉ query 
     *   số lượng bản ghi tương ứng với kích thước trang hiện tại (OFFSET/LIMIT).
     *
     * @param startDate Ngày bắt đầu báo cáo.
     * @param endDate Ngày kết thúc báo cáo.
     * @param page Số trang hiện tại (bắt đầu từ 1).
     * @param size Số bản ghi trên mỗi trang.
     * @return Đối tượng Page chứa 1 phần dữ liệu doanh thu.
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
     * [BE_FN_003] Xuất toàn bộ dữ liệu Doanh thu ra file CSV.
     * - API Endpoint: GET /api/v1/finance/revenue/export
     * - Chức năng: Sử dụng StreamingResponseBody để đẩy trực tiếp dữ liệu (Streaming) vào luồng tải xuống thay vì 
     *   nạp tất cả vào RAM. Ghi thêm Byte Order Mark (BOM) để MS Excel hiển thị đúng Tiếng Việt (UTF-8).
     *
     * @param startDate Ngày bắt đầu báo cáo.
     * @param endDate Ngày kết thúc báo cáo.
     * @return Luồng dữ liệu file CSV.
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
                    writer.println("Ngày giờ ra;Biển số;Loại xe;Cổng ra;Tiền vé;Tiền lố giờ;Tiền phạt;Tổng thu;Thanh toán");

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
                        BigDecimal baseFee = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                        BigDecimal overtimeFee = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
                        BigDecimal penaltyFee = row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO;
                        BigDecimal totalFee = row[7] != null ? new BigDecimal(row[7].toString()) : BigDecimal.ZERO;
                        String paymentMethod = (String) row[8];

                        writer.printf("%s;%s;%s;%s;%s;%s;%s;%s;%s\n",
                                checkoutTime, plate, vehicleType, gateName, baseFee, overtimeFee, penaltyFee, totalFee, paymentMethod);
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
                    .baseFee(row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO)
                    .overtimeFee(row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO)
                    .penaltyFee(row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO)
                    .totalFee(row[7] != null ? new BigDecimal(row[7].toString()) : BigDecimal.ZERO)
                    .paymentMethod((String) row[8])
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
