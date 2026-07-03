package com.pbms.modules.operation.service;

import com.pbms.modules.operation.domain.Vehicle;
import com.pbms.modules.operation.dto.VehicleDTO;
import com.pbms.modules.operation.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public List<VehicleDTO> getAllVehicles() {
        return vehicleRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VehicleDTO getVehicleByPlate(String plate) {
        return vehicleRepository.findByPlateNumber(plate.trim().toUpperCase())
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
    }

    @Transactional
    public VehicleDTO setBlacklist(Long id, String reason) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        vehicle.setIsBlacklisted(true);
        vehicle.setBlacklistReason(reason);
        vehicleRepository.save(vehicle);
        return mapToDTO(vehicle);
    }

    @Transactional
    public VehicleDTO setBlacklistByPlate(String plate, String reason, String evidenceUrl) {
        Vehicle vehicle = vehicleRepository.findByPlateNumber(plate.trim().toUpperCase())
                .orElseGet(() -> {
                    Vehicle newVehicle = new Vehicle();
                    newVehicle.setPlateNumber(plate.trim().toUpperCase());
                    newVehicle.setStatus("ACTIVE");
                    return newVehicle;
                });
        vehicle.setIsBlacklisted(true);
        vehicle.setBlacklistReason(reason);
        vehicle.setBlacklistEvidenceUrl(evidenceUrl);
        vehicleRepository.save(vehicle);
        return mapToDTO(vehicle);
    }

    @Transactional
    public VehicleDTO removeBlacklist(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        vehicle.setIsBlacklisted(false);
        vehicle.setBlacklistReason(null);
        vehicle.setBlacklistEvidenceUrl(null);
        vehicleRepository.save(vehicle);
        return mapToDTO(vehicle);
    }

    private VehicleDTO mapToDTO(Vehicle vehicle) {
        return VehicleDTO.builder()
                .id(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .color(vehicle.getColor())
                .brand(vehicle.getBrand())
                .vehicleTypeName(vehicle.getVehicleType() != null ? vehicle.getVehicleType().getTypeName() : null)
                .vehicleTypeId(vehicle.getVehicleType() != null ? vehicle.getVehicleType().getId() : null)
                .ownerName(vehicle.getUser() != null ? vehicle.getUser().getFullName() : null)
                .ownerId(vehicle.getUser() != null ? vehicle.getUser().getId() : null)
                .status(vehicle.getStatus())
                .isBlacklisted(vehicle.getIsBlacklisted())
                .blacklistReason(vehicle.getBlacklistReason())
                .blacklistEvidenceUrl(vehicle.getBlacklistEvidenceUrl())
                .build();
    }
}

