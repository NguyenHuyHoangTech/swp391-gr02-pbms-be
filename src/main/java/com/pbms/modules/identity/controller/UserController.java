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
 * @created 03/05/2026
 */
package com.pbms.modules.identity.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.identity.dto.UserDTO;
import com.pbms.modules.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;

@RestController
@RequestMapping("/api/v1/identity/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserDTO.UserResponse>>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("status"), Sort.Order.asc("role"), Sort.Order.desc("createdAt")));
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(keyword, role, status, pageable), "Users fetched successfully"));
    }

    @PostMapping
    @LogAudit(action = "CREATE", resource = "User", description = "Create new user")
    /**
     * =========================================================================
     * NGHIỆP VỤ: CREATEUSER
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho createUser.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> createUser(@Valid @RequestBody UserDTO.CreateUserRequest request) {
        userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", "A password has been sent to the user's email"));
    }

    @PutMapping("/{id}")
    @LogAudit(action = "UPDATE", resource = "User", description = "Update user details")
    /**
     * =========================================================================
     * NGHIỆP VỤ: UPDATEUSER
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho updateUser.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDTO.UpdateUserRequest request,
            @AuthenticationPrincipal String currentUserEmail) {
        userService.updateUser(id, request, currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", "User info updated"));
    }

    @PutMapping("/{id}/status")
    @LogAudit(action = "UPDATE", resource = "User", description = "Update user status")
    /**
     * =========================================================================
     * NGHIỆP VỤ: CHANGEUSERSTATUS
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho changeUserStatus.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> changeUserStatus(
            @PathVariable Long id,
            @RequestParam boolean activate,
            @AuthenticationPrincipal String currentUserEmail) {
        userService.changeUserStatus(id, activate, currentUserEmail);
        String action = activate ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success("User " + action + " successfully", "User status updated"));
    }

    @PutMapping("/{id}/reset-password")
    @LogAudit(action = "UPDATE", resource = "User", description = "Reset user password")
    /**
     * =========================================================================
     * NGHIỆP VỤ: RESETUSERPASSWORD
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho resetUserPassword.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> resetUserPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", "New password has been sent to the user's email"));
    }
}
