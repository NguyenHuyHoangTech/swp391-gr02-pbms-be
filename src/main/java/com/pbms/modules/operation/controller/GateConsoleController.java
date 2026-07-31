
/**
 * @Author: Nguyễn Huy Hoàng
 * @Date: 2026-06-28
 * @Description: REST Controller đóng vai trò cầu nối giao tiếp giữa Web Frontend (Giao diện nhân viên điều khiển cổng) 
 * và Backend để chốt các nghiệp vụ Check-in, Check-out và điều hướng xe.
 * Lưu ý kiến trúc: Controller này KHÔNG trực tiếp nhận tín hiệu quét biển số từ giả lập IoT (nhiệm vụ đó của IotHardwareController), 
 * mà nó chịu trách nhiệm xử lý bước cuối (Human-in-the-loop) khi nhân viên nhấn nút "Xác nhận" trên Frontend.
 * 
 * @Dependencies (Các Service được tiêm vào nội bộ):
 * - GateOperationService: Xử lý nghiệp vụ lõi (lưu db phiên đỗ, cập nhật trạng thái thẻ).
 * 
 * @Interactions (Tương tác hệ thống thực tế):
 * - Frontend (React): Được gọi bởi GateInConsoleScreen.tsx và GateOutConsoleScreen.tsx (gửi request xác nhận).
 * - IoT Simulator (NodeJS): Không tương tác trực tiếp, nhưng xử lý chốt hạ dữ liệu do IoT simulator bắn nháp trước đó.
 *
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ REQUEST CỦA HỆ THỐNG (KÈM MINH CHỨNG CODE)
 * (Trình bày chi tiết luồng dữ liệu ánh xạ trực tiếp vào các dòng code trong file này)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO BỘ ĐIỀU PHỐI VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Tại dòng 82 có khai báo `@RestController`. Báo cho Spring Boot biết class này 
 *   là một Web API (chuyên phục vụ Web Frontend).
 * - Minh chứng 2: Tại dòng 84 khai báo `@RequiredArgsConstructor` (của Lombok). Ký hiệu này 
 *   tự động sinh ra một Constructor để Spring "tiêm" (inject) các Service vào Controller.
 * - Minh chứng 3: Biến `gateOperationService` (dòng 87) được khai báo `final`. Spring sẽ lấy bộ não từ RAM nhét vào đây 
 *   để Controller có vũ khí xử lý.
 * 
 * BƯỚC 2: TIẾP NHẬN REQUEST TỪ FRONTEND (DISPATCHER SERVLET & HANDLER MAPPING)
 * - Minh chứng 1: Dòng 83 khai báo `@RequestMapping("/api/v1/operation/gates")` là Gốc của URL.
 * - Minh chứng 2: Dòng 93 khai báo `@PostMapping("/check-in")`. Spring ghép nối URL để định tuyến 
 *   yêu cầu "Nhân viên xác nhận xe vào" từ Web (React) vào trúng hàm `processCheckIn`.
 * 
 * BƯỚC 3: RÀNG BUỘC VÀ CHUYỂN ĐỔI DỮ LIỆU (DESERIALIZATION)
 * - Minh chứng: Dòng 94 chứa `(@RequestBody CheckInRequestDTO request)`. 
 *   + `@RequestBody` lệnh cho thư viện Jackson biến chuỗi JSON gửi từ Frontend (sau khi nhân viên 
 *     bấm nút xác nhận) thành đối tượng Java (`CheckInRequestDTO`).
 * 
 * BƯỚC 4: ỦY QUYỀN XỬ LÝ NGHIỆP VỤ LÕI (SERVICE DELEGATION - N-TIER ARCHITECTURE)
 * - Controller không tự lưu Database. Nó đẩy việc cho Service.
 * - Minh chứng: Dòng 96 chứa lệnh `GateResponseDTO response = gateOperationService.processCheckIn(request);`.
 *   Controller ném cái hộp dữ liệu `request` sang cho bộ não `gateOperationService` để nó lưu phiên đỗ xe 
 *   vào CSDL, giải phóng bãi, v.v. (Giai đoạn Process - Chốt hạ của Human-in-the-loop).
 * 
 * BƯỚC 5: ĐÓNG GÓI VÀ TRẢ VỀ RESPONSE (SERIALIZATION & HTTP 200 OK)
 * - Minh chứng: Dòng 100 chứa `return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));`.
 *   + Đóng gói kết quả vào `ApiResponse` (chuẩn chung của dự án).
 *   + Gắn thẻ mã trạng thái HTTP 200 (Thành công).
 *   + Spring Boot (thông qua Jackson) dịch ngược đối tượng này thành JSON rồi bắn về Web Frontend để tắt popup.
 * 
 * PHỤ LỤC 1: HÀNH TRÌNH REQUEST CỦA LUỒNG CHECK-OUT (XE RA)
 * - Minh chứng Handler: Dòng 106 `@PostMapping("/check-out")`.
 * - Minh chứng Deserialization: Dòng 107 `(@RequestBody CheckOutRequestDTO request)`.
 * - Minh chứng Delegation: Dòng 109 `GateResponseDTO response = gateOperationService.processCheckOut(request);`. (Chốt phiên, thu tiền thật).
 * - Minh chứng Serialization: Dòng 116 `return ResponseEntity.ok(...)`. Báo về Web để nâng Barie.
 * 
 * PHỤ LỤC 2: CÁC LUỒNG TRUY VẤN DỮ LIỆU TỪ WEB (GET REQUESTS)
 * - Luồng lấy hóa đơn tạm tính: Dòng 122 `@GetMapping("/checkout-session-info")`. Gọi `gateOperationService.getCheckOutSessionInfo(...)` (Dòng 127) để hiện bảng giá lên màn hình nhân viên trước khi thu tiền.
 */
package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;

import org.springframework.http.ResponseEntity;
import com.pbms.modules.operation.dto.CheckInRequestDTO;
import com.pbms.modules.operation.dto.CheckOutRequestDTO;
import com.pbms.modules.operation.dto.GateResponseDTO;
import com.pbms.modules.operation.service.GateOperationService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operation/gates")
@RequiredArgsConstructor
public class GateConsoleController {

    private final GateOperationService gateOperationService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<GateResponseDTO>> processCheckIn(@RequestBody CheckInRequestDTO request) {
        try {
            GateResponseDTO response = gateOperationService.processCheckIn(request);
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
            }
            return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<GateResponseDTO>> processCheckOut(@RequestBody CheckOutRequestDTO request) {
        try {
            GateResponseDTO response = gateOperationService.processCheckOut(request);
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
            }
            if ("WARNING".equals(response.getStatus())) {
                return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
            }
            return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/checkout-session-info")
    public ResponseEntity<ApiResponse<com.pbms.modules.operation.dto.CheckOutSessionInfoDTO>> getCheckOutSessionInfo(
            @RequestParam(required = false) String rfid,
            @RequestParam(required = false) String plate) {
        try {
            com.pbms.modules.operation.dto.CheckOutSessionInfoDTO info = gateOperationService.getCheckOutSessionInfo(rfid, plate);
            return ResponseEntity.ok(ApiResponse.success(info, "Fetched session info successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
