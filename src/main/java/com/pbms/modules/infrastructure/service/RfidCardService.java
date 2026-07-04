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

    private final RfidCardRepository rfidCardRepository; // Khai báo Repository để thao tác với DB

    // Hàm lấy danh sách toàn bộ thẻ và chuyển đổi sang định dạng DTO cho Frontend
    public List<RfidCardDTO> getAllCards() {
        return rfidCardRepository.findAll().stream() // Lấy tất cả thẻ từ DB và mở luồng xử lý
                .map(card -> RfidCardDTO.builder() // Dùng Builder để tạo đối tượng DTO
                        .uid(card.getCardCode()) // Ánh xạ mã thẻ
                        .visualId("CARD-VL-" + String.format("%03d", card.getId())) // Tạo ID hiển thị theo định dạng 001, 002...
                        .status(card.getStatus()) // Gán trạng thái
                        // Logic kiểm tra vị trí dựa trên trạng thái (In Session, Gate Staff, hoặc Unknown)
                        .location(card.getStatus().equals("IN_USE") ? "In Session" : (card.getStatus().equals("AVAILABLE") ? "Gate Staff" : "Unknown"))
                        .build())
                .collect(Collectors.toList()); // Gom tất cả lại thành List và trả về
    }

    // Hàm cập nhật trạng thái thẻ dựa trên mã UID
    public void updateStatus(String uid, String newStatus) {
        // Tìm thẻ trong DB theo mã, nếu không thấy thì ném lỗi
        RfidCard card = rfidCardRepository.findByCardCode(uid)
                .orElseThrow(() -> new RuntimeException("RFID Card not found: " + uid));
        card.setStatus(newStatus); // Cập nhật trạng thái mới cho thẻ
        rfidCardRepository.save(card); // Lưu thay đổi vào DB
    }

    // Hàm xử lý việc import file CSV
    public int importCardsFromCsv(MultipartFile file) {
        int count = 0; // Đếm số thẻ import thành công
        // Mở file và đọc dữ liệu với định dạng UTF-8
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Bỏ qua dòng trống hoặc dòng tiêu đề (header)
                if (line.trim().isEmpty() || line.startsWith("cardCode")) continue;
                String[] columns = line.split(","); // Chia dòng thành mảng các cột dựa trên dấu phẩy
                if (columns.length > 0) {
                    String cardCode = columns[0].trim(); // Cột đầu tiên là mã thẻ
                    // Nếu mã thẻ không rỗng và chưa tồn tại trong DB thì tiến hành lưu
                    if (!cardCode.isEmpty() && rfidCardRepository.findByCardCode(cardCode).isEmpty()) {
                        RfidCard card = new RfidCard();
                        card.setCardCode(cardCode); // Gán mã thẻ
                        // Gán trạng thái (mặc định là AVAILABLE nếu không có cột 2)
                        card.setStatus(columns.length > 1 ? columns[1].trim() : "AVAILABLE");
                        // Gán biển số xe nếu có cột 3
                        card.setAssignedPlate(columns.length > 2 ? columns[2].trim() : null);
                        rfidCardRepository.save(card); // Lưu thẻ mới vào DB
                        count++; // Tăng biến đếm
                    }
                }
            }
            log.info("Successfully imported {} RFID cards", count);
        } catch (Exception e) {

            throw new RuntimeException("Error processing CSV file", e);
        }
        return count; // Trả về số lượng đã import
    }
}