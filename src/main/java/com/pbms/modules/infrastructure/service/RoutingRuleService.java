/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-09
 * @Description: Service for managing parking zone routing rules and suggestion chains.
 * @Dependencies: RoutingRuleRepository, ZoneRepository
 */
package com.pbms.modules.infrastructure.service;

// =========================================================================
// PHẦN 1: ENTITY VÀ DTO CỦA TÍNH NĂNG ROUTING RULE
// =========================================================================
import com.pbms.modules.infrastructure.domain.RoutingRule; // Khuôn Entity ánh xạ bảng routing_rules trong Database.
import com.pbms.modules.infrastructure.domain.Zone; // Khuôn Entity ánh xạ bảng zones (khu vực đỗ xe).
import com.pbms.modules.infrastructure.dto.RoutingRuleDTO; // Gói dữ liệu trao đổi với Frontend (cả 2 chiều Request/Response).

// =========================================================================
// PHẦN 2: CÁC REPOSITORY (KẾT NỐI DATABASE)
// =========================================================================
import com.pbms.modules.infrastructure.repository.RoutingRuleRepository; // Thao tác với bảng RoutingRule.
import com.pbms.modules.infrastructure.repository.ZoneRepository; // Thao tác với bảng Zone.

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK VÀ SPRING BOOT
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự sinh Constructor cho các field "final" bên dưới (Constructor Injection gọn hơn @Autowired thủ công).
import lombok.extern.slf4j.Slf4j; // Lombok tự sinh sẵn biến `log` để ghi log ra console/file.
import org.springframework.stereotype.Service; // Đánh dấu class này là tầng nghiệp vụ (Business Logic), Spring sẽ quản lý làm 1 Bean.
import org.springframework.transaction.annotation.Transactional; // Đảm bảo nhiều thao tác Database trong 1 hàm hoặc thành công hết, hoặc rollback hết.

// =========================================================================
// PHẦN 4: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.time.LocalTime; // Kiểu dữ liệu Giờ:Phút:Giây của Java, dùng cho khung giờ luật.
import java.util.*; // Các cấu trúc dữ liệu: List, Map, ArrayList, HashMap, UUID...
import java.util.stream.Collectors; // Công cụ gom kết quả Stream (.filter/.map) thành List/Map.

