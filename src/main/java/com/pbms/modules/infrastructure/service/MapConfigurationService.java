/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-06
 * @Description: Service for managing map configurations, including floors, zones, slots, and gates.
 * @Dependencies: FloorRepository, ZoneRepository, GateRepository, SlotRepository, VehicleTypeRepository, BuildingProfileRepository, ReservationRepository, SystemConfigService
 */
package com.pbms.modules.infrastructure.service;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY TRONG HỆ THỐNG HẠ TẦNG VÀ VẬN HÀNH
// =========================================================================
import com.pbms.modules.infrastructure.domain.Floor; // Tầng đậu xe.
import com.pbms.modules.infrastructure.domain.Gate; // Cổng ra/vào.
import com.pbms.modules.infrastructure.domain.Slot; // Ô đậu xe cá nhân.
import com.pbms.modules.infrastructure.domain.Zone; // Khu vực đậu xe theo chức năng.
import com.pbms.modules.operation.domain.VehicleType; // Loại phương tiện (4 bánh, 2 bánh).

// =========================================================================
// PHẦN 2: CÁC DATA TRANSFER OBJECT (DTO) CẤU HÌNH SƠ ĐỒ
// =========================================================================
import com.pbms.modules.infrastructure.dto.config.*; // Gói chứa MapConfigDTO, FloorConfigDTO, ZoneConfigDTO...

// =========================================================================
// PHẦN 3: CÁC REPOSITORY TRUY VẤN VÀ CẤU HÌNH HỆ THỐNG
// =========================================================================
import com.pbms.modules.infrastructure.repository.FloorRepository;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.StaffWorkSessionRepository; // Tra ca trực đang ACTIVE để biết Cổng nào đang có nhân viên đứng quầy.
import com.pbms.modules.system.domain.BuildingProfile;
import com.pbms.modules.system.repository.BuildingProfileRepository;

