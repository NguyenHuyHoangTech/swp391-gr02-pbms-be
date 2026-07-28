/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ REQUEST CỦA HỆ THỐNG (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO BỘ ĐIỀU PHỐI VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Khai báo @RestController báo cho Spring Boot biết class này là một Web API.
 * - Minh chứng 2: Khai báo @RequiredArgsConstructor (hoặc Constructor) để Spring Boot 
 *   tự động tiêm các Service vào biến cục bộ (Constructor Injection).
 * 
 * BƯỚC 2: TIẾP NHẬN REQUEST TỪ CLIENT (DISPATCHER SERVLET & HANDLER MAPPING)
 * - Minh chứng 1: Khai báo @RequestMapping ở đầu class quy định Gốc của URL.
 * - Minh chứng 2: Các hàm được đánh dấu @PostMapping, @GetMapping, @PutMapping 
 *   là bằng chứng cho việc HandlerMapping sẽ định tuyến chính xác mọi Request vào đúng hàm.
 * 
 * BƯỚC 3: RÀNG BUỘC VÀ CHUYỂN ĐỔI DỮ LIỆU (DESERIALIZATION)
 * - Minh chứng: Các tham số @RequestBody, @PathVariable, @RequestParam kích hoạt 
 *   thư viện Jackson đọc chuỗi văn bản JSON thành dạng Object Java.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.controller;

import com.pbms.modules.system.service.DataMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/system/migration")
@RequiredArgsConstructor
public class DataMigrationController {
    
    private final DataMigrationService migrationService;

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * =========================================================================
     * NGHIỆP VỤ: RUNMIGRATION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho runMigration.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> runMigration() {
        migrationService.runMigration();
        return ResponseEntity.ok(ApiResponse.success(null, "Migration triggered successfully"));
    }
}
