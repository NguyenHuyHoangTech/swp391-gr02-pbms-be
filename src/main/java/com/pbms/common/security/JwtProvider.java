package com.pbms.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Class dùng để tạo, đọc và kiểm tra JWT token trong hệ thống.
 * JwtProvider được JwtAuthFilter dùng để xác thực HTTP request,
 * và được WebSocketConfig dùng để xác thực WebSocket connection.
 * Thời gian tạo token được lấy từ TimeProvider trong common.utils để hỗ trợ thời gian hệ thống hoặc thời gian giả lập.
 *
 * Pseudo code:
 * 1. Đọc secret key và thời gian hết hạn token từ file cấu hình.
 * 2. Tạo signing key để ký JWT.
 * 3. Tạo token chứa email và role của user.
 * 4. Đọc email từ token khi cần xác thực request.
 * 5. Đọc role từ token để phân quyền.
 * 6. Kiểm tra token có hợp lệ, đúng chữ ký và chưa bị lỗi format hay không.
 */
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Tạo secret key dùng để ký và kiểm tra JWT token.
     *
     * Pseudo code:
     * 1. Lấy jwtSecret từ file cấu hình.
     * 2. Chuyển jwtSecret thành mảng byte.
     * 3. Tạo HMAC secret key từ mảng byte đó.
     * 4. Trả về key để dùng khi generate hoặc validate token.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Tạo JWT token đăng nhập chính cho user.
     * Token này chứa email, role, thời gian tạo và thời gian hết hạn.
     *
     * Pseudo code:
     * 1. Nhận email và role của user.
     * 2. Lấy thời gian hiện tại từ TimeProvider.
     * 3. Tạo JWT mới.
     * 4. Gán email vào subject của token.
     * 5. Gán role vào claim "role".
     * 6. Gán thời gian tạo token.
     * 7. Gán thời gian hết hạn dựa trên jwtExpirationMs.
     * 8. Ký token bằng signing key.
     * 9. Trả về token dạng chuỗi.
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
     * Method này có thể dùng cho các luồng cần token ngắn hạn như xác minh, reset password hoặc thao tác tạm thời.
     *
     * Pseudo code:
     * 1. Nhận email, role và thời gian hết hạn riêng.
     * 2. Lấy thời gian hiện tại từ TimeProvider.
     * 3. Tạo JWT mới.
     * 4. Gán email vào subject.
     * 5. Gán role vào claim "role".
     * 6. Gán thời gian tạo token.
     * 7. Gán thời gian hết hạn dựa trên expirationMs được truyền vào.
     * 8. Ký token bằng signing key.
     * 9. Trả về token dạng chuỗi.
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
     * Lấy email của user từ JWT token.
     * JwtAuthFilter dùng method này để biết request hiện tại thuộc về user nào.
     *
     * Pseudo code:
     * 1. Tạo JWT parser với signing key.
     * 2. Parse token và kiểm tra chữ ký.
     * 3. Lấy payload của token.
     * 4. Lấy subject trong payload.
     * 5. Trả về subject dưới dạng email.
     */
    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Lấy role của user từ JWT token.
     * JwtAuthFilter và WebSocketConfig dùng method này để tạo quyền cho user trong Spring Security.
     *
     * Pseudo code:
     * 1. Tạo JWT parser với signing key.
     * 2. Parse token và kiểm tra chữ ký.
     * 3. Lấy payload của token.
     * 4. Lấy claim "role".
     * 5. Trả về role dưới dạng String.
     */
    public String getRoleFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    /**
     * Kiểm tra JWT token có hợp lệ hay không.
     * Method này chỉ trả về true hoặc false, không ném lỗi ra ngoài.
     *
     * Pseudo code:
     * 1. Nhận token cần kiểm tra.
     * 2. Tạo JWT parser với signing key.
     * 3. Parse token để kiểm tra chữ ký và format.
     * 4. Nếu parse thành công thì trả về true.
     * 5. Nếu token sai, hết hạn, sai chữ ký hoặc lỗi format thì trả về false.
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}