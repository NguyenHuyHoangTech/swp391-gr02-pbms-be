package com.pbms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO dùng để chuẩn hóa format message gửi qua WebSocket.
 * WebSocketConfig cấu hình kênh WebSocket, còn WsMessageWrapper giúp dữ liệu gửi realtime
 * có cùng cấu trúc gồm eventId, timestamp, eventType, priority và data.
 * Thời gian gửi message được lấy từ TimeProvider trong common.utils.
 *
 * Pseudo code:
 * 1. Tạo format chung cho mọi message WebSocket.
 * 2. Mỗi message có một eventId riêng để dễ theo dõi.
 * 3. Mỗi message có timestamp để biết thời điểm backend gửi event.
 * 4. eventType cho biết loại sự kiện đang được gửi.
 * 5. priority cho biết mức độ quan trọng của message.
 * 6. data chứa dữ liệu thật cần gửi cho frontend.
 */
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
    private String priority = "NORMAL";
    
    private T data;

    /**
     * Tạo WebSocket message với priority mặc định là NORMAL.
     * Method này dùng khi backend cần gửi một event realtime đơn giản cho frontend.
     *
     * Pseudo code:
     * 1. Nhận loại event cần gửi.
     * 2. Nhận dữ liệu cần gửi.
     * 3. Gán eventType vào message.
     * 4. Gán data vào message.
     * 5. Dùng priority mặc định là NORMAL.
     * 6. Build và trả về WsMessageWrapper.
     */
    public static <T> WsMessageWrapper<T> of(String eventType, T data) {
        return WsMessageWrapper.<T>builder()
                .eventType(eventType)
                .data(data)
                .build();
    }

    /**
     * Tạo WebSocket message với priority được chỉ định.
     * Method này dùng khi backend cần gửi event realtime có mức độ ưu tiên riêng như LOW, NORMAL, HIGH hoặc CRITICAL.
     *
     * Pseudo code:
     * 1. Nhận loại event cần gửi.
     * 2. Nhận mức độ ưu tiên của event.
     * 3. Nhận dữ liệu cần gửi.
     * 4. Gán eventType, priority và data vào message.
     * 5. Build và trả về WsMessageWrapper.
     */
    public static <T> WsMessageWrapper<T> of(String eventType, String priority, T data) {
        return WsMessageWrapper.<T>builder()
                .eventType(eventType)
                .priority(priority)
                .data(data)
                .build();
    }
}