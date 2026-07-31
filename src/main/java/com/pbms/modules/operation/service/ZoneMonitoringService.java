/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-22
 * @Description: Xử lý sự kiện cảm biến slot (từ phần cứng IoT / mô phỏng) và
 * phát cảnh báo realtime. 2 nhiệm vụ: (1) cập nhật trạng thái slot vào DB +
 * bắn WebSocket để bản đồ FE đổi màu ngay; (2) giám sát riêng cho zone
 * MONTHLY - phát hiện xe vãng lai đỗ lụi vào chỗ thuê tháng rồi cảnh báo
 * quản lý.
 * @Dependencies:
 * - SlotRepository (Local)
 * - ParkingSessionRepository (Local)
 * - WebSocketEventPublisher (Local)
 */
package com.pbms.modules.operation.service;

// =========================================================================
// PHẦN 1: ENTITY LIÊN QUAN
// =========================================================================
import com.pbms.modules.infrastructure.domain.Slot; // Ô đỗ xe cụ thể (mỗi ô gắn 1 cảm biến), có trạng thái EMPTY/OCCUPIED/DISABLED.
import com.pbms.modules.infrastructure.domain.Zone; // Khu vực đỗ xe chứa nhiều ô, mang loại chức năng WALK_IN/MONTHLY/BACKUP.

// =========================================================================
// PHẦN 2: CÁC REPOSITORY (KẾT NỐI DATABASE)
// =========================================================================
import com.pbms.modules.infrastructure.repository.SlotRepository; // Thao tác bảng Slot, ở đây dùng để ĐẾM số ô đang có xe trong các Zone vé tháng.
import com.pbms.modules.operation.repository.ParkingSessionRepository; // Thao tác bảng lượt đỗ, dùng để đếm số xe vé tháng ĐANG THỰC SỰ ở trong bãi.

// =========================================================================
// PHẦN 3: SERVICE NỘI BỘ VÀ THƯ VIỆN SPRING BOOT
// =========================================================================
import com.pbms.modules.infrastructure.service.WebSocketEventPublisher; // "Ông MC" duy nhất được cầm micro WebSocket — mọi thông báo realtime đều đi qua đây.
import org.springframework.stereotype.Service; // Đánh dấu đây là tầng nghiệp vụ, Spring quản lý làm 1 Bean.
import org.springframework.transaction.annotation.Transactional; // Gói 1 hàm vào 1 giao dịch DB: lỗi giữa chừng thì rollback toàn bộ.

// =========================================================================
// PHẦN 4: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal; // Kiểu số thập phân chính xác cao, dùng cho tỷ lệ % lấp đầy.
import java.util.HashMap; // Cấu trúc "khóa - giá trị", dùng để dựng gói dữ liệu (payload) gửi qua WebSocket.
import java.util.Map;

