package com.pbms.common.event;

import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

/**
 * Event dùng để thông báo rằng thời gian giả lập của hệ thống đã được tua nhanh.
 * Class này thường liên quan đến TimeProvider trong common.utils hoặc các service xử lý thời gian.
 * Khi thời gian giả lập thay đổi, hệ thống có thể phát event này để các module khác cập nhật lại trạng thái cần thiết.
 *
 * Pseudo code:
 * 1. Lưu lại thời gian giả lập cũ.
 * 2. Lưu lại thời gian giả lập mới.
 * 3. Gửi event này trong Spring ApplicationEvent system.
 * 4. Các listener khác có thể bắt event này để xử lý logic sau khi thời gian bị thay đổi.
 */
public class TimeFastForwardedEvent extends ApplicationEvent {
    
    private final LocalDateTime newSimulatedTime;
    private final LocalDateTime oldSimulatedTime;

    /**
     * Tạo event khi thời gian giả lập được tua nhanh.
     *
     * Pseudo code:
     * 1. Nhận source là nơi phát ra event.
     * 2. Nhận thời gian giả lập cũ.
     * 3. Nhận thời gian giả lập mới.
     * 4. Gọi constructor của ApplicationEvent.
     * 5. Lưu oldSimulatedTime và newSimulatedTime vào event.
     */
    public TimeFastForwardedEvent(Object source, LocalDateTime oldSimulatedTime, LocalDateTime newSimulatedTime) {
        super(source);
        this.oldSimulatedTime = oldSimulatedTime;
        this.newSimulatedTime = newSimulatedTime;
    }

    /**
     * Lấy thời gian giả lập mới sau khi hệ thống được tua nhanh.
     *
     * Pseudo code:
     * 1. Trả về newSimulatedTime.
     */
    public LocalDateTime getNewSimulatedTime() {
        return newSimulatedTime;
    }

    /**
     * Lấy thời gian giả lập cũ trước khi hệ thống được tua nhanh.
     *
     * Pseudo code:
     * 1. Trả về oldSimulatedTime.
     */
    public LocalDateTime getOldSimulatedTime() {
        return oldSimulatedTime;
    }
}