// Author: Võ Trung Hiếu
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
import com.pbms.modules.finance.repository.PaymentOrderRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PricingConfigurationService {

    private final PricingPolicyRepository policyRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    public PricingConfigurationService(PricingPolicyRepository policyRepository, VehicleTypeRepository vehicleTypeRepository, PaymentOrderRepository paymentOrderRepository) {
        this.policyRepository = policyRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.paymentOrderRepository = paymentOrderRepository;
    }

    public List<PricingPolicyDTO> getAllPolicies() {
        return policyRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public PricingPolicyDTO savePolicy(PricingPolicyDTO dto) {
        if (paymentOrderRepository.countByStatus("PROCESSING") > 0) {
            throw new RuntimeException("Không thể lưu cấu hình bảng giá lúc này. Đang có giao dịch đang được hệ thống xử lý. Vui lòng thử lại sau vài giây.");
        }

        VehicleType vt = vehicleTypeRepository.findById(dto.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("VehicleType not found with id " + dto.getVehicleTypeId()));

        PricingPolicy policy;
        if (dto.getId() != null) {
            policy = policyRepository.findById(dto.getId()).orElseThrow(() -> new RuntimeException("Policy not found"));
            captureOldValue(policy);
            policy.getShifts().clear(); // Orphan removal will delete old shifts and blocks
        } else {
            policy = policyRepository.findByVehicleTypeIdAndStatus(dto.getVehicleTypeId(), "ACTIVE").orElse(new PricingPolicy());
            if (policy.getId() != null) {
                captureOldValue(policy);
                policy.getShifts().clear();
            }
        }

        policy.setPolicyName(dto.getPolicyName());
        policy.setVehicleType(vt);
        policy.setGlobalBaseMins(dto.getGlobalBaseMins());
        policy.setGlobalBaseFee(dto.getGlobalBaseFee());

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
                throw new RuntimeException("Sum of block durations (" + calculatedDuration + ") does not match total shift duration (" + shift.getTotalDurationMins() + ")");
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

    private void captureOldValue(PricingPolicy policy) {
        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null && policy.getId() != null) {
                context.setOldValue(objectMapper.writeValueAsString(mapToDTO(policy)));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public PricingPolicy createTransientPolicy(PricingPolicyDTO dto) {
        PricingPolicy policy = new PricingPolicy();
        policy.setGlobalBaseMins(dto.getGlobalBaseMins());
        policy.setGlobalBaseFee(dto.getGlobalBaseFee());


        if (dto.getShifts() != null) {
            for (PricingShiftDTO sDTO : dto.getShifts()) {
                PricingShift shift = new PricingShift();
                shift.setShiftName(sDTO.getShiftName());
                shift.setStartTime(LocalTime.parse(sDTO.getStartTime()));
                shift.setEndTime(LocalTime.parse(sDTO.getEndTime()));
                shift.setTotalDurationMins(sDTO.getTotalDurationMins());

                if (sDTO.getBlocks() != null) {
                    for (PricingBlockDTO bDTO : sDTO.getBlocks()) {
                        PricingBlock block = new PricingBlock();
                        block.setBlockOrder(bDTO.getBlockOrder());
                        block.setDurationMins(bDTO.getDurationMins());
                        block.setFee(bDTO.getFee());
                        shift.getBlocks().add(block);
                    }
                }
                policy.getShifts().add(shift);
            }
        }
        return policy;
    }
}

