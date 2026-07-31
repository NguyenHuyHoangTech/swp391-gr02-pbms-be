/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-25
 * @Description: REST controller exposing the AI routing advisor to managers. Takes the
 *               current occupancy data and routing configuration, returns a suggested
 *               configuration produced by the language model.
 * @Dependencies:
 * - GeminiService (com.pbms.modules.ai.service.GeminiService)
 * - AiRoutingRequest (com.pbms.modules.ai.dto.AiRoutingRequest)
 * - ApiResponse (com.pbms.common.dto.ApiResponse)
 */
package com.pbms.modules.ai.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.ai.dto.AiRoutingRequest;
import com.pbms.modules.ai.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER')")
public class AiAdvisorController {

    private final GeminiService geminiService;

    /**
     * @Function: getRoutingAdvice
     * @Description: Nhận dữ liệu biểu đồ mật độ + cấu hình điều phối hiện tại từ màn
     *               hình quản lý, chuyển cho GeminiService phân tích và trả về gợi ý
     *               cấu hình mới dưới dạng chuỗi JSON.
     * @Logic_Steps:
     * 1. Chỉ cho phép tài khoản SUPER_ADMIN hoặc MANAGER gọi (chặn ở cấp class).
     * 2. Chuyển nguyên gói dữ liệu sang GeminiService để dựng prompt và gọi mô hình.
     * 3. Bọc kết quả trong ApiResponse để Frontend nhận đúng định dạng chung.
     */
    @PostMapping("/routing-advice")
    public ResponseEntity<ApiResponse<String>> getRoutingAdvice(@RequestBody AiRoutingRequest request) {
        String advice = geminiService.getRoutingAdvice(request);
        return ResponseEntity.ok(ApiResponse.success(advice, "Success"));
    }
}
