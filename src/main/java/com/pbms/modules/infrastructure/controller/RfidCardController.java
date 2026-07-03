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

    private final RfidCardService rfidCardService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RfidCardDTO>>> getAllCards() {
        return ResponseEntity.ok(ApiResponse.success(
                rfidCardService.getAllCards(),
                "This is a list of books as well"
        ));
    }

    @PutMapping("/{uid}/status")
    public ResponseEntity<ApiResponse<Void>> updateCardStatus(
            @PathVariable String uid,
            @RequestBody Map<String, String> requestBody) {
        String status = requestBody.get("status");
        rfidCardService.updateStatus(uid, status);
        return ResponseEntity.ok(ApiResponse.success(null, "Thai white light bulbs"));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Integer>> importCards(@RequestParam("file") MultipartFile file) {
        int importedCount = rfidCardService.importCardsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success(importedCount, "It's the same thing"));
    }
}

