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
 * @created 03/05/2026
 */
package com.pbms.modules.identity.service;

import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.dto.UserDTO;
import com.pbms.modules.identity.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pbms.common.service.EmailService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.messagingTemplate = messagingTemplate;
    }

    private void broadcastUserUpdate(String action, Long userId, String email) {
        messagingTemplate.convertAndSend("/topic/identity/users", "{\"action\": \"" + action + "\", \"userId\": " + userId + ", \"email\": \"" + email + "\"}");
    }

    private String generateRandomPassword() {
        int length = 8;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public Page<UserDTO.UserResponse> searchUsers(String keyword, String role, String status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                String searchPattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), searchPattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchPattern);
                predicates.add(cb.or(nameMatch, emailMatch));
            }

            if (role != null && !role.trim().isEmpty()) {
                String normalizedRole = role.startsWith("ROLE_") ? role.substring(5) : role;
                predicates.add(cb.or(
                        cb.equal(root.get("role"), normalizedRole),
                        cb.equal(root.get("role"), "ROLE_" + normalizedRole)
                ));
            }

            if (status != null && !status.trim().isEmpty()) {
                if (status.equalsIgnoreCase("ACTIVE")) {
                    predicates.add(cb.equal(root.get("status"), "ACTIVE"));
                } else if (status.equalsIgnoreCase("INACTIVE") || status.equalsIgnoreCase("LOCKED")) {
                    predicates.add(cb.notEqual(root.get("status"), "ACTIVE"));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable).map(user -> {
            UserDTO.UserResponse dto = new UserDTO.UserResponse();
            dto.setId(user.getId());
            dto.setName(user.getFullName());
            dto.setEmail(user.getEmail());
            dto.setRole(user.getRole());
            dto.setIsActive("ACTIVE".equals(user.getStatus()));
            return dto;
        });
    }

    @Transactional
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
    public void createUser(UserDTO.CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        String rawPassword = generateRandomPassword();
        User user = User.builder()
                .fullName(request.getName())
                .email(request.getEmail())
                .role(request.getRole())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status("ACTIVE")
                .build();
        
        userRepository.save(user);
        broadcastUserUpdate("CREATE", user.getId(), user.getEmail());

        try {
            String htmlContent = "<p>Your account has been created.</p><p>Email: <b>" + request.getEmail() + "</b></p><p>Password: <b>" + rawPassword + "</b></p><p>Please change it after logging in.</p>";
            emailService.sendHtmlEmail(request.getEmail(), "Your PBMS Account Created", htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }
    }

    @Transactional
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
    public void updateUser(Long id, UserDTO.UpdateUserRequest request, String currentUserEmail) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate role value
        String normalizedRole = normalizeRole(request.getRole());
        if (!isValidRole(normalizedRole)) {
            throw new IllegalArgumentException("Error occurred: " + request.getRole());
        }

        user.setFullName(request.getName());
        user.setRole(normalizedRole);
        userRepository.save(user);
        broadcastUserUpdate("UPDATE", user.getId(), user.getEmail());
    }

    @Transactional
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
    public void changeUserStatus(Long id, boolean activate, String currentUserEmail) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Prevent self-lock
        if (!activate && user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new IllegalArgumentException("You don't know if I can lock you personally.");
        }

        user.setStatus(activate ? "ACTIVE" : "INACTIVE");
        userRepository.save(user);
        broadcastUserUpdate("STATUS_CHANGE", user.getId(), user.getEmail());
    }

    /** Strip ROLE_ prefix for consistent storage */
    private String normalizeRole(String role) {
        if (role == null) return null;
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }

    private boolean isValidRole(String role) {
        return role != null && List.of("SUPER_ADMIN", "MANAGER", "STAFF", "CUSTOMER").contains(role);
    }

    @Transactional
    /**
     * =========================================================================
     * NGHIỆP VỤ: RESETPASSWORD
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho resetPassword.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public void resetPassword(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        String rawPassword = generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        broadcastUserUpdate("RESET_PASSWORD", user.getId(), user.getEmail());

        try {
            String htmlContent = "<p>Your password has been reset by the Admin.</p><p>New Password: <b>" + rawPassword + "</b></p><p>Please change it after logging in.</p>";
            emailService.sendHtmlEmail(user.getEmail(), "Your PBMS Password Reset", htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send reset password email: " + e.getMessage());
        }
    }
}

