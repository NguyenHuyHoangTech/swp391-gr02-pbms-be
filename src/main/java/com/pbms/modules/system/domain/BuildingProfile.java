// Author: Võ Trung Hiếu
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

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "is_247", columnDefinition = "BIT DEFAULT 0")
    private Boolean is247;

    @Column(name = "operating_start", length = 5)
    private String operatingStart;

    @Column(name = "operating_end", length = 5)
    private String operatingEnd;

    @Column(columnDefinition = "VARCHAR(MAX)")
    private String rules;
}

