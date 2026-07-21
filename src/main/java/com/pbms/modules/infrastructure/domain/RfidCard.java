/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Entity đại diện cho 1 thẻ RFID vật lý trong kho (bảng
 * rfid_cards) - dùng để gắn với xe khi check-in, thu hồi khi check-out.
 * @Dependencies: Không có
 */
package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rfid_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", unique = true)
    private String cardId;

    @Column(name = "card_code", unique = true, nullable = false)
    private String cardCode;

    // AVAILABLE, IN_USE, LOST, DAMAGED
    @Column(name = "status")
    private String status;

    @Column(name = "assigned_plate", length = 50)
    private String assignedPlate;
}
