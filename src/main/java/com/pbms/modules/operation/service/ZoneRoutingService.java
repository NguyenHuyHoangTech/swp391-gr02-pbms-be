/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Bộ điều hướng xe theo thời gian thực của mảng Spatial/Routing.
 * Đọc cấu hình RoutingRule + trạng thái slot/reservation hiện tại để chọn zone
 * phù hợp cho xe check-in (suggestZone), và dựng bảng trạng thái routing cho
 * màn hình gate console (getRoutingStatus).
 * @Dependencies:
 * - ZoneRepository, SlotRepository, RoutingRuleRepository (Local)
 * - ReservationRepository (Local)
 * - SystemConfigService (Local)
 */
package com.pbms.modules.operation.service;

// =========================================================================
// PHẦN 1: ENTITY VÀ DTO LIÊN QUAN
// =========================================================================
import com.pbms.modules.infrastructure.domain.RoutingRule; // Luật điều phối (ngưỡng % + Zone gợi ý), cấu hình sẵn bởi quản lý.
import com.pbms.modules.infrastructure.domain.Zone; // Khu vực đỗ xe.
import com.pbms.modules.operation.domain.VehicleType; // Loại xe (Ô tô, Xe máy...).

// =========================================================================
// PHẦN 2: CÁC REPOSITORY (KẾT NỐI DATABASE)
// =========================================================================
import com.pbms.modules.infrastructure.repository.RoutingRuleRepository; // Thao tác bảng luật điều phối.
import com.pbms.modules.infrastructure.repository.SlotRepository; // Thao tác bảng Slot (ô đỗ), dùng để đếm số ô trống/đang chiếm.
import com.pbms.modules.infrastructure.repository.ZoneRepository; // Thao tác bảng Zone.
import com.pbms.modules.operation.repository.ReservationRepository; // Thao tác bảng đặt chỗ trước, dùng để tính tải "sắp tới" của 1 Zone.

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK VÀ SPRING BOOT
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự sinh Constructor cho các field "final" bên dưới.
import lombok.extern.slf4j.Slf4j; // Lombok tự sinh sẵn biến `log` để ghi log.
import org.springframework.stereotype.Service; // Đánh dấu đây là tầng nghiệp vụ, Spring quản lý làm 1 Bean.

// =========================================================================
// PHẦN 4: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal; // Kiểu số thập phân chính xác cao, dùng để tính % lấp đầy tránh sai số float.
import java.math.RoundingMode; // Quy tắc làm tròn khi chia BigDecimal.
import java.time.LocalTime; // Kiểu Giờ:Phút:Giây, dùng để so khớp khung giờ hiệu lực của luật.
import java.util.ArrayList; // Danh sách động dùng để gom kết quả.
import java.util.List;
import java.util.stream.Collectors; // Công cụ gom kết quả Stream (.filter/.map) thành List/Map.

