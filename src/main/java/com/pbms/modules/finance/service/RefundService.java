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
     * Đồng bộ trạng thái hoàn tiền (Status, Lý do từ chối, Biên lai) ngược lại cho Đơn đặt chỗ (Reservation).
     * Chỉ áp dụng nếu yêu cầu hoàn tiền này sinh ra từ một Reservation bị hủy/lỗi.
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
     * Lấy danh sách tất cả các yêu cầu hoàn tiền đang có trong hệ thống và chuyển thành DTO.
     */
    public List<RefundRequestDTO> getAllRefunds() {
        return refundRequestRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Xử lý khi Admin "Duyệt" yêu cầu hoàn tiền.
     * Trạng thái đơn chuyển thành REFUNDED, đồng bộ sang Reservation và gửi thông báo WebSocket cho User.
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
     * Xử lý khi Admin "Từ chối" hoàn tiền.
     * Lưu lại lý do từ chối (reason), đồng bộ và thông báo cho User.
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

    /**
     * Hàm mapper chuyển đổi từ Entity sang DTO.
     * Xử lý tìm kiếm thêm Biển số xe (Plate Number) bằng cách tra cứu ngược lại bảng Reservation hoặc MonthlyTicket.
     */
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
     * Cập nhật đường link của ảnh biên lai/chứng từ (proof) xác nhận đã chuyển khoản thành công cho khách.
     */
    public void uploadProof(Long id, String proofUrl) {
        RefundRequest request = refundRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refund request not found"));
        request.setProofUrl(proofUrl);
        refundRequestRepository.save(request);
        syncReservation(request);
    }
}

