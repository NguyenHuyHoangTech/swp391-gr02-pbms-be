package com.pbms.modules.finance.service;

import com.pbms.modules.finance.domain.RefundRequest;
import com.pbms.modules.finance.dto.RefundRequestDTO;
import com.pbms.modules.finance.repository.RefundRequestRepository;
import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final ReservationRepository reservationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private void syncReservation(RefundRequest request) {
        if ("RESERVATION".equals(request.getReferenceType())) {
            try {
                Long resId = Long.valueOf(request.getReferenceId());
                Reservation res = reservationRepository.findById(resId).orElse(null);
                if (res != null) {
                    res.setRefundStatus(request.getStatus());
                    res.setRefundProofUrl(request.getProofUrl());
                    res.setRefundRejectReason(request.getRejectReason());
                    reservationRepository.save(res);
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
    }

    public List<RefundRequestDTO> getAllRefunds() {
        return refundRequestRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void approveRefund(Long id) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Do not pay attention to this requirement"));
        request.setStatus("REFUNDED");
        refundRequestRepository.save(request);
        syncReservation(request);

        // Báº¯n WebSocket thÃ´ng bÃ¡o
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request processed successfully for ID: " + id);
    }

    public void rejectRefund(Long id, String reason) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Do not pay attention to this requirement"));
        request.setStatus("REJECTED");
        request.setRejectReason(reason);
        refundRequestRepository.save(request);
        syncReservation(request);

        // Báº¯n WebSocket thÃ´ng bÃ¡o
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request rejected for ID: " + id + ". Reason: " + reason);
    }

    private RefundRequestDTO mapToDTO(RefundRequest req) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return RefundRequestDTO.builder()
                .id("REF-" + req.getId())
                .customerName(req.getUser().getFullName() != null ? req.getUser().getFullName() : "Customers")
                .registeredName(req.getUser().getFullName() != null ? req.getUser().getFullName() : "Customers")
                .plateNumber("XX-XXXX.XX") // Mock fallback
                .bookingTime(req.getCreatedAt() != null ? req.getCreatedAt().format(formatter) : "")
                .expectedInTime(req.getCancelTime() != null ? req.getCancelTime().plusHours(1).format(formatter) : "")
                .cancelTime(req.getCancelTime() != null ? req.getCancelTime().format(formatter) : "")
                .paidAmount(req.getPaidAmount())
                .penaltyFee(req.getPenaltyFee())
                .refundAmount(req.getRefundAmount())
                .status(req.getStatus())
                .bankName(req.getBankName())
                .accountNumber(req.getAccountNumber())
                .accountName(req.getAccountName())
                .rejectReason(req.getRejectReason())
                .proofUrl(req.getProofUrl())
                .build();
    }
    
    public void uploadProof(Long id, String proofUrl) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Do not pay attention to this requirement"));
        request.setProofUrl(proofUrl);
        refundRequestRepository.save(request);
        syncReservation(request);
    }
}

