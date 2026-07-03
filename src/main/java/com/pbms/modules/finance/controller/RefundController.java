package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundRequestDTO>>> getAllRefunds() {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.getAllRefunds(),
                "Make a list of projects"
        ));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveRefund(@PathVariable Long id) {
        refundService.approveRefund(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Success"));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("rejectReason");
        refundService.rejectRefund(id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Success"));
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

