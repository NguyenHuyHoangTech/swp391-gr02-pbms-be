/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-22
 * @Description: CRUD "loại xe" (VehicleType) - đơn vị định nghĩa kích thước ô
 * lưới (matrixWidth x matrixHeight) mà mỗi chỗ đỗ chiếm trên bản đồ
 * SpaceMap, đồng thời gắn với chính sách giá mặc định. Có các quy tắc an
 * toàn: không cho đổi kích thước/khoá loại xe khi đang có xe đỗ hoặc đã có
 * slot trên bản đồ.
 * @Dependencies:
 * - VehicleTypeRepository (Local)
 * - PricingConfigurationService (Local)
 * - ParkingSessionRepository, SlotRepository (Local)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.infrastructure.dto.config.VehicleTypeDTO;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import com.pbms.modules.finance.service.PricingConfigurationService;
import com.pbms.modules.finance.dto.PricingPolicyDTO;
import com.pbms.modules.finance.dto.PricingShiftDTO;
import com.pbms.modules.finance.dto.PricingBlockDTO;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VehicleTypeService {

    private final VehicleTypeRepository repository;
    private final PricingConfigurationService pricingService;
    private final ParkingSessionRepository sessionRepository;
    private final SlotRepository slotRepository;

    public VehicleTypeService(VehicleTypeRepository repository, PricingConfigurationService pricingService, ParkingSessionRepository sessionRepository, SlotRepository slotRepository) {
        this.repository = repository;
        this.pricingService = pricingService;
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
    }

    /**
     * @Function: getAllVehicleTypes
     * @Description: Liệt kê loại xe cho FE. activeOnly=true thì bỏ loại đã
     * INACTIVE. hasMapSlots cho biết loại này đã có slot trên bản đồ chưa
     * (FE dùng để khoá ô nhập kích thước).
     */
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
                        .hasMapSlots(slotRepository.countByVehicleTypeId(vt.getId()) > 0)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * @Function: createVehicleType
     * @Description: Tạo loại xe mới, đồng thời tự sinh 1 chính sách giá mặc
     * định (5.000đ, thuê tháng 200.000đ, 1 ca "All Day") để tránh lỗi "tính
     * ra phí 0đ" khi loại xe mới này check-out mà manager chưa kịp cấu hình
     * giá.
     */
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

        PricingPolicyDTO policyDTO = new PricingPolicyDTO();
        policyDTO.setPolicyName("Default " + dto.getTypeName() + " Policy");
        policyDTO.setVehicleTypeId(vt.getId());
        policyDTO.setGlobalBaseMins(0);
        policyDTO.setGlobalBaseFee(new BigDecimal("5000"));
        policyDTO.setMonthlyRate(new BigDecimal("200000"));
        policyDTO.setStatus("ACTIVE");

        PricingShiftDTO shift = new PricingShiftDTO();
        shift.setShiftName("All Day");
        shift.setStartTime("00:00");
        shift.setEndTime("23:59");
        shift.setTotalDurationMins(1439);

        PricingBlockDTO block = new PricingBlockDTO();
        block.setBlockOrder(1);
        block.setDurationMins(1439);
        block.setFee(new BigDecimal("5000"));

        shift.getBlocks().add(block);
        policyDTO.getShifts().add(shift);
        pricingService.savePolicy(policyDTO);

        dto.setId(vt.getId());
        dto.setStatus("ACTIVE");
        return dto;
    }

    /**
     * @Function: updateVehicleType
     * @Description: Cập nhật loại xe với 2 chốt chặn an toàn: (1) đổi hạng
     * mục hoặc khoá (INACTIVE) khi đang có xe đỗ -> chặn; (2) đổi kích thước
     * matrix khi đã có slot trên bản đồ -> chặn.
     */
    @Transactional
    public VehicleTypeDTO updateVehicleType(Long id, VehicleTypeDTO dto) {
        VehicleType vt = repository.findById(id).orElseThrow(() -> new RuntimeException("VehicleType not found"));

        boolean categoryChanged = vt.getCategory() != null && !vt.getCategory().equals(dto.getCategory());
        boolean statusChangedToInactive = dto.getStatus() != null && "INACTIVE".equals(dto.getStatus()) && !"INACTIVE".equals(vt.getStatus());

        if (categoryChanged || statusChangedToInactive) {
            long activeSessions = sessionRepository.countByVehicleTypeIdAndStatus(id, "ACTIVE");
            if (activeSessions > 0) {
                throw new RuntimeException("Cannot lock or change category of this vehicle type while there are vehicles of this type currently parking.");
            }
        }

        boolean matrixChanged = (dto.getMatrixWidth() != null && !dto.getMatrixWidth().equals(vt.getMatrixWidth()))
                             || (dto.getMatrixHeight() != null && !dto.getMatrixHeight().equals(vt.getMatrixHeight()));

        if (matrixChanged) {
            long slotsOnMap = slotRepository.countByVehicleTypeId(id);
            if (slotsOnMap > 0) {
                throw new RuntimeException("Cannot change grid dimensions for this vehicle type because there are currently slots on the map belonging to this type. Please delete them from the map first.");
            }
        }

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

    /**
     * @Function: deleteVehicleType
     * @Description: "Xoá mềm" loại xe - thực chất là đảo trạng thái
     * ACTIVE/INACTIVE.
     */
    @Transactional
    public void deleteVehicleType(Long id) {
        VehicleType vt = repository.findById(id).orElseThrow(() -> new RuntimeException("VehicleType not found"));
        vt.setStatus("ACTIVE".equals(vt.getStatus()) ? "INACTIVE" : "ACTIVE");
        repository.save(vt);
    }

    /**
     * @Function: updateIcon
     * @Description: Cập nhật riêng đường dẫn icon (sau khi upload ảnh) và
     * trả về DTO đầy đủ để FE làm mới ngay avatar loại xe.
     */
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
                .hasMapSlots(slotRepository.countByVehicleTypeId(vt.getId()) > 0)
                .build();
    }
}
