package com.pbms.modules.finance.service;

import com.pbms.modules.finance.domain.RefundRequest;
import com.pbms.modules.finance.dto.RefundRequestDTO;
import com.pbms.modules.finance.repository.RefundRequestRepository;
import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
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
    private final MonthlyTicketRepository monthlyTicketRepository;
    private final SimpMessagingTemplate messagingTemplate;



    public List<RefundRequestDTO> getAllRefunds() {
        return refundRequestRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void approveRefund(Long id) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setStatus("REFUNDED");
        refundRequestRepository.save(request);

        // Báº¯n WebSocket thÃ´ng bÃ¡o
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request processed successfully for ID: " + id);
    }

    public void rejectRefund(Long id, String reason) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setStatus("REJECTED");
        request.setRejectReason(reason);
        refundRequestRepository.save(request);

        // Báº¯n WebSocket thÃ´ng bÃ¡o
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request rejected for ID: " + id + ". Reason: " + reason);
    }
    public void resubmitRefund(Long id, String bankName, String accountNumber, String accountName) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setBankName(bankName);
        request.setAccountNumber(accountNumber);
        request.setAccountName(accountName);
        request.setStatus("PENDING");
        request.setRejectReason(null);
        refundRequestRepository.save(request);

        // Notify manager via websocket
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request resubmitted for ID: " + id);
    }

    private RefundRequestDTO mapToDTO(RefundRequest req) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String plate = "Unknown";
        String expectedInTime = "";
        try {
            if ("RESERVATION".equals(req.getReferenceType())) {
                Reservation res = reservationRepository.findById(Long.valueOf(req.getReferenceId())).orElse(null);
                if (res != null) {
                    plate = res.getVehicle() != null ? res.getVehicle().getPlateNumber() : "Unknown";
                    expectedInTime = res.getExpectedEntryTime() != null ? res.getExpectedEntryTime().format(formatter) : "";
                }
            } else if ("MONTHLY_PASS".equals(req.getReferenceType())) {
                com.pbms.modules.operation.domain.MonthlyTicket mt = monthlyTicketRepository.findById(Long.valueOf(req.getReferenceId())).orElse(null);
                if (mt != null) plate = mt.getPlateNumber();
            }
        } catch (Exception e) {
            // Ignore parse errors
        }

        return RefundRequestDTO.builder()
                .id("REF-" + req.getId())
                .customerName(req.getUser().getFullName() != null ? req.getUser().getFullName() : "Unknown Customer")
                .customerEmail(req.getUser().getEmail() != null ? req.getUser().getEmail() : "Unknown Email")
                .registeredName(req.getUser().getFullName() != null ? req.getUser().getFullName() : "Unknown Customer")
                .plateNumber(plate)
                .bookingTime(req.getCreatedAt() != null ? req.getCreatedAt().format(formatter) : "")
                .expectedInTime(expectedInTime)
                .paidAmount(req.getPaidAmount())
                .penaltyFee(req.getPenaltyFee())
                .refundAmount(req.getRefundAmount())
                .status(req.getStatus())
                .bankName(req.getBankName())
                .accountNumber(req.getAccountNumber())
                .accountName(req.getAccountName())
                .rejectReason(req.getRejectReason())
                .referenceType(req.getReferenceType())
                .referenceId(req.getReferenceId())
                .cancelTime(req.getCreatedAt() != null ? req.getCreatedAt().format(formatter) : "")
                .proofUrl(req.getProofUrl())
                .build();
    }
    
    public void uploadProof(Long id, String proofUrl) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setProofUrl(proofUrl);
        refundRequestRepository.save(request);
    }
}

