// TODO(Member2): Full implementation pending - SPATIAL & ROUTING MASTER
package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "slots")
@Getter
@Setter
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_name")
    private String slotName;

    @Column(name = "status")
    private String status;
}
