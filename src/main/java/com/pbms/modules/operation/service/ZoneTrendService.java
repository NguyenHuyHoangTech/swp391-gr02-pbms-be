/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Lưu và truy vấn xu hướng lấp đầy theo giờ của từng zone, phục
 * vụ biểu đồ lịch sử trên dashboard manager. recordZoneTrend ghi/cập nhật
 * đỉnh; getZoneTrends dựng ma trận (ngày x giờ x zone) cho FE vẽ biểu đồ.
 * @Dependencies:
 * - ZoneHourlyTrendRepository (Local, module incident)
 * - ZoneRepository (Local)
 * - ZoneRoutingService (Local)
 */
package com.pbms.modules.operation.service;

// =========================================================================
// PHẦN 1: ENTITY VÀ DTO LIÊN QUAN
// =========================================================================
import com.pbms.modules.operation.domain.ZoneHourlyTrend; // Entity lưu 1 dòng lịch sử "Zone X đạt đỉnh Y% trong giờ Z" xuống Database.
import com.pbms.modules.operation.dto.ZoneTrendDTO; // Gói dữ liệu trả về cho Frontend vẽ biểu đồ.

// =========================================================================
// PHẦN 2: CÁC REPOSITORY (KẾT NỐI DATABASE)
// =========================================================================
import com.pbms.modules.operation.repository.ZoneHourlyTrendRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK VÀ SPRING BOOT
// =========================================================================
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

// =========================================================================
// PHẦN 4: THƯ VIỆN JAVA CHUẨN (THỜI GIAN)
// =========================================================================
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * =========================================================================================
 * DỊCH VỤ GHI NHẬN VÀ TRUY VẤN LỊCH SỬ XU HƯỚNG LẤP ĐẦY THEO GIỜ (ZONE HOURLY TREND)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Class này có 2 nhiệm vụ tách biệt:
 * 1. GHI (`recordZoneTrend`): được `ZoneTrendSchedulingService` gọi định kỳ
 *    (mỗi giờ) để chốt lại đỉnh điểm lấp đầy của từng Zone trong giờ vừa qua
 *    xuống bảng `zone_hourly_trends`.
 * 2. ĐỌC (`getZoneTrends`): dựng lại toàn bộ chuỗi dữ liệu theo giờ trong 1
 *    khoảng ngày, kể cả những giờ CHƯA có bản ghi lịch sử (điền 0% cho quá
 *    khứ chưa ghi, hoặc tính realtime cho giờ hiện tại), để Frontend vẽ biểu
 *    đồ liền mạch không bị đứt quãng.
 * =========================================================================================
 */
@Service
@RequiredArgsConstructor
public class ZoneTrendService {

    private final ZoneHourlyTrendRepository zoneHourlyTrendRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneRoutingService zoneRoutingService;

