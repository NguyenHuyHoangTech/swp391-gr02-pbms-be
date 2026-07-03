package com.pbms.modules.operation.service;

import com.pbms.modules.infrastructure.dto.config.VehicleTypeDTO;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VehicleTypeService {

    private final VehicleTypeRepository repository;

    public VehicleTypeService(VehicleTypeRepository repository) {
        this.repository = repository;
    }

    public List<VehicleTypeDTO> getAllVehicleTypes(boolean activeOnly) {
        return repository.findAll().stream()
                .filter(vt -> !activeOnly || "ACTIVE".equals(vt.getStatus() != null ? vt.getStatus() : "ACTIVE"))
                .map(vt -> VehicleTypeDTO.builder()
                        .id(vt.getId())
                        .typeName(vt.getTypeName())
                        .category(vt.getCategory())
                        .matrixWidth(vt.getMatrixWidth())
                        .matrixHeight(vt.getMatrixHeight())
                        .status(vt.getStatus() != null ? vt.getStatus() : "ACTIVE")
                        .iconUrl(vt.getIconUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public VehicleTypeDTO createVehicleType(VehicleTypeDTO dto) {
        VehicleType vt = VehicleType.builder()
                .typeName(dto.getTypeName())
                .category(dto.getCategory())
                .matrixWidth(dto.getMatrixWidth())
                .matrixHeight(dto.getMatrixHeight())
                .status("ACTIVE")
                .iconUrl(dto.getIconUrl())
                .build();
        vt = repository.save(vt);
        dto.setId(vt.getId());
        dto.setStatus("ACTIVE");
        return dto;
    }

    @Transactional
    public VehicleTypeDTO updateVehicleType(Long id, VehicleTypeDTO dto) {
        VehicleType vt = repository.findById(id).orElseThrow(() -> new RuntimeException("VehicleType not found"));
        vt.setTypeName(dto.getTypeName());
        vt.setCategory(dto.getCategory());
        vt.setMatrixWidth(dto.getMatrixWidth());
        vt.setMatrixHeight(dto.getMatrixHeight());
        if (dto.getStatus() != null) {
            vt.setStatus(dto.getStatus());
        }
        if (dto.getIconUrl() != null) {
            vt.setIconUrl(dto.getIconUrl());
        }
        vt = repository.save(vt);
        dto.setId(vt.getId());
        dto.setStatus(vt.getStatus());
        return dto;
    }

    @Transactional
    public void deleteVehicleType(Long id) {
        VehicleType vt = repository.findById(id).orElseThrow(() -> new RuntimeException("VehicleType not found"));
        vt.setStatus("ACTIVE".equals(vt.getStatus()) ? "INACTIVE" : "ACTIVE");
        repository.save(vt);
    }

    @Transactional
    public VehicleTypeDTO updateIcon(Long id, String iconUrl) {
        VehicleType vt = repository.findById(id).orElseThrow(() -> new RuntimeException("VehicleType not found"));
        vt.setIconUrl(iconUrl);
        vt = repository.save(vt);
        
        return VehicleTypeDTO.builder()
                .id(vt.getId())
                .typeName(vt.getTypeName())
                .category(vt.getCategory())
                .matrixWidth(vt.getMatrixWidth())
                .matrixHeight(vt.getMatrixHeight())
                .status(vt.getStatus())
                .iconUrl(vt.getIconUrl())
                .build();
    }
}