/**
 * =========================================================================================
 * BỘ MÁY ĐIỀU PHỐI XE TỰ ĐỘNG THEO THỜI GIAN THỰC (SLIDING THRESHOLD ROUTING ENGINE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đây là "bộ não" chạy MỖI KHI có xe check-in thực tế (khác với
 * `RoutingRuleService` bên package infrastructure — nơi chỉ lo phần CẤU HÌNH
 * luật). Nhiệm vụ: cho 1 loại xe + 1 loại khách (vãng lai/vé tháng) + 1 tầng,
 * trả lời câu hỏi "xe này nên đỗ ở Zone nào NGAY BÂY GIỜ", dựa trên độ đầy
 * (occupancy) đang đo được và chuỗi luật điều phối (RoutingRule) đã cấu hình.
 *
 * TƯ TƯỞNG THUẬT TOÁN "SLIDING THRESHOLD" (NGƯỠNG TRƯỢT):
 * 1. Bắt đầu từ Zone ưu tiên nhất (Zone gốc của chuỗi luật, hoặc Zone đầu danh sách).
 * 2. Đo độ đầy Zone đó. Nếu CHƯA vượt ngưỡng cấu hình -> dùng luôn Zone này.
 * 3. Nếu ĐÃ vượt ngưỡng -> "trượt" sang Zone kế tiếp mà luật chỉ định
 *    (`suggestedZone`), rồi lặp lại bước 2 cho Zone mới.
 * 4. Nếu trượt hết cả chuỗi mà vẫn không tìm được Zone nào dưới ngưỡng -> rơi
 *    vào cơ chế dự phòng (Fallback): chấp nhận bất kỳ Zone nào còn dưới 100%
 *    (dù đã vượt ngưỡng cấu hình), theo đúng thứ tự ưu tiên.
 * 5. Nếu tất cả Zone đều đã 100% đầy -> trả về null (báo bãi xe đã hết chỗ).
 *
 * LƯU Ý 1 (bug đã biết, chưa xử lý — đồ án demo, không chạy production):
 * `getApplicableRules()` so khớp khung giờ bằng `!now.isBefore(start) &&
 * !now.isAfter(end)` — với khung giờ "qua đêm" (22:00 -> 06:00, start > end),
 * phép so sánh này không bao giờ đúng nên luật qua đêm không kích hoạt được.
 * Cách sửa đúng: xét riêng `start.isAfter(end)` và dùng OR thay AND (giống
 * `PricingCalculatorService.findShiftForTime`).
 *
 * LƯU Ý 2 (bản chất dữ liệu, không phải bug): bảng `routing_rules` CHỈ TỪNG
 * ĐƯỢC TẠO CHO ZONE `WALK_IN` (cả `VehicleRoutingScreen.tsx` lẫn
 * `RoutingRuleService` đều hard-filter WALK_IN) — nên `roots`/`applicableRules`
 * bên dưới không bao giờ chứa Zone `MONTHLY`/`BACKUP`. Hệ quả: Zone
 * MONTHLY/BACKUP không tham gia cơ chế trượt ngưỡng %, chỉ xử lý nhị phân
 * (còn/hết chỗ) ở nhánh "no rule" trong vòng `while`. Cơ chế "tràn" chỉ 1
 * chiều: MONTHLY đầy -> tràn BACKUP -> tràn WALK_IN (qua Fallback bậc 1) —
 * chiều ngược lại không xảy ra vì khách vãng lai đã bị lọc `zones` chỉ còn
 * WALK_IN ngay từ đầu. Nếu có nhiều Zone MONTHLY, Fallback bậc 1 (duyệt theo
 * đúng thứ tự ưu tiên) tự nhiên thử hết các Zone MONTHLY trước khi tràn
 * xuống BACKUP, dù không có `RoutingRule` nào nối chúng.
 * =========================================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneRoutingService {

    private final ZoneRepository zoneRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;

    /**
     * =========================================================================
     * HÀM NỘI BỘ: TÍNH ĐỘ ƯU TIÊN CỦA 1 ZONE THEO LOẠI CHỨC NĂNG (functionType)
     * =========================================================================
     * MỤC ĐÍCH:
     * Số càng NHỎ nghĩa là càng được ưu tiên xét trước. Logic ưu tiên khác nhau
     * tùy khách vãng lai hay khách vé tháng:
     * - Khách VÉ THÁNG: ưu tiên Zone dành riêng MONTHLY (1) -> Zone dự phòng
     *   BACKUP (2) -> Zone vãng lai WALK_IN (3) -> còn lại xếp cuối (99).
     * - Khách VÃNG LAI: chỉ ưu tiên Zone WALK_IN (1), mọi loại khác xếp cuối (99).
     *
     * LƯU Ý: nhánh `return 99` của khách vãng lai thực tế không bao giờ chạy
     * tới, vì `suggestZone()` đã lọc `zones` chỉ còn `WALK_IN` cho khách vãng
     * lai trước khi gọi hàm này — mọi Zone đều nhận ưu tiên 1, chỉ tên Zone
     * (A-Z) mới thực sự phân biệt thứ tự.
     */
    private int getZonePriority(String functionType, String customerType) {
        if ("MONTHLY".equalsIgnoreCase(customerType)) {
            if ("MONTHLY".equalsIgnoreCase(functionType)) return 1;
            if ("BACKUP".equalsIgnoreCase(functionType)) return 2;
            if ("WALK_IN".equalsIgnoreCase(functionType)) return 3;
            return 99;
        } else {
            if ("WALK_IN".equalsIgnoreCase(functionType)) return 1;
            return 99;
        }
    }

    /**
     * =========================================================================
     * HÀM NỘI BỘ: LỌC RA LUẬT ĐANG CÓ HIỆU LỰC TẠI THỜI ĐIỂM `now` CHO MỖI ZONE
     * =========================================================================
     * MỤC ĐÍCH:
     * 1 Zone có thể có NHIỀU luật cấu hình (mỗi luật ứng với 1 khung giờ khác
     * nhau + 1 luật mặc định). Hàm này chọn ra ĐÚNG 1 luật áp dụng cho mỗi
     * Zone tại thời điểm hiện tại: ưu tiên luật có khung giờ khớp với `now`,
     * nếu không luật nào khớp thì rơi về luật mặc định (isDefault) của Zone đó.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Gom nhóm toàn bộ luật `activeRules` theo `zoneId`.
     * 2. Với mỗi Zone, tìm luật KHÔNG mặc định mà khung giờ [startTime, endTime]
     *    bao trùm thời điểm `now` hiện tại.
     * 3. Nếu tìm thấy -> dùng luật đó. Nếu không -> rơi về luật `isDefault=true`
     *    của Zone đó (nếu có).
     * 4. Trả về danh sách gồm tối đa 1 luật/Zone.
     */
    private List<RoutingRule> getApplicableRules(List<RoutingRule> activeRules, LocalTime now) {
        List<RoutingRule> applicableRules = new ArrayList<>();
        java.util.Map<Long, List<RoutingRule>> rulesByZone = activeRules.stream()
                .collect(Collectors.groupingBy(r -> r.getZone().getId()));

        for (java.util.Map.Entry<Long, List<RoutingRule>> entry : rulesByZone.entrySet()) {
            List<RoutingRule> zoneRules = entry.getValue();
            RoutingRule timeframeRule = zoneRules.stream()
                .filter(r -> !r.getIsDefault() && r.getStartTime() != null && r.getEndTime() != null)
                .filter(r -> !now.isBefore(r.getStartTime()) && !now.isAfter(r.getEndTime()))
                .findFirst()
                .orElse(null);

            if (timeframeRule != null) {
                applicableRules.add(timeframeRule);
            } else {
                zoneRules.stream().filter(rule -> rule.getIsDefault()).findFirst().ifPresent(applicableRules::add);
            }
        }
        return applicableRules;
    }

    /**
     * Calculates the real-time occupancy percentage of a zone.
     * Formula: (Occupied + Pending Reservations) / (Total - Disabled/Maintenance) * 100
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Đếm tổng số ô (Slot) thuộc Zone. Nếu bằng 0 -> trả về 0% (Zone rỗng, không có ô nào).
     * 2. Trừ đi số ô đang DISABLED (bảo trì/khóa) để ra "sức chứa hiệu dụng"
     *    (effectiveCapacity). Nếu sức chứa hiệu dụng <= 0 -> coi như đầy 100%.
     * 3. Đếm số ô đang có xe (OCCUPIED).
     * 4. Đếm thêm số ĐẶT CHỖ đang chờ (PENDING) mà thời điểm hiện tại rơi vào
     *    "cửa sổ thời gian" của đơn đặt đó — cửa sổ này bắt đầu sớm hơn giờ hẹn
     *    `windowMinutes` phút (đọc từ cấu hình hệ thống, mặc định 30 nếu đọc
     *    cấu hình lỗi) và kết thúc sau khi cộng thêm thời lượng dự kiến đỗ xe
     *    (`expectedDurationMinutes`) — mục đích: "giữ chỗ trước" cho khách đã
     *    đặt để không bị người khác chiếm mất ô ngay trước giờ họ tới.
     * 5. Tổng tải hiệu dụng = số ô đang chiếm + số đặt chỗ đang trong cửa sổ.
     * 6. Tỷ lệ % = tổng tải / sức chứa hiệu dụng * 100, làm tròn 2 chữ số thập
     *    phân theo kiểu HALF_UP (làm tròn lên khi phần lẻ từ 0.5 trở lên).
     */
    public BigDecimal calculateZoneOccupancy(Long zoneId) {
        long totalSlots = slotRepository.countByZoneId(zoneId);
        if (totalSlots == 0) return BigDecimal.ZERO;

        long disabledSlots = slotRepository.countByZoneIdAndStatus(zoneId, "DISABLED");
        long effectiveCapacity = totalSlots - disabledSlots;

        if (effectiveCapacity <= 0) return BigDecimal.valueOf(100); // Fully disabled

        long occupiedSlots = slotRepository.countByZoneIdAndStatus(zoneId, "OCCUPIED");

        // Only count reservations that are PENDING or ACTIVE right now
        // Window: [expectedEntryTime - window_minutes, expectedEntryTime + expectedDurationMinutes]
        java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        List<com.pbms.modules.operation.domain.Reservation> pendingList = reservationRepository.findByZoneIdAndStatusIn(zoneId, java.util.Arrays.asList("PENDING"));
        int windowMinutes = 30;
        try {
            windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue());
        } catch (Exception e) {
            log.warn("Could not read RESERVATION_EARLY_MINS config, defaulting to 30");
        }
        final int finalWindowMinutes = windowMinutes;
        long countInWindow = pendingList.stream().filter(r -> {
            java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(finalWindowMinutes);
            java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes());
            return !now.isBefore(startWindow) && !now.isAfter(endWindow);
        }).count();

        long pendingReservations = countInWindow;
        long effectiveLoad = occupiedSlots + pendingReservations;

        return BigDecimal.valueOf(effectiveLoad)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(effectiveCapacity), 2, RoundingMode.HALF_UP);
    }

    /**
     * =========================================================================
     * HÀM: KIỂM TRA ZONE ĐÃ ĐẦY VẬT LÝ (100% CHỖ ĐỖ) HAY CHƯA
     * =========================================================================
     * Khác với `calculateZoneOccupancy` (có tính cả đặt chỗ trước), hàm này CHỈ
     * xét số ô đang thực sự có xe (OCCUPIED) so với sức chứa hiệu dụng — dùng
     * cho các chỗ cần biết "còn ô trống thật sự hay không" bất kể đặt chỗ.
     */
    public boolean isZonePhysicallyFull(Long zoneId) {
        long totalSlots = slotRepository.countByZoneId(zoneId);
        if (totalSlots == 0) return true;

        long disabledSlots = slotRepository.countByZoneIdAndStatus(zoneId, "DISABLED");
        long effectiveCapacity = totalSlots - disabledSlots;

        if (effectiveCapacity <= 0) return true;

        long occupiedSlots = slotRepository.countByZoneIdAndStatus(zoneId, "OCCUPIED");
        return occupiedSlots >= effectiveCapacity;
    }

    /**
     * Suggests the best zone for a given vehicle type based on the sliding threshold algorithm.
     *
     * MÃ GIẢ CHI TIẾT TOÀN BỘ THUẬT TOÁN:
     * 1. Lấy danh sách Zone ACTIVE phù hợp loại xe + tầng, với khách vãng lai
     *    thì chỉ giữ Zone WALK_IN — rồi sắp theo độ ưu tiên (`getZonePriority`)
     *    và tên Zone.
     * 2. Nếu không có Zone nào phù hợp -> log cảnh báo, trả về null.
     * 3. Lấy toàn bộ luật điều phối đang active của loại xe + tầng này, sau đó
     *    thu hẹp còn đúng 1 luật/Zone tại thời điểm hiện tại (`getApplicableRules`).
     * 4. XÁC ĐỊNH ZONE BẮT ĐẦU CHUỖI: tìm các Zone "gốc" (root) — Zone có luật
     *    nhưng KHÔNG bị Zone luật nào khác trỏ `suggestedZone` vào. Trong các
     *    Zone gốc, ưu tiên chọn Zone đúng loại chức năng mong muốn
     *    (`preferredFunctionType`); nếu không có gốc nào khớp thì tìm trong
     *    toàn bộ `zones` đã sắp ưu tiên; nếu vẫn không có thì lấy gốc đầu tiên;
     *    cuối cùng nếu vẫn null thì lấy Zone đầu tiên trong danh sách đã sắp.
     * 5. LẦN THEO CHUỖI (vòng lặp while): với Zone hiện tại, đo độ đầy
     *    (`calculateZoneOccupancy`). Có chặn vòng lặp vô hạn (ví dụ luật cấu
     *    hình lỗi A -> B -> A) bằng cách ghi nhớ `visitedChain` và dừng ngay
     *    nếu gặp lại Zone đã đi qua.
     *    - Nếu Zone có luật riêng: so độ đầy với ngưỡng (`fillThresholdPct`).
     *      Nếu CHƯA vượt ngưỡng -> trả về ngay Zone này (tìm được kết quả).
     *      Nếu ĐÃ vượt ngưỡng -> trượt sang Zone kế tiếp (`suggestedZone`);
     *      nếu Zone kế tiếp không nằm trong danh sách hợp lệ (khác tầng/đã bị
     *      vô hiệu hóa) thì dừng cascade tại đây.
     *    - Nếu Zone KHÔNG có luật riêng: chỉ cần chưa đầy 100% là dùng luôn;
     *      nếu đã 100% và không có luật để trượt tiếp thì dừng chuỗi.
     * 6. CƠ CHẾ DỰ PHÒNG (Fallback — "vét/100%"): nếu đi hết chuỗi mà vẫn
     *    không chọn được Zone nào (tất cả đều vượt ngưỡng cấu hình), duyệt lại
     *    các Zone CHƯA đi qua theo thứ tự ưu tiên, chấp nhận Zone đầu tiên còn
     *    dưới ngưỡng của nó (hoặc dưới 100% nếu không có luật riêng).
     * 7. Nếu Fallback theo `remainingZones` vẫn thất bại, duyệt lại TOÀN BỘ
     *    `zones` một lần cuối, chấp nhận Zone đầu tiên chưa đầy 100%.
     * 8. Nếu tất cả các bước trên đều thất bại -> trả về null (bãi xe hết chỗ
     *    hoàn toàn cho loại xe này).
     */
    public Zone suggestZone(VehicleType vehicleType, String customerType, com.pbms.modules.infrastructure.domain.Floor floor) {
        // 1. Get all active zones for this vehicle type on this floor, sorted alphabetically
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleType.getId()) && "ACTIVE".equals(z.getStatus()))
                .filter(z -> floor == null || (z.getFloor() != null && z.getFloor().getId().equals(floor.getId())))
                // Khách vãng lai: chỉ giữ WALK_IN (chặn cứng, không bao giờ
                // lọt vào MONTHLY/BACKUP). Khách vé tháng (return true): giữ
                // hết mọi loại — nền tảng cho cơ chế tràn MONTHLY->BACKUP->WALK_IN.
                .filter(z -> {
                    if (!"MONTHLY".equalsIgnoreCase(customerType)) {
                        return "WALK_IN".equalsIgnoreCase(z.getFunctionType());
                    }
                    return true;
                })
                .sorted((z1, z2) -> {
                    int p1 = getZonePriority(z1.getFunctionType(), customerType);
                    int p2 = getZonePriority(z2.getFunctionType(), customerType);
                    if (p1 != p2) return Integer.compare(p1, p2);
                    return z1.getZoneName().compareTo(z2.getZoneName());
                })
                .collect(Collectors.toList());

        if (zones.isEmpty()) {
            log.warn("No active zones found for vehicle type: {}", vehicleType.getTypeName());
            return null;
        }

        LocalTime now = com.pbms.common.utils.TimeProvider.now().toLocalTime();

        // Find ALL active rules for this vehicle type on this floor
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() && r.getZone().getVehicleType().getId().equals(vehicleType.getId()))
                .filter(r -> floor == null || (r.getZone().getFloor() != null && r.getZone().getFloor().getId().equals(floor.getId())))
                .collect(Collectors.toList());

        // Determine the applicable timeframe or default rules per zone
        List<RoutingRule> applicableRules = getApplicableRules(activeRules, now);

        // 2. Determine starting zone
        // Tìm các Zone "gốc" của chuỗi: Zone có luật nhưng không bị Zone luật
        // nào khác trỏ suggestedZone vào (nghĩa là nó đứng đầu 1 chuỗi, không
        // phải trạm trung gian/cuối của Zone khác).
        Zone currentZone = null;
        List<Zone> roots = new ArrayList<>();
        if (!applicableRules.isEmpty()) {
            for (RoutingRule rule : applicableRules) {
                boolean isSuggestedByOther = applicableRules.stream()
                        .anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
                if (!isSuggestedByOther) {
                    roots.add(rule.getZone());
                }
            }
        }

        String preferredFunctionType = "MONTHLY".equalsIgnoreCase(customerType) ? "MONTHLY" : "WALK_IN";

        // Ưu tiên chọn Zone gốc đúng loại chức năng mong muốn (MONTHLY/WALK_IN).
        // LƯU Ý: `roots` chỉ chứa Zone WALK_IN (xem LƯU Ý 2 đầu file) nên khối
        // này với khách vé tháng luôn rỗng (rơi xuống khối `zones` bên dưới),
        // còn với khách vãng lai thì luôn đúng 100% (không thực sự lọc gì).
        if (!roots.isEmpty()) {
            currentZone = roots.stream()
                    .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                    .findFirst()
                    .orElse(null);
        }

        // Không có Zone gốc nào khớp loại chức năng -> tìm trong toàn bộ danh
        // sách Zone đã sắp ưu tiên. Đây là nơi khách vé tháng thực sự được
        // gán Zone MONTHLY (vì khối `roots` phía trên luôn thất bại với họ).
        if (currentZone == null) {
            currentZone = zones.stream()
                    .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                    .findFirst()
                    .orElse(null);
        }

        // Vẫn không có -> tạm chấp nhận Zone gốc đầu tiên bất kể loại chức năng.
        // Chỉ chạy tới khi vé tháng nhưng thiếu Zone MONTHLY -> vô tình lấy 1
        // Zone WALK_IN từ `roots`.
        if (currentZone == null && !roots.isEmpty()) {
            currentZone = roots.get(0);
        }

        // Không còn lựa chọn nào khác -> lấy Zone đầu tiên trong danh sách đã sắp.
        // Chỉ chạy tới khi vé tháng, thiếu Zone MONTHLY, và cũng không có
        // RoutingRule nào cho tầng đó (`roots` rỗng luôn).
        if (currentZone == null) {
            currentZone = zones.isEmpty() ? null : zones.get(0);
        }

        List<Zone> visitedChain = new ArrayList<>();

        while (currentZone != null) {
            // Prevent infinite loop (e.g. A -> B -> A)
            final Long currentZoneId = currentZone.getId();
            if (visitedChain.stream().anyMatch(z -> z.getId().equals(currentZoneId))) {
                log.warn("Infinite routing loop detected at zone: {}", currentZone.getZoneName());
                break;
            }
            visitedChain.add(currentZone);

            BigDecimal occupancy = calculateZoneOccupancy(currentZone.getId());

            // Find the specific rule for THIS zone within the applicable rules
            final Long czId = currentZone.getId();
            RoutingRule ruleToUse = applicableRules.stream()
                    .filter(r -> r.getZone().getId().equals(czId))
                    .findFirst()
                    .orElse(null);

            if (ruleToUse != null) {
                if (occupancy.compareTo(BigDecimal.valueOf(ruleToUse.getFillThresholdPct())) >= 0) {
                    // Threshold exceeded, slide to the next zone
                    Zone nextZone = ruleToUse.getSuggestedZone();
                    if (nextZone != null && zones.stream().noneMatch(z -> z.getId().equals(nextZone.getId()))) {
                        log.info("Suggested zone {} is not on the same floor or is inactive. Stopping cascade.", nextZone.getZoneName());
                        break;
                    }
                    log.info("Zone {} exceeded threshold ({} >= {}). Sliding to {}",
                            currentZone.getZoneName(), occupancy, ruleToUse.getFillThresholdPct(),
                            nextZone != null ? nextZone.getZoneName() : "NULL");

                    currentZone = nextZone;
                } else {
                    // Below threshold, we can use this zone
                    return currentZone;
                }
            } else {
                // No routing rule for this zone.
                // We just check if it's full (100%). If not, use it.
                // Đây là nhánh xử lý Zone MONTHLY/BACKUP (không bao giờ có
                // RoutingRule) — với khách vé tháng, gần như toàn bộ logic
                // thực sự áp dụng cho họ nằm ở đây: nhị phân còn/hết chỗ.
                if (occupancy.compareTo(BigDecimal.valueOf(100)) < 0) {
                    return currentZone;
                } else {
                    // It's 100% full, and no rule to cascade. Stop chain.
                    break;
                }
            }
        }

        // 3. Fallback Mechanism (Vét/100%): All zones in the chain are either full or exceeded thresholds.
        // We iterate again over the routing chain (priority order) and return the first one that has ANY available slots (< 100%).
        log.warn("All zones exceeded routing thresholds for vehicle type {}. Falling back to 100% capacity check by priority order.", vehicleType.getTypeName());

        // FALLBACK BẬC 1 — gần như chỉ có tác dụng thật với khách VÉ THÁNG.
        // Khách vãng lai: UI luôn gom hết Zone WALK_IN vào 1 chuỗi liền mạch,
        // nên khi tới đây `visitedChain` đã gần như trùng `zones` ->
        // `remainingZones` gần rỗng, rơi thẳng xuống Fallback bậc 2.
        // Khách vé tháng: `visitedChain` chỉ có 1 Zone MONTHLY (chuỗi không
        // trượt tiếp được) -> `remainingZones` còn đủ BACKUP/WALK_IN, và cả
        // các Zone MONTHLY khác nếu có nhiều hơn 1 (đứng đầu do cùng ưu tiên
        // cao nhất) -> vòng for này tự nhiên thử hết Zone MONTHLY khác trước
        // khi tràn xuống BACKUP rồi WALK_IN, dù không có RoutingRule nối chúng.
        List<Zone> remainingZones = zones.stream()
            .filter(z -> visitedChain.stream().noneMatch(v -> v.getId().equals(z.getId())))
            .collect(Collectors.toList());

        for (Zone z : remainingZones) {
            BigDecimal occ = calculateZoneOccupancy(z.getId());
            RoutingRule r = applicableRules.stream().filter(rule -> rule.getZone().getId().equals(z.getId())).findFirst().orElse(null);
            double threshold = r != null ? r.getFillThresholdPct() : 100.0;
            if (occ.compareTo(BigDecimal.valueOf(threshold)) < 0) {
                return z;
            }
        }

        // FALLBACK BẬC 2 — "phao cứu sinh" cuối: quét lại TOÀN BỘ `zones`, bỏ
        // hẳn ngưỡng cấu hình, chỉ cần chưa đầy 100% vật lý là nhận ngay.
        for (Zone z : zones) {
            BigDecimal occ = calculateZoneOccupancy(z.getId());
            if (occ.compareTo(BigDecimal.valueOf(100)) < 0) {
                return z;
            }
        }

        // Absolutely full
        return null;
    }


}
