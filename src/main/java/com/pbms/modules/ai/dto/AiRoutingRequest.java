/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-25
 * @Description: Request payload for the AI routing advisor. Carries the hourly
 *               occupancy chart, the current routing configuration and the manager's
 *               free-text context that together form the prompt.
 * @Dependencies:
 * - ZoneTrendDTO (com.pbms.modules.operation.dto.ZoneTrendDTO)
 */
package com.pbms.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pbms.modules.operation.dto.ZoneTrendDTO;
import lombok.Data;

import java.util.List;

@Data
public class AiRoutingRequest {
    private String vehicleType;
    private String dateRange;
    private List<ZoneTrendDTO> chartData;
    private String extraContext;

    // BẮT BUỘC phải có @JsonProperty ở đây — KHÔNG được xóa.
    // Với field kiểu `boolean` đặt tên bắt đầu bằng "is", Lombok sinh getter
    // `isRoutingEnabled()` / setter `setRoutingEnabled()`, nên Jackson tự suy ra
    // tên thuộc tính JSON là "routingEnabled" (bị nuốt mất chữ "is"). Trong khi
    // Frontend lại gửi lên đúng key "isRoutingEnabled". Hai tên lệch nhau ->
    // Jackson bỏ qua, field luôn giữ giá trị mặc định `false`. Hậu quả: prompt
    // gửi cho AI LUÔN khẳng định "tính năng điều phối đang TẮT" kể cả khi quản
    // lý đã bật, khiến AI khuyên ngược ("hãy bật lại đi").
    @JsonProperty("isRoutingEnabled")
    private boolean isRoutingEnabled;

    private Object currentRules;
}
