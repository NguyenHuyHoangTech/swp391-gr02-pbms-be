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

// =========================================================================
// PHẦN 1: ENTITY VÀ DTO CỦA THẺ RFID
// =========================================================================
import com.pbms.modules.infrastructure.domain.RfidCard; // Entity bảng rfid_cards.
import com.pbms.modules.infrastructure.dto.RfidCardDTO; // DTO chuyển tiếp thông tin thẻ cho Client.

// =========================================================================
// PHẦN 2: REPOSITORY TRUY VẤN
// =========================================================================
import com.pbms.modules.infrastructure.repository.RfidCardRepository; // Kho thao tác DB với bảng rfid_cards.

// =========================================================================
// PHẦN 3: LOMBOK, SPRING BOOT SERVICE VÀ CÔNG CỤ ĐỌC FILE
// =========================================================================
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * =========================================================================================
 * DỊCH VỤ NGHIỆP VỤ QUẢN LÝ THẺ RFID (RFID CARD SERVICE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Service này chuyên quản lý vòng đời và trạng thái của các thẻ RFID được sử dụng tại
 * các cổng kiểm soát bãi xe, bao gồm liệt kê thẻ, đổi trạng thái và xử lý đọc tệp tin
 * CSV hàng loạt để import thẻ mới vào hệ thống.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Hàm `getAllCards` chuyển đổi trạng thái `"IN_USE"` sang vị trí
 *   `"In Session"`, và `"AVAILABLE"` sang `"Gate Staff"` để Frontend hiển thị dễ hiểu.
 * - Minh chứng 2: Hàm `importCardsFromCsv` tự động bỏ qua dòng tiêu đề CSV và thẻ đã tồn tại
 *   (`rfidCardRepository.findByCardId`), tránh trùng lặp dữ liệu trong Database.
 * =========================================================================================
 */
@Service
@RequiredArgsConstructor
public class RfidCardService {

    private final RfidCardRepository rfidCardRepository;

    /**
     * =========================================================================
     * API/HÀM: LẤY DANH SÁCH TOÀN BỘ THẺ RFID CÙNG VỊ TRÍ SỬ DỤNG
     * =========================================================================
     * MỤC ĐÍCH:
     * Truy vấn toàn bộ bảng `rfid_cards`, ánh xạ thành DTO và dịch trạng thái
     * sang chuỗi định vị thân thiện với người dùng.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lấy tất cả RfidCard từ database.
     * 2. Duyệt qua từng thẻ, kiểm tra status để tính toán thuộc tính `location`:
     *    - "IN_USE" -> "In Session" (đang gắn với xe trong bãi).
     *    - "AVAILABLE" -> "Gate Staff" (sẵn sàng tại cổng).
     * 3. Trả về danh sách RfidCardDTO hoàn chỉnh.
     */
    public List<RfidCardDTO> getAllCards() {
        return rfidCardRepository.findAll().stream()
                .map(card -> {
                    // QUY ĐỔI ĐỊA ĐIỂM (LOCATION):
                    // - Trạng thái "IN_USE": Thẻ đang gắn với xe gửi trong bãi -> hiển thị "In Session".
                    // - Trạng thái "AVAILABLE": Thẻ rảnh, đang để tại quầy kiểm soát -> hiển thị "Gate Staff".
                    // - Các trạng thái khác (DAMAGED, LOST...): Không xác định vị trí -> hiển thị "Unknown".
                    String mappedLocation = card.getStatus().equals("IN_USE") ? "In Session"
                            : (card.getStatus().equals("AVAILABLE") ? "Gate Staff" : "Unknown");

                    return RfidCardDTO.builder()
                            .uid(card.getCardCode())
                            .visualId(card.getCardId() != null ? card.getCardId() : "N/A")
                            .status(card.getStatus())
                            .location(mappedLocation)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * =========================================================================
     * API/HÀM: CẬP NHẬT TRẠNG THÁI THẺ RFID (UPDATE STATUS)
     * =========================================================================
     * MỤC ĐÍCH:
     * Cập nhật trạng thái của thẻ theo mã UID (ví dụ: AVAILABLE, DAMAGED, LOST).
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Tìm thẻ RFID trong DB bằng `rfidCardRepository.findByCardCode(uid)`.
     * 2. Nếu không thấy -> Ném ngoại lệ `RuntimeException`.
     * 3. Gán trạng thái mới `newStatus` và lưu lại vào Database.
     */
    @org.springframework.transaction.annotation.Transactional
    public void updateStatus(String uid, String newStatus) {
        // Tìm kiếm chính xác theo mã Chip (cardCode/UID). Ném ngoại lệ nếu không tồn tại
        // để API phản hồi 400/500 cho Client.
        RfidCard card = rfidCardRepository.findByCardCode(uid)
                .orElseThrow(() -> new RuntimeException("RFID Card not found: " + uid));
        card.setStatus(newStatus);
        rfidCardRepository.save(card);
    }

    /**
     * =========================================================================
     * API/HÀM: IMPORT DANH SÁCH THẺ TỪ FILE CSV
     * =========================================================================
     * MỤC ĐÍCH:
     * Đọc tệp tin CSV chứa danh sách thẻ (định dạng `cardId,cardCode`), kiểm tra
     * không trùng lặp và lưu vào Database.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Khởi tạo `BufferedReader` đọc file CSV với bảng mã UTF-8.
     * 2. Bỏ qua dòng trắng hoặc dòng tiêu đề (`cardid` / `card_id`).
     * 3. Tách chuỗi theo dấu phẩy `,` để lấy `cardId` và `cardCode`.
     * 4. Kiểm tra trong DB nếu `cardId` chưa từng tồn tại -> Khởi tạo Entity mới
     *    với trạng thái ban đầu `"AVAILABLE"`.
     * 5. Lưu vào Database và đếm tổng số lượng thẻ đã import thành công.
     */
    @org.springframework.transaction.annotation.Transactional
    public int importCardsFromCsv(MultipartFile file) {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Tự động bỏ qua dòng trống hoặc dòng chứa tiêu đề CSV (cardid/card_id)
                if (line.trim().isEmpty() || line.toLowerCase().startsWith("cardid") || line.toLowerCase().startsWith("card_id")) continue;
                String[] columns = line.split(",");
                if (columns.length >= 2) {
                    String cardId = columns[0].trim();
                    String cardCode = columns[1].trim();
                    // KIỂM TRA TRÙNG LẶP: Chỉ nạp thẻ vào DB nếu mã in trên thẻ (cardId)
                    // chưa từng được khai báo trước đó trong hệ thống.
                    if (!cardId.isEmpty() && !cardCode.isEmpty() && rfidCardRepository.findByCardId(cardId).isEmpty()) {
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