    /**
     * =========================================================================
     * GHI NHẬN (UPSERT) 1 ĐIỂM DỮ LIỆU XU HƯỚNG CHO 1 ZONE TRONG 1 GIỜ
     * =========================================================================
     * MÃ GIẢ CHI TIẾT:
     * 1. Nếu không truyền `timeWindow` cụ thể, mặc định lấy giờ hiện tại và
     *    làm tròn về đầu giờ (bỏ phút/giây/nano) — mỗi giờ chỉ có đúng 1 "cửa
     *    sổ" (window) đại diện, ví dụ 08:00:00.000 đại diện cho cả khung 08h-09h.
     * 2. Tìm xem đã có bản ghi (`ZoneHourlyTrend`) nào của ĐÚNG Zone này, rơi
     *    vào ĐÚNG cửa sổ giờ này chưa (dò trong khoảng [window, window+1h-1ns]).
     * 3. NẾU CHƯA CÓ: tạo mới 1 dòng với giá trị `occupancyPct` hiện tại làm
     *    giá trị khởi điểm của giờ này.
     * 4. NẾU ĐÃ CÓ: chỉ cập nhật khi giá trị mới truyền vào CAO HƠN giá trị
     *    đang lưu (giữ đúng bản chất "đỉnh điểm trong giờ", không phải giá trị
     *    tức thời cuối cùng).
     * 5. DỌN DẸP BẢN GHI TRÙNG (do Race Condition): về lý thuyết mỗi
     *    Zone+giờ chỉ nên có 1 dòng, nhưng nếu 2 lần gọi hàm này chạy gần như
     *    đồng thời (ví dụ scheduler job chạy chồng lấn), có thể lỡ tạo ra 2
     *    dòng trùng cửa sổ giờ. Đoạn này gộp lại: giữ giá trị đỉnh điểm cao
     *    nhất trong số các dòng trùng vào dòng đầu tiên, rồi xóa hết các dòng
     *    trùng còn lại — đảm bảo cuối cùng chỉ còn đúng 1 dòng/Zone/giờ.
     */
    @Transactional
    public void recordZoneTrend(Long zoneId, BigDecimal occupancyPct, LocalDateTime timeWindow) {
        LocalDateTime window = timeWindow != null ? timeWindow : com.pbms.common.utils.TimeProvider.now().withMinute(0).withSecond(0).withNano(0);

        List<ZoneHourlyTrend> trends = zoneHourlyTrendRepository.findByTimeWindowBetween(window, window.plusHours(1).minusNanos(1));
        List<ZoneHourlyTrend> matchingTrends = trends.stream().filter(t -> t.getZone().getId().equals(zoneId)).collect(Collectors.toList());
        ZoneHourlyTrend trend;

        if (matchingTrends.isEmpty()) {
            trend = ZoneHourlyTrend.builder()
                    .zone(zoneRepository.findById(zoneId).orElse(null))
                    .timeWindow(window)
                    .occupancyPct(occupancyPct)
                    .build();
            zoneHourlyTrendRepository.save(trend);
        } else {
            trend = matchingTrends.get(0);
            // Keep the peak occupancy for this hour
            if (occupancyPct.compareTo(trend.getOccupancyPct()) > 0) {
                trend.setOccupancyPct(occupancyPct);
                zoneHourlyTrendRepository.save(trend);
            }
            // Clean up duplicates caused by race conditions
            if (matchingTrends.size() > 1) {
                for (int i = 1; i < matchingTrends.size(); i++) {
                    ZoneHourlyTrend dup = matchingTrends.get(i);
                    if (dup.getOccupancyPct().compareTo(trend.getOccupancyPct()) > 0) {
                        trend.setOccupancyPct(dup.getOccupancyPct());
                        zoneHourlyTrendRepository.save(trend);
                    }
                    zoneHourlyTrendRepository.delete(dup);
                }
            }
        }
    }

