package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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

