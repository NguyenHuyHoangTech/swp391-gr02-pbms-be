package com.pbms.common.security;

import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Filter dùng để xác thực người dùng bằng JWT token.
 * File này đọc token từ header Authorization, kiểm tra token bằng JwtProvider,
 * sau đó lấy thông tin user từ UserRepository để đảm bảo tài khoản vẫn còn tồn tại và đang ACTIVE.
 * JwtAuthFilter được gắn vào SecurityFilterChain trong SecurityConfig.
 *
 * Pseudo code:
 * 1. Mỗi request đi vào backend sẽ đi qua filter này một lần.
 * 2. Lấy JWT token từ header Authorization.
 * 3. Kiểm tra token có hợp lệ không bằng JwtProvider.
 * 4. Lấy email và role từ token.
 * 5. Kiểm tra user trong database còn tồn tại và đang ACTIVE.
 * 6. Nếu hợp lệ thì tạo Authentication cho Spring Security.
 * 7. Gắn Authentication vào SecurityContextHolder để backend biết user hiện tại là ai.
 * 8. Cho request đi tiếp đến filter hoặc controller tiếp theo.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    /**
     * Nhận JwtProvider và UserRepository từ Spring container.
     * JwtProvider dùng để kiểm tra và đọc dữ liệu trong JWT token.
     * UserRepository dùng để kiểm tra trạng thái thật của user trong database.
     *
     * Pseudo code:
     * 1. Nhận JwtProvider từ Spring.
     * 2. Nhận UserRepository từ Spring.
     * 3. Lưu hai dependency này vào biến của class.
     */
    public JwtAuthFilter(JwtProvider jwtProvider, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    /**
     * Xử lý xác thực JWT cho mỗi HTTP request.
     * Nếu token hợp lệ và user đang ACTIVE, method này sẽ tạo Authentication cho request hiện tại.
     *
     * Pseudo code:
     * 1. Lấy JWT token từ request.
     * 2. Nếu token tồn tại và hợp lệ thì lấy email từ token.
     * 3. Tìm user trong database bằng email.
     * 4. Kiểm tra user có tồn tại và trạng thái là ACTIVE không.
     * 5. Nếu user hợp lệ thì lấy role từ token.
     * 6. Chuyển role thành authority theo format ROLE_.
     * 7. Tạo UsernamePasswordAuthenticationToken.
     * 8. Gắn thông tin request vào authentication.
     * 9. Lưu authentication vào SecurityContextHolder.
     * 10. Nếu user bị khóa hoặc bị xóa thì không xác thực request.
     * 11. Cho request tiếp tục đi qua filter chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = parseJwt(request);

            if (jwt != null && jwtProvider.validateToken(jwt)) {
                String email = jwtProvider.getEmailFromToken(jwt);
                
                Optional<User> userOpt = userRepository.findByEmail(email);

                if (userOpt.isPresent() && "ACTIVE".equals(userOpt.get().getStatus())) {
                    String role = jwtProvider.getRoleFromToken(jwt);
                    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            email, null, Collections.singletonList(new SimpleGrantedAuthority(authority)));
                    
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.warn("User " + email + " is locked or deleted. Rejecting token.");
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Lấy JWT token từ header Authorization của request.
     * Header hợp lệ phải có dạng Bearer token.
     *
     * Pseudo code:
     * 1. Đọc header Authorization từ request.
     * 2. Kiểm tra header có dữ liệu và bắt đầu bằng "Bearer " không.
     * 3. Nếu đúng thì cắt bỏ chữ "Bearer " để lấy token thật.
     * 4. Nếu không có token hợp lệ thì trả về null.
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}