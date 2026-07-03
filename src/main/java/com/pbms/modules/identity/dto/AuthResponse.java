package com.pbms.modules.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String role;
    private String fullName;
    private boolean hasPassword;
    private boolean linkedGoogle;
    private boolean needsPasswordSetup; // true for new Google users
}