/**
 * =========================================================================================
 * BỘ GIÁM SÁT KHU VỰC ĐỖ XE THEO CẢM BIẾN (ZONE MONITORING SERVICE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đây là nơi tiếp nhận và xử lý MỖI tín hiệu từ cảm biến gắn dưới từng ô đỗ.
 * Cảm biến chỉ biết nói đúng 2 điều: "tôi là ô số mấy" và "trên tôi có xe hay
 * không". Từ 2 mẩu tin thô đó, service này làm 3 việc:
 * 1. CẬP NHẬT + VẼ LẠI BẢN ĐỒ: ghi trạng thái mới xuống DB rồi bắn WebSocket
 *    để sơ đồ ô đỗ trên màn hình nhân viên tự đổi màu tức thì.
 * 2. NẠP SỐ LIỆU CHO BIỂU ĐỒ XU HƯỚNG: tính lại % lấp đầy của Zone rồi báo cho
 *    `ZoneOccupancyTracker` ghi nhớ nếu đây là mức cao nhất trong giờ. Đây là
 *    nơi duy nhất biết "độ đầy vừa đổi", nên nếu bỏ qua bước này thì toàn bộ
 *    tính năng xu hướng theo giờ mất số liệu đỉnh điểm (xem chi tiết ở thân hàm).
 * 3. PHÁT HIỆN ĐỖ LẬU Ở KHU VỰC VÉ THÁNG: suy luận xem có xe nào đang chiếm
 *    chỗ dành riêng cho khách vé tháng hay không, nếu có thì hú còi báo động.
 *
 * TƯ TƯỞNG THUẬT TOÁN PHÁT HIỆN ĐỖ LẬU (ĐẾM CHÉO 2 NGUỒN SỐ LIỆU):
 * Cảm biến KHÔNG đọc được biển số, nên không thể biết chiếc xe đang nằm trên ô
 * là xe vé tháng hay xe lạ. Cách lách: so hai con số đếm từ hai nguồn độc lập.
 * - Nguồn A (thế giới vật lý — cảm biến): có bao nhiêu ô trong các Zone MONTHLY
 *   của cùng loại xe đang có vật đè lên.
 * - Nguồn B (thế giới giấy tờ — DB): có bao nhiêu xe vé tháng CÒN HẠN đang có
 *   lượt đỗ mở (đã check-in, chưa check-out) với loại xe đó.
 * Nếu A > B nghĩa là số xe nằm trên chỗ vé tháng NHIỀU HƠN số xe vé tháng hợp
 * lệ đang ở trong bãi -> phần dư chắc chắn là xe đỗ lậu -> bắn cảnh báo đỏ.
 *
 * LƯU Ý 1 (bug tiềm ẩn — CHƯA TRỪ số xe đã bị lập phiếu phạt):
 * Nguồn B ở đây KHÔNG trừ đi số xe đỗ lậu đã bị lập biên bản. Do thiếu bước
 * trừ này, một chiếc xe đỗ lậu ĐÃ bị lập biên bản rồi vẫn tiếp tục kích hoạt
 * cảnh báo mỗi lần có cảm biến khác trong khu vé tháng báo tín hiệu — nhân
 * viên bị làm phiền lặp lại cho cùng 1 vi phạm đã xử lý. Cách tính đúng là
 * lấy (số vé tháng còn hạn) + (số xe đã bị phạt) rồi mới so với nguồn A.
 *
 * LƯU Ý 2 (dữ liệu vào không được kiểm tra — nguồn của vài lỗi tiềm ẩn):
 * Hàm `processSensorEvent` tin tuyệt đối vào tham số nhận được:
 * - `sensorId` được ép thẳng sang số; cảm biến gửi mã không phải số -> vỡ
 *   `NumberFormatException` -> API trả lỗi 500.
 * - `status` được ghi thẳng vào DB mà không kiểm tra nằm trong tập hợp lệ
 *   (EMPTY/OCCUPIED/DISABLED).
 * - `slot.getZone()` và `zone.getVehicleType()` không kiểm tra null; ô đỗ mồ
 *   côi (chưa gán Zone) hoặc Zone chưa gán loại xe sẽ gây `NullPointerException`.
 * =========================================================================================
 */
@Service
public class ZoneMonitoringService {

    private final SlotRepository slotRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final ZoneRoutingService zoneRoutingService; // Dùng để tính % lấp đầy hiện tại của Zone sau mỗi tín hiệu cảm biến.
    private final ZoneOccupancyTracker zoneOccupancyTracker; // Bộ nhớ đỉnh điểm lấp đầy trong giờ, phục vụ biểu đồ xu hướng.

    /**
     * Constructor viết tay để Spring "tiêm" các phụ thuộc vào (Dependency Injection).
     * Khác các service anh em dùng Lombok `@RequiredArgsConstructor` sinh tự
     * động, file này khai báo thủ công — hai cách cho kết quả như nhau.
     */
    public ZoneMonitoringService(SlotRepository slotRepository,
            ParkingSessionRepository parkingSessionRepository, WebSocketEventPublisher eventPublisher,
            ZoneRoutingService zoneRoutingService, ZoneOccupancyTracker zoneOccupancyTracker) {
        this.slotRepository = slotRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.eventPublisher = eventPublisher;
        this.zoneRoutingService = zoneRoutingService;
        this.zoneOccupancyTracker = zoneOccupancyTracker;
    }