    /**
     * =========================================================================
     * TRUY VẤN CHUỖI DỮ LIỆU XU HƯỚNG THEO GIỜ ĐỂ VẼ BIỂU ĐỒ
     * =========================================================================
     * MỤC ĐÍCH:
     * Trả về 1 điểm dữ liệu cho MỖI (Zone x Giờ) trong khoảng [startDate, endDate],
     * kể cả những giờ Database chưa có bản ghi lịch sử — để biểu đồ luôn liền
     * mạch 24 điểm/ngày/Zone, không bị "thủng" dữ liệu.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lấy toàn bộ bản ghi lịch sử (`ZoneHourlyTrend`) nằm trong khoảng ngày
     *    yêu cầu, và danh sách Zone đang ACTIVE (lọc thêm theo loại xe nếu có).
     * 2. Xác định "cửa sổ giờ hiện tại" (`currentHourWindow`) để biết giờ nào
     *    là quá khứ, giờ nào là "đang chạy dở" (giờ hiện tại), giờ nào là
     *    tương lai (không vẽ).
     * 3. Lặp qua TỪNG NGÀY trong khoảng, rồi TỪNG GIỜ trong ngày (0h - 23h):
     *    - Nếu giờ đó ở TƯƠNG LAI (sau giờ hiện tại) -> bỏ qua, không vẽ.
     *    - Với mỗi Zone đang active, tìm bản ghi lịch sử khớp đúng Zone + giờ
     *      này (nếu có nhiều bản ghi trùng sót lại từ trước, lấy giá trị cao
     *      nhất trong số đó).
     *    - Nếu giờ đang xét CHÍNH LÀ giờ hiện tại: không dùng số liệu lịch sử
     *      (vì giờ chưa kết thúc, đỉnh điểm chưa chốt) mà TÍNH REALTIME ngay
     *      tại thời điểm gọi API bằng `zoneRoutingService.calculateZoneOccupancy()`.
     *    - Nếu là giờ QUÁ KHỨ và có bản ghi lịch sử -> dùng giá trị đã chốt đó.
     *    - Nếu là giờ QUÁ KHỨ nhưng KHÔNG có bản ghi (job chưa từng chạy vào
     *      giờ đó, ví dụ Zone mới tạo sau) -> điền 0% để biểu đồ không bị null.
     * 4. Gộp tất cả điểm dữ liệu (Zone x Giờ) thành 1 danh sách phẳng trả về
     *    cho Frontend tự nhóm lại theo Zone khi vẽ.
     *
     * LƯU Ý HIỆU NĂNG ĐÃ BIẾT (chưa xử lý — chấp nhận được vì đây là đồ án
     * demo, số lượng Zone nhỏ): tại đúng giờ hiện tại, với MỖI Zone đang active
     * hàm đều gọi riêng `calculateZoneOccupancy()` (N lần gọi cho N Zone), và
     * bản thân hàm đó lại tự truy vấn Database nhiều lần (đếm Slot, đếm đặt
     * chỗ...) — thay vì gộp lại thành 1-2 truy vấn duy nhất cho toàn bộ Zone
     * rồi tính trên bộ nhớ (kiểu lỗi N+1 truy vấn quen thuộc).
     */
    public List<ZoneTrendDTO> getZoneTrends(LocalDate startDate, LocalDate endDate, Long vehicleTypeId) {
        LocalDateTime startWindow = startDate.atStartOfDay();
        LocalDateTime endWindow = endDate.atTime(LocalTime.MAX);

        List<ZoneHourlyTrend> trends = zoneHourlyTrendRepository.findByTimeWindowBetween(startWindow, endWindow);
        List<com.pbms.modules.infrastructure.domain.Zone> activeZones = zoneRepository.findAll().stream()
                .filter(z -> "ACTIVE".equals(z.getStatus()))
                .filter(z -> vehicleTypeId == null || (z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleTypeId)))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00 dd/MM");
        List<ZoneTrendDTO> result = new java.util.ArrayList<>();

        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        LocalDateTime currentHourWindow = now.withMinute(0).withSecond(0).withNano(0);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (int h = 0; h < 24; h++) {
                LocalDateTime window = date.atStartOfDay().plusHours(h);
                // Biểu đồ không cần vẽ trước tương lai
                if (window.isAfter(currentHourWindow)) {
                    continue;
                }
                String timeStr = window.format(formatter);
                for (com.pbms.modules.infrastructure.domain.Zone z : activeZones) {
                    List<ZoneHourlyTrend> matchingT = trends.stream()
                            .filter(tr -> tr.getZone().getId().equals(z.getId()) && tr.getTimeWindow().equals(window))
                            .collect(Collectors.toList());
                    ZoneHourlyTrend t = null;
                    if (!matchingT.isEmpty()) {
                        t = matchingT.stream().max((t1, t2) -> t1.getOccupancyPct().compareTo(t2.getOccupancyPct())).get();
                    }

                    BigDecimal occupancy = null;
                    if (window.equals(currentHourWindow)) {
                        occupancy = zoneRoutingService.calculateZoneOccupancy(z.getId());
                    } else if (t != null) {
                        occupancy = t.getOccupancyPct();
                    } else if (window.isBefore(currentHourWindow)) {
                        occupancy = BigDecimal.ZERO;
                    }

                    result.add(ZoneTrendDTO.builder()
                            .timeWindow(timeStr)
                            .zoneId(z.getId())
                            .zoneName(z.getZoneName())
                            .occupancyPct(occupancy)
                            .build());
                }
            }
        }
        return result;
    }
}
