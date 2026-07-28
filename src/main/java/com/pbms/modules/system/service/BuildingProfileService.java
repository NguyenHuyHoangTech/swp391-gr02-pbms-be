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

import com.pbms.modules.system.domain.BuildingProfile;
import com.pbms.modules.system.repository.BuildingProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuildingProfileService {

    private final BuildingProfileRepository repository;

    public BuildingProfileService(BuildingProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * =========================================================================
     * NGHIỆP VỤ: GETPROFILE
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho getProfile.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public BuildingProfile getProfile() {
        return repository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Building profile not found in database"));
    }

    @Transactional
    /**
     * =========================================================================
     * NGHIỆP VỤ: UPDATEPROFILE
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho updateProfile.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public BuildingProfile updateProfile(BuildingProfile updateData) {
        BuildingProfile existingProfile = getProfile();
        
        // Singleton constraint: Always strictly use ID 1, ignore any ID from updateData
        existingProfile.setName(updateData.getName());
        existingProfile.setAddress(updateData.getAddress());
        existingProfile.setHotline(updateData.getHotline());
        existingProfile.setIs247(updateData.getIs247());
        existingProfile.setOperatingStart(updateData.getOperatingStart());
        existingProfile.setOperatingEnd(updateData.getOperatingEnd());
        existingProfile.setRules(updateData.getRules());
        
        return repository.save(existingProfile);
    }
}

