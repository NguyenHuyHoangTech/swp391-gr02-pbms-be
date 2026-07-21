/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Bọc quanh SimpMessagingTemplate của Spring, chuẩn hoá mọi tin
 * nhắn WebSocket (STOMP) gửi đi theo cùng 1 khuôn dạng (WsMessageWrapper), để
 * FE chỉ cần 1 cách parse chung cho mọi loại sự kiện thay vì mỗi nơi 1 kiểu.
 * @Dependencies:
 * - SimpMessagingTemplate (Spring)
 * - WsMessageWrapper (Local)
 */
package com.pbms.modules.infrastructure.service;

import com.pbms.common.dto.WsMessageWrapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * @Function: broadcastEvent
     * @Description: Gửi tin broadcast - tất cả client đang subscribe đúng
     * topic đều nhận được cùng lúc (mô hình publish/subscribe của STOMP).
     */
    public void broadcastEvent(String topic, String eventType, Object payload) {
        WsMessageWrapper<Object> message = WsMessageWrapper.of(eventType, payload);
        messagingTemplate.convertAndSend(topic, message);
    }

    /**
     * @Function: broadcastCriticalEvent
     * @Description: Giống broadcastEvent, nhưng gắn priority = "CRITICAL" để
     * FE biết hiển thị nổi bật hơn tin thường (VD toast đỏ, không tự tắt).
     */
    public void broadcastCriticalEvent(String topic, String eventType, Object payload) {
        WsMessageWrapper<Object> message = WsMessageWrapper.of(eventType, "CRITICAL", payload);
        messagingTemplate.convertAndSend(topic, message);
    }
}
