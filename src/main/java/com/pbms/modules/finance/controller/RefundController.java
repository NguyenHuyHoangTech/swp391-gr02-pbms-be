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
     * Truy xuất toàn bộ danh sách các yêu cầu hoàn tiền (Refund Requests).
     * Dành cho quản trị viên kiểm tra và xử lý các giao dịch thất bại cần hoàn tiền cho khách.
     *
     * @return Danh sách yêu cầu hoàn tiền dưới dạng DTO.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundRequestDTO>>> getAllRefunds() {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.getAllRefunds(),
                "Make a list of projects"
        ));
    }

    @PutMapping("/{id}/approve")
    @LogAudit(action = "UPDATE", resource = "Refund", description = "Approve refund request")
    public ResponseEntity<ApiResponse<Void>> approveRefund(@PathVariable Long id) {
        refundService.approveRefund(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Success"));
    }

    /**
     * Từ chối (Reject) một yêu cầu hoàn tiền với lý do cụ thể (Ví dụ: Đã trả tiền mặt).
     * Trạng thái sẽ cập nhật thành REJECTED và lưu lại lý do từ chối.
     *
     * @param id Mã định danh của yêu cầu hoàn tiền.
     * @param body Payload chứa lý do từ chối (rejectReason).
     * @return Thông báo từ chối thành công.
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
     * Cập nhật chứng từ (ví dụ: ảnh chụp màn hình chuyển khoản) cho một yêu cầu hoàn tiền.
     * Chứng từ được tải lên hệ thống lưu trữ (MinIO/S3) và lấy về URL lưu vào CSDL.
     *
     * @param id Mã định danh của yêu cầu hoàn tiền.
     * @param file File chứng từ tải lên (MultipartFile).
     * @return Đường dẫn URL của file chứng từ đã tải lên.
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

