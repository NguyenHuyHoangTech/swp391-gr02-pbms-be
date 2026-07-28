/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ NGHIỆP VỤ (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO DỊCH VỤ VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Ký hiệu @Service báo cho Spring Boot biết class này chứa Logic lõi. 
 *   Spring sẽ khởi tạo nó thành Singleton Bean.
 * - Minh chứng 2: Dùng @RequiredArgsConstructor để tự động tiêm các Repository vào Service.
 * 
 * BƯỚC 2: BẢO ĐẢM TOÀN VẸN GIAO DỊCH (TRANSACTION MANAGEMENT)
 * - Minh chứng: Các hàm thay đổi dữ liệu được gắn @Transactional. Điều này đảm bảo 
 *   khi có lỗi xảy ra, toàn bộ thao tác DB sẽ được Rollback, không gây rác dữ liệu.
 * 
 * BƯỚC 3: THỰC THI LOGIC NGHIỆP VỤ
 * - Minh chứng: Gọi các hàm từ Repository (như indById, save) để tương tác 
 *   trực tiếp với CSDL, xử lý các ngoại lệ (Exception) và trả về DTO cho Controller.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.service;

import com.pbms.modules.infrastructure.domain.RfidCard;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import com.pbms.modules.operation.domain.MonthlyTicket;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.operation.domain.Vehicle;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.repository.VehicleRepository;

import com.pbms.modules.infrastructure.utils.LicensePlateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataMigrationService {

    private final RfidCardRepository rfidCardRepository;
    private final MonthlyTicketRepository monthlyTicketRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final VehicleRepository vehicleRepository;


    private static final String HEX_CHARS = "0123456789ABCDEF";
    private final SecureRandom random = new SecureRandom();

    @Transactional
    /**
     * =========================================================================
     * NGHIỆP VỤ: RUNMIGRATION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho runMigration.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public void runMigration() {
        log.info("Starting Data Migration...");

        // 1. RFID Cards Migration
        List<RfidCard> cards = rfidCardRepository.findAll();
        for (RfidCard card : cards) {
            if (card.getCardId() == null || card.getCardId().isEmpty()) {
                // Move existing cardCode to cardId
                card.setCardId(card.getCardCode());
                
                // Generate a new random HEX code for cardCode (e.g. 8 chars)
                card.setCardCode(generateRandomHex(8));
                
                if (card.getAssignedPlate() != null) {
                    card.setAssignedPlate(LicensePlateUtils.normalize(card.getAssignedPlate()));
                }
            }
        }
        rfidCardRepository.saveAll(cards);
        log.info("Migrated {} RFID cards.", cards.size());

        // 2. License Plate Normalization
        List<Vehicle> vehicles = vehicleRepository.findAll();
        for (Vehicle v : vehicles) {
            if (v.getPlateNumber() != null) {
                v.setPlateNumber(LicensePlateUtils.normalize(v.getPlateNumber()));
            }
        }
        vehicleRepository.saveAll(vehicles);
        log.info("Migrated {} vehicles.", vehicles.size());

        List<ParkingSession> sessions = parkingSessionRepository.findAll();
        for (ParkingSession ps : sessions) {
            if (ps.getPlate() != null) {
                ps.setPlate(LicensePlateUtils.normalize(ps.getPlate()));
            }
            if (ps.getPlateOut() != null) {
                ps.setPlateOut(LicensePlateUtils.normalize(ps.getPlateOut()));
            }
        }
        parkingSessionRepository.saveAll(sessions);
        log.info("Migrated {} parking sessions.", sessions.size());

        List<MonthlyTicket> monthlyTickets = monthlyTicketRepository.findAll();
        for (MonthlyTicket mt : monthlyTickets) {
            if (mt.getPlateNumber() != null) {
                mt.setPlateNumber(LicensePlateUtils.normalize(mt.getPlateNumber()));
            }
        }
        monthlyTicketRepository.saveAll(monthlyTickets);
        log.info("Migrated {} monthly tickets.", monthlyTickets.size());

        log.info("License plates normalized across all entities successfully.");
    }

    private String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HEX_CHARS.charAt(random.nextInt(HEX_CHARS.length())));
        }
        return sb.toString();
    }
}
