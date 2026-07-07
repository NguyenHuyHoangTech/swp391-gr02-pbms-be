package com.pbms.common.config;

import com.pbms.common.security.JwtProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

/**
 * Cấu hình WebSocket cho hệ thống PBMS.
 * File này cho phép frontend kết nối WebSocket qua endpoint /ws-pbms để nhận dữ liệu realtime.
 * WebSocketConfig sử dụng JwtProvider trong common.security để kiểm tra JWT khi client kết nối.
 * Các endpoint WebSocket cũng được mở public trong SecurityConfig để client có thể bắt đầu kết nối.
 *
 * Pseudo code:
 * 1. Bật WebSocket message broker cho project.
 * 2. Cấu hình kênh gửi message realtime bằng /topic và /queue.
 * 3. Cấu hình endpoint /ws-pbms để frontend kết nối.
 * 4. Kiểm tra JWT token khi client gửi lệnh CONNECT.
 * 5. Nếu token hợp lệ thì gắn thông tin user vào WebSocket session.
 * 6. Cấu hình giới hạn dung lượng và thời gian gửi message.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    private final JwtProvider jwtProvider;

    /**
     * Nhận JwtProvider từ package common.security để xử lý JWT cho WebSocket.
     *
     * Pseudo code:
     * 1. Nhận JwtProvider từ Spring container.
     * 2. Lưu JwtProvider vào biến của class.
     * 3. Dùng JwtProvider để validate token khi client kết nối WebSocket.
     */
    public WebSocketConfig(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Cấu hình message broker cho WebSocket.
     * Broker này dùng để gửi message realtime từ backend tới frontend.
     *
     * Pseudo code:
     * 1. Tạo scheduler riêng để xử lý heartbeat của WebSocket.
     * 2. Bật simple broker cho các destination bắt đầu bằng /topic và /queue.
     * 3. Cấu hình heartbeat để kiểm tra kết nối còn sống hay không.
     * 4. Gắn scheduler vào broker.
     * 5. Đặt prefix /app cho các message frontend gửi lên backend.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("wss-heartbeat-thread-");
        taskScheduler.initialize();

        config.enableSimpleBroker("/topic", "/queue")
              .setHeartbeatValue(new long[]{10000, 10000})
              .setTaskScheduler(taskScheduler);
              
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Đăng ký endpoint WebSocket để frontend kết nối vào backend.
     * allowedOrigins được lấy từ cấu hình CORS giống CorsConfig.
     *
     * Pseudo code:
     * 1. Đăng ký endpoint /ws-pbms có hỗ trợ SockJS.
     * 2. Cho phép các frontend origin trong cors.allowed-origins được kết nối.
     * 3. Đăng ký thêm endpoint /ws-pbms không dùng SockJS.
     * 4. Cho phép frontend kết nối WebSocket theo cả hai cách.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-pbms")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
        
        registry.addEndpoint("/ws-pbms")
                .setAllowedOrigins(allowedOrigins);
    }

    /**
     * Kiểm tra request CONNECT từ client trước khi cho WebSocket hoạt động.
     * Method này đọc JWT trong header Authorization và gắn user vào WebSocket session nếu token hợp lệ.
     *
     * Pseudo code:
     * 1. Thêm interceptor cho inbound channel.
     * 2. Khi client gửi lệnh CONNECT, đọc header Authorization.
     * 3. Nếu header bắt đầu bằng Bearer thì lấy token phía sau.
     * 4. Dùng JwtProvider để kiểm tra token.
     * 5. Nếu token hợp lệ thì lấy email và role từ token.
     * 6. Tạo Authentication chứa email và quyền của user.
     * 7. Gắn Authentication vào SecurityContextHolder.
     * 8. Gắn Authentication vào accessor để WebSocket session biết user hiện tại.
     * 9. Trả message về để tiếp tục xử lý.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");

                    if (authorization != null && !authorization.isEmpty()) {
                        String authHeader = authorization.get(0);

                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            String token = authHeader.substring(7);

                            if (jwtProvider.validateToken(token)) {
                                String email = jwtProvider.getEmailFromToken(token);
                                String role = jwtProvider.getRoleFromToken(token);
                                
                                List<GrantedAuthority> authorities = Collections.singletonList(
                                        new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role)
                                );
                                
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(email, null, authorities);

                                SecurityContextHolder.getContext().setAuthentication(auth);
                                accessor.setUser(auth);
                            }
                        }
                    }
                }

                return message;
            }
        });
    }

    /**
     * Cấu hình giới hạn truyền dữ liệu cho WebSocket.
     * Method này giúp WebSocket xử lý được message lớn hơn mặc định, ví dụ dữ liệu realtime hoặc payload lớn.
     *
     * Pseudo code:
     * 1. Đặt giới hạn kích thước message là 20MB.
     * 2. Đặt giới hạn bộ nhớ đệm khi gửi message là 20MB.
     * 3. Đặt thời gian gửi tối đa là 20 giây.
     * 4. Áp dụng các giới hạn này cho WebSocket transport.
     */
    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(20 * 1024 * 1024);
        registration.setSendBufferSizeLimit(20 * 1024 * 1024);
        registration.setSendTimeLimit(20000);
    }
}