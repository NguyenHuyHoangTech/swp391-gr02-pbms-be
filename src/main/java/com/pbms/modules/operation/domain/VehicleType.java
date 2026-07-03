/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Entity class representing the vehicle_types table in the database.
 * @Dependencies: jakarta.persistence.*, lombok.*
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

    /**
     * Unique identifier for the vehicle type.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the vehicle type.
     */
    @Column(name = "type_name", nullable = false, unique = true, length = 100)
    private String typeName;

    /**
     * Matrix width associated with the vehicle type.
     */
    @Column(name = "matrix_width", nullable = false)
    private Integer matrixWidth;

    /**
     * Matrix height associated with the vehicle type.
     */
    @Column(name = "matrix_height", nullable = false)
    private Integer matrixHeight;

    /**
     * Category of the vehicle type such as FOUR_WHEEL, TWO_WHEEL.
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * Status of the vehicle type.
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * URL of the icon representing the vehicle type.
     */
    @Column(name = "icon_url", length = 255)
    private String iconUrl;
}
