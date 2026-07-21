/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: CRUD/kho hàng cho thẻ RFID - phục vụ màn hình "Card
 * Warehouse" của manager: liệt kê thẻ, đổi trạng thái thủ công, import CSV
 * hàng loạt. Không chứa logic vòng đời thật của thẻ (gắn thẻ lúc check-in,
 * thu thẻ lúc check-out) - phần đó nằm ở GateOperationService/IncidentService.
 * @Dependencies:
 * - RfidCardRepository (Local)
 */
package com.pbms.modules.infrastructure.service;

import com.pbms.modules.infrastructure.domain.RfidCard;
import com.pbms.modules.infrastructure.dto.RfidCardDTO;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RfidCardService {

    private final RfidCardRepository rfidCardRepository;

    /**
     * @Function: getAllCards
     * @Description: Liệt kê toàn bộ thẻ trong kho. visualId/location là giá
     * trị suy luận: visualId ghép từ cardId; location suy từ status (IN_USE
     * -> "đang trong 1 phiên đỗ", AVAILABLE -> "đang ở quầy nhân viên", còn
     * lại -> "Unknown").
     */
    public List<RfidCardDTO> getAllCards() {
        return rfidCardRepository.findAll().stream()
                .map(card -> RfidCardDTO.builder()
                        .uid(card.getCardCode())
                        .visualId(card.getCardId() != null ? card.getCardId() : "N/A")
                        .status(card.getStatus())
                        .location(card.getStatus().equals("IN_USE") ? "In Session" : (card.getStatus().equals("AVAILABLE") ? "Gate Staff" : "Unknown"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * @Function: updateStatus
     * @Description: Đổi trạng thái 1 thẻ theo mã UID - dùng cho các nút thao
     * tác thủ công (Báo mất, Đánh dấu hỏng, Khôi phục...). Không kiểm tra
     * nghiệp vụ thêm ở đây (FE tự chặn trước khi gọi).
     */
    @org.springframework.transaction.annotation.Transactional
    public void updateStatus(String uid, String newStatus) {
        RfidCard card = rfidCardRepository.findByCardCode(uid)
                .orElseThrow(() -> new RuntimeException("RFID Card not found: " + uid));
        card.setStatus(newStatus);
        rfidCardRepository.save(card);
    }

    /**
     * @Function: importCardsFromCsv
     * @Description: Import hàng loạt thẻ mới từ file CSV (cột:
     * cardId,cardCode,assignedPlate - chỉ cardCode bắt buộc).
     * @Logic_Steps:
     * 1. Đọc file theo từng dòng, bỏ qua dòng trống/dòng tiêu đề.
     * 2. Mỗi dòng hợp lệ: nếu cardId hoặc cardCode đã tồn tại thì bỏ qua âm
     *    thầm (tránh vi phạm ràng buộc unique trên cardCode); chưa có thì
     *    tạo mới, status mặc định AVAILABLE.
     * 3. Trả về số thẻ mới đã tạo.
     */
    @org.springframework.transaction.annotation.Transactional
    public int importCardsFromCsv(MultipartFile file) {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.toLowerCase().startsWith("cardid") || line.toLowerCase().startsWith("card_id")) continue;
                String[] columns = line.split(",");
                if (columns.length >= 2) {
                    String cardId = columns[0].trim();
                    String cardCode = columns[1].trim();
                    if (!cardId.isEmpty() && !cardCode.isEmpty()
                            && rfidCardRepository.findByCardId(cardId).isEmpty()
                            && rfidCardRepository.findByCardCode(cardCode).isEmpty()) {
                        RfidCard card = new RfidCard();
                        card.setCardId(cardId);
                        card.setCardCode(cardCode);
                        card.setStatus("AVAILABLE");
                        card.setAssignedPlate(null);
                        rfidCardRepository.save(card);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing CSV file", e);
        }
        return count;
    }
}
