/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ NGHIỆP VỤ (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO DỊCH VỤ VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Ký hiệu @Service báo cho Spring Boot biết class này chứa Logic lõi. 
 *   Spring sẽ khởi tạo nó thành Singleton Bean.
 * - Minh chứng 2: Dùng @RequiredArgsConstructor để tự động tiêm các Repository vào Service.
 * 
 * BƯỚC 2: BẢO ĐẢM TOÀN VẸN GIAO DỊCH (TRANSACTION MANAGEMENT)
 * - Minh chứng: Các hàm thay đổi dữ liệu được gắn @Transactional. Điều này đảm bảo 
 *   khi có lỗi xảy ra, toàn bộ thao tác DB sẽ được Rollback, không gây rác dữ liệu.
 * 
 * BƯỚC 3: THỰC THI LOGIC NGHIỆP VỤ
 * - Minh chứng: Gọi các hàm từ Repository (như indById, save) để tương tác 
 *   trực tiếp với CSDL, xử lý các ngoại lệ (Exception) và trả về DTO cho Controller.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.dto.AuditLogDTO;
import com.pbms.modules.system.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
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
    public Page<AuditLogDTO> getLogs(String action, String resource, String email, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findWithFilters(action, resource, email, startDate, endDate, pageable)
                .map(this::mapToDTO);
    }

    private AuditLogDTO mapToDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .action(log.getAction())
                .resource(log.getResource())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .actor(log.getActor() != null ? 
                       AuditLogDTO.ActorDTO.builder().email(log.getActor().getEmail()).build() : null)
                .build();
    }
}

