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

    private void broadcastUserUpdate(String action, Long userId) {
        messagingTemplate.convertAndSend("/topic/users", "{\"action\": \"" + action + "\", \"userId\": " + userId + "}");
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
                String searchPattern = "%" + keyword.toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), searchPattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchPattern);
                predicates.add(cb.or(nameMatch, emailMatch));
            }

            if (role != null && !role.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null && !status.trim().isEmpty()) {
                if (status.equalsIgnoreCase("ACTIVE")) {
                    predicates.add(cb.equal(root.get("status"), "ACTIVE"));
                } else if (status.equalsIgnoreCase("INACTIVE")) {
                    predicates.add(cb.equal(root.get("status"), "INACTIVE"));
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
            dto.setIsVerified(user.getIsVerified());
            dto.setIsActive("ACTIVE".equals(user.getStatus()));
            return dto;
        });
    }

    @Transactional
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
                .isVerified(true)
                .status("ACTIVE")
                .build();
        
        userRepository.save(user);
        broadcastUserUpdate("CREATE", user.getId());

        try {
            String htmlContent = "<p>Your account has been created.</p><p>Email: <b>" + request.getEmail() + "</b></p><p>Password: <b>" + rawPassword + "</b></p><p>Please change it after logging in.</p>";
            emailService.sendHtmlEmail(request.getEmail(), "Your PBMS Account Created", htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }
    }

    @Transactional
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
        broadcastUserUpdate("UPDATE", user.getId());
    }

    @Transactional
    public void changeUserStatus(Long id, boolean activate, String currentUserEmail) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Prevent self-lock
        if (!activate && user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new IllegalArgumentException("You don't know if I can lock you personally.");
        }

        user.setStatus(activate ? "ACTIVE" : "INACTIVE");
        userRepository.save(user);
        broadcastUserUpdate("STATUS_CHANGE", user.getId());
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
    public void resetPassword(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        String rawPassword = generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        broadcastUserUpdate("RESET_PASSWORD", user.getId());

        try {
            String htmlContent = "<p>Your password has been reset by the Admin.</p><p>New Password: <b>" + rawPassword + "</b></p><p>Please change it after logging in.</p>";
            emailService.sendHtmlEmail(user.getEmail(), "Your PBMS Password Reset", htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send reset password email: " + e.getMessage());
        }
    }
}

