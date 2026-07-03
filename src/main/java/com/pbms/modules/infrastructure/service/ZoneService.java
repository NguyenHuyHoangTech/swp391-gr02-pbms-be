package com.pbms.modules.infrastructure.service;

import com.pbms.modules.infrastructure.domain.Slot;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.dto.SlotDTO;
import com.pbms.modules.infrastructure.dto.ZoneDTO;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final SlotRepository slotRepository;

    public List<ZoneDTO> getMapZones() {
        List<Zone> zones = zoneRepository.findAll();
        
        return zones.stream().map(zone -> {
            List<Slot> slots = slotRepository.findByZoneId(zone.getId());
            long totalSlots = slots.size();
            long availableSlots = slots.stream().filter(s -> "EMPTY".equals(s.getStatus()) || "AVAILABLE".equals(s.getStatus())).count();
            
            List<SlotDTO> slotDTOs = slots.stream().map(s -> SlotDTO.builder()
                .id(String.valueOf(s.getId())) // FE expects string
                .name(s.getSlotName())
                .status(s.getStatus())
                .build()
            ).collect(Collectors.toList());

            return ZoneDTO.builder()
                .id(zone.getId())
                .floorId(zone.getFloor().getId())
                .floorName(zone.getFloor().getFloorName())
                .name(zone.getZoneName())
                .capacity((int) totalSlots)
                .availableSlots((int) availableSlots)
                .vehicleTypeId(zone.getVehicleType().getId())
                .vehicleType(zone.getVehicleType().getTypeName())
                .vehicleMatrixWidth(zone.getVehicleType().getMatrixWidth())
                .vehicleMatrixHeight(zone.getVehicleType().getMatrixHeight())
                .functionType(zone.getFunctionType())
                .layoutX(zone.getLayoutX())
                .layoutY(zone.getLayoutY())
                .rotation(zone.getRotation())
                .slots(slotDTOs)
                .build();
        }).collect(Collectors.toList());
    }
}

