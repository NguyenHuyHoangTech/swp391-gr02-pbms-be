/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller quản lý kho thẻ RFID (màn hình Card Warehouse của
 * manager) - liệt kê thẻ, đổi trạng thái thủ công, import CSV hàng loạt.
 * @Dependencies:
 * - RfidCardService (Local)
 */
package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.RfidCardDTO;
import com.pbms.modules.infrastructure.service.RfidCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/infrastructure/cards")
@RequiredArgsConstructor
public class RfidCardController {

    private final RfidCardService rfidCardService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RfidCardDTO>>> getAllCards() {
        return ResponseEntity.ok(ApiResponse.success(
                rfidCardService.getAllCards(),
                "Fetched cards successfully"
        ));
    }

    @PutMapping("/{uid}/status")
    @LogAudit(action = "UPDATE", resource = "RfidCard", description = "Update RFID card status")
    public ResponseEntity<ApiResponse<Void>> updateCardStatus(
            @PathVariable String uid,
            @RequestBody Map<String, String> requestBody) {
        String status = requestBody.get("status");
        rfidCardService.updateStatus(uid, status);
        return ResponseEntity.ok(ApiResponse.success(null, "Card status updated successfully"));
    }

    @PostMapping("/import")
    @LogAudit(action = "CREATE", resource = "RfidCard", description = "Import RFID cards")
    public ResponseEntity<ApiResponse<Integer>> importCards(@RequestParam("file") MultipartFile file) {
        int importedCount = rfidCardService.importCardsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success(importedCount, "Imported cards successfully"));
    }
}
