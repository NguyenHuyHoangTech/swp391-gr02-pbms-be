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

    @Column(name = "category", length = 50)
    private String category; // FOUR_WHEEL, TWO_WHEEL

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "icon_url", length = 255)
    private String iconUrl;
}

