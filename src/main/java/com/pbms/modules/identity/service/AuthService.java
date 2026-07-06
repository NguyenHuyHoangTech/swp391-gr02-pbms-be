package com.pbms.modules.identity.service;

import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.dto.*;
import com.pbms.modules.identity.repository.UserRepository;

import com.pbms.common.service.EmailService;
import com.pbms.common.security.JwtProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtProvider tokenProvider;

    @Value("${google.client.id}")
    private String googleClientId;

    private static final long OTP_TTL_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;

    // In-memory OTP Store (Replaces Redis for local development without requiring Redis server)
    private final ConcurrentHashMap<String, OtpData> otpStore = new ConcurrentHashMap<>();

    private static class OtpData {
        String otpCode;
        long expiryTime;
        int attempts;
        RegisterRequest registerRequest; // Only for REGISTER purpose

        OtpData(String otpCode, long expiryTime) {
            this.otpCode = otpCode;
            this.expiryTime = expiryTime;
            this.attempts = 0;
        }

        OtpData(String otpCode, long expiryTime, RegisterRequest registerRequest) {
            this.otpCode = otpCode;
            this.expiryTime = expiryTime;
            this.attempts = 0;
            this.registerRequest = registerRequest;
        }
    }

    @Transactional
    public void register(RegisterRequest request) {
        Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        if (existingUserOpt.isPresent() && existingUserOpt.get().getIsVerified()) {
            throw new IllegalArgumentException("Email already exists");
        }

        String otp = generateOtp();
        String cacheKey = "OTP:REGISTER:" + request.getEmail();
        long expiryTime = com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + (15 * 60 * 1000); // 15 mins for registration
        
        // Save OTP and registration details to in-memory store instead of DB
        otpStore.put(cacheKey, new OtpData(otp, expiryTime, request));

        // Send Email
        String htmlContent = "<h3>Your PBMS Verification Code</h3><p>Your OTP is: <b>" + otp + "</b>. It will expire in 15 minutes.</p>";
        emailService.sendHtmlEmail(request.getEmail(), "PBMS Account Verification", htmlContent);
        
        log.info("Generated OTP for {} is {}", request.getEmail(), otp);
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String cacheKey = "OTP:" + request.getPurpose() + ":" + request.getEmail();

        OtpData otpData = otpStore.get(cacheKey);
        
        if (otpData == null || com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() > otpData.expiryTime) {
            otpStore.remove(cacheKey);
            throw new IllegalArgumentException("OTP expired or invalid");
        }

        if (!otpData.otpCode.equals(request.getOtpCode())) {
            // Increment attempts
            otpData.attempts++;
            if (otpData.attempts >= MAX_OTP_ATTEMPTS) {
                otpStore.remove(cacheKey);
                throw new IllegalArgumentException("Max OTP attempts reached. OTP invalidated.");
            }
            throw new IllegalArgumentException("Incorrect OTP Code");
        }

        // OTP is correct
        otpStore.remove(cacheKey);

        User user;
        if ("REGISTER".equals(request.getPurpose())) {
            if (otpData.registerRequest == null) {
                throw new IllegalArgumentException("Registration details expired. Please register again.");
            }
            
            Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
            if (existingUserOpt.isPresent()) {
                user = existingUserOpt.get();
                user.setPasswordHash(passwordEncoder.encode(otpData.registerRequest.getPassword()));
                user.setFullName(otpData.registerRequest.getFullName());
                user.setIsVerified(true);
                user.setStatus("ACTIVE");
            } else {
                user = User.builder()
                        .email(otpData.registerRequest.getEmail())
                        .passwordHash(passwordEncoder.encode(otpData.registerRequest.getPassword()))
                        .fullName(otpData.registerRequest.getFullName())
                        .role("CUSTOMER")
                        .isVerified(true)
                        .status("ACTIVE")
                        .build();
            }
            userRepository.save(user);
        } else {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
        }

        if ("INACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("I'm going to lock you up, you're welcome");
        }

        // Normalize role: avoid double ROLE_ prefix
        String authority = user.getRole().startsWith("ROLE_") ? user.getRole() : "ROLE_" + user.getRole();
        // Generate Real JWT Token
        String token = tokenProvider.generateToken(user.getEmail(), authority);
        
        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(token)
                .email(user.getEmail())
                .role(authority)
                .fullName(user.getFullName())
                .hasPassword(user.getPasswordHash() != null)
                .linkedGoogle(user.getGoogleId() != null)
                .needsPasswordSetup(false)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email or email address"));

        if (!user.getIsVerified()) {
            // Allow SUPER_ADMIN seed accounts to bypass email verification
            boolean isSuperAdmin = user.getRole().equals("ROLE_SUPER_ADMIN") || user.getRole().equals("SUPER_ADMIN");
            if (!isSuperAdmin) {
                throw new IllegalArgumentException("I'm locked to check the email address and click on the OTPe code.");
            }
            // Auto-verify for admin accounts
            user.setIsVerified(true);
            userRepository.save(user);
        }

        if ("INACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("An error occurred");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Email or email address");
        }

        // Normalize role: avoid double ROLE_ prefix
        String authority = user.getRole().startsWith("ROLE_") ? user.getRole() : "ROLE_" + user.getRole();
        // Generate Real JWT
        String token = tokenProvider.generateToken(user.getEmail(), authority);

        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(token)
                .email(user.getEmail())
                .role(authority)
                .fullName(user.getFullName())
                .hasPassword(true)
                .linkedGoogle(user.getGoogleId() != null)
                .needsPasswordSetup(false)
                .build();
    }

    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyGoogleToken(request.getGoogleIdToken());
        
        String email = payload.getEmail(); 
        String googleId = payload.getSubject();
        String fullName = (String) payload.get("name");

        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        User user;
        boolean isNewUser = false;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();

            if ("INACTIVE".equals(user.getStatus())) {
                throw new IllegalArgumentException("I'm going to lock you up, you're welcome");
            }

            // Update Google ID if not set
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }
        } else {
            // Create new Google User
            user = User.builder()
                    .email(email)
                    .googleId(googleId)
                    .fullName(fullName)
                    .role("CUSTOMER")
                    .isVerified(true) // Google users are pre-verified
                    .status("ACTIVE")
                    .build();
            userRepository.save(user);
            isNewUser = true;
        }

        // Normalize role: avoid double ROLE_ prefix
        String authority = user.getRole().startsWith("ROLE_") ? user.getRole() : "ROLE_" + user.getRole();
        String token = tokenProvider.generateToken(user.getEmail(), authority);
        boolean hasPassword = user.getPasswordHash() != null;

        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(token)
                .email(user.getEmail())
                .role(authority)
                .fullName(user.getFullName())
                .hasPassword(hasPassword)
                .linkedGoogle(true)
                .needsPasswordSetup(isNewUser || !hasPassword) // Prompt setup for new Google users
                .build();
    }

    @Transactional
    public void setPassword(String email, SetPasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void linkGoogle(String email, GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyGoogleToken(request.getGoogleIdToken());
        String googleId = payload.getSubject();

        // Check if googleId already exists
        // if (userRepository.findByGoogleId(googleId).isPresent()) throw ConflictException;

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setGoogleId(googleId);
        userRepository.save(user);
    }

    @Transactional
    public void sendOtp(String email, String purpose) {
        String otp = generateOtp();
        String cacheKey = "OTP:" + (purpose != null ? purpose : "REGISTER") + ":" + email;
        long expiryTime = com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + (OTP_TTL_MINUTES * 60 * 1000);
        
        OtpData existingData = otpStore.get(cacheKey);
        RegisterRequest savedRequest = null;
        if (existingData != null && "REGISTER".equals(purpose != null ? purpose : "REGISTER")) {
            savedRequest = existingData.registerRequest;
            // Extend registration expiry by 15 mins from now
            expiryTime = com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + (15 * 60 * 1000);
        }
        
        otpStore.put(cacheKey, new OtpData(otp, expiryTime, savedRequest));

        String htmlContent = "<h3>PBMS Verification Code</h3><p>Your OTP code is: <b style='font-size:24px;letter-spacing:4px'>" + otp + "</b></p><p>This code will expire in 5 minutes.</p>";
        emailService.sendHtmlEmail(email, "[PBMS] Verification Code", htmlContent);
        
        log.info("Generated OTP for {} is {}", email, otp);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with this email."));

        if (!user.getIsVerified()) {
            throw new IllegalArgumentException("Account is not verified yet.");
        }

        String otp = generateOtp();
        String cacheKey = "OTP:FORGOT_PASSWORD:" + email;
        long expiryTime = com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + (OTP_TTL_MINUTES * 60 * 1000);
        otpStore.put(cacheKey, new OtpData(otp, expiryTime));

        String htmlContent = "<h3>PBMS Reset Password</h3><p>Your OTP: <b style='font-size:24px;letter-spacing:4px'>" + otp + "</b></p><p>Expires in 5 minutes.</p>";
        emailService.sendHtmlEmail(email, "[PBMS] \u0110\u1eb7t L\u1ea1i M\u1eadt Kh\u1ea9u", htmlContent);
        log.info("Forgot-password OTP for {} is {}", email, otp);
    }

    @Transactional
    public String verifyForgotPasswordOtp(String email, String otp) {
        String cacheKey = "OTP:FORGOT_PASSWORD:" + email;
        OtpData otpData = otpStore.get(cacheKey);

        if (otpData == null || com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() > otpData.expiryTime) {
            otpStore.remove(cacheKey);
            throw new IllegalArgumentException("OTP code is expired or invalid.");
        }
        if (!otpData.otpCode.equals(otp)) {
            otpData.attempts++;
            if (otpData.attempts >= MAX_OTP_ATTEMPTS) {
                otpStore.remove(cacheKey);
                throw new IllegalArgumentException("Maximum attempts exceeded. Please request a new code.");
            }
            throw new IllegalArgumentException("Incorrect OTP. You have " + (MAX_OTP_ATTEMPTS - otpData.attempts) + " attempts left.");
        }

        otpStore.remove(cacheKey);
        // Return a temp reset token (short-lived JWT)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return tokenProvider.generateToken(user.getEmail(), "RESET_PASSWORD");
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(java.util.Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                return idToken.getPayload();
            } else {
                throw new IllegalArgumentException("Invalid Google ID Token");
            }
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new IllegalArgumentException("Google token verification failed", e);
        }
    }

    public String getOtpForTest(String email, String purpose) {
        String cacheKey = "OTP:" + purpose + ":" + email;
        OtpData otpData = otpStore.get(cacheKey);
        return otpData != null ? otpData.otpCode : "NOT_FOUND";
    }
}


