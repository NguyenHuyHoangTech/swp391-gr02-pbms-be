/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: REST Controller cung cấp các API quản lý vé tháng (Monthly Ticket).
 *               Bao gồm các chức năng: đăng ký mới, gia hạn, đổi biển số xe, và cấu hình ngưỡng cảnh báo/giảm giá.
 * @Dependencies: 
 * - MonthlyTicketService: Xử lý logic nghiệp vụ chính của vé tháng.
 * - SystemConfigService & SystemConfigRepository: Truy xuất và lưu trữ cấu hình hệ thống (ngưỡng cảnh báo, chiết khấu).
 */
package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.MonthlyTicketDTO;
import com.pbms.modules.operation.service.MonthlyTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operation/monthly-tickets")
@RequiredArgsConstructor
public class MonthlyTicketController {

    private final MonthlyTicketService monthlyTicketService;

    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final com.pbms.modules.system.repository.SystemConfigRepository configRepo;

    /**
     * API: Lấy danh sách tất cả các vé tháng đang có trong hệ thống.
     * Logic mã giả:
     * 1. Gọi hàm monthlyTicketService.getAllTickets() để lấy danh sách DTO.
     * 2. Bọc vào ApiResponse và trả về cho client.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MonthlyTicketDTO>>> getAllTickets() {
        return ResponseEntity.ok(ApiResponse.success(
                monthlyTicketService.getAllTickets(),
                "Lấy danh sách vé tháng thành công"
        ));
    }

    /**
     * API: Đăng ký một vé tháng mới.
     * Có ghi log hệ thống (LogAudit) để theo dõi.
     * Logic mã giả:
     * 1. Nhận payload JSON chứa thông tin đăng ký (chủ xe, biển số, loại xe, thời hạn, v.v.).
     * 2. Gọi hàm monthlyTicketService.createTicket() để xử lý tạo vé mới.
     * 3. Trả về kết quả cho client.
     */
    @com.pbms.common.annotation.LogAudit(action = "CREATE", resource = "MonthlyTicket", description = "Tạo vé tháng mới")
    @PostMapping
    public ResponseEntity<ApiResponse<MonthlyTicketDTO>> createTicket(@RequestBody java.util.Map<String, Object> payload) {
        try {
            MonthlyTicketDTO dto = monthlyTicketService.createTicket(payload);
            return ResponseEntity.ok(ApiResponse.success(dto, "Đăng ký thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Gia hạn vé tháng đã có.
     * Logic mã giả:
     * 1. Nhận ID của vé từ URL.
     * 2. Lấy số tháng gia hạn (duration) và phương thức thanh toán từ payload. Mặc định duration=1, paymentMethod=CASH.
     * 3. Gọi hàm renewTicket() trong service.
     * 4. Trả về kết quả vé sau khi được gia hạn.
     */
    @com.pbms.common.annotation.LogAudit(action = "UPDATE", resource = "MonthlyTicket", description = "Gia hạn vé tháng")
    @PutMapping("/{id}/renew")
    public ResponseEntity<ApiResponse<MonthlyTicketDTO>> renewTicket(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload) {
        try {
            int duration = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString()) : 1;
            String paymentMethod = payload.get("paymentMethod") != null ? payload.get("paymentMethod").toString() : "CASH";
            MonthlyTicketDTO dto = monthlyTicketService.renewTicket(id, duration, paymentMethod, null);
            return ResponseEntity.ok(ApiResponse.success(dto, "Gia hạn vé thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Cập nhật biển số xe cho vé tháng (ví dụ khách hàng đổi xe mới nhưng giữ nguyên vé).
     * Logic mã giả:
     * 1. Nhận ID vé từ URL và biển số mới từ payload.
     * 2. Kiểm tra tính hợp lệ của biển số mới.
     * 3. Gọi hàm updateTicketPlate() trong service để lưu thay đổi.
     * 4. Trả về thông tin vé sau khi cập nhật.
     */
    @com.pbms.common.annotation.LogAudit(action = "UPDATE", resource = "MonthlyTicket", description = "Cập nhật biển số vé tháng")
    @PutMapping("/{id}/plate")
    public ResponseEntity<ApiResponse<MonthlyTicketDTO>> updatePlate(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String newPlate = payload.get("plate");
            if (newPlate == null || newPlate.isBlank()) {
                throw new IllegalArgumentException("Vui lòng cung cấp biển số xe");
            }
            MonthlyTicketDTO dto = monthlyTicketService.updateTicketPlate(id, newPlate);
            return ResponseEntity.ok(ApiResponse.success(dto, "Cập nhật biển số thành công"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Lấy cấu hình ngưỡng sắp hết hạn của vé tháng (tính bằng số ngày trước khi hết hạn).
     * Logic mã giả:
     * 1. Đặt giá trị mặc định threshold = 90 ngày.
     * 2. Gọi systemConfigService để lấy giá trị MONTHLY_TICKET_ALERT_THRESHOLD từ database.
     * 3. Trả về giá trị cấu hình cho hệ thống cảnh báo.
     */
    @GetMapping("/config-threshold")
    public ResponseEntity<ApiResponse<Integer>> getThreshold() {
        int threshold = 90;
        try {
            String val = systemConfigService.getConfigByKey("MONTHLY_TICKET_ALERT_THRESHOLD").getConfigValue();
            if (val != null) threshold = Integer.parseInt(val);
        } catch (Exception e) {}
        return ResponseEntity.ok(ApiResponse.success(threshold, "Thành công"));
    }

    /**
     * API: Cập nhật cấu hình ngưỡng cảnh báo hết hạn vé tháng.
     * Logic mã giả:
     * 1. Nhận ngưỡng (threshold) mới từ payload.
     * 2. Tìm record cấu hình trong cơ sở dữ liệu. Nếu chưa có thì tạo mới entity SystemConfig.
     * 3. Gán giá trị configValue và lưu lại.
     */
    @com.pbms.common.annotation.LogAudit(action = "UPDATE", resource = "SystemConfig", description = "Cập nhật ngưỡng cảnh báo vé tháng")
    @PutMapping("/config-threshold")
    public ResponseEntity<ApiResponse<Integer>> updateThreshold(@RequestBody java.util.Map<String, Integer> payload) {
        Integer threshold = payload.get("threshold");
        if (threshold == null) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Vui lòng nhập ngưỡng (threshold)"));
        
        try {
            com.pbms.modules.system.domain.SystemConfig config = configRepo.findByConfigKey("MONTHLY_TICKET_ALERT_THRESHOLD")
                    .orElseGet(() -> {
                        com.pbms.modules.system.domain.SystemConfig c = new com.pbms.modules.system.domain.SystemConfig();
                        c.setConfigKey("MONTHLY_TICKET_ALERT_THRESHOLD");
                        return c;
                    });
            config.setConfigValue(String.valueOf(threshold));
            configRepo.save(config);
            return ResponseEntity.ok(ApiResponse.success(threshold, "Cập nhật cấu hình thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Lấy danh sách tỷ lệ giảm giá khi khách hàng mua vé nhiều tháng.
     * Logic mã giả:
     * 1. Khởi tạo một Map mặc định chứa các mức giảm giá: 1 tháng = 0%, 3 tháng = 5%, 6 tháng = 10%, 12 tháng = 15%.
     * 2. Truy xuất từng cấu hình trong database (MONTHLY_DISCOUNT_1, 3, 6, 12).
     * 3. Nếu tìm thấy cấu hình ghi đè trong database, cập nhật lại Map.
     * 4. Trả về cho client mức giảm giá áp dụng hiện tại.
     */
    @GetMapping("/config-discounts")
    public ResponseEntity<ApiResponse<java.util.Map<String, Double>>> getDiscounts() {
        java.util.Map<String, Double> discounts = new java.util.HashMap<>();
        discounts.put("1", 0.0);
        discounts.put("3", 0.05);
        discounts.put("6", 0.10);
        discounts.put("12", 0.15);
        
        try {
            String val1 = systemConfigService.getConfigByKey("MONTHLY_DISCOUNT_1").getConfigValue();
            if (val1 != null) discounts.put("1", Double.parseDouble(val1));
            String val3 = systemConfigService.getConfigByKey("MONTHLY_DISCOUNT_3").getConfigValue();
            if (val3 != null) discounts.put("3", Double.parseDouble(val3));
            String val6 = systemConfigService.getConfigByKey("MONTHLY_DISCOUNT_6").getConfigValue();
            if (val6 != null) discounts.put("6", Double.parseDouble(val6));
            String val12 = systemConfigService.getConfigByKey("MONTHLY_DISCOUNT_12").getConfigValue();
            if (val12 != null) discounts.put("12", Double.parseDouble(val12));
        } catch (Exception e) {}
        
        return ResponseEntity.ok(ApiResponse.success(discounts, "Thành công"));
    }

    /**
     * API: Cập nhật tỷ lệ giảm giá mua nhiều tháng.
     * Logic mã giả:
     * 1. Nhận một JSON Object chứa các cặp [số tháng]: [tỷ lệ].
     * 2. Lặp qua từng entry, tạo key tương ứng (ví dụ MONTHLY_DISCOUNT_3).
     * 3. Tìm hoặc tạo mới entity SystemConfig.
     * 4. Ép kiểu giá trị mới về Double và lưu lại.
     */
    @com.pbms.common.annotation.LogAudit(action = "UPDATE", resource = "SystemConfig", description = "Cập nhật tỷ lệ giảm giá vé tháng")
    @PostMapping("/config-discounts")
    public ResponseEntity<ApiResponse<Void>> setDiscounts(
            @RequestBody java.util.Map<String, Object> payload) {
        try {
            for (java.util.Map.Entry<String, Object> entry : payload.entrySet()) {
                String key = "MONTHLY_DISCOUNT_" + entry.getKey();
                com.pbms.modules.system.domain.SystemConfig config = configRepo.findByConfigKey(key)
                        .orElseGet(() -> {
                            com.pbms.modules.system.domain.SystemConfig c = new com.pbms.modules.system.domain.SystemConfig();
                            c.setConfigKey(key);
                            return c;
                        });
                double val = 0.0;
                if (entry.getValue() instanceof Number) {
                    val = ((Number) entry.getValue()).doubleValue();
                } else if (entry.getValue() instanceof String) {
                    val = Double.parseDouble((String) entry.getValue());
                }
                config.setConfigValue(String.valueOf(val));
                configRepo.save(config);
            }
            return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật giảm giá thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }
}