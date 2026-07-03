package com.pbms.modules.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO representing the authentication response returned after successful identity actions.
 *               Contains access token, user profile, and account setup flags.
 * @Dependencies:
 * - Lombok (lombok)
 */
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
    private boolean needsPasswordSetup;
}


