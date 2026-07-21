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

    /**
     * Đồng bộ trạng thái hoàn tiền về module Vận hành (Operation).
     * Nếu giao dịch hoàn tiền xuất phát từ tiền cọc Đặt chỗ (Reservation), hệ thống sẽ cập nhật 
     * trạng thái hoàn tiền (REFUNDED/REJECTED) và chứng từ vào bản ghi Đặt chỗ tương ứng.
     *
     * @param request Yêu cầu hoàn tiền đang được xử lý.
     */
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

    /**
     * Lấy danh sách toàn bộ các yêu cầu hoàn tiền trên hệ thống (chờ duyệt, đã duyệt, từ chối).
     * @return Danh sách DTO hiển thị cho Admin.
     */
    public List<RefundRequestDTO> getAllRefunds() {
        return refundRequestRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Phê duyệt (Approve) yêu cầu hoàn tiền.
     * Đổi trạng thái thành REFUNDED, đồng bộ về module liên quan và gửi thông báo Real-time (WebSocket).
     *
     * @param id Mã định danh của RefundRequest.
     */
    public void approveRefund(Long id) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setStatus("REFUNDED");
        refundRequestRepository.save(request);
        syncReservation(request);

        // Báº¯n WebSocket thÃ´ng bÃ¡o
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request processed successfully for ID: " + id);
    }

    /**
     * Từ chối (Reject) yêu cầu hoàn tiền kèm theo lý do cụ thể.
     * Đổi trạng thái thành REJECTED, ghi lại lý do và gửi thông báo Real-time (WebSocket).
     *
     * @param id Mã định danh của RefundRequest.
     * @param reason Lý do từ chối (Ví dụ: Chuyển khoản thất bại, đã nhận tiền mặt).
     */
    public void rejectRefund(Long id, String reason) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setStatus("REJECTED");
        request.setRejectReason(reason);
        refundRequestRepository.save(request);
        syncReservation(request);

        // Báº¯n WebSocket thÃ´ng bÃ¡o
        messagingTemplate.convertAndSend("/topic/alerts", "Refund request rejected for ID: " + id + ". Reason: " + reason);
    }

    private RefundRequestDTO mapToDTO(RefundRequest req) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String plate = "Unknown";
        try {
            if ("RESERVATION".equals(req.getReferenceType())) {
                Reservation res = reservationRepository.findById(Long.valueOf(req.getReferenceId())).orElse(null);
                if (res != null) plate = res.getVehicle() != null ? res.getVehicle().getPlateNumber() : "Unknown";
            } else if ("MONTHLY_PASS".equals(req.getReferenceType())) {
                com.pbms.modules.operation.domain.MonthlyTicket mt = monthlyTicketRepository.findById(Long.valueOf(req.getReferenceId())).orElse(null);
                if (mt != null) plate = mt.getPlate();
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
                .referenceType(req.getReferenceType())
                .proofUrl(req.getProofUrl())
                .build();
    }
    
    /**
     * Cập nhật đường dẫn hình ảnh chứng từ (Proof) chuyển khoản cho Yêu cầu hoàn tiền.
     *
     * @param id Mã định danh của RefundRequest.
     * @param proofUrl URL của file chứng từ (đã lưu trên MinIO/S3).
     */
    public void uploadProof(Long id, String proofUrl) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setProofUrl(proofUrl);
        refundRequestRepository.save(request);
        syncReservation(request);
    }
}

