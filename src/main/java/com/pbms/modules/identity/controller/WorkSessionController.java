package com.pbms.modules.identity.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.identity.domain.StaffWorkSession;
import com.pbms.modules.identity.service.WorkSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/identity/work-sessions")
@RequiredArgsConstructor
public class WorkSessionController {

    private final WorkSessionService workSessionService;

    /**
     * POST /api/v1/work-sessions/start
     * Body: { "gateId": 1 }
     * Staff báº¥m Má»Ÿ ca trá»±c
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startSession(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        try {
            String email = authentication.getName();
            Long gateId = Long.valueOf(body.get("gateId").toString());
            String gateType = body.get("gateType") != null ? body.get("gateType").toString() : null;
            StaffWorkSession session = workSessionService.startSession(email, gateId, gateType);

            Map<String, Object> result = Map.of(
                    "sessionId", session.getId(),
                    "gateId", session.getGate().getId(),
                    "gateName", session.getGate().getGateName(),
                    "gateType", session.getGate().getGateType(),
                    "loginTime", session.getLoginTime(),
                    "status", session.getStatus()
            );
            return ResponseEntity.ok(ApiResponse.success(result, "The truth is that"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/work-sessions/end
     * Body: { "declaredCash": 500000 }
     * Staff báº¥m ÄÃ³ng ca trá»±c
     */
    @PutMapping("/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endSession(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        try {
            String email = authentication.getName();
            BigDecimal declaredCash = body.get("declaredCash") != null
                    ? new BigDecimal(body.get("declaredCash").toString())
                    : BigDecimal.ZERO;
            String varianceReason = body.get("varianceReason") != null ? body.get("varianceReason").toString() : null;
            Map<String, Object> result = workSessionService.endSession(email, declaredCash, varianceReason);
            return ResponseEntity.ok(ApiResponse.success(result, "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/work-sessions/current
     * Get current active session
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentSession(Authentication authentication) {
        try {
            String email = authentication.getName();
            Map<String, Object> preview = workSessionService.getPreviewSettlement(email);
            if (Boolean.TRUE.equals(preview.get("hasActiveSession"))) {
                return ResponseEntity.ok(ApiResponse.success(preview, "Found active session"));
            }
            return ResponseEntity.ok(ApiResponse.success(null, "No active session"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(null, "No active session"));
        }
    }

    /**
     * GET /api/v1/work-sessions/current/preview-settlement
     * Preview doanh thu trÆ°á»›c khi Ä‘Ã³ng ca
     */
    @GetMapping("/current/preview-settlement")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewSettlement(Authentication authentication) {
        try {
            String email = authentication.getName();
            Map<String, Object> preview = workSessionService.getPreviewSettlement(email);
            return ResponseEntity.ok(ApiResponse.success(preview, "Fetched preview settlement"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/work-sessions/history
     * Láº¥y lá»‹ch sá»­ ca trá»±c
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Object>> getHistory(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "logoutTime"));
            Object history = workSessionService.getWorkSessionHistory(startDate, endDate, pageable);
            return ResponseEntity.ok(ApiResponse.success(history, "Leave a comment"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
}

