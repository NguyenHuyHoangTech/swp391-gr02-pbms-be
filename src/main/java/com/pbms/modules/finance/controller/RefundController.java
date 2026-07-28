package com.pbms.modules.finance.controller;

// Thư mục DTO chung dùng để chuẩn hóa dữ liệu trả về từ API (chứa mã lỗi, thông báo, data)
import com.pbms.common.dto.ApiResponse;
// Custom annotation hỗ trợ ghi lại lịch sử thao tác của người dùng (Audit Log)
import com.pbms.common.annotation.LogAudit;
// Data Transfer Object chứa cấu trúc dữ liệu gửi và nhận yêu cầu hoàn tiền (Refund Request)
import com.pbms.modules.finance.dto.RefundRequestDTO;
// Service quản lý nghiệp vụ và các thay đổi trạng thái yêu cầu hoàn tiền
import com.pbms.modules.finance.service.RefundService;
// Lombok annotation tự động tạo constructor cho các thuộc tính final
import lombok.RequiredArgsConstructor;
// Lớp đóng gói HTTP status code, headers và body trong Spring MVC
import org.springframework.http.ResponseEntity;
// Nhập toàn bộ các annotation phục vụ viết REST Endpoint (RestController, RequestMapping, GetMapping,...)
import org.springframework.web.bind.annotation.*;
// Interface đại diện cho tệp tải lên (upload) được gửi trong request đa phần (Multipart Request)
import org.springframework.web.multipart.MultipartFile;
// Service dùng để lưu trữ file (ví dụ: hình ảnh bằng chứng giao dịch) lên ổ đĩa cục bộ hoặc cloud
import com.pbms.common.service.FileStorageService;

// Giao diện List đại diện cho tập hợp phần tử có thứ tự trong Java
import java.util.List;
// Giao diện (Interface) Map đại diện cho tập hợp lưu trữ khóa-giá trị (Key-Value)
import java.util.Map;

/**
 * =========================================================================================
 * 🌟 BỨC TRANH TOÀN CẢNH CỤ THỂ CHUẨN KỸ THUẬT CỦA RefundController.java 🌟
 * =========================================================================================
 * 
 * 1. AI KHỞI TẠO NÓ LÊN? (VÒNG ĐỜI - LIFECYCLE)
 * - Khi ứng dụng Spring Boot chạy, IoC Container sẽ quét qua class này nhờ tag `@RestController`.
 * - Lớp này được khởi tạo dưới dạng một thực thể Singleton duy nhất (Singleton Bean) để tiếp nhận các yêu cầu hoàn tiền.
 * - Lớp `RefundService` và `FileStorageService` sẽ được tự động bơm (Inject) thông qua Constructor nhờ Lombok `@RequiredArgsConstructor`.
 * 
 * 2. AI GỌI ĐẾN NÓ? (ĐẦU VÀO CỤ THỂ - INPUT)
 * Web Frontend (màn hình RefundManagementScreen.tsx) gửi các yêu cầu HTTP Request cụ thể tới các URL sau (tiền tố `/api/v1/finance/refunds`):
 * - GET  `/`                   : Lấy danh sách tất cả các yêu cầu hoàn tiền đang chờ duyệt hoặc lịch sử hoàn tiền.
 * - PUT  `/{id}/approve`       : Chấp nhận yêu cầu hoàn tiền (ID = id).
 * - PUT  `/{id}/reject`        : Mang theo body JSON chứa lý do từ chối hoàn tiền (`rejectReason`).
 * - PUT  `/{id}/resubmit`      : Khách hàng gửi lại thông tin ngân hàng mới (`bankName`, `accountNumber`, `accountName`) sau khi bị từ chối.
 * - POST `/{id}/proof`         : Nhân viên/Quản lý tải lên tệp ảnh/tài liệu biên lai chuyển khoản thành công (dưới dạng MultipartFile).
 * 
 * 3. NÓ GỌI ĐẾN AI? (ĐẦU RA CỤ THỂ TỚI SERVICE VÀ DATABASE)
 * Lớp Controller này phân bổ nghiệp vụ xuống các lớp Service tương ứng:
 * 
 * - Hàm `getAllRefunds()`:
 *   -> GỌI: `refundService.getAllRefunds()`
 *   -> Hậu quả: Truy vấn bảng `refund_requests` trong Database để hiển thị danh sách cho người quản trị.
 * 
 * - Hàm `approveRefund()`:
 *   -> GỌI: `refundService.approveRefund(id)`
 *   -> Hậu quả: Cập nhật trạng thái yêu cầu hoàn tiền thành APPROVED trong bảng `refund_requests`.
 * 
 * - Hàm `rejectRefund()`:
 *   -> GỌI: `refundService.rejectRefund(id, reason)`
 *   -> Hậu quả: Ghi nhận lý do từ chối vào cột `reject_reason` và chuyển trạng thái yêu cầu thành REJECTED.
 * 
 * - Hàm `resubmitRefund()`:
 *   -> GỌI: `refundService.resubmitRefund(id, bankName, accountNumber, accountName)`
 *   -> Hậu quả: Cập nhật thông tin tài khoản thụ hưởng mới của khách hàng và chuyển trạng thái về PENDING.
 * 
 * - Hàm `uploadProof()`:
 *   -> GỌI 1: `fileStorageService.storeFile(file)` để lưu trữ file ảnh chụp màn hình chuyển khoản vào thư mục máy chủ (bảng `uploads`) và nhận về đường dẫn URL.
 *   -> GỌI 2: `refundService.uploadProof(id, fileUrl)` để đính kèm link ảnh minh chứng giao dịch đó vào cột `proof_url` của bảng `refund_requests` trong Database.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/finance/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final FileStorageService fileStorageService;

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

    @PutMapping("/{id}/reject")
    @LogAudit(action = "UPDATE", resource = "Refund", description = "Reject refund request")
    public ResponseEntity<ApiResponse<Void>> rejectRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("rejectReason");
        refundService.rejectRefund(id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Success"));
    }
    @PutMapping("/{id}/resubmit")
    @LogAudit(action = "UPDATE", resource = "Refund", description = "Resubmit refund request")
    public ResponseEntity<ApiResponse<Void>> resubmitRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String bankName = body.get("bankName");
        String accountNumber = body.get("accountNumber");
        String accountName = body.get("accountName");
        refundService.resubmitRefund(id, bankName, accountNumber, accountName);
        return ResponseEntity.ok(ApiResponse.success(null, "Resubmitted successfully"));
    }

    @PostMapping("/{id}/proof")
    public ResponseEntity<ApiResponse<String>> uploadProof(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file);
        refundService.uploadProof(id, fileUrl);
        return ResponseEntity.ok(ApiResponse.success(fileUrl, "Success"));
    }
}