    /**
     * =========================================================================
     * HÀM: XỬ LÝ MỘT TÍN HIỆU CẢM BIẾN Ô ĐỖ
     * =========================================================================
     * MỤC ĐÍCH:
     * Điểm vào duy nhất của service này, được gọi từ API
     * `POST /operation/iot/hardware/sensors/update` (`IotHardwareController`).
     * Nhận tín hiệu thô của cảm biến -> lưu DB -> báo realtime -> kiểm tra vi phạm.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. ÁNH XẠ CẢM BIẾN SANG Ô ĐỖ: quy ước của đồ án là mã cảm biến CHÍNH LÀ id
     *    của ô đỗ, nên chỉ cần ép chuỗi sang số. Hệ thống thật sẽ cần một bảng
     *    ánh xạ riêng (1 cảm biến có thể đổi vị trí, thay mới...).
     * 2. Tìm ô đỗ theo id, không thấy -> ném lỗi.
     * 3. Ghi trạng thái mới do cảm biến báo xuống DB.
     * 4. Lấy Zone chứa ô đỗ này — cần cho cả bước bắn tin lẫn bước kiểm tra vi phạm.
     * 5. GHI NHẬN ĐỈNH ĐIỂM: tính lại % lấp đầy hiện tại của Zone rồi đưa cho
     *    `ZoneOccupancyTracker` — nó chỉ lưu nếu con số này CAO HƠN mức cao nhất
     *    đã ghi trong giờ. Job chốt giờ sẽ lấy đúng con số đó vẽ lên biểu đồ.
     * 6. VẼ LẠI BẢN ĐỒ: dựng gói dữ liệu gồm id ô, id Zone và trạng thái mới, bắn
     *    lên kênh `/topic/slots/status` với nhãn `SLOT_UPDATED`. Đây là tin
     *    thường (không phải báo động), frontend chỉ đổi màu ô tương ứng.
     * 7. KIỂM TRA ĐỖ LẬU — chỉ chạy khi HỘI ĐỦ 2 điều kiện: tín hiệu là
     *    `OCCUPIED` (có xe vừa đè lên; xe rời đi thì không thể sinh vi phạm mới)
     *    VÀ ô đỗ này thuộc Zone loại `MONTHLY` (khu dành riêng khách vé tháng —
     *    khu vãng lai ai đỗ cũng hợp lệ nên không cần xét).
     * 8. ĐẾM NGUỒN A: tổng số ô đang `OCCUPIED` thuộc MỌI Zone `MONTHLY` có cùng
     *    loại xe. Cố ý đếm theo LOẠI XE chứ không theo từng Zone riêng lẻ: khách
     *    vé tháng ô tô có thể đỗ ở bất kỳ Zone MONTHLY nào của ô tô, nên phải
     *    gộp cả nhóm lại mới so sánh đúng.
     * 9. ĐẾM NGUỒN B: số lượt đỗ đang `ACTIVE` của loại xe đó mà biển số nằm
     *    trong danh sách vé tháng còn hiệu lực tại thời điểm hiện tại (vé
     *    `ACTIVE` và chưa quá hạn `validUntil`). Đây chính là "số xe vé tháng
     *    hợp lệ đang thực sự nằm trong bãi".
     * 10. SO SÁNH: nếu A > B -> tồn tại xe chiếm chỗ vé tháng mà không có quyền ->
     *    dựng gói cảnh báo kèm tên Zone và câu mô tả đầy đủ 2 con số, rồi bắn
     *    lên kênh `/topic/alerts` bằng `broadcastCriticalEvent` (gắn cờ
     *    `CRITICAL` để frontend hú còi / bật popup đỏ, khác hẳn tin thường ở
     *    bước 6). Nếu A <= B -> mọi thứ bình thường, không làm gì cả.
     *
     * LƯU Ý: cảnh báo ở đây chỉ là thông báo tức thời qua WebSocket — hàm này
     * KHÔNG tự lập phiếu sự cố (IncidentTicket). Việc lập biên bản do nhân viên
     * quyết định thủ công sau khi nhận cảnh báo.
     */
    @Transactional
    public void processSensorEvent(String sensorId, String status) {
        // In a real system, sensorId might be mapped to a Slot. For this demo, let's
        // assume sensorId == slot.id
        Long slotId = Long.parseLong(sensorId);
        Slot slot = slotRepository.findById(slotId).orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        slot.setStatus(status);
        slotRepository.save(slot);

        Zone zone = slot.getZone();

        // GHI NHẬN ĐỈNH ĐIỂM LẤP ĐẦY (HIGH-WATER MARK) CHO BIỂU ĐỒ XU HƯỚNG:
        // Đây là nơi DUY NHẤT trong hệ thống biết được "độ đầy vừa thay đổi", nên
        // bắt buộc phải nạp số liệu cho `ZoneOccupancyTracker` tại đây. Trước kia
        // việc này nằm ở endpoint `/infrastructure/slots/iot-update` — mà endpoint
        // đó không FE/simulator nào gọi và nay đã bị xóa, khiến `updateOccupancy`
        // mất sạch nơi gọi. Hậu quả: kho đỉnh điểm luôn rỗng, nên job chốt giờ
        // (`ZoneTrendSchedulingService`) ghi vào biểu đồ độ đầy ĐO TẠI ĐẦU GIỜ chứ
        // không phải đỉnh điểm TRONG giờ — Zone đầy 100% lúc 8h30 rồi trống lúc
        // 8h59 vẫn bị vẽ theo con số lúc 8h00, làm biểu đồ báo thấp hơn thực tế và
        // đường ngưỡng đỏ 90% gần như không bao giờ chạm tới.
        BigDecimal currentOccupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
        zoneOccupancyTracker.updateOccupancy(zone.getId(), currentOccupancy);

        // Broadcast the real-time slot update to the Grid Map
        Map<String, Object> slotPayload = new HashMap<>();
        slotPayload.put("slotId", slot.getId());
        slotPayload.put("zoneId", zone.getId());
        slotPayload.put("status", slot.getStatus());
        eventPublisher.broadcastEvent("/topic/slots/status", "SLOT_UPDATED", slotPayload);

        // Algorithm: Dynamic Zone Monitoring for MONTHLY zones
        // Chỉ xét khi xe VỪA ĐỖ VÀO (OCCUPIED) một ô thuộc khu vé tháng — hai
        // điều kiện này lọc bỏ toàn bộ tín hiệu không thể sinh ra vi phạm mới.
        if ("OCCUPIED".equals(status) && "MONTHLY".equals(zone.getFunctionType())) {
            // NGUỒN A (cảm biến): số ô đang có xe trong TẤT CẢ Zone vé tháng
            // của cùng loại xe — gộp cả nhóm vì khách vé tháng được đỗ ở bất kỳ
            // Zone MONTHLY nào phù hợp loại xe của mình.
            long occupiedMonthlySlots = slotRepository.countByFunctionTypeAndVehicleTypeIdAndStatus("MONTHLY",
                    zone.getVehicleType().getId(), "OCCUPIED");
            // NGUỒN B (giấy tờ): số lượt đỗ đang mở của loại xe này mà biển số
            // khớp một vé tháng còn hiệu lực tại thời điểm `now`.
            long activeMonthlyCars = parkingSessionRepository
                    .countActiveMonthlyCarsByVehicleType(zone.getVehicleType().getId(), com.pbms.common.utils.TimeProvider.now());

            // Số ô bị chiếm nhiều hơn số xe hợp lệ -> phần dư là xe đỗ lậu.
            if (occupiedMonthlySlots > activeMonthlyCars) {
                // Alert! Violation detected
                Map<String, Object> alertPayload = new HashMap<>();
                alertPayload.put("zoneId", zone.getId());
                alertPayload.put("zoneName", zone.getZoneName());
                alertPayload.put("message", "Overload Alert: There are " + occupiedMonthlySlots
                        + " slots occupied in the Monthly Zone (type " + zone.getVehicleType().getTypeName()
                        + "), but only " + activeMonthlyCars
                        + " monthly cars of this type are currently in the parking lot! Walk-in vehicles might have parked improperly.");

                // Dùng bản "báo động đỏ" (gắn cờ CRITICAL) chứ không phải tin
                // thường như ở bước cập nhật bản đồ phía trên.
                eventPublisher.broadcastCriticalEvent("/topic/alerts", "ZONE_VIOLATION", alertPayload);
            }
        }
    }
}
