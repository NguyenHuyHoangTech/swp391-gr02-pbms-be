package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.common.annotation.LogAudit;
import com.pbms.modules.finance.dto.RefundRequestDTO;
import com.pbms.modules.finance.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.pbms.common.service.FileStorageService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final FileStorageService fileStorageService;

    /**
     * GET /api/v1/finance/refunds
     * Lấy danh sách tất cả các yêu cầu hoàn tiền (Refund Requests).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundRequestDTO>>> getAllRefunds() {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.getAllRefunds(),
                "Make a list of projects"
        ));
    }

    /**
     * PUT /api/v1/finance/refunds/{id}/approve
     * Phê duyệt yêu cầu hoàn tiền.
     * Cập nhật trạng thái thành APPROVED và thực hiện logic trả tiền.
     */
    @PutMapping("/{id}/approve")
    @LogAudit(action = "UPDATE", resource = "Refund", description = "Approve refund request")
    public ResponseEntity<ApiResponse<Void>> approveRefund(@PathVariable Long id) {
        refundService.approveRefund(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Success"));
    }

    /**
     * PUT /api/v1/finance/refunds/{id}/reject
     * Từ chối yêu cầu hoàn tiền kèm theo lý do từ chối (rejectReason).
     */
    @PutMapping("/{id}/reject")
    @LogAudit(action = "UPDATE", resource = "Refund", description = "Reject refund request")
    public ResponseEntity<ApiResponse<Void>> rejectRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("rejectReason");
        refundService.rejectRefund(id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Success"));
    }

    /**
     * POST /api/v1/finance/refunds/{id}/proof
     * Tải lên hình ảnh bằng chứng (proof) hoàn tiền (ví dụ biên lai chuyển khoản).
     */
    @PostMapping("/{id}/proof")
    public ResponseEntity<ApiResponse<String>> uploadProof(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file);
        refundService.uploadProof(id, fileUrl);
        return ResponseEntity.ok(ApiResponse.success(fileUrl, "Success"));
    }
}

