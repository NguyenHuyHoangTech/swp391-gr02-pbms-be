package com.pbms.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Chức năng của file:
 * JwtProvider là component dùng để tạo, đọc và kiểm tra JWT token trong hệ thống PBMS.
 * File này hỗ trợ backend tạo token sau khi đăng nhập thành công, lấy email và role từ token,
 * đồng thời kiểm tra token có hợp lệ hoặc hết hạn hay chưa.
 *
 * Liên quan backend:
 * - SecurityConfig sử dụng JwtAuthFilter để bảo vệ các API cần đăng nhập.
 * - JwtAuthFilter dùng JwtProvider để validate token và lấy thông tin user từ token.
 * - AuthService hoặc AuthController dùng JwtProvider để tạo token sau khi login thành công.
 * - TimeProvider cung cấp thời gian hiện tại của hệ thống, bao gồm cả simulated time nếu có.
 *
 * Pseudo code:
 * 1. Đọc secret key và thời gian hết hạn token từ file cấu hình.
 * 2. Tạo signing key từ jwtSecret.
 * 3. Khi user login thành công, tạo JWT chứa email và role.
 * 4. Khi cần token tạm thời, tạo JWT với thời gian hết hạn riêng.
 * 5. Khi request gửi token lên, parse token để lấy email.
 * 6. Parse token để lấy role.
 * 7. Validate token bằng secret key và simulated clock.
 * 8. Trả true nếu token hợp lệ, trả false nếu token sai hoặc hết hạn.
 */
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Tạo signing key dùng để ký và xác thực JWT token.
     *
     * Pseudo code:
     * 1. Lấy jwtSecret từ cấu hình.
     * 2. Chuyển jwtSecret thành mảng byte.
     * 3. Tạo HMAC SHA key từ mảng byte đó.
     * 4. Trả về SecretKey để dùng khi ký hoặc verify token.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Tạo JWT token chính cho user sau khi đăng nhập thành công.
     * Token này chứa email trong subject và role trong claim.
     *
     * Pseudo code:
     * 1. Lấy thời gian hiện tại từ TimeProvider.
     * 2. Tạo JWT builder.
     * 3. Gán subject là email của user.
     * 4. Gán claim role là quyền của user.
     * 5. Gán thời điểm phát hành token.
     * 6. Gán thời điểm hết hạn token bằng now + jwtExpirationMs.
     * 7. Ký token bằng signing key.
     * 8. Trả về token dạng chuỗi.
     */
    public String generateToken(String email, String role) {
        Date now = java.util.Date.from(com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Tạo JWT token tạm thời với thời gian hết hạn được truyền vào.
     * Method này thường dùng cho các luồng cần token ngắn hạn.
     *
     * Pseudo code:
     * 1. Lấy thời gian hiện tại từ TimeProvider.
     * 2. Tạo JWT builder.
     * 3. Gán subject là email.
     * 4. Gán claim role là quyền của user.
     * 5. Gán thời điểm phát hành token.
     * 6. Gán thời điểm hết hạn bằng now + expirationMs.
     * 7. Ký token bằng signing key.
     * 8. Trả về token dạng chuỗi.
     */
    public String generateTemporaryToken(String email, String role, long expirationMs) {
        Date now = java.util.Date.from(com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Tạo clock dùng để validate token theo thời gian mô phỏng của hệ thống.
     * Method này giúp JWT hoạt động đúng với TimeProvider thay vì chỉ dùng thời gian thật của máy chủ.
     *
     * Pseudo code:
     * 1. Lấy thời gian hiện tại từ TimeProvider.
     * 2. Chuyển LocalDateTime sang Date.
     * 3. Trả về clock cho thư viện JWT sử dụng khi kiểm tra expiration.
     */
    private io.jsonwebtoken.Clock getSimulatedClock() {
        return () -> java.util.Date.from(com.pbms.common.utils.TimeProvider.now().atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    /**
     * Lấy email từ JWT token.
     * Email được lưu trong subject của token khi token được tạo.
     *
     * Pseudo code:
     * 1. Tạo JWT parser.
     * 2. Verify token bằng signing key.
     * 3. Sử dụng simulated clock để kiểm tra thời gian token.
     * 4. Parse token để lấy payload.
     * 5. Lấy subject trong payload.
     * 6. Trả về email của user.
     */
    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .clock(getSimulatedClock())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Lấy role từ JWT token.
     * Role được lưu trong claim tên "role" khi token được tạo.
     *
     * Pseudo code:
     * 1. Tạo JWT parser.
     * 2. Verify token bằng signing key.
     * 3. Sử dụng simulated clock để kiểm tra thời gian token.
     * 4. Parse token để lấy payload.
     * 5. Lấy claim role từ payload.
     * 6. Trả về role của user.
     */
    public String getRoleFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .clock(getSimulatedClock())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    /**
     * Kiểm tra JWT token có hợp lệ hay không.
     * Method này trả false nếu token sai chữ ký, sai format, hết hạn hoặc không parse được.
     *
     * Pseudo code:
     * 1. Tạo JWT parser.
     * 2. Verify token bằng signing key.
     * 3. Sử dụng simulated clock để kiểm tra thời gian token.
     * 4. Parse token.
     * 5. Nếu parse thành công thì trả true.
     * 6. Nếu có lỗi thì trả false.
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).clock(getSimulatedClock()).build().parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}