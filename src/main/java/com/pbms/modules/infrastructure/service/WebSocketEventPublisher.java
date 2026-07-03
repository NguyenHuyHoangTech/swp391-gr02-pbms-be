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
     * Gá»­i tin nháº¯n Broadcast (Táº¥t cáº£ nhá»¯ng ai subscribe Topic Ä‘á»u nháº­n Ä‘Æ°á»£c)
     * @param topic Destination (VD: "/topic/slots/status")
     * @param eventType Loáº¡i sá»± kiá»‡n (VD: "SLOT_UPDATED")
     * @param payload Dá»¯ liá»‡u lÃµi
     */
    public void broadcastEvent(String topic, String eventType, Object payload) {
        WsMessageWrapper<Object> message = WsMessageWrapper.of(eventType, payload);
        messagingTemplate.convertAndSend(topic, message);
    }

    /**
     * Gá»­i tin nháº¯n Broadcast kháº©n cáº¥p
     */
    public void broadcastCriticalEvent(String topic, String eventType, Object payload) {
        WsMessageWrapper<Object> message = WsMessageWrapper.of(eventType, "CRITICAL", payload);
        messagingTemplate.convertAndSend(topic, message);
    }

    /**
     * Gá»­i tin nháº¯n Unicast cho má»™t User cá»¥ thá»ƒ (VÃ­ dá»¥ mÃ n hÃ¬nh Gateway cá»§a nhÃ¢n viÃªn)
     * @param username Email hoáº·c ID cá»§a User
     * @param queue Destination queue (VD: "/queue/gates/GATE_IN_01/scans")
     * @param eventType Loáº¡i sá»± kiá»‡n
     * @param payload Dá»¯ liá»‡u lÃµi
     */
    public void unicastEvent(String username, String queue, String eventType, Object payload) {
        WsMessageWrapper<Object> message = WsMessageWrapper.of(eventType, payload);
        messagingTemplate.convertAndSendToUser(username, queue, message);
    }
}

