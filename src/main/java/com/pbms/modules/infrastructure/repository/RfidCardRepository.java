/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: JPA Repository cho entity RfidCard.
 * @Dependencies:
 * - RfidCard (Local)
 */
package com.pbms.modules.infrastructure.repository;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN SPRING DATA JPA / LOCK
// =========================================================================
import com.pbms.modules.infrastructure.domain.RfidCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU THẺ RFID (RFID CARD REPOSITORY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface chịu trách nhiệm thao tác đọc/ghi cơ sở dữ liệu đối với bảng `rfid_cards`.
 * Cung cấp các hàm tìm kiếm theo mã thẻ in (`cardId`) và mã chip vô tuyến (`cardCode`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Cơ chế Khóa bi quan (Pessimistic Write Lock) tại `@Lock(LockModeType.PESSIMISTIC_WRITE)`
 *   trên `findByCardCode` bảo đảm không xảy ra tranh chấp dữ liệu khi hai đầu đọc tại cổng
 *   cùng xử lý một thẻ RFID đồng thời.
 * =========================================================================================
 */
@Repository
public interface RfidCardRepository extends JpaRepository<RfidCard, Long> {

    // Tìm kiếm thẻ theo mã in hiển thị trên mặt thẻ (ví dụ: "CARD-001")
    Optional<RfidCard> findByCardId(String cardId);

    // Tìm kiếm thẻ theo chuỗi UID chip vô tuyến đọc từ đầu đọc RFID, có khóa Pessimistic
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RfidCard> findByCardCode(String cardCode);
    
    // Lọc danh sách thẻ theo trạng thái (AVAILABLE, IN_USE, LOST...)
    List<RfidCard> findByStatus(String status);
}

