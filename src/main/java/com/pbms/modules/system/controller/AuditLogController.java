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

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.system.dto.AuditLogDTO;
import com.pbms.modules.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/system/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    /**
     * =========================================================================
     * NGHIỆP VỤ: GETLOGS
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho getLogs.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> getLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        
        return ResponseEntity.ok(ApiResponse.success(
                auditLogService.getLogs(action, resource, email, startDate, endDate, pageable),
                "Retrieve audit log list"
        ));
    }
}

