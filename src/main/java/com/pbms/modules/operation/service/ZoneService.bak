package com.pbms.modules.operation.service;

import com.pbms.modules.operation.dto.ZoneLayoutDTO;
import com.pbms.modules.operation.entity.Zone;
import com.pbms.modules.operation.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public List<Zone> getAllZones() {
        return zoneRepository.findAll();
    }

    @Transactional
    public Zone updateZoneLayout(Long id, ZoneLayoutDTO dto) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone not found"));
        
        zone.setLayoutX(dto.getLayoutX());
        zone.setLayoutY(dto.getLayoutY());
        zone.setRotation(dto.getRotation());

        return zoneRepository.save(zone);
    }
}
