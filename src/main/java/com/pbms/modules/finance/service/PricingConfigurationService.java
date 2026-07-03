package com.pbms.modules.finance.service;

import com.pbms.modules.finance.domain.PricingBlock;
import com.pbms.modules.finance.domain.PricingPolicy;
import com.pbms.modules.finance.domain.PricingShift;
import com.pbms.modules.finance.dto.PricingBlockDTO;
import com.pbms.modules.finance.dto.PricingPolicyDTO;
import com.pbms.modules.finance.dto.PricingShiftDTO;
import com.pbms.modules.finance.repository.PricingPolicyRepository;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PricingConfigurationService {

    private final PricingPolicyRepository policyRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public PricingConfigurationService(PricingPolicyRepository policyRepository, VehicleTypeRepository vehicleTypeRepository) {
        this.policyRepository = policyRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
    }

    public List<PricingPolicyDTO> getAllPolicies() {
        return policyRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public PricingPolicyDTO savePolicy(PricingPolicyDTO dto) {
        VehicleType vt = vehicleTypeRepository.findById(dto.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("VehicleType not found with id " + dto.getVehicleTypeId()));

        PricingPolicy policy;
        if (dto.getId() != null) {
            policy = policyRepository.findById(dto.getId()).orElseThrow(() -> new RuntimeException("Policy not found"));
            policy.getShifts().clear(); // Orphan removal will delete old shifts and blocks
        } else {
            policy = policyRepository.findByVehicleTypeIdAndStatus(dto.getVehicleTypeId(), "ACTIVE").orElse(new PricingPolicy());
            if (policy.getId() != null) {
                policy.getShifts().clear();
            }
        }

        policy.setPolicyName(dto.getPolicyName());
        policy.setVehicleType(vt);
        policy.setGlobalBaseMins(dto.getGlobalBaseMins());
        policy.setGlobalBaseFee(dto.getGlobalBaseFee());
        policy.setMaxParkingCap(dto.getMaxParkingCap());
        policy.setMonthlyRate(dto.getMonthlyRate() != null ? dto.getMonthlyRate() : java.math.BigDecimal.ZERO);
        policy.setStatus(dto.getStatus());

        for (PricingShiftDTO sDTO : dto.getShifts()) {
            PricingShift shift = new PricingShift();
            shift.setPolicy(policy);
            shift.setShiftName(sDTO.getShiftName());
            shift.setStartTime(LocalTime.parse(sDTO.getStartTime()));
            shift.setEndTime(LocalTime.parse(sDTO.getEndTime()));
            shift.setTotalDurationMins(sDTO.getTotalDurationMins());

            int calculatedDuration = 0;
            for (PricingBlockDTO bDTO : sDTO.getBlocks()) {
                PricingBlock block = new PricingBlock();
                block.setShift(shift);
                block.setBlockOrder(bDTO.getBlockOrder());
                block.setDurationMins(bDTO.getDurationMins());
                block.setFee(bDTO.getFee());
                shift.getBlocks().add(block);
                calculatedDuration += bDTO.getDurationMins();
            }

            if (calculatedDuration != shift.getTotalDurationMins()) {
                throw new RuntimeException("Block time (" + calculatedDuration + ") is not good and the state of (" + shift.getTotalDurationMins() + ")");
            }

            policy.getShifts().add(shift);
        }

        policy = policyRepository.save(policy);
        return mapToDTO(policy);
    }

    private PricingPolicyDTO mapToDTO(PricingPolicy policy) {
        PricingPolicyDTO dto = PricingPolicyDTO.builder()
                .id(policy.getId())
                .policyName(policy.getPolicyName())
                .vehicleTypeId(policy.getVehicleType() != null ? policy.getVehicleType().getId() : null)
                .globalBaseMins(policy.getGlobalBaseMins())
                .globalBaseFee(policy.getGlobalBaseFee())
                .maxParkingCap(policy.getMaxParkingCap())
                .monthlyRate(policy.getMonthlyRate())
                .status(policy.getStatus())
                .build();

        for (PricingShift shift : policy.getShifts()) {
            PricingShiftDTO sDTO = PricingShiftDTO.builder()
                    .id(shift.getId())
                    .shiftName(shift.getShiftName())
                    .startTime(shift.getStartTime().toString())
                    .endTime(shift.getEndTime().toString())
                    .totalDurationMins(shift.getTotalDurationMins())
                    .build();

            for (PricingBlock block : shift.getBlocks()) {
                PricingBlockDTO bDTO = PricingBlockDTO.builder()
                        .id(block.getId())
                        .blockOrder(block.getBlockOrder())
                        .durationMins(block.getDurationMins())
                        .fee(block.getFee())
                        .build();
                sDTO.getBlocks().add(bDTO);
            }
            dto.getShifts().add(sDTO);
        }
        return dto;
    }
}

