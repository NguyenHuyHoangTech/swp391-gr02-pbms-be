package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.RfidCardDTO;
import com.pbms.modules.infrastructure.service.RfidCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/infrastructure/cards")
@RequiredArgsConstructor
public class RfidCardController {

    // Khai báo Service để gọi các logic nghiệp vụ.
    // Bắt buộc phải có chữ 'final' để Lombok tự động đưa vào Constructor.
    private final RfidCardService rfidCardService;

    /**
     * API 1: LẤY DANH SÁCH TẤT CẢ CÁC THẺ RFID
     * Phương thức: GET
     * URL thực tế: GET /api/v1/infrastructure/cards
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RfidCardDTO>>> getAllCards() {
        // Gọi service lấy data, sau đó dùng ApiResponse.success để đóng gói thành chuẩn của hệ thống
        return ResponseEntity.ok(ApiResponse.success(
                rfidCardService.getAllCards(),
                "Get the list of RFID cards successfully" // Đã sửa lại câu message cho chuẩn ngữ cảnh
        ));
    }

    /**
     * API 2: CẬP NHẬT TRẠNG THÁI CỦA MỘT THẺ (Ví dụ: Khóa thẻ, Kích hoạt thẻ)
     * Phương thức: PUT
     * URL thực tế: PUT /api/v1/infrastructure/cards/{uid}/status (Ví dụ: /cards/CARD_001/status)
     * * @param uid: Lấy trực tiếp từ đường dẫn URL nhờ annotation @PathVariable
     * @param requestBody: Dữ liệu JSON Frontend gửi lên nhờ annotation @RequestBody (Ví dụ: {"status": "INACTIVE"})
     */
    @PutMapping("/{uid}/status")
    public ResponseEntity<ApiResponse<Void>> updateCardStatus(
            @PathVariable String uid,
            @RequestBody Map<String, String> requestBody) {

        // Trích xuất trạng thái mới từ cục JSON mà Frontend gửi lên
        String status = requestBody.get("status");

        // Gọi service để lưu thay đổi xuống database
        rfidCardService.updateStatus(uid, status);

        // Trả về thành công (không cần trả data, nên để là null)
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật trạng thái thẻ thành công"));
    }

    /**
     * API 3: IMPORT DANH SÁCH THẺ TỪ FILE CSV
     * Phương thức: POST
     * URL thực tế: POST /api/v1/infrastructure/cards/import
     * * @param file: Đại diện cho file vật lý được tải lên.
     * @RequestParam("file"): Frontend/Postman phải gửi file này ở dạng form-data với key là "file".
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Integer>> importCards(@RequestParam("file") MultipartFile file) {
        // Gọi service để đọc file và lưu vào DB. Service sẽ trả về số lượng thẻ import thành công
        int importedCount = rfidCardService.importCardsFromCsv(file);

        // Trả về số lượng thẻ kèm theo thông báo
        return ResponseEntity.ok(ApiResponse.success(importedCount, "Đã import thành công " + importedCount + " thẻ"));
    }
}