/**
 * =========================================================================================
 * BỘ NÃO XỬ LÝ NGHIỆP VỤ CHO CẤU HÌNH LUẬT ĐIỀU PHỐI (ROUTING RULE) - PHẦN CRUD/CẤU HÌNH
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Class này KHÔNG chạy điều phối xe theo thời gian thực (việc đó nằm ở
 * `ZoneRoutingService` bên package operation) — vai trò ở đây là quản lý
 * PHẦN CẤU HÌNH: đọc luật đang lưu để hiển thị lên màn hình quản lý, và ghi
 * lại toàn bộ luật mới mỗi khi quản lý bấm "Lưu".
 *
 * BÀI TOÁN KHÓ NHẤT CỦA FILE NÀY — "PHẲNG" VS "CHUỖI" (CHAIN):
 * - Database lưu mỗi luật là 1 dòng phẳng, độc lập (xem `RoutingRule` Entity).
 * - Nhưng về nghiệp vụ, nhiều luật ghép lại thành 1 "chuỗi điều phối" liên tiếp
 *   trong cùng khung giờ, ví dụ: Zone A đầy -> đẩy sang B; B đầy -> đẩy sang C.
 * - Hàm `buildChain()` bên dưới chịu trách nhiệm dựng lại đúng thứ tự chuỗi đó
 *   từ các dòng phẳng, dựa vào việc lần theo con trỏ `suggestedZone`.
 * =========================================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingRuleService {

    // Kho truy vấn bảng routing_rules — nguồn dữ liệu chính của toàn bộ class.
    private final RoutingRuleRepository routingRuleRepository;

    // Kho truy vấn bảng zones — dùng để lấy danh sách Zone hợp lệ và tra tên hiển thị.
    private final ZoneRepository zoneRepository;

    // Công cụ chuyển Object Java <-> chuỗi JSON, dùng riêng để ghi log lịch sử
    // thay đổi (Audit). Đăng ký thêm JavaTimeModule để có thể serialize đúng
    // các kiểu thời gian của Java 8+ (LocalTime, LocalDate...) mà không văng lỗi.
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * =========================================================================
     * API NỘI BỘ: LẤY TOÀN BỘ CẤU HÌNH LUẬT THEO LOẠI XE + TẦNG
     * =========================================================================
     * MỤC ĐÍCH:
     * Dựng lại cấu trúc lồng nhau (TimeFrame -> chuỗi Rule) từ các dòng luật
     * phẳng trong Database, để trả về cho màn hình quản lý cấu hình vẽ giao diện.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lọc ra danh sách Zone thuộc đúng loại xe + tầng, đang hoạt động (ACTIVE)
     *    và có chức năng WALK_IN (khu vực dành cho xe vãng lai, không phải Zone
     *    dành riêng cho vé tháng/đặt chỗ).
     * 2. Lọc ra danh sách luật (RoutingRule) đang active, cùng điều kiện Zone như trên.
     * 3. NẾU không có luật nào (chưa cấu hình): trả về 1 khung giờ MẶC ĐỊNH giả,
     *    liệt kê tất cả Zone với ngưỡng cứng 90% (chỉ để Frontend có gì đó hiển thị).
     * 4. NGƯỢC LẠI: gom nhóm các luật theo "chữ ký thời gian" (startTime-endTime,
     *    hoặc "DEFAULT" nếu là luật mặc định) — mỗi nhóm tương ứng với 1 khung giờ.
     * 5. Với mỗi nhóm, gọi `buildChain()` để dựng lại đúng thứ tự chuỗi điều phối.
     * 6. Sắp xếp kết quả: khung giờ cụ thể hiện trước, khung giờ mặc định luôn ở cuối.
     */
    public List<RoutingRuleDTO> getRoutingRulesByVehicleTypeAndFloor(String vehicleTypeName, Long floorId) {
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType().getTypeName().equalsIgnoreCase(vehicleTypeName)
                          && (floorId == null || z.getFloor().getId().equals(floorId))
                          && "ACTIVE".equals(z.getStatus())
                          && "WALK_IN".equalsIgnoreCase(z.getFunctionType()))
                .collect(Collectors.toList());

        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive()
                          && r.getZone().getVehicleType().getTypeName().equalsIgnoreCase(vehicleTypeName)
                          && (floorId == null || r.getZone().getFloor().getId().equals(floorId))
                          && "WALK_IN".equalsIgnoreCase(r.getZone().getFunctionType()))
                .collect(Collectors.toList());

        // Trường hợp CHƯA CÓ luật nào được cấu hình: dựng 1 khung giờ giả, liệt
        // kê tất cả Zone hợp lệ với ngưỡng mặc định cứng 90% để màn hình quản lý
        // vẫn có dữ liệu hiển thị thay vì màn hình trắng.
        if (activeRules.isEmpty()) {
            List<RoutingRuleDTO.RuleItemDTO> chain = new ArrayList<>();
            for (Zone z : zones) {
                chain.add(RoutingRuleDTO.RuleItemDTO.builder()
                        .zoneId(z.getId())
                        .zoneName(z.getZoneName())
                        .fillThresholdPct(90)
                        .build());
            }
            RoutingRuleDTO dto = RoutingRuleDTO.builder()
                    .timeFrameId("tf_" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Other price brackets")
                    .startTime(null)
                    .endTime(null)
                    .isDefault(true)
                    .rules(chain)
                    .build();
            return List.of(dto);
        }

        // Group rules by their time characteristics (startTime + endTime + isDefault)
        // Gom các luật cùng khung giờ vào chung 1 nhóm — khóa gom nhóm là chuỗi
        // "start-end", riêng luật mặc định luôn dùng khóa cố định "DEFAULT" bất
        // kể startTime/endTime của nó là gì.
        Map<String, List<RoutingRule>> groupedRules = new HashMap<>();
        for (RoutingRule rule : activeRules) {
            String key = rule.getIsDefault() ? "DEFAULT" : (rule.getStartTime() + "-" + rule.getEndTime());
            groupedRules.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        }

        List<RoutingRuleDTO> result = new ArrayList<>();

        // Với mỗi nhóm (khung giờ), lấy luật đầu tiên trong nhóm làm đại diện để
        // đọc thông tin chung (tên/giờ bắt đầu/giờ kết thúc), rồi nhờ buildChain()
        // dựng lại đúng thứ tự chuỗi điều phối của toàn bộ luật trong nhóm.
        for (Map.Entry<String, List<RoutingRule>> entry : groupedRules.entrySet()) {
            List<RoutingRule> group = entry.getValue();
            RoutingRule firstRule = group.get(0);

            RoutingRuleDTO dto = RoutingRuleDTO.builder()
                    .timeFrameId("tf_" + UUID.randomUUID().toString().substring(0, 8))
                    .name(firstRule.getIsDefault() ? "Default rules" : ("Timeframe " + firstRule.getStartTime() + " - " + firstRule.getEndTime()))
                    .startTime(firstRule.getStartTime() != null ? firstRule.getStartTime().toString() : null)
                    .endTime(firstRule.getEndTime() != null ? firstRule.getEndTime().toString() : null)
                    .isDefault(firstRule.getIsDefault())
                    .rules(buildChain(group, zones))
                    .build();
            result.add(dto);
        }

        // Sort: specific timeframes first, default last
        // Khung giờ mặc định luôn bị đẩy xuống cuối danh sách; các khung giờ cụ
        // thể còn lại được sắp theo giờ bắt đầu tăng dần để dễ đọc trên giao diện.
        result.sort((a, b) -> {
            if (a.getIsDefault() && !b.getIsDefault()) return 1;
            if (!a.getIsDefault() && b.getIsDefault()) return -1;
            if (a.getStartTime() != null && b.getStartTime() != null) {
                return a.getStartTime().compareTo(b.getStartTime());
            }
            return 0;
        });

        return result;
    }

    /**
     * =========================================================================
     * HÀM NỘI BỘ: DỰNG LẠI CHUỖI ĐIỀU PHỐI (CHAIN) TỪ CÁC LUẬT PHẲNG
     * =========================================================================
     * MỤC ĐÍCH:
     * Các luật trong `rules` chỉ biết "Zone của tôi trỏ sang Zone nào tiếp theo"
     * (`suggestedZone`) — không biết thứ tự chuỗi tổng thể. Hàm này lần theo các
     * con trỏ đó để dựng lại đúng thứ tự A -> B -> C như quản lý đã cấu hình.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Dựng `ruleMap`: tra cứu nhanh "luật của Zone nào" theo `zoneId`.
     * 2. Tìm ĐIỂM ĐẦU chuỗi: là luật mà Zone của nó KHÔNG bị bất kỳ luật nào
     *    khác trỏ `suggestedZone` vào — nghĩa là nó không phải "trạm trung
     *    gian/cuối" của ai cả, nên chắc chắn là gốc chuỗi.
     * 3. Từ điểm đầu, lặp đi theo `suggestedZone` cho tới khi gặp luật có
     *    `suggestedZone = null` (kết thúc chuỗi).
     * 4. Với các Zone hợp lệ nhưng KHÔNG nằm trong chuỗi vừa dựng (ví dụ Zone
     *    chưa được đưa vào cấu hình), gắn thêm vào cuối danh sách với ngưỡng
     *    mặc định cứng 90% để không bị "mất tích" khỏi giao diện.
     *
     * GIỚI HẠN ĐÃ BIẾT (chưa cần sửa vì giao diện hiện tại luôn lưu đúng 1
     * chuỗi liên tục cho mỗi khung giờ, nên trường hợp dưới đây chưa xảy ra):
     * nếu trong cùng 1 nhóm tồn tại NHIỀU chuỗi độc lập song song (ví dụ A->B
     * và tách biệt C->D), hàm chỉ dựng đúng 1 chuỗi (chuỗi có điểm đầu được
     * tìm thấy trước), các luật thuộc chuỗi còn lại sẽ bị rơi vào nhánh
     * "Zone lẻ" ở bước 4 và MẤT ngưỡng/`suggestedZone` thật đã cấu hình.
     */
    private List<RoutingRuleDTO.RuleItemDTO> buildChain(List<RoutingRule> rules, List<Zone> zones) {
        Map<Long, RoutingRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(r -> r.getZone().getId(), r -> r, (a, b) -> a));

        List<RoutingRuleDTO.RuleItemDTO> chain = new ArrayList<>();
        RoutingRule currentRule = null;

        // Tìm điểm đầu chuỗi: luật mà Zone của nó chưa bị luật nào khác "trỏ tới".
        for (RoutingRule rule : rules) {
            boolean isSuggestedByOther = rules.stream()
                    .anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
            if (!isSuggestedByOther) {
                currentRule = rule;
                break;
            }
        }

        // Lần theo con trỏ suggestedZone để đi hết chuỗi, từng luật một.
        while (currentRule != null) {
            chain.add(RoutingRuleDTO.RuleItemDTO.builder()
                    .id(currentRule.getId())
                    .zoneId(currentRule.getZone().getId())
                    .zoneName(currentRule.getZone().getZoneName())
                    .fillThresholdPct(currentRule.getFillThresholdPct())
                    .suggestedZoneId(currentRule.getSuggestedZone() != null ? currentRule.getSuggestedZone().getId() : null)
                    .suggestedZoneName(currentRule.getSuggestedZone() != null ? currentRule.getSuggestedZone().getZoneName() : null)
                    .build());

            if (currentRule.getSuggestedZone() != null) {
                currentRule = ruleMap.get(currentRule.getSuggestedZone().getId());
            } else {
                currentRule = null;
            }
        }

        // Append any remaining active zones not in the chain
        // Zone hợp lệ nào chưa xuất hiện trong chuỗi vừa dựng thì thêm vào cuối
        // với ngưỡng mặc định cứng 90%, để không có Zone nào "biến mất" khỏi UI.
        for (Zone z : zones) {
            if (chain.stream().noneMatch(dto -> dto.getZoneId().equals(z.getId()))) {
                chain.add(RoutingRuleDTO.RuleItemDTO.builder()
                        .zoneId(z.getId())
                        .zoneName(z.getZoneName())
                        .fillThresholdPct(90)
                        .build());
            }
        }
        return chain;
    }

    /**
     * =========================================================================
     * API: GHI ĐÈ TOÀN BỘ CẤU HÌNH LUẬT ĐIỀU PHỐI (BATCH UPDATE)
     * =========================================================================
     * MỤC ĐÍCH:
     * Khi quản lý bấm "Lưu cấu hình" trên giao diện, toàn bộ luật cũ (theo loại
     * xe + tầng đang sửa) bị Soft-Delete, sau đó ghi mới hoàn toàn từ dữ liệu
     * Frontend gửi lên — thay vì dò từng luật để Update/Insert/Delete riêng lẻ.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Tìm toàn bộ luật đang active của đúng loại xe (+ tầng nếu có) — đây là
     *    tập luật sẽ bị vô hiệu hóa.
     * 2. (Ghi log lịch sử/Audit — không bắt buộc) Nếu có AuditContext đang mở,
     *    đọc lại cấu hình HIỆN TẠI (trước khi sửa) bằng chính hàm đọc ở trên,
     *    quy đổi ngược sang hình dạng Request rồi lưu thành chuỗi JSON vào
     *    context để nơi khác ghi log "giá trị cũ". Toàn bộ bước này được bọc
     *    trong try/catch nuốt lỗi — vì đây chỉ là ghi log phụ, KHÔNG được phép
     *    làm hỏng luồng lưu cấu hình chính nếu lỡ có lỗi khi tuần tự hóa.
     * 3. Soft-delete: đặt `isActive = false` cho toàn bộ luật cũ và lưu lại
     *    (KHÔNG xóa cứng khỏi Database, giữ lại để tra cứu lịch sử sau này).
     * 4. Với mỗi khung giờ (TimeFrame) Frontend gửi lên, parse chuỗi "HH:mm"
     *    thành LocalTime (hoặc null nếu rỗng), rồi lặp qua danh sách luật theo
     *    ĐÚNG THỨ TỰ mảng: luật đứng ngay sau trong mảng chính là
     *    `suggestedZone` của luật đứng trước (dựng chuỗi bằng vị trí mảng, thay
     *    vì cần Frontend gửi tường minh con trỏ). Luật cuối mảng không có
     *    `suggestedZone` (chuỗi kết thúc tại đó).
     * 5. Lưu toàn bộ luật mới, rồi đọc lại và trả về cấu hình vừa lưu (đảm bảo
     *    Frontend nhận đúng dữ liệu thật đã ghi xuống Database, không phải dữ
     *    liệu Frontend tự gửi lên).
     */
    @Transactional
    public List<RoutingRuleDTO> updateRoutingRules(RoutingRuleDTO.BatchUpdateRequest request) {
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive()
                          && r.getZone().getVehicleType().getTypeName().equalsIgnoreCase(request.getVehicleTypeName())
                          && (request.getFloorId() == null || r.getZone().getFloor().getId().equals(request.getFloorId())))
                .collect(Collectors.toList());

        // Ghi log giá trị CŨ (trước khi sửa) phục vụ Audit, nếu có phiên Audit
        // đang mở cho request hiện tại. Mọi lỗi ở đây đều bị nuốt (catch rỗng)
        // vì đây chỉ là tính năng phụ trợ, không được phép chặn việc lưu chính.
        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null) {
                List<RoutingRuleDTO> oldDtos = getRoutingRulesByVehicleTypeAndFloor(request.getVehicleTypeName(), request.getFloorId());

                RoutingRuleDTO.BatchUpdateRequest oldRequestFormat = new RoutingRuleDTO.BatchUpdateRequest();
                oldRequestFormat.setVehicleTypeName(request.getVehicleTypeName());
                oldRequestFormat.setFloorId(request.getFloorId());

                // Quy đổi ngược từ hình dạng Response (đã có tên Zone) sang hình
                // dạng Request (chỉ cần ID) để có thể so sánh "cũ" và "mới" cùng
                // 1 định dạng dữ liệu khi ghi log.
                List<RoutingRuleDTO.TimeFrameConfig> oldTimeFrames = new ArrayList<>();
                for (RoutingRuleDTO dto : oldDtos) {
                    RoutingRuleDTO.TimeFrameConfig tf = new RoutingRuleDTO.TimeFrameConfig();
                    tf.setStartTime(dto.getStartTime());
                    tf.setEndTime(dto.getEndTime());
                    tf.setIsDefault(dto.getIsDefault());

                    List<RoutingRuleDTO.RuleItem> oldRules = new ArrayList<>();
                    if (dto.getRules() != null) {
                        for (RoutingRuleDTO.RuleItemDTO rDto : dto.getRules()) {
                            RoutingRuleDTO.RuleItem item = new RoutingRuleDTO.RuleItem();
                            item.setZoneId(rDto.getZoneId());
                            item.setFillThresholdPct(rDto.getFillThresholdPct());
                            oldRules.add(item);
                        }
                    }
                    tf.setRules(oldRules);
                    oldTimeFrames.add(tf);
                }
                oldRequestFormat.setTimeFrames(oldTimeFrames);

                context.setOldValue(objectMapper.writeValueAsString(oldRequestFormat));
            }
        } catch (Exception e) {
            // ignore
        }

        // Soft-delete toàn bộ luật cũ: không xóa cứng, chỉ tắt cờ isActive để
        // vẫn còn dấu vết lịch sử trong Database.
        activeRules.forEach(r -> r.setIsActive(false));
        routingRuleRepository.saveAll(activeRules);

        List<RoutingRule> newRules = new ArrayList<>();

        if (request.getTimeFrames() != null) {
            for (RoutingRuleDTO.TimeFrameConfig tf : request.getTimeFrames()) {
                LocalTime startTime = (tf.getStartTime() != null && !tf.getStartTime().isEmpty()) ? LocalTime.parse(tf.getStartTime()) : null;
                LocalTime endTime = (tf.getEndTime() != null && !tf.getEndTime().isEmpty()) ? LocalTime.parse(tf.getEndTime()) : null;
                Boolean isDefault = tf.getIsDefault() != null ? tf.getIsDefault() : false;

                List<RoutingRuleDTO.RuleItem> items = tf.getRules();
                if (items == null) continue;

                // Dựng chuỗi điều phối theo ĐÚNG THỨ TỰ mảng: item kế tiếp trong
                // mảng chính là Zone được gợi ý (suggestedZone) của item hiện tại.
                for (int i = 0; i < items.size(); i++) {
                    RoutingRuleDTO.RuleItem item = items.get(i);
                    Zone zone = zoneRepository.findById(item.getZoneId())
                            .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + item.getZoneId()));

                    Zone suggestedZone = null;
                    if (i < items.size() - 1) {
                        Long nextZoneId = items.get(i + 1).getZoneId();
                        suggestedZone = zoneRepository.findById(nextZoneId)
                                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + nextZoneId));
                    }

                    RoutingRule newRule = RoutingRule.builder()
                            .zone(zone)
                            .ruleName("Rule for " + zone.getZoneName())
                            .fillThresholdPct(item.getFillThresholdPct() != null ? item.getFillThresholdPct() : 90)
                            .suggestedZone(suggestedZone)
                            .startTime(startTime)
                            .endTime(endTime)
                            .isDefault(isDefault)
                            .isActive(true)
                            .build();

                    newRules.add(newRule);
                }
            }
        }

        routingRuleRepository.saveAll(newRules);
        // Đọc lại từ Database (thay vì trả thẳng `newRules`) để đảm bảo Frontend
        // nhận đúng dữ liệu thật vừa lưu, kể cả khi có sai lệch phát sinh ở
        // bước ghi/đọc.
        return getRoutingRulesByVehicleTypeAndFloor(request.getVehicleTypeName(), request.getFloorId());
    }
}
