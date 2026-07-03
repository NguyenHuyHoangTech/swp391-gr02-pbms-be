package com.pbms.modules.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> getRevenueOverview(String startDate, String endDate) {
        String query = """
            SELECT 
                SUM(amount) as totalRevenue,
                COUNT(id) as totalTransactions
            FROM transactions 
            WHERE status = 'SUCCESS' 
              AND created_at >= ? AND created_at <= ?
        """;
        Map<String, Object> overview = jdbcTemplate.queryForMap(query, startDate, endDate);
        
        String dailyQuery = """
            SELECT 
                CAST(created_at AS DATE) as date,
                SUM(amount) as revenue
            FROM transactions
            WHERE status = 'SUCCESS'
              AND created_at >= ? AND created_at <= ?
            GROUP BY CAST(created_at AS DATE)
            ORDER BY date
        """;
        List<Map<String, Object>> dailyRevenue = jdbcTemplate.queryForList(dailyQuery, startDate, endDate);
        
        String byVehicleQuery = """
            SELECT 
                COALESCE(vt.type_name, 'UNKNOWN') as name, 
                SUM(t.amount) as value 
            FROM transactions t
            LEFT JOIN parking_sessions ps ON t.parking_session_id = ps.id
            LEFT JOIN monthly_tickets mt ON t.monthly_ticket_id = mt.id
            LEFT JOIN vehicle_types vt ON ps.vehicle_type_id = vt.id OR mt.vehicle_type_id = vt.id
            WHERE t.status = 'SUCCESS' AND t.created_at >= ? AND t.created_at <= ? 
            GROUP BY COALESCE(vt.type_name, 'UNKNOWN')
        """;
        List<Map<String, Object>> byVehicle = jdbcTemplate.queryForList(byVehicleQuery, startDate, endDate);

        String bySourceQuery = """
            SELECT 
                CASE 
                    WHEN mt.id IS NOT NULL THEN 'MONTHLY_PASS'
                    WHEN ps.id IS NOT NULL AND ps.reservation_id IS NOT NULL THEN 'PRE_BOOKING'
                    WHEN ps.id IS NOT NULL THEN 'WALK_IN'
                    WHEN t.transaction_reference LIKE 'PENALTY-RES-%' THEN 'PENALTY_FEE'
                    ELSE 'OTHER'
                END as name, 
                SUM(t.amount) as value 
            FROM transactions t
            LEFT JOIN parking_sessions ps ON t.parking_session_id = ps.id
            LEFT JOIN monthly_tickets mt ON t.monthly_ticket_id = mt.id
            WHERE t.status = 'SUCCESS' AND t.created_at >= ? AND t.created_at <= ? 
            GROUP BY 
                CASE 
                    WHEN mt.id IS NOT NULL THEN 'MONTHLY_PASS'
                    WHEN ps.id IS NOT NULL AND ps.reservation_id IS NOT NULL THEN 'PRE_BOOKING'
                    WHEN ps.id IS NOT NULL THEN 'WALK_IN'
                    WHEN t.transaction_reference LIKE 'PENALTY-RES-%' THEN 'PENALTY_FEE'
                    ELSE 'OTHER'
                END
        """;
        List<Map<String, Object>> bySource = jdbcTemplate.queryForList(bySourceQuery, startDate, endDate);

        String byPaymentQuery = """
            SELECT 
                payment_method as name, 
                SUM(amount) as value 
            FROM transactions 
            WHERE status = 'SUCCESS' AND created_at >= ? AND created_at <= ? 
            GROUP BY payment_method
        """;
        List<Map<String, Object>> byPayment = jdbcTemplate.queryForList(byPaymentQuery, startDate, endDate);

        return Map.of(
            "overview", overview,
            "dailyRevenue", dailyRevenue,
            "byVehicle", byVehicle,
            "bySource", bySource,
            "byPayment", byPayment
        );
    }

    public Map<String, Object> getOperationalOverview() {
        String vehicleStatsQuery = """
            SELECT 
                v.type_name as name, 
                (SELECT COUNT(s.id) FROM slots s JOIN zones z ON s.zone_id = z.id WHERE z.vehicle_type_id = v.id AND z.status = 'ACTIVE') as capacity,
                (SELECT COUNT(ps.id) FROM parking_sessions ps WHERE ps.vehicle_type_id = v.id AND ps.status = 'ACTIVE') as occupied
            FROM vehicle_types v
            WHERE v.status = 'ACTIVE'
        """;
        List<Map<String, Object>> vehicleStats = jdbcTemplate.queryForList(vehicleStatsQuery);

        // Check-ins today
        String checkInQuery = """
            SELECT COUNT(id) 
            FROM parking_sessions 
            WHERE CAST(time_in AS DATE) = CAST(GETDATE() AS DATE)
        """;
        Integer checkIns = jdbcTemplate.queryForObject(checkInQuery, Integer.class);

        // Check-outs today
        String checkOutQuery = """
            SELECT COUNT(id) 
            FROM parking_sessions 
            WHERE CAST(time_out AS DATE) = CAST(GETDATE() AS DATE) AND status = 'COMPLETED'
        """;
        Integer checkOuts = jdbcTemplate.queryForObject(checkOutQuery, Integer.class);

        Map<String, Object> liveData = new java.util.HashMap<>();
        liveData.put("vehicleStats", vehicleStats);
        liveData.put("checkIns", checkIns != null ? checkIns : 0);
        liveData.put("checkOuts", checkOuts != null ? checkOuts : 0);

        return Map.of(
            "liveData", liveData
        );
    }

    /**
     * TÃ­nh sá»‘ lÆ°á»£ng xe Ä‘ang trong bÃ£i (occupancy) táº¡i má»—i khung giá» trong ngÃ y.
     * CÅ©ng bao gá»“m sá»‘ check-in vÃ  check-out theo giá».
     */
    public List<Map<String, Object>> getHourlyOccupancy(LocalDate date) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Query check-in count per hour
        String checkInQuery = """
            SELECT DATEPART(HOUR, time_in) AS hour, COUNT(*) AS cnt
            FROM parking_sessions
            WHERE CAST(time_in AS DATE) = ?
            GROUP BY DATEPART(HOUR, time_in)
        """;
        List<Map<String, Object>> checkIns = jdbcTemplate.queryForList(checkInQuery, date.toString());

        // Query check-out count per hour  
        String checkOutQuery = """
            SELECT DATEPART(HOUR, time_out) AS hour, COUNT(*) AS cnt
            FROM parking_sessions
            WHERE CAST(time_out AS DATE) = ? AND status = 'COMPLETED'
            GROUP BY DATEPART(HOUR, time_out)
        """;
        List<Map<String, Object>> checkOuts = jdbcTemplate.queryForList(checkOutQuery, date.toString());

        // Build maps for easy lookup
        int[] inByHour = new int[24];
        int[] outByHour = new int[24];
        for (Map<String, Object> row : checkIns) {
            int h = ((Number) row.get("hour")).intValue();
            inByHour[h] = ((Number) row.get("cnt")).intValue();
        }
        for (Map<String, Object> row : checkOuts) {
            int h = ((Number) row.get("hour")).intValue();
            outByHour[h] = ((Number) row.get("cnt")).intValue();
        }

        // Count sessions that were already active BEFORE this date (carried-over)
        String priorActiveQuery = """
            SELECT COUNT(*) FROM parking_sessions
            WHERE time_in < ? AND (time_out IS NULL OR CAST(time_out AS DATE) >= ?)
        """;
        Integer priorActive = jdbcTemplate.queryForObject(priorActiveQuery, Integer.class,
                date.toString(), date.toString());
        
        int cumulative = priorActive != null ? priorActive : 0;

        for (int h = 0; h < 24; h++) {
            cumulative += inByHour[h];
            cumulative -= outByHour[h];
            if (cumulative < 0) cumulative = 0;

            Map<String, Object> row = new HashMap<>();
            row.put("hour", String.format("%02d:00", h));
            row.put("occupied", cumulative);
            row.put("checkIn", inByHour[h]);
            row.put("checkOut", outByHour[h]);
            result.add(row);
        }

        return result;
    }

    /**
     * Láº¥y dá»¯ liá»‡u lÆ°u lÆ°á»£ng vÃ o/ra theo tá»«ng giá»  trong 1 ngÃ y cá»¥ thá»ƒ (DÃ nh cho Khu vá»±c 4)
     * PhÃ¢n tÃ¡ch riÃªng biá»‡t theo loại xe.
     */
    public List<Map<String, Object>> getHourlyFlow(LocalDate date) {
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

    /**
     * Láº¥y dá»¯ liá»‡u vÄ© mÃ´ (Macro Trends) cho 1 khoáº£ng ngÃ y (DÃ nh cho Khu vá»±c 5)
     */
    public Map<String, Object> getMacroTrends(String startDate, String endDate, String category) {
        boolean hasCategory = category != null && !category.isEmpty() && !"ALL".equalsIgnoreCase(category);
        String categoryFilter = hasCategory ? " AND v.type_name = ? " : "";

        // 5.1 Daily Net Flow
        String dailyInQuery = """
            SELECT CAST(p.time_in AS DATE) as d, COUNT(p.id) as cnt
            FROM parking_sessions p
            JOIN vehicle_types v ON p.vehicle_type_id = v.id
            WHERE CAST(p.time_in AS DATE) >= ? AND CAST(p.time_in AS DATE) <= ?
        """ + categoryFilter + """
            GROUP BY CAST(p.time_in AS DATE)
            ORDER BY d ASC
        """;
        List<Map<String, Object>> dailyIn = hasCategory 
            ? jdbcTemplate.queryForList(dailyInQuery, startDate, endDate, category)
            : jdbcTemplate.queryForList(dailyInQuery, startDate, endDate);

        String dailyOutQuery = """
            SELECT CAST(p.time_out AS DATE) as d, COUNT(p.id) as cnt
            FROM parking_sessions p
            JOIN vehicle_types v ON p.vehicle_type_id = v.id
            WHERE CAST(p.time_out AS DATE) >= ? AND CAST(p.time_out AS DATE) <= ? AND p.status = 'COMPLETED'
        """ + categoryFilter + """
            GROUP BY CAST(p.time_out AS DATE)
            ORDER BY d ASC
        """;
        List<Map<String, Object>> dailyOut = hasCategory
            ? jdbcTemplate.queryForList(dailyOutQuery, startDate, endDate, category)
            : jdbcTemplate.queryForList(dailyOutQuery, startDate, endDate);

        Map<String, Map<String, Object>> dailyMap = new HashMap<>();
        for (Map<String, Object> row : dailyIn) {
            String dStr = row.get("d").toString();
            Map<String, Object> map = dailyMap.computeIfAbsent(dStr, k -> new HashMap<>(Map.of("date", dStr, "totalIn", 0, "totalOut", 0)));
            map.put("totalIn", ((Number) row.get("cnt")).intValue());
        }
        for (Map<String, Object> row : dailyOut) {
            String dStr = row.get("d").toString();
            Map<String, Object> map = dailyMap.computeIfAbsent(dStr, k -> new HashMap<>(Map.of("date", dStr, "totalIn", 0, "totalOut", 0)));
            map.put("totalOut", ((Number) row.get("cnt")).intValue());
        }
        List<Map<String, Object>> dailyNetFlow = new ArrayList<>(dailyMap.values());
        dailyNetFlow.sort((a, b) -> a.get("date").toString().compareTo(b.get("date").toString()));

        // 5.2 Vehicle Type Ratio
        String vehicleRatioQuery = """
            SELECT v.category, COUNT(p.id) as cnt
            FROM parking_sessions p
            JOIN vehicle_types v ON p.vehicle_type_id = v.id
            WHERE CAST(p.time_in AS DATE) >= ? AND CAST(p.time_in AS DATE) <= ?
        """ + categoryFilter + """
            GROUP BY v.category
        """;
        List<Map<String, Object>> vehicleRatio = hasCategory
            ? jdbcTemplate.queryForList(vehicleRatioQuery, startDate, endDate, category)
            : jdbcTemplate.queryForList(vehicleRatioQuery, startDate, endDate);
        
        int fourWheelCount = 0;
        int twoWheelCount = 0;
        for (Map<String, Object> row : vehicleRatio) {
            if ("FOUR_WHEEL".equals(row.get("category"))) fourWheelCount = ((Number) row.get("cnt")).intValue();
            else if ("TWO_WHEEL".equals(row.get("category"))) twoWheelCount = ((Number) row.get("cnt")).intValue();
        }

        // 5.3 Customer Segment Ratio (WALK_IN vs BOOKING vs MONTHLY)
        String customerRatioQuery = """
            SELECT segment, COUNT(id) as cnt
            FROM (
                SELECT 
                    p.id,
                    CASE 
                        WHEN p.reservation_id IS NOT NULL THEN 'BOOKING'
                        WHEN EXISTS (SELECT 1 FROM monthly_tickets mt WHERE mt.plate = p.plate AND mt.status = 'ACTIVE' AND p.time_in BETWEEN mt.valid_from AND mt.valid_until) THEN 'MONTHLY'
                        ELSE 'WALK_IN' 
                    END as segment
                FROM parking_sessions p
                JOIN vehicle_types v ON p.vehicle_type_id = v.id
                WHERE CAST(p.time_in AS DATE) >= ? AND CAST(p.time_in AS DATE) <= ?
            """ + categoryFilter + """
            ) as sub
            GROUP BY segment
        """;
        List<Map<String, Object>> customerRatio = hasCategory
            ? jdbcTemplate.queryForList(customerRatioQuery, startDate, endDate, category)
            : jdbcTemplate.queryForList(customerRatioQuery, startDate, endDate);
            
        int walkInCount = 0;
        int bookingCount = 0;
        int monthlyCount = 0;
        for (Map<String, Object> row : customerRatio) {
            if ("WALK_IN".equals(row.get("segment"))) walkInCount = ((Number) row.get("cnt")).intValue();
            else if ("BOOKING".equals(row.get("segment"))) bookingCount = ((Number) row.get("cnt")).intValue();
            else if ("MONTHLY".equals(row.get("segment"))) monthlyCount = ((Number) row.get("cnt")).intValue();
        }

        return Map.of(
            "dailyNetFlow", dailyNetFlow,
            "vehicleTypeRatio", List.of(
                Map.of("name", "FOUR_WHEEL", "value", fourWheelCount),
                Map.of("name", "TWO_WHEEL", "value", twoWheelCount)
            ),
            "customerSegmentRatio", List.of(
                Map.of("name", "WALK_IN", "value", walkInCount),
                Map.of("name", "BOOKING", "value", bookingCount),
                Map.of("name", "MONTHLY", "value", monthlyCount)
            )
        );
    }
}

