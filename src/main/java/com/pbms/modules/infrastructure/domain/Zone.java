// TODO(Member2): Full implementation pending - SPATIAL & ROUTING MASTER
package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "zones")
@Getter
@Setter
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_name")
    private String zoneName;

    @Column(name = "function_type")
    private String functionType;
}
