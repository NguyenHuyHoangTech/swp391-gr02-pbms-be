package com.pbms.modules.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

     /**
     * [BE_FN_001] Hàm chính tính toán các chỉ số cho Báo cáo Vận hành (Operational Overview).
     * Nhiệm vụ:
     * 1. Đếm tổng số lượt xe vào (Total Check-ins) trong ngày.
     * 2. Đếm tổng số lượt xe ra (Total Check-outs) trong ngày.
     * 3. Tính toán Sức chứa thực tế (Live Capacity) của từng loại xe, phân chia rõ ràng
     *    giữa sức chứa của Khách vãng lai (Walk-in Zone) và Khách vé tháng (Monthly Zone).
     * 
     * @param date Ngày cần lấy báo cáo (thường là ngày hiện tại)
     * @return Map chứa các chỉ số vận hành tổng hợp
     */
    public Map<String, Object> getOperationalOverview(LocalDate date) {
        if (date == null)
            date = com.pbms.common.utils.TimeProvider.now().toLocalDate();

        /**
         * [SQL_STATS_001] Truy vấn đếm sức chứa tĩnh và số vị trí đang bị chiếm dụng (Occupied) theo loại xe.
         * - Các bảng tham gia: vehicle_types v, slots s, zones z, parking_sessions ps, monthly_tickets mt, incident_tickets it.
         * - Phân tích chức năng:
         *   + capacity_walk_in: Đếm số slot thuộc các Zone có thể đỗ xe vãng lai (function_type = 'WALK_IN' hoặc 'ALL').
         *   + capacity_monthly: Đếm số slot thuộc các Zone dành cho vé tháng (function_type = 'MONTHLY' hoặc 'ALL').
         *   + capacity_total: Tổng số slot hoạt động của loại phương tiện đó.
         *   + occupied_walk_in: Đếm các phiên đỗ xe đang hoạt động (ACTIVE) không có đặt chỗ trước, không phải vé tháng và không bị khóa do đỗ quá giờ.
         *   + occupied_booking: Đếm các phiên đỗ xe đang hoạt động (ACTIVE) có đặt chỗ trước (reservation_id) và không bị khóa quá giờ.
         *   + occupied_monthly: Đếm các phiên đỗ xe đang hoạt động (ACTIVE) khớp biển số với vé tháng còn hiệu lực (monthly_tickets).
         *   + occupied_slots_monthly: Đếm số slot thực tế đang bị chiếm dụng cơ học (hardware sensors OCCUPIED) ở khu vực Monthly.
         *   + wrong_zone_tickets_count: Đếm các sự cố đỗ sai khu vực (ZONE_VIOLATION) đang chờ xử lý checkout.
         */
        String vehicleStatsQuery = """
                    SELECT
                        v.type_name as name,
                        (SELECT COUNT(s.id) FROM slots s JOIN zones z ON s.zone_id = z.id WHERE z.vehicle_type_id = v.id AND z.status = 'ACTIVE' AND (z.function_type = 'WALK_IN' OR z.function_type = 'ALL')) as capacity_walk_in,
                        (SELECT COUNT(s.id) FROM slots s JOIN zones z ON s.zone_id = z.id WHERE z.vehicle_type_id = v.id AND z.status = 'ACTIVE' AND (z.function_type = 'MONTHLY' OR z.function_type = 'ALL')) as capacity_monthly,
                        0 as capacity_incident,
                        (SELECT COUNT(s.id) FROM slots s JOIN zones z ON s.zone_id = z.id WHERE z.vehicle_type_id = v.id AND z.status = 'ACTIVE') as capacity_total,
                        (SELECT COUNT(ps.id) FROM parking_sessions ps WHERE ps.vehicle_type_id = v.id AND ps.status = 'ACTIVE' AND ps.reservation_id IS NULL AND NOT EXISTS (SELECT 1 FROM monthly_tickets mt WHERE mt.plate = ps.plate AND mt.status = 'ACTIVE' AND ps.time_in BETWEEN mt.valid_from AND mt.valid_until) AND NOT EXISTS (SELECT 1 FROM incident_tickets it WHERE it.session_id = ps.id AND it.issue_type = 'OVERSTAY' AND it.status = 'RESOLVED')) as occupied_walk_in,
                        (SELECT COUNT(ps.id) FROM parking_sessions ps WHERE ps.vehicle_type_id = v.id AND ps.status = 'ACTIVE' AND ps.reservation_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM incident_tickets it WHERE it.session_id = ps.id AND it.issue_type = 'OVERSTAY' AND it.status = 'RESOLVED')) as occupied_booking,
                        (SELECT COUNT(ps.id) FROM parking_sessions ps WHERE ps.vehicle_type_id = v.id AND ps.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM monthly_tickets mt WHERE mt.plate = ps.plate AND mt.status = 'ACTIVE' AND ps.time_in BETWEEN mt.valid_from AND mt.valid_until) AND NOT EXISTS (SELECT 1 FROM incident_tickets it WHERE it.session_id = ps.id AND it.issue_type = 'OVERSTAY' AND it.status = 'RESOLVED')) as occupied_monthly,
                        (SELECT COUNT(s.id) FROM slots s JOIN zones z ON s.zone_id = z.id WHERE z.vehicle_type_id = v.id AND z.status = 'ACTIVE' AND z.function_type = 'MONTHLY' AND s.status = 'OCCUPIED') as occupied_slots_monthly,
                        (SELECT COUNT(it.id) FROM incident_tickets it JOIN parking_sessions ps ON it.session_id = ps.id WHERE ps.vehicle_type_id = v.id AND ps.status IN ('ACTIVE', 'LOCKED') AND it.issue_type = 'ZONE_VIOLATION' AND it.status = 'WAITING_CHECKOUT') as wrong_zone_tickets_count,
                        0 as occupied_incident
                    FROM vehicle_types v
                    WHERE v.status = 'ACTIVE'
                """;
        List<Map<String, Object>> vehicleStats = jdbcTemplate.queryForList(vehicleStatsQuery);

        String targetDate = date != null ? date.toString()
                : com.pbms.common.utils.TimeProvider.now().toLocalDate().toString();

        /**
         * [SQL_IN_002] Đếm tổng lượt xe vào (Check-ins) của ngày cụ thể.
         * - Bảng tham gia: parking_sessions.
         * - Chức năng: Đếm tất cả các bản ghi bắt đầu gửi xe (time_in khớp ngày lọc).
         */
        String checkInQuery = """
                    SELECT COUNT(id)
                    FROM parking_sessions
                    WHERE CAST(time_in AS DATE) = ?
                """;
        Integer checkIns = jdbcTemplate.queryForObject(checkInQuery, Integer.class, targetDate);

        /**
         * [SQL_OUT_003] Đếm tổng lượt xe ra (Check-outs) hoàn tất trong ngày.
         * - Bảng tham gia: parking_sessions.
         * - Chức năng: Đếm tất cả bản ghi đã checkout thành công (time_out khớp ngày lọc, trạng thái COMPLETED).
         */
        String checkOutQuery = """
                    SELECT COUNT(id)
                    FROM parking_sessions
                    WHERE CAST(time_out AS DATE) = ? AND status = 'COMPLETED'
                """;
        Integer checkOuts = jdbcTemplate.queryForObject(checkOutQuery, Integer.class, targetDate);

        /**
         * [SQL_VIOLATION_004] Thống kê vi phạm phân bổ vị trí theo tầng (Floor Violations).
         * - Các bảng tham gia: floors f, vehicle_types v, slots s, zones z, parking_sessions ps, monthly_tickets mt, incident_tickets it.
         * - Chức năng: Đối soát giữa cảm biến phần cứng báo đỗ (occupied_slots) và số lượng vé tháng được hệ thống chỉ định vào tầng đó (assigned_monthly) cùng số lượng vi phạm phân vùng (wrong_zone_tickets_count) để cảnh báo xe đỗ sai chỗ.
         */
        String floorViolationsQuery = """
                    SELECT
                        f.floor_name as floor_name,
                        v.type_name as vehicle_type,
                        (SELECT COUNT(s.id) FROM slots s JOIN zones z ON s.zone_id = z.id WHERE z.floor_id = f.id AND z.vehicle_type_id = v.id AND z.status = 'ACTIVE' AND z.function_type = 'MONTHLY' AND s.status = 'OCCUPIED') as occupied_slots,
                        (SELECT COUNT(ps.id) FROM parking_sessions ps JOIN zones z ON ps.suggested_zone_id = z.id WHERE z.floor_id = f.id AND z.vehicle_type_id = v.id AND z.function_type = 'MONTHLY' AND ps.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM monthly_tickets mt WHERE mt.plate = ps.plate AND mt.status = 'ACTIVE' AND ps.time_in BETWEEN mt.valid_from AND mt.valid_until) AND NOT EXISTS (SELECT 1 FROM incident_tickets it WHERE it.session_id = ps.id AND it.issue_type = 'OVERSTAY' AND it.status = 'RESOLVED')) as assigned_monthly,
                        (SELECT COUNT(it.id) FROM incident_tickets it JOIN parking_sessions ps ON it.session_id = ps.id WHERE ps.vehicle_type_id = v.id AND ps.status IN ('ACTIVE', 'LOCKED') AND it.issue_type = 'ZONE_VIOLATION' AND it.status = 'WAITING_CHECKOUT') as wrong_zone_tickets_count
                    FROM floors f
                    CROSS JOIN vehicle_types v
                    WHERE v.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.id AND z.vehicle_type_id = v.id AND z.status = 'ACTIVE' AND z.function_type = 'MONTHLY')
                """;
        List<Map<String, Object>> floorViolations = jdbcTemplate.queryForList(floorViolationsQuery);

        Map<String, Object> liveData = new java.util.HashMap<>();
        liveData.put("vehicleStats", vehicleStats);
        liveData.put("floorViolations", floorViolations);
        liveData.put("checkIns", checkIns != null ? checkIns : 0);
        liveData.put("checkOuts", checkOuts != null ? checkOuts : 0);

        return Map.of(
                "liveData", liveData);
    }

    /**
     * [BE_FN_002] Hàm tính toán lưu lượng xe vào/ra theo từng khung giờ trong 1 ngày cụ thể.
     * Dữ liệu được tính độc lập cho mỗi loại phương tiện (Car, Motorbike, v.v.).
     * Biểu đồ Hourly Traffic Flow sẽ sử dụng API này để vẽ đường line biểu diễn In/Out.
     * 
     * @param date Ngày cần thống kê.
     * @return Danh sách map biểu diễn lưu lượng 24h.
     */
    public List<Map<String, Object>> getHourlyFlow(LocalDate date) {
        /**
         * [SQL_HOURLY_IN_005] Đếm số lượng xe vào (In-flow) chia theo từng khung giờ (0h-23h) và loại xe.
         * - Các bảng tham gia: parking_sessions p, vehicle_types v.
         * - Chức năng: Lọc theo ngày xe vào, nhóm theo giờ (DATEPART) và tên loại xe.
         */
        String query = """
                    SELECT
                        DATEPART(HOUR, p.time_in) AS hour_in,
                        v.type_name,
                        COUNT(p.id) AS in_count
                    FROM parking_sessions p
                    JOIN vehicle_types v ON p.vehicle_type_id = v.id
                    WHERE CAST(p.time_in AS DATE) = ?
                    GROUP BY DATEPART(HOUR, p.time_in), v.type_name
                """;
        List<Map<String, Object>> inData = jdbcTemplate.queryForList(query, date.toString());

        /**
         * [SQL_HOURLY_OUT_006] Đếm số lượng xe ra (Out-flow) chia theo từng khung giờ (0h-23h) và loại xe.
         * - Các bảng tham gia: parking_sessions p, vehicle_types v.
         * - Chức năng: Lọc các xe hoàn tất đỗ (status = COMPLETED) trong ngày, nhóm theo giờ xe ra và loại xe.
         */
        String outQuery = """
                    SELECT
                        DATEPART(HOUR, p.time_out) AS hour_out,
                        v.type_name,
                        COUNT(p.id) AS out_count
                    FROM parking_sessions p
                    JOIN vehicle_types v ON p.vehicle_type_id = v.id
                    WHERE CAST(p.time_out AS DATE) = ? AND p.status = 'COMPLETED'
                    GROUP BY DATEPART(HOUR, p.time_out), v.type_name
                """;
        List<Map<String, Object>> outData = jdbcTemplate.queryForList(outQuery, date.toString());

        List<String> allVehicleTypes = jdbcTemplate.queryForList("SELECT type_name FROM vehicle_types", String.class);

        // Gom dữ liệu 24h
        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> hourData = new java.util.HashMap<>();
            hourData.put("hour", String.format("%02d:00", h));
            int totalVolume = 0;

            for (String vt : allVehicleTypes) {
                hourData.put(vt + "_in", 0);
                hourData.put(vt + "_out", 0);
            }

            for (Map<String, Object> row : inData) {
                if (((Number) row.get("hour_in")).intValue() == h) {
                    String type = (String) row.get("type_name");
                    int count = ((Number) row.get("in_count")).intValue();
                    hourData.put(type + "_in", count);
                    totalVolume += count;
                }
            }

            for (Map<String, Object> row : outData) {
                if (((Number) row.get("hour_out")).intValue() == h) {
                    String type = (String) row.get("type_name");
                    int count = ((Number) row.get("out_count")).intValue();
                    hourData.put(type + "_out", count);
                    totalVolume += count;
                }
            }

            hourData.put("totalVolume", totalVolume);
            result.add(hourData);
        }

        return result;
    }

}
