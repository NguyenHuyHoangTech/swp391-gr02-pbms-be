/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller trả về danh sách cổng còn hoạt động kèm thông tin
 * nhân viên đang trực (nếu có), phục vụ màn hình theo dõi cổng của manager.
 * @Dependencies:
 * - GateRepository (Local)
 * - StaffWorkSessionRepository (Local)
 */
package com.pbms.modules.infrastructure.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ RESPONSE
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho mọi phản hồi HTTP từ Backend trả về Client.
import com.pbms.modules.infrastructure.dto.config.GateConfigDTO; // Gói dữ liệu chuyển tiếp thông tin cấu hình và trạng thái Cổng ra/vào.

// =========================================================================
// PHẦN 2: CÁC REPOSITORY VÀ ENTITY (KẾT NỐI DATABASE VÀ TRA CỨU CA TRỰC)
// =========================================================================
import com.pbms.modules.infrastructure.domain.Gate; // Entity ánh xạ bảng `gates` trong Database.
import com.pbms.modules.infrastructure.repository.GateRepository; // Kho truy vấn dữ liệu bảng Cổng.
import com.pbms.modules.operation.repository.StaffWorkSessionRepository; // Kho truy vấn phiên làm việc (ca trực) của nhân viên tại cổng.

// =========================================================================
// PHẦN 3: THƯ VIỆN SPRING BOOT VÀ LOMBOK
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự động sinh Constructor cho các field final.
import org.springframework.http.ResponseEntity; // Lớp bọc gói tin HTTP (mã trạng thái 200, 400...).
import org.springframework.transaction.annotation.Transactional; // Đảm bảo tính nhất quán của giao dịch tra cứu dữ liệu.
import org.springframework.web.bind.annotation.*; // Các annotation định nghĩa REST Controller và mapping URL.

import java.util.List;
import java.util.stream.Collectors;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO QUẢN LÝ CỔNG (GATE CONTROLLER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này cung cấp danh sách toàn bộ các Cổng ra/vào (Gate) trong bãi xe
 * cùng trạng thái thực tế của cổng (Đang có nhân viên trực, Rảnh, hoặc Bảo trì)
 * để hiển thị lên Sơ đồ bãi xe (SpaceMapScreen) và giao diện điều khiển cổng.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: `@RequestMapping("/api/v1/infrastructure/gates")` là endpoint
 *   chuẩn thuộc module hạ tầng (infrastructure), phục vụ cả Staff và Manager.
 * - Minh chứng 2: Hàm `toDTO` liên kết chéo với `StaffWorkSessionRepository` để
 *   xác định trạng thái OCCUPIED dựa trên phiên trực thực tế đang ACTIVE của nhân viên.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/infrastructure/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateRepository gateRepository;
    private final StaffWorkSessionRepository staffWorkSessionRepository;

    /**
     * =========================================================================
     * API: LẤY DANH SÁCH TẤT CẢ CÁC CỔNG VÀ TRẠNG THÁI CA TRỰC HIỆN TẠI
     * =========================================================================
     * MỤC ĐÍCH:
     * Trả về toàn bộ danh sách cổng trong bãi xe (trừ các cổng đã bị xóa) cùng
     * thông tin nhân viên đang trực tại cổng đó.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lấy tất cả Gate từ database qua `gateRepository.findAll()`.
     * 2. Lọc bỏ các cổng có trạng thái "DELETED".
     * 3. Chuyển đổi từng Gate sang GateConfigDTO qua hàm `toDTO()`.
     * 4. Trả về danh sách gói trong ApiResponse với mã HTTP 200 OK.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<GateConfigDTO>>> getAllGates() {
        // Lấy danh sách toàn bộ các cổng từ database, lọc bỏ các cổng đã bị soft-delete
        // (status = DELETED) nhằm đảm bảo sơ đồ bãi xe chỉ vẽ những cổng đang hiện hữu.
        List<GateConfigDTO> gates = gateRepository.findAll().stream()
                .filter(g -> !"DELETED".equals(g.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(gates, "Fetched gates successfully"));
    }

    /**
     * =========================================================================
     * HÀM NỘI BỘ: CHUYỂN ĐỔI ENTITY GATE SANG GATECONFIGDTO
     * =========================================================================
     * MỤC ĐÍCH:
     * Kiểm tra xem cổng hiện tại có nhân viên đang trực hay không để gán trạng
     * thái chính xác (IDLE, OCCUPIED, hoặc MAINTENANCE).
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Kiểm tra bảng `staff_work_sessions` xem có phiên trực nào tại Gate có
     *    trạng thái "ACTIVE" hay không.
     * 2. Nếu có: gán trạng thái cổng là "OCCUPIED" và lấy tên, email nhân viên trực.
     * 3. Nếu không có và cổng có status gốc là "DELETED": gán "MAINTENANCE".
     * 4. Ngược lại: gán trạng thái "IDLE".
     */
    private GateConfigDTO toDTO(Gate gate) {
        String staffName = null;
        String staffEmail = null;
        String mappedStatus = "IDLE";
        
        // Kiểm tra xem tại Cổng này có phiên làm việc (WorkSession) nào của nhân viên
        // đang ở trạng thái ACTIVE (đang trực tại cổng) hay không.
        // Đây là minh chứng kiến trúc về việc gán trạng thái động: thay vì lưu cờ
        // "occupied" tĩnh trên Gate, hệ thống tự động phát hiện ca trực thực tế.
        var activeSessionOpt = staffWorkSessionRepository.findByGateIdAndStatus(gate.getId(), "ACTIVE");
        if (activeSessionOpt.isPresent()) {
            mappedStatus = "OCCUPIED";
            staffName = activeSessionOpt.get().getStaff().getFullName();
            staffEmail = activeSessionOpt.get().getStaff().getEmail();
        } else if ("MAINTENANCE".equals(gate.getStatus())) {
            // Cổng không có người trực nhưng đang được đánh dấu bảo trì.
            // LƯU Ý: trước đây nhánh này so với "DELETED" — không bao giờ chạy được,
            // vì `getAllGates()` phía trên đã lọc bỏ sạch Cổng DELETED trước khi gọi
            // hàm này. Sửa lại thành "MAINTENANCE" cho khớp bộ từ vựng của
            // `GateConfigDTO` và khớp đúng cách `MapConfigurationService` suy trạng
            // thái — 2 endpoint cùng trả Cổng nên phải cùng 1 quy tắc.
            mappedStatus = "MAINTENANCE";
        }

        return GateConfigDTO.builder()
                .id(gate.getId())
                .floorId(gate.getFloor() != null ? gate.getFloor().getId() : null)
                .floor(gate.getFloor() != null ? gate.getFloor().getFloorName() : null)
                .staffName(staffName)
                .staffEmail(staffEmail)
                .name(gate.getGateName())
                .status(mappedStatus)
                .layoutX(gate.getLayoutX())
                .layoutY(gate.getLayoutY())
                .rotation(gate.getRotation())
                .build();
    }

}