// =========================================================================
// PHẦN 4: THƯ VIỆN LOMBOK VÀ SPRING BOOT SERVICE
// =========================================================================
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * =========================================================================================
 * DỊCH VỤ XỬ LÝ NGHIỆP VỤ CẤU HÌNH SƠ ĐỒ BÃI XE (MAP CONFIGURATION SERVICE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Service này chịu trách nhiệm tổng hợp toàn bộ dữ liệu sơ đồ bãi đỗ xe (tầng, khu vực,
 * ô đỗ, cổng) để chuyển cho Frontend vẽ giao diện bản đồ, đồng thời xử lý lưu trữ/cập
 * nhật/xóa an toàn cấu hình khi quản lý thực hiện thay đổi trên giao diện thiết kế.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Liên kết nhiều Repository (`FloorRepository`, `ZoneRepository`,
 *   `SlotRepository`, `GateRepository`) để bảo đảm tính toàn vẹn khóa ngoại 3NF.
 * - Minh chứng 2: Trước khi xóa mềm 1 Zone, kiểm tra toàn bộ ô đỗ thuộc Zone đó
 *   (`slotRepository.findByZoneId`) — còn ô nào "OCCUPIED" thì từ chối xóa, nhằm
 *   bảo vệ xe đang đỗ tại khu vực sắp bị gỡ khỏi sơ đồ.
 * =========================================================================================
 */
@Service
@RequiredArgsConstructor
public class MapConfigurationService {

    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final GateRepository gateRepository;
    private final SlotRepository slotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final BuildingProfileRepository buildingProfileRepository;
    private final ReservationRepository reservationRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final StaffWorkSessionRepository staffWorkSessionRepository;
    
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * =========================================================================
     * API/HÀM: LẤY CẤU HÌNH SƠ ĐỒ BÃI XE HIỆN TẠI (GET MAP CONFIGURATION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Tổng hợp thông tin từ tất cả bảng liên quan (Floors, Zones, Gates, Slots)
     * thành một gói DTO duy nhất để giao diện hiển thị sơ đồ.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lấy toàn bộ danh sách tầng (Floor).
     * 2. Lấy danh sách khu vực (Zone) có trạng thái khác "DELETED", chuyển sang DTO
     *    kèm số ô trống và số lượng đặt chỗ trước (pending reservations).
     * 3. Lấy danh sách cổng (Gate) khác "DELETED".
     * 4. Gộp toàn bộ thành một đối tượng `MapConfigDTO` hoàn chỉnh trả về cho Client.
     */
    @Transactional(readOnly = true)
    public MapConfigDTO getMapConfiguration() {
        List<Floor> floors = floorRepository.findAll();
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> !"DELETED".equals(z.getStatus()))
                .collect(Collectors.toList());
        List<Gate> gates = gateRepository.findAll().stream()
                .filter(g -> !"DELETED".equals(g.getStatus()))
                .collect(Collectors.toList());

        Map<Long, VehicleType> vehicleTypes = vehicleTypeRepository.findAll().stream()
                .collect(Collectors.toMap(vt -> vt.getId(), Function.identity()));

        List<FloorConfigDTO> floorDTOs = floors.stream().map(f -> FloorConfigDTO.builder()
                .id(f.getId())
                .name(f.getFloorName())
                .type(f.getFloorType())
                .mapCols(f.getMapCols())
                .mapRows(f.getMapRows())
                .build()).collect(Collectors.toList());



        List<ZoneConfigDTO> zoneDTOs = zones.stream().map(z -> {
            List<SlotConfigDTO> slotDTOs = slotRepository.findByZoneId(z.getId()).stream()
                    .map(s -> SlotConfigDTO.builder()
                            .id(s.getId())
                            .name(s.getSlotName())
                            .status(s.getStatus())
                            .build())
                    .collect(Collectors.toList());

            VehicleType vt = z.getVehicleType() != null ? vehicleTypes.get(z.getVehicleType().getId()) : null;

            // Tính toán số lượng suất đặt chỗ trước (Reservation) đang có hiệu lực trong khu vực.
            // LƯU Ý: Chỉ các đơn PENDING nằm trong khung thời gian sớm cho phép (RESERVATION_EARLY_MINS,
            // mặc định 30 phút trước giờ dự kiến đến) mới được tính vào activeReservationsCount
            // để bảo lưu chỗ đậu cho khách, tránh tính sớm quá gây lãng phí ô đỗ trống.
            java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
            List<com.pbms.modules.operation.domain.Reservation> pendingList = reservationRepository
                    .findByZoneIdAndStatus(z.getId(), "PENDING");
            int windowMinutes = 30;
            try {
                windowMinutes = Integer
                        .parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue());
            } catch (Exception e) {
                // Sử dụng mặc định 30 phút nếu chưa cấu hình trong CSDL
            }
            final int finalWindowMinutes = windowMinutes;
            long activeReservations = pendingList.stream().filter(r -> {
                java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(finalWindowMinutes);
                java.time.LocalDateTime endWindow = r.getExpectedEntryTime()
                        .plusMinutes(r.getExpectedDurationMinutes());
                return !now.isBefore(startWindow) && !now.isAfter(endWindow);
            }).count();

            return ZoneConfigDTO.builder()
                    .id(z.getId())
                    .floorId(z.getFloor() != null ? z.getFloor().getId() : null)
                    .name(z.getZoneName())
                    .capacity(slotDTOs.size())
                    .vehicleTypeId(vt != null ? vt.getId() : null)
                    .vehicleTypeName(vt != null ? vt.getTypeName() : null)
                    .vehicleCategory(vt != null ? vt.getCategory() : null)
                    .functionType(z.getFunctionType())
                    .layoutX(z.getLayoutX())
                    .layoutY(z.getLayoutY())
                    .rotation(z.getRotation())
                    .activeReservationsCount(activeReservations)
                    .slots(slotDTOs)
                    .build();
        }).collect(Collectors.toList());

        // TRẠNG THÁI CỔNG ĐƯỢC SUY RA ĐỘNG TỪ CA TRỰC, KHÔNG ĐỌC CỘT `status` THÔ:
        // Cổng nào đang có 1 StaffWorkSession "ACTIVE" thì chắc chắn đang có người
        // trực -> gán "OCCUPIED" kèm tên/email nhân viên cho panel thông tin bên
        // phải màn hình sơ đồ hiển thị. Cách này khớp đúng với `GateController.toDTO()`
        // (2 endpoint cùng trả Cổng nên phải cùng 1 quy tắc), đồng thời tự miễn nhiễm
        // với dữ liệu `status` cũ/bị lệch trong DB — kể cả ca trực mở từ trước khi
        // `WorkSessionService` biết ghi cột này, hoặc server tắt đột ngột giữa ca.
        List<GateConfigDTO> gateDTOs = gates.stream().map(g -> {
            var activeSessionOpt = staffWorkSessionRepository.findByGateIdAndStatus(g.getId(), "ACTIVE");
            String staffName = null;
            String staffEmail = null;
            String mappedStatus = "IDLE";
            if (activeSessionOpt.isPresent()) {
                mappedStatus = "OCCUPIED";
                staffName = activeSessionOpt.get().getStaff().getFullName();
                staffEmail = activeSessionOpt.get().getStaff().getEmail();
            } else if ("MAINTENANCE".equals(g.getStatus())) {
                mappedStatus = "MAINTENANCE";
            }

            return GateConfigDTO.builder()
                .id(g.getId())
                .floorId(g.getFloor() != null ? g.getFloor().getId() : null)
                .name(g.getGateName())
                .staffName(staffName)
                .staffEmail(staffEmail)
                .status(mappedStatus)
                .layoutX(g.getLayoutX())
                .layoutY(g.getLayoutY())
                .rotation(g.getRotation())
                .build();
        }).collect(Collectors.toList());

        List<VehicleTypeDTO> vtDTOs = vehicleTypeRepository.findAll().stream()
                .filter(vt -> "ACTIVE".equals(vt.getStatus() != null ? vt.getStatus() : "ACTIVE"))
                .map(vt -> VehicleTypeDTO.builder()
                        .id(vt.getId())
                        .typeName(vt.getTypeName())
                        .category(vt.getCategory())
                        .matrixWidth(vt.getMatrixWidth())
                        .matrixHeight(vt.getMatrixHeight())
                        .iconUrl(vt.getIconUrl())
                        .build())
                .collect(Collectors.toList());

        return MapConfigDTO.builder()
                .floors(floorDTOs)
                .zones(zoneDTOs)
                .gates(gateDTOs)
                .vehicleTypes(vtDTOs)
                .build();
    }

    /**
     * =========================================================================
     * API/HÀM: LƯU VÀ ĐỒNG BỘ CẤU HÌNH SƠ ĐỒ BÃI XE (SAVE MAP CONFIGURATION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Đồng bộ hóa danh sách Tầng, Khu vực, Ô đỗ và Cổng từ Frontend xuống Database,
     * thực hiện thêm mới, cập nhật hoặc soft-delete các đối tượng không còn trong sơ đồ.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lấy thông tin tòa nhà (BuildingProfile) mặc định.
     * 2. Xử lý Tầng (Floor): Cập nhật tầng cũ, thêm tầng mới, đánh dấu xóa (DELETED) tầng bị loại.
     * 3. Xử lý Khu vực (Zone): Kiểm tra xem Zone bị xóa có ô đỗ nào đang "OCCUPIED"
     *    không (`slotRepository.findByZoneId`). Nếu có -> Từ chối xóa.
     * 4. Xử lý Ô đỗ (Slot): Đồng bộ vị trí, số lượng và kích thước theo ma trận xe.
     * 5. Xử lý Cổng (Gate): Cập nhật tọa độ và trạng thái cổng ra/vào.
     */
    @Transactional
    public void saveMapConfiguration(MapConfigDTO mapConfig) {
        BuildingProfile defaultBuilding = buildingProfileRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No building profile configured"));

        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null) {
                MapConfigDTO oldConfig = getMapConfiguration();
                if (oldConfig.getZones() != null) {
                    oldConfig.getZones().forEach(z -> z.setSlots(new java.util.ArrayList<>()));
                }
                context.setOldValue(objectMapper.writeValueAsString(oldConfig));
            }
        } catch (Exception e) {
            // ignore
        }

        // 1. Process Floors
        List<Floor> currentFloors = floorRepository.findAll();
        Map<Long, Floor> floorMap = currentFloors.stream()
                .collect(Collectors.toMap(f -> f.getId(), Function.identity()));

        for (FloorConfigDTO fDTO : mapConfig.getFloors()) {
            Floor floor;
            if (fDTO.getId() > 1000000000L || !floorMap.containsKey(fDTO.getId())) {
                // New Floor
                floor = Floor.builder()
                        .building(defaultBuilding)
                        .floorName(fDTO.getName())
                        .floorType(fDTO.getType())
                        .mapCols(fDTO.getMapCols())
                        .mapRows(fDTO.getMapRows())
                        .build();
                floor = floorRepository.save(floor);
                // Map the old temp ID to new ID for children
                Long oldId = fDTO.getId();
                fDTO.setId(floor.getId());
                final Long newFloorId = floor.getId();
                mapConfig.getZones().stream().filter(z -> z.getFloorId().equals(oldId))
                        .forEach(z -> z.setFloorId(newFloorId));
                mapConfig.getGates().stream().filter(g -> g.getFloorId().equals(oldId))
                        .forEach(g -> g.setFloorId(newFloorId));
            } else {
                floor = floorMap.get(fDTO.getId());

                // Rule: Cannot change floorType if it has zones with status != DELETED
                final Long currentFloorId = floor.getId();
                if (!floor.getFloorType().equals(fDTO.getType())) {
                    boolean hasZones = zoneRepository.findAll().stream()
                            .anyMatch(z -> z.getFloor().getId().equals(currentFloorId)
                                    && !"DELETED".equals(z.getStatus()));
                    if (hasZones) {
                        throw new RuntimeException(
                                "Cannot change floor type because it has active zones: " + floor.getFloorName());
                    }
                }

                floor.setFloorName(fDTO.getName());
                floor.setFloorType(fDTO.getType());
                floor.setMapCols(fDTO.getMapCols());
                floor.setMapRows(fDTO.getMapRows());
                floorRepository.save(floor);
            }
        }

        // =========================================================================
        // BƯỚC 2: XỬ LÝ ĐỒNG BỘ KHU VỰC (ZONES) VÀ CÁC Ô ĐỖ (SLOTS)
        // =========================================================================
        List<Zone> currentZones = zoneRepository.findAll();
        Map<Long, Zone> zoneMap = currentZones.stream().collect(Collectors.toMap(z -> z.getId(), Function.identity()));

        // Tìm các khu vực hiện có trong CSDL nhưng bị gỡ bỏ khỏi sơ đồ gửi lên từ Frontend.
        // LƯU Ý KIẾN TRÚC: Trước khi soft-delete (gán status = DELETED), bắt buộc kiểm tra
        // toàn bộ ô đỗ thuộc khu vực này. Nếu có bất kỳ ô nào đang "OCCUPIED" (có xe gửi),
        // hệ thống phải từ chối xóa ngay lập tức để bảo vệ tài sản và ca xe đang trực.
        List<Long> incomingZoneIds = mapConfig.getZones().stream()
                .filter(z -> z.getId() < 1000000000L)
                .map(z -> z.getId()).collect(Collectors.toList());
        for (Zone cz : currentZones) {
            if (!incomingZoneIds.contains(cz.getId()) && !"DELETED".equals(cz.getStatus())) {
                List<Slot> cSlots = slotRepository.findByZoneId(cz.getId());
                if (cSlots.stream().anyMatch(s -> "OCCUPIED".equals(s.getStatus()))) {
                    throw new RuntimeException("Cannot delete zone because it has occupied slots: " + cz.getZoneName());
                }
                cz.setStatus("DELETED");
                zoneRepository.save(cz);
                // Xóa triệt để các ô đỗ rỗng thuộc khu vực đã bị gỡ bỏ khỏi sơ đồ
                for (Slot s : cSlots) {
                    slotRepository.delete(s);
                }
            }
        }

        for (ZoneConfigDTO zDTO : mapConfig.getZones()) {
            if (zDTO.getCapacity() == 0) {
                // Nếu Quản lý chỉnh sức chứa khu vực về 0 trên UI -> Xử lý tương đương
                // việc xóa khu vực (soft-delete Zone sau khi kiểm tra an toàn ô đỗ OCCUPIED).
                if (zDTO.getId() < 1000000000L && zoneMap.containsKey(zDTO.getId())) {
                    Zone cz = zoneMap.get(zDTO.getId());
                    List<Slot> cSlots = slotRepository.findByZoneId(cz.getId());
                    if (cSlots.stream().anyMatch(s -> "OCCUPIED".equals(s.getStatus()))) {
                        throw new RuntimeException(
                                "Cannot delete zone because it has occupied slots: " + cz.getZoneName());
                    }
                    cz.setStatus("DELETED");
                    zoneRepository.save(cz);
                    for (Slot s : cSlots) {
                        slotRepository.delete(s);
                    }
                }
                continue;
            }

            Floor f = floorRepository.findById(zDTO.getFloorId()).orElseThrow();
            VehicleType vt = vehicleTypeRepository.findById(zDTO.getVehicleTypeId()).orElseThrow();

            // RÀNG BUỘC KIẾN TRÚC: Phân loại phương tiện của Zone (4 bánh / 2 bánh) bắt buộc
            // phải khớp với loại phương tiện cho phép của Tầng (Floor Type).
            // Tránh vi phạm quy định gửi xe ô tô vào tầng chỉ chịu tải xe máy.
            if (!f.getFloorType().equals(vt.getCategory())) {
                throw new RuntimeException("Zone vehicle type does not match floor type for zone: " + zDTO.getName());
            }

            Zone zone;
            if (zDTO.getId() > 1000000000L || !zoneMap.containsKey(zDTO.getId())) {
                // Khởi tạo Khu vực (Zone) mới được vẽ thêm trên sơ đồ
                zone = Zone.builder()
                        .floor(f)
                        .zoneName(zDTO.getName())
                        .vehicleType(vt)
                        .functionType(zDTO.getFunctionType())
                        .layoutX(zDTO.getLayoutX())
                        .layoutY(zDTO.getLayoutY())
                        .rotation(zDTO.getRotation())
                        .status("ACTIVE")
                        .build();
                zone = zoneRepository.save(zone);

                // Cập nhật ID thực từ DB phản hồi ngược cho DTO để tra cứu sau này
                zDTO.setId(zone.getId());
            } else {
                zone = zoneMap.get(zDTO.getId());
                zone.setZoneName(zDTO.getName());
                zone.setVehicleType(vt);
                zone.setFunctionType(zDTO.getFunctionType());
                zone.setLayoutX(zDTO.getLayoutX());
                zone.setLayoutY(zDTO.getLayoutY());
                zone.setRotation(zDTO.getRotation());
                zone.setStatus("ACTIVE");
                zone = zoneRepository.save(zone);
            }

            List<Slot> existingSlots = slotRepository.findByZoneId(zone.getId());
            Map<Long, Slot> existingSlotMap = existingSlots.stream()
                    .collect(Collectors.toMap(s -> s.getId(), Function.identity()));
            List<Long> incomingSlotIds = zDTO.getSlots().stream().map(s -> s.getId()).collect(Collectors.toList());

            // Xóa các ô đỗ không còn tồn tại trên sơ đồ mới.
            // QUY TẮC AN TOÀN: Từ chối xóa nếu ô đỗ đang ở trạng thái OCCUPIED (đang có xe gửi).
            for (Slot es : existingSlots) {
                if (!incomingSlotIds.contains(es.getId())) {
                    if ("OCCUPIED".equals(es.getStatus())) {
                        throw new RuntimeException("Cannot delete occupied slot: " + es.getSlotName());
                    }
                    slotRepository.delete(es);
                }
            }

            // Thêm mới hoặc cập nhật thông tin từng ô đỗ trong khu vực
            for (SlotConfigDTO sDTO : zDTO.getSlots()) {
                if (sDTO.getId() != null && existingSlotMap.containsKey(sDTO.getId())) {
                    Slot es = existingSlotMap.get(sDTO.getId());
                    es.setSlotName(sDTO.getName());
                    // BẢO VỆ NGHIỆP VỤ: Không cho phép chuyển ô đỗ sang DISABLED (bảo trì)
                    // khi ô đó đang có xe đỗ hợp lệ bên trong.
                    if ("DISABLED".equals(sDTO.getStatus()) && "OCCUPIED".equals(es.getStatus())) {
                        throw new RuntimeException("Cannot disable an occupied slot: " + es.getSlotName());
                    }
                    if (!"OCCUPIED".equals(es.getStatus())) {
                        es.setStatus(sDTO.getStatus());
                    }
                    slotRepository.save(es);
                } else {
                    Slot newSlot = Slot.builder()
                            .zone(zone)
                            .slotName(sDTO.getName())
                            .status(sDTO.getStatus())
                            .build();
                    slotRepository.save(newSlot);
                }
            }
        }

        // 3. Process Gates
        List<Gate> currentGates = gateRepository.findAll();
        Map<Long, Gate> gateMap = currentGates.stream().collect(Collectors.toMap(g -> g.getId(), Function.identity()));

        List<Long> incomingGateIds = mapConfig.getGates().stream()
                .filter(g -> g.getId() != null && g.getId() < 1000000000L)
                .map(g -> g.getId()).collect(Collectors.toList());

        // CHỐT CHẶN AN TOÀN: không cho xóa Cổng đang có nhân viên trực.
        // Kiểm tra bằng ca trực ACTIVE thật trong bảng staff_work_sessions thay vì
        // đọc cột `status` — cột đó có thể lệch (ca mở từ trước khi hệ thống biết
        // ghi trạng thái, hoặc server tắt đột ngột giữa ca) khiến chốt chặn bị vô
        // hiệu và Cổng đang có người làm việc vẫn bị xóa mất.
        for (Gate cg : currentGates) {
            if (!incomingGateIds.contains(cg.getId()) && !"DELETED".equals(cg.getStatus())) {
                if (staffWorkSessionRepository.findByGateIdAndStatus(cg.getId(), "ACTIVE").isPresent()) {
                    throw new IllegalStateException(
                        "Cannot delete gate \"" + cg.getGateName() + "\" because a staff member is currently on duty at this gate.");
                }
                cg.setStatus("DELETED");
                gateRepository.save(cg);
            }
        }

        for (GateConfigDTO gDTO : mapConfig.getGates()) {
            if (gDTO.getStatus() != null && gDTO.getStatus().equals("DELETED")) {
                // Explicit soft-delete request from frontend
                if (gDTO.getId() != null && gateMap.containsKey(gDTO.getId())) {
                    Gate gToDelete = gateMap.get(gDTO.getId());
                    if (staffWorkSessionRepository.findByGateIdAndStatus(gToDelete.getId(), "ACTIVE").isPresent()) {
                        throw new IllegalStateException(
                            "Cannot delete gate \"" + gToDelete.getGateName() + "\" because a staff member is currently on duty at this gate.");
                    }
                    if (!"DELETED".equals(gToDelete.getStatus())) {
                        gToDelete.setStatus("DELETED");
                        gateRepository.save(gToDelete);
                    }
                }
                continue;
            }

            Floor f = null;
            if (gDTO.getFloorId() != null) {
                f = floorRepository.findById(gDTO.getFloorId()).orElse(null);
            }
            
            // LƯU Ý QUAN TRỌNG — MÀN HÌNH SƠ ĐỒ KHÔNG ĐƯỢC PHÉP GHI `status` CỦA CỔNG:
            // `getMapConfiguration()` nay trả về trạng thái SUY RA từ ca trực
            // (OCCUPIED/IDLE/MAINTENANCE), nên nếu tin và ghi thẳng giá trị Frontend
            // gửi ngược lên thì một ca trực vừa kết thúc giữa lúc quản lý đang mở
            // sơ đồ sẽ bị ghi đè "OCCUPIED" vĩnh viễn vào DB — Cổng kẹt trạng thái
            // và không bao giờ xóa được nữa. Quyền đổi trạng thái Cổng chỉ thuộc về
            // `WorkSessionService` (mở/đóng ca) và luồng soft-delete ở trên; màn hình
            // sơ đồ chỉ được sửa tên/vị trí/góc quay. Cổng tạo mới luôn bắt đầu "IDLE".
            Gate gate;
            if (gDTO.getId() == null || gDTO.getId() > 1000000000L || !gateMap.containsKey(gDTO.getId())) {
                gate = Gate.builder()
                        .floor(f)
                        .gateName(gDTO.getName())
                        .status("IDLE")
                        .layoutX(gDTO.getLayoutX())
                        .layoutY(gDTO.getLayoutY())
                        .rotation(gDTO.getRotation())
                        .build();
                gate = gateRepository.save(gate);
                gDTO.setId(gate.getId());
            } else {
                gate = gateMap.get(gDTO.getId());
                gate.setGateName(gDTO.getName());
                gate.setFloor(f);
                gate.setLayoutX(gDTO.getLayoutX());
                gate.setLayoutY(gDTO.getLayoutY());
                gate.setRotation(gDTO.getRotation());
                gateRepository.save(gate);
            }
        }

        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null) {
                if (mapConfig.getZones() != null) {
                    mapConfig.getZones().forEach(z -> z.setSlots(new java.util.ArrayList<>()));
                }
                context.setNewValue(objectMapper.writeValueAsString(mapConfig));
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
