package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.domain.Gate;
import com.pbms.modules.infrastructure.dto.config.GateConfigDTO;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.operation.repository.StaffWorkSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/infrastructure/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateRepository gateRepository;
    private final StaffWorkSessionRepository staffWorkSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<GateConfigDTO>>> getAllGates() {
        List<GateConfigDTO> gates = gateRepository.findAll().stream()
                .filter(g -> !"DELETED".equals(g.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(gates, "Fetched gates successfully"));
    }

    private GateConfigDTO toDTO(Gate gate) {
        String staffName = null;
        String staffEmail = null;
        String mappedStatus = "IDLE";
        
        // Check if gate has an ACTIVE work session
        var activeSessionOpt = staffWorkSessionRepository.findByGateIdAndStatus(gate.getId(), "ACTIVE");
        if (activeSessionOpt.isPresent()) {
            mappedStatus = "OCCUPIED";
            staffName = activeSessionOpt.get().getStaff().getFullName();
            staffEmail = activeSessionOpt.get().getStaff().getEmail();
        } else if ("DELETED".equals(gate.getStatus())) {
            mappedStatus = "MAINTENANCE";
        }

        return GateConfigDTO.builder()
                .id(gate.getId())
                .floorId(gate.getFloor() != null ? gate.getFloor().getId() : null)
                .floor(gate.getFloor() != null ? gate.getFloor().getFloorName() : null)
                .staffName(staffName)
                .staffEmail(staffEmail)
                .name(gate.getGateName())
                .vehicleTypeId(gate.getVehicleType() != null ? gate.getVehicleType().getId() : null)
                .type(gate.getGateType())
                .status(mappedStatus)
                .layoutX(gate.getLayoutX())
                .layoutY(gate.getLayoutY())
                .rotation(gate.getRotation())
                .build();
    }
}
