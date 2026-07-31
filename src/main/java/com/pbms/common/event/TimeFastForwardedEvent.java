/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-28
 * @Description: Lớp sự kiện (Event) được kích hoạt (publish) khi hệ thống thực hiện thao tác "Tua nhanh thời gian".
 *               Dùng để đánh thức các tác vụ chạy ngầm (như Schedulers của Reservation) cập nhật lại logic giờ giấc.
 * @Dependencies: 
 * - ApplicationEvent (Core Spring Framework)
 */
package com.pbms.common.event;

import org.springframework.context.ApplicationEvent;

public class TimeFastForwardedEvent extends ApplicationEvent {
    
    /**
     * Khởi tạo sự kiện tua thời gian.
     * @param source Object phát ra sự kiện này (thường là Controller hoặc Service thực hiện thao tác tua).
     */
    public TimeFastForwardedEvent(Object source) { 
        super(source); 
    }
    
    /**
     * Lấy thời gian mới của hệ thống (sau khi đã tua).
     * Logic mã giả:
     * Trả về mốc thời gian giả lập hiện hành để các Listener (vd: ReservationService) 
     * dùng làm mốc tính toán (ví dụ: phát hiện đơn đặt chỗ đã quá hạn).
     */
    public java.time.LocalDateTime getNewSimulatedTime() { 
        return java.time.LocalDateTime.now(); 
    }
    
    /**
     * Lấy thời gian cũ của hệ thống (trước khi tua).
     * Logic mã giả:
     * Hỗ trợ tính toán khoảng thời gian đã bị tua đi (tính bằng cách lấy Mốc mới - Mốc cũ) nếu cần thiết.
     */
    public java.time.LocalDateTime getOldSimulatedTime() { 
        return java.time.LocalDateTime.now(); 
    }
}
