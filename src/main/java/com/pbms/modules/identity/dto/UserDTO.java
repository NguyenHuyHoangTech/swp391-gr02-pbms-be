package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO container for user management request and response payloads.
 *               Used by identity services for user creation, update, and listing.
 * @Dependencies:
 * - Validation annotations (jakarta.validation.constraints)
 */
public class UserDTO {

    @Data
    public static class CreateUserRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Role is required")
        private String role;
    }

    @Data
    public static class UpdateUserRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Role is required")
        private String role;
    }

    @Data
    public static class UserResponse {
        private Long id;
        private String name;
        private String email;
        private String role;
        private Boolean isVerified;
        private Boolean isActive;
    }
}

