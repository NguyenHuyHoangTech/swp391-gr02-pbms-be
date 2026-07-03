package com.pbms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WsMessageWrapper<T> {
    
    @Builder.Default
    private String eventId = "EVT_" + UUID.randomUUID().toString();
    
    @Builder.Default
    private LocalDateTime timestamp = com.pbms.common.utils.TimeProvider.now();
    
    private String eventType;
    
    @Builder.Default
    private String priority = "NORMAL"; // LOW, NORMAL, HIGH, CRITICAL
    
    private T data;
    
    public static <T> WsMessageWrapper<T> of(String eventType, T data) {
        return WsMessageWrapper.<T>builder()
                .eventType(eventType)
                .data(data)
                .build();
    }
    
    public static <T> WsMessageWrapper<T> of(String eventType, String priority, T data) {
        return WsMessageWrapper.<T>builder()
                .eventType(eventType)
                .priority(priority)
                .data(data)
                .build();
    }
}

