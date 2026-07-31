/**
<<<<<<< HEAD
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-15
 * @Description: Entity class representing the vehicle_types table in the database.
 * @Dependencies: jakarta.persistence.*, lombok.*
=======
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: JPA Entity mapping to the [vehicle_types] table.
 *               Defines the categories of vehicles supported by the parking system
 *               (e.g., CAR, MOTORBIKE, EBIKE). Used for routing, pricing,
 *               and slot assignment logic.
 * @Dependencies:
 *  - None (standalone entity, referenced by Vehicle and MonthlyTicket)
>>>>>>> origin/develop
 */
package com.pbms.modules.operation.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicle_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_name", nullable = false, unique = true, length = 100)
    private String typeName;

    @Column(name = "matrix_width", nullable = false)
    private Integer matrixWidth;

    @Column(name = "matrix_height", nullable = false)
    private Integer matrixHeight;

<<<<<<< HEAD
    @Column(name = "category", length = 50)
    private String category; // FOUR_WHEEL, TWO_WHEEL
=======
    /**
     * Vehicle category classification.
     * Expected values: FOUR_WHEEL, TWO_WHEEL
     */
    @Column(name = "category", length = 50)
    private String category;
>>>>>>> origin/develop

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "icon_url", length = 255)
    private String iconUrl;
}
<<<<<<< HEAD

=======
>>>>>>> origin/develop
