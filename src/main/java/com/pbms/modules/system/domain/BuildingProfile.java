package com.pbms.modules.system.domain;

import com.pbms.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "building_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildingProfile extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 50)
    private String hotline;

    @Column(name = "operating_hours")
    private String operatingHours;

    @Column(columnDefinition = "VARCHAR(MAX)")
    private String rules;
}

