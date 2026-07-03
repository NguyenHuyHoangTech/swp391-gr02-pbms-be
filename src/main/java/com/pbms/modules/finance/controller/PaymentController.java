package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.finance.dto.PaymentActionRequest;
import com.pbms.modules.finance.dto.PaymentExecutionResponse;
import com.pbms.modules.finance.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;



@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    // Chỉ inject Service
    private final PaymentService paymentService;

    /**
     * POST /api/v1/payments/generate-link
     * API khởi tạo đơn hàng và sinh link thanh toán.
     * * @param request DTO chứa thông tin thanh toán (Số tiền, loại cổng, ID đối tượng tham chiếu).
     * @return ApiResponse chứa link thanh toán và mã đơn hàng đã được khởi tạo.
     */
    @PostMapping("/generate-link")
    public ResponseEntity<ApiResponse<PaymentExecutionResponse>> generatePaymentLink(@RequestBody PaymentActionRequest request) {
        try {
            // [PBMS-76] Controller gọi duy nhất 1 phương thức xử lý nghiệp vụ.
            // Mọi logic tạo đơn hàng, lưu DB và chọn Strategy đều ẩn bên trong Service.
            PaymentExecutionResponse response = paymentService.processPayment(request);

            return ResponseEntity.ok(ApiResponse.success(response, "Generated payment link successfully"));
        } catch (IllegalArgumentException e) {
            // [PBMS-76] Handle lỗi nghiệp vụ (ví dụ: cổng thanh toán chưa được hỗ trợ)
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            // [PBMS-76] Handle lỗi hệ thống/Exception không mong muốn
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "System error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/v1/payments/paypal/capture
     * XÃ¡c nháº­n Ä‘Æ¡n hÃ ng PayPal
     */
    @PostMapping("/paypal/capture")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capturePayPalOrder(@RequestBody Map<String, String> requestBody) {
        try {
            String token = requestBody.get("token");
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Token is required"));
            }
            boolean success = paymentService.capturePayPalOrder(token);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(Map.of("status", "COMPLETED"), "Payment is the same"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Payment of goods or completion of goods"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
}

