/**
 * @Author: Nguyễn Huy Hoàng
 * @Date: 2026-06-28
 * @Description: REST Controller chuyên trách việc tiếp nhận tín hiệu trực tiếp từ thiết bị phần cứng thực tế (hoặc phần mềm giả lập IoT Simulator bằng NodeJS).
 * Nó nhận dữ liệu quét biển số xe, thẻ RFID và kích hoạt bước 1 (bắn dữ liệu nháp qua WebSocket lên Web Frontend - GateIn/GateOut Console) 
 * thông qua GateOperationService. File này đóng vai trò là "cửa ngõ" giao tiếp đầu tiên giữa Hardware (phần cứng) và Backend.
 * 
 * @Dependencies (Các thành phần được tiêm vào nội bộ):
 * - GateOperationService: Xử lý nghiệp vụ lõi (bắn sự kiện WebSocket lên Web UI, tính toán bãi đỗ nháp).
 * - ZoneMonitoringService: Quản lý, giám sát tình trạng sức chứa của bãi.
 * - Các Repositories (SlotRepository, ParkingSessionRepository, GateRepository...): Phục vụ việc tra cứu dữ liệu gốc dưới Database.
 * - SimpMessagingTemplate & ApplicationEventPublisher: Kênh phát sóng WebSocket và Event nội bộ.
 * 
 * @Interactions (Tương tác hệ thống thực tế):
 * - IoT Simulator (NodeJS): Là nguồn gọi (Client) trực tiếp của các API trong file này (vd: POST /gates/checkin, POST /gates/checkout).
 * - Frontend (React): Không gọi trực tiếp file này, nhưng bị tác động bởi WebSocket do file này gián tiếp phát ra.
 */
package com.pbms.modules.operation.controller;

/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ REQUEST CỦA HỆ THỐNG (KÈM MINH CHỨNG CODE)
 * (Trình bày chi tiết luồng dữ liệu ánh xạ trực tiếp vào các dòng code trong file này)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO BỘ ĐIỀU PHỐI VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Tại dòng 107 có khai báo `@RestController`. Đây là ký hiệu (Annotation) 
 *   báo cho Spring Boot biết class này là một Web API. Từ đó Spring sẽ tự động đúc (instantiate) 
 *   nó thành một Singleton Bean lưu thường trực trên RAM.
 * - Minh chứng 2: Tại dòng 106 có `@CrossOrigin("*")`. Ký hiệu này là bằng chứng cho thấy 
 *   file đã tháo gỡ hàng rào bảo mật CORS, cho phép mọi thiết bị (dấu `*`) gửi Request tới.
 * - Minh chứng 3: Từ dòng 127 đến 158 là khối Constructor `IotHardwareController(...)`. 
 *   Việc khai báo các tham số bên trong ngoặc tròn như `GateOperationService gateOperationService` 
 *   chính là kỹ thuật Constructor Injection. Spring Boot sẽ tự động tìm các Service này trong 
 *   bộ nhớ và nhét (tiêm) vào các biến cục bộ (ví dụ lệnh `this.gateOperationService = gateOperationService`),
 *   giúp Controller có đủ "vũ khí" (kết nối với Database và Logic) để hoạt động.
 * 
 * BƯỚC 2: TIẾP NHẬN REQUEST TỪ THIẾT BỊ IOT (DISPATCHER SERVLET & HANDLER MAPPING)
 * - Minh chứng 1: Dòng 108 khai báo `@RequestMapping("/api/v1/operation/iot/hardware")` ở đầu 
 *   class, đây là Gốc của URL.
 * - Minh chứng 2: Dòng 273 khai báo `@PostMapping("/gates/checkin")`. Việc ghép nối gốc URL 
 *   ở trên với đuôi `/gates/checkin` và quy định Method là POST chính là bằng chứng cho việc 
 *   `HandlerMapping` (người điều phối của Spring) sẽ định tuyến chính xác mọi Request từ thiết 
 *   bị IoT vào đúng hàm `simulateCheckIn()` này chứ không đi nhầm sang hàm khác.
 * 
 * BƯỚC 3: RÀNG BUỘC VÀ CHUYỂN ĐỔI DỮ LIỆU (DESERIALIZATION)
 * - Minh chứng: Tại dòng 275, tham số của hàm được khai báo là: `(@RequestBody CheckInRequestDTO request)`.
 * - Cụ thể tại sao đây là minh chứng? 
 *   + Chữ `@RequestBody` là lệnh bắt buộc Spring Boot phải kích hoạt thư viện Jackson để đọc 
 *     chuỗi văn bản JSON (gửi từ thiết bị IoT) thành dạng Java.
 *   + `CheckInRequestDTO` là khuôn mẫu. Jackson sẽ bóc tách chuỗi JSON (tìm thuộc tính biển 
 *     số, ảnh Base64) và nhét vào khuôn mẫu này.
 *   + Kết quả được lưu vào biến có tên là `request`. Từ giây phút này, biến `request` mang 
 *     trong mình toàn bộ dữ liệu hợp lệ (sẵn sàng trên RAM) để truyền vào thân hàm.
 * 
 * BƯỚC 4: ỦY QUYỀN XỬ LÝ NGHIỆP VỤ LÕI (SERVICE DELEGATION - N-TIER ARCHITECTURE)
 * - Controller (Lễ tân) không chứa logic tính toán. Nó phải đẩy việc cho Service.
 * - Minh chứng: Dòng 281 chứa câu lệnh: 
 *   `GateResponseDTO response = gateOperationService.triggerScanCheckIn(request);`
 * - Cụ thể tại sao dòng này là minh chứng? 
 *   + Gọi đối tượng: `gateOperationService` chính là bộ não đã được tiêm vào từ Bước 1. 
 *     Controller đang gọi bộ não ra làm việc.
 *   + Gọi hàm và truyền tham số: Lệnh `triggerScanCheckIn(request)` hành động ném thẳng 
 *     biến `request` (chứa dữ liệu xe vào từ Bước 3) sang cho lớp Service để nó lo việc kết nối 
 *     CSDL, kiểm tra Blacklist, tìm bãi trống. Điều này chứng minh Controller hoàn toàn "vô can" 
 *     trong việc tính toán (Tuân thủ nguyên lý Separation of Concerns).
 *   + Hứng kết quả: `GateResponseDTO response = ...` là hành động hứng (gán) kết quả do Service 
 *     tạo ra sau khi tính toán xong. Biến `response` lúc này là một Object chứa thông báo trạng thái,
 *     tên cổng mở, thời gian vào.
 * 
 * BƯỚC 5: ĐÓNG GÓI VÀ TRẢ VỀ RESPONSE (SERIALIZATION & HTTP 200 OK)
 * - Cuối cùng phải trả kết quả về cho thiết bị IoT để màn hình hiện chữ Thành công.
 * - Minh chứng: Dòng 286 chứa câu lệnh: 
 *   `return ResponseEntity.ok(ApiResponse.success(response, "Check-in triggered"));`
 * - Cụ thể tại sao dòng này là minh chứng?
 *   + Lệnh `ApiResponse.success(response, ...)`: Tiến hành đóng gói Object `response` (từ Bước 4) 
 *     vào trong một cấu trúc JSON chuẩn của dự án (luôn có trường statusCode, message, data).
 *   + Lệnh `ResponseEntity.ok(...)`: Đây là lệnh đóng dấu mã HTTP Status Code 200 (Thành công) 
 *     cho toàn bộ gói tin HTTP.
 *   + Lệnh `return`: Chuyển quyền điều khiển lại cho Spring Boot. Quá trình Serialization 
 *     (ngược với Bước 3) được kích hoạt: Biến đối tượng Java thành một chuỗi JSON thuần túy 
 *     và bắn thẳng qua cáp mạng về lại cho Tool IoT. Kết thúc vòng đời luồng dữ liệu!
 * 
 * PHỤ LỤC 1: HÀNH TRÌNH REQUEST CỦA LUỒNG CHECK-OUT (TƯƠNG TỰ BƯỚC 1 ĐẾN 5)
 * - Minh chứng Handler: Dòng 357 `@PostMapping("/gates/checkout")`. Spring định tuyến tín hiệu xe ra vào hàm `simulateCheckOut`.
 * - Minh chứng Deserialization: Dòng 358 `(@RequestBody CheckOutRequestDTO request)`. Jackson bóc tách JSON thành object `request` mang dữ liệu xe ra (biển số, thẻ).
 * - Minh chứng Delegation: Dòng 363 `GateResponseDTO response = gateOperationService.triggerScanCheckOut(request);`. Controller ném cái `request` sang bộ não Service để nó tính toán giá tiền (nếu là vé lượt) rồi gán vào biến `response`.
 * - Minh chứng Serialization: Dòng 367 `return ResponseEntity.ok(ApiResponse.success(response, ...));`. Đóng gói kết quả và trả về HTTP 200 OK cho máy quét cổng ra.
 * 
 * PHỤ LỤC 2: HÀNH TRÌNH REQUEST CỦA LUỒNG SENSOR UPDATE (CẬP NHẬT CHỖ TRỐNG)
 * - Minh chứng Handler: Dòng 202 `@PostMapping("/sensors/update")`. Hứng tín hiệu từ cảm biến gắn tại từng ô đậu xe.
 * - Minh chứng Deserialization: Dòng 203 `(@RequestBody SensorEventDto request)`. Bóc tách mã ô đỗ và trạng thái đè lên/rời đi.
 * - Minh chứng Delegation: Dòng 205 `zoneMonitoringService.processSensorEvent(...)`. Giao việc cho Service đổi màu ô đỗ trên bản đồ.
 * - Minh chứng Serialization: Dòng 207 `return ResponseEntity.ok(...)`. Báo lại cho cảm biến.
 * 
 * PHỤ LỤC 3: HÀNH TRÌNH REQUEST CỦA LUỒNG FAST FORWARD TIME (TUA THỜI GIAN)
 * - Minh chứng Handler: Dòng 267 `@PostMapping("/time/fast-forward")`. Hứng lệnh tua thời gian từ Simulator.
 * - Minh chứng Delegation đa tầng (Không có ở Check-In): 
 *   + Dòng 282 `systemConfigService.saveOrUpdateConfigValue(...)`: Lưu độ lệch thời gian vào Cấu hình hệ thống.
 *   + Dòng 288 `messagingTemplate.convertAndSend(...)`: Gọi công cụ WebSocket bắn tín hiệu Real-time bắt toàn bộ giao diện Web phải cập nhật lại đồng hồ hiển thị.
 *   + Dòng 292 `eventPublisher.publishEvent(...)`: Dùng kiến trúc Event-Driven bắn 1 sự kiện ngầm trong nội bộ Backend (Ví dụ để đánh thức các hàm Dọn dẹp vé quá hạn).
 * 
 * PHỤ LỤC 4: HÀNH TRÌNH REQUEST ĐỒNG BỘ DỮ LIỆU (DATA SYNC)
 * - Minh chứng Handler (GET): Hàm `syncData` (dòng 382) khai báo `@GetMapping`.
 *   (Hàm `debugSession` từng có ở đây đã bị xóa vì là code chết — không
 *   FE/simulator nào gọi tới, xem lịch sử git.)
 * - Minh chứng Tối ưu hóa: Dòng 383 `@Transactional(readOnly = true)`. Báo cho bộ máy Hibernate biết đây chỉ là lệnh SELECT (không có thao tác Ghi/Xóa dữ liệu) để nó tối ưu hóa RAM và khóa (Lock) của cơ sở dữ liệu.
 * - Minh chứng Gom dữ liệu: Không có Service Delegation phức tạp. Các hàm này chỉ gọi trực tiếp các kho dữ liệu (như dòng 391 `slotRepository.findAll()`), nhét vào một cái túi bọc Hash Map khổng lồ và quăng ra bằng Jackson.
 * =========================================================================================
 */

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO (DATA TRANSFER OBJECT)
// Công dụng: Định nghĩa hình dáng của các "gói hàng" (dữ liệu) đi ra đi vào API
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp dùng để bọc kết quả trả về cho Client theo 1 chuẩn chung (Ví dụ luôn có code: 200, message: "OK", data: {...}).
import com.pbms.common.utils.TimeProvider; // Tiện ích quản lý thời gian của hệ thống (giúp xử lý múi giờ hoặc tính năng tua nhanh thời gian test).
import com.pbms.modules.operation.dto.CheckInRequestDTO; // Gói dữ liệu chứa thông tin khi xe VÀO (Ví dụ: biển số đọc được, chuỗi mã hóa hình ảnh, mã thẻ từ).
import com.pbms.modules.operation.dto.CheckOutRequestDTO; // Gói dữ liệu chứa thông tin khi xe RA (tương tự như CheckIn, nhưng dùng cho luồng ra).
import com.pbms.modules.operation.dto.GateResponseDTO; // Gói dữ liệu hệ thống trả ngược lại cho thiết bị IoT sau khi xử lý xong quét thẻ/biển số.
import com.pbms.modules.operation.dto.SensorEventDto; // Gói dữ liệu từ cảm biến đậu xe (báo cáo có xe đậu vào ô hay vừa rời đi).

// =========================================================================
// PHẦN 2: CÁC LỚP SERVICE (CHỨA LOGIC NGHIỆP VỤ)
// Công dụng: Đây là các "bộ não" thực hiện tính toán, kiểm tra đúng/sai.
// =========================================================================
import com.pbms.modules.operation.service.GateOperationService; // Dịch vụ LÕI xử lý toàn bộ luồng xe vào, xe ra, mở cổng, báo lỗi.
import com.pbms.modules.operation.service.ZoneMonitoringService; // Dịch vụ giám sát các khu vực đậu xe, đếm số chỗ trống.

// =========================================================================
// PHẦN 3: CÁC REPOSITORY (KẾT NỐI DATABASE)
// Công dụng: Các class này dùng để lấy dữ liệu từ CSDL (SQL) hoặc Lưu mới vào CSDL.
// =========================================================================
import com.pbms.modules.infrastructure.repository.SlotRepository; // Thao tác với bảng Slot (Ô đậu xe).
import com.pbms.modules.operation.repository.ParkingSessionRepository; // Thao tác với bảng ParkingSession (Lịch sử các phiên xe ra/vào).
import com.pbms.modules.operation.repository.ReservationRepository; // Thao tác với bảng Reservation (Các đơn đặt chỗ trước của khách).
import com.pbms.modules.infrastructure.repository.GateRepository; // Thao tác với bảng Gate (Thông tin các cổng ra vào).
import com.pbms.modules.infrastructure.repository.FloorRepository; // Thao tác với bảng Floor (Thông tin tầng hầm/tầng nổi).
import com.pbms.modules.infrastructure.repository.ZoneRepository; // Thao tác với bảng Zone (Các khu đậu xe).
import com.pbms.modules.operation.repository.VehicleTypeRepository; // Thao tác với bảng VehicleType (Loại xe: Ô tô, xe máy...).
import com.pbms.modules.operation.repository.MonthlyTicketRepository; // Thao tác với bảng MonthlyTicket (Vé tháng).
import com.pbms.modules.infrastructure.repository.RfidCardRepository; // Thao tác với bảng RfidCard (Thẻ từ).
import com.pbms.modules.operation.repository.StaffWorkSessionRepository; // Thao tác với bảng StaffWorkSession (Ca làm việc của nhân viên).

// =========================================================================
// PHẦN 4: CÁC THƯ VIỆN CỦA SPRING BOOT VÀ JAVA
// Công dụng: Framework hỗ trợ tạo API, kết nối mạng, xử lý thời gian...
// =========================================================================
import org.springframework.context.ApplicationEventPublisher; // Thư viện giúp phát ra các sự kiện (Event) ngầm trong hệ thống.
import com.pbms.common.event.TimeFastForwardedEvent; // Lớp tự định nghĩa để lắng nghe sự kiện tua nhanh thời gian.
import com.pbms.modules.system.service.SystemConfigService; // Dịch vụ lấy cấu hình hệ thống (Ví dụ: giá tiền, quy định).
import org.springframework.beans.factory.annotation.Autowired; // Đánh dấu để Spring Boot tự động "bơm" (Inject) các service/repo vào file này để dùng.
import org.springframework.http.ResponseEntity; // Lớp bọc của Spring đại diện cho 1 gói tin HTTP trả về (chứa mã 200, 400...).
import org.springframework.transaction.annotation.Transactional; // Đảm bảo tính toàn vẹn dữ liệu: Nếu bị lỗi giữa chừng thì sẽ hủy toàn bộ thao tác Database.
import org.springframework.web.bind.annotation.*; // Chứa các thẻ quan trọng như @RestController, @PostMapping... để tạo API.
import org.springframework.messaging.simp.SimpMessagingTemplate; // Công cụ dùng để bắn tin nhắn WebSocket tới Frontend (Real-time).

import java.time.LocalDateTime; // Thư viện Java chuẩn để xử lý Ngày - Giờ.
import java.util.HashMap; // Thư viện Java để tạo Map (giống Object trong JS) chứa Key-Value.
import java.util.Map; // Giao diện (Interface) chung của các loại Map.

/**
 * === PHẦN 5: ĐỊNH NGHĨA CLASS CONTROLLER ===
 * Nơi hứng mọi tín hiệu từ các thiết bị IoT bắn lên (như Camera AI chụp biển
 * số, đầu đọc thẻ tít thẻ).
 */
@CrossOrigin("*") // Dòng này cho phép mọi website, ứng dụng (từ localhost hoặc ip khác) được phép
                  // gọi API này mà không bị chặn lỗi bảo mật CORS.
@RestController // Biến file Java này thành một Web API Controller. Nó sẽ tự động chuyển đổi dữ
                // liệu trả về thành chuỗi JSON.
@RequestMapping("/api/v1/operation/iot/hardware") // Khai báo đường dẫn gốc. Muốn gọi các hàm bên dưới, phải nối đường
                                                  // dẫn này vào trước.
public class IotHardwareController {

    // =========================================================================
    // NHÓM 1: CÁC "BỘ NÃO" XỬ LÝ LOGIC (SERVICES)
    // =========================================================================
    // Dùng để đổi màu ô đỗ trên bản đồ Web khi có xe đè lên cảm biến hồng ngoại.
    private final ZoneMonitoringService zoneMonitoringService;
    
    // Dịch vụ Lõi: Xử lý quy trình quẹt thẻ vào/ra, đối chiếu vé tháng, tính tiền, mở barie.
    private final GateOperationService gateOperationService;
    
    // =========================================================================
    // NHÓM 2: CÁC "NHÀ KHO" TRUY VẤN DỮ LIỆU (REPOSITORIES)
    // =========================================================================
    // Kho chứa danh sách các ô đậu xe (để gom dữ liệu gửi cho Simulator vẽ bản đồ).
    private final SlotRepository slotRepository;
    
    // Kho chứa lịch sử các chuyến đỗ xe (Ai đang đậu ở đâu, mấy giờ vào, ảnh Base64).
    private final ParkingSessionRepository sessionRepository;
    
    // Kho chứa danh sách các khách hàng đã đặt chỗ trước trên App.
    private final ReservationRepository reservationRepository;
    
    // Kho chứa danh sách các cổng ra/vào (Gate In/Gate Out).
    private final GateRepository gateRepository;
    
    // Kho chứa danh sách các loại hình xe (Ô tô 4 chỗ, Xe máy...) để kiểm tra kích thước đỗ.
    private final VehicleTypeRepository vehicleTypeRepository;
    
    // Kho chứa thẻ nhựa RFID chưa ai dùng (để Simulator hiện danh sách giả lập quẹt thẻ).
    private final RfidCardRepository rfidCardRepository;
    
    // Kho chứa danh sách Tầng hầm/Tầng nổi (Để lấy kích thước hàng/cột cho lưới Grid 2D).
    private final FloorRepository floorRepository;
    
    // Kho chứa các Khu vực đỗ xe A, B, C (Để lấy tọa độ X, Y vẽ lên bản đồ).
    private final ZoneRepository zoneRepository;
    
    // Kho kiểm tra xem Cổng (Gate) này hiện tại có nhân viên bảo vệ nào đang trực không.
    private final StaffWorkSessionRepository staffWorkSessionRepository;
    
    // Kho kiểm tra xem khách này có mua vé tháng (để đi thẳng qua trạm) không.
    private final MonthlyTicketRepository monthlyTicketRepository;
    
    // =========================================================================
    // NHÓM 3: CÁC CÔNG CỤ HỖ TRỢ CỦA SPRING BOOT
    // =========================================================================
    // Dùng để lấy và lưu cấu hình "Độ chênh lệch thời gian" khi tua nhanh (Fast-Forward).
    private final SystemConfigService systemConfigService;
    
    // Dùng để "bắn một phát súng" (Event) báo hiệu cho toàn hệ thống biết là giờ đã bị tua.
    private final ApplicationEventPublisher eventPublisher;
    
    // Dùng để bắn dữ liệu (ví dụ: giờ đồng hồ bị tua) thẳng lên màn hình Web (WebSocket Real-time).
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * =========================================================================
     * CONSTRUCTOR INJECTION (KỸ THUẬT TIÊM PHỤ THUỘC)
     * =========================================================================
     * Bối cảnh: Khi đọc code, bạn có thể thắc mắc "Tại sao không dùng từ khóa `new` 
     * (ví dụ: `new GateOperationService()`) mà lại phải truyền tham số dài dằng dặc thế này?"
     * 
     * Giải thích: 
     * - Khối code này thể hiện cơ chế Dependency Injection (DI) - xương sống của Spring Boot. 
     * - Annotation `@Autowired` là mệnh lệnh gửi tới Spring Container: "Khi khởi động Server, 
     *   hãy đúc (instantiate) sẵn 15 cái Service và Repository kia để trên RAM, sau đó tự động 
     *   'tiêm' (bơm) chúng vào cái Constructor này cho tôi".
     * - Lợi ích (Best Practice): Việc viết Constructor như thế này kết hợp với từ khóa `final` 
     *   ở trên giúp đảm bảo rằng 15 món đồ nghề này một khi đã tiêm vào là dính chặt, không 
     *   bị một hàm rác nào đó vô tình đổi giá trị làm sập hệ thống (Thread-safe). Đồng thời, 
     *   nó giúp việc viết Unit Test cực kỳ dễ dàng (chỉ việc tạo Mock Object truyền vào).
     */
    @Autowired
    public IotHardwareController(ZoneMonitoringService zoneMonitoringService,
            GateOperationService gateOperationService,
            SlotRepository slotRepository,
            ParkingSessionRepository sessionRepository,
            ReservationRepository reservationRepository,
            GateRepository gateRepository,
            VehicleTypeRepository vehicleTypeRepository,
            RfidCardRepository rfidCardRepository,
            FloorRepository floorRepository,
            ZoneRepository zoneRepository,
            StaffWorkSessionRepository staffWorkSessionRepository,
            MonthlyTicketRepository monthlyTicketRepository,
            SystemConfigService systemConfigService,
            ApplicationEventPublisher eventPublisher,
            SimpMessagingTemplate messagingTemplate) {
        this.zoneMonitoringService = zoneMonitoringService;
        this.gateOperationService = gateOperationService;
        this.slotRepository = slotRepository;
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
        this.gateRepository = gateRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.rfidCardRepository = rfidCardRepository;
        this.floorRepository = floorRepository;
        this.zoneRepository = zoneRepository;
        this.staffWorkSessionRepository = staffWorkSessionRepository;
        this.monthlyTicketRepository = monthlyTicketRepository;
        this.systemConfigService = systemConfigService;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * =========================================================================
     * API: NHẬN TÍN HIỆU TỪ CẢM BIẾN CHỖ ĐẬU XE (SENSOR UPDATE)
     * =========================================================================
     * MỤC ĐÍCH:
     * Bãi xe có các cảm biến (sensor) gắn ở từng ô đậu xe. Khi có xe đè lên hoặc
     * rời đi,
     * cảm biến sẽ bắn API này để báo cáo trạng thái (Ví dụ: "Ô A1 đã có xe", "Ô B2
     * đã trống").
     * 
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe method POST tại endpoint "/sensors/update".
     * 2. Nhận gói dữ liệu (SensorEventDto) chứa Mã cảm biến (SensorId) và Trạng
     * thái mới (Status).
     * 3. Chuyền thông tin này cho ZoneMonitoringService để nó cập nhật vào Database
     * và đổi màu sơ đồ trên Web.
     * 4. Trả về thông báo thành công cho cảm biến.
     */
    @PostMapping("/sensors/update")
    public ResponseEntity<ApiResponse<String>> handleSensorUpdate(@RequestBody SensorEventDto request) {
        // Chuyển việc xử lý lõi cho bộ não (ZoneMonitoringService)
        zoneMonitoringService.processSensorEvent(request.getSensorId(), request.getStatus());
        // Báo lại cho thiết bị là đã cập nhật xong
        return ResponseEntity.ok(ApiResponse.success("Processed", "Sensor update processed successfully"));
    }

    /**
     * =========================================================================
     * API: TUA NHANH THỜI GIAN HỆ THỐNG (FAST FORWARD TIME)
     * =========================================================================
     * MỤC ĐÍCH:
     * Dùng cho việc DEMO đồ án. Vì không thể chờ xe đậu vài tiếng đồng hồ để tính
     * tiền,
     * API này giúp "Tua nhanh thời gian" của toàn bộ hệ thống tới 1 giờ bất kỳ
     * trong tương lai
     * để test tính năng thu phí (Pricing) hoặc tự động hủy chỗ đặt (Expire
     * Booking).
     * 
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe method POST tại "/time/fast-forward".
     * 2. Lấy `targetTime` (thời gian muốn tua tới) từ gói dữ liệu (payload).
     * 3. Lưu lại độ chênh lệch thời gian (Offset) vào Database (bảng SystemConfig)
     * để các bộ phận khác lấy đúng giờ giả lập.
     * 4. Bắn WebSocket báo cho Frontend (Màn hình nhân viên) biết là "Giờ đã bị
     * tua" để giao diện tự cập nhật đồng hồ.
     * 5. Kích hoạt Event ngầm `TimeFastForwardedEvent` để chạy các job như: Hủy vé
     * quá hạn.
     */
    @PostMapping("/time/fast-forward")
    public ResponseEntity<ApiResponse<String>> fastForwardTime(@RequestBody Map<String, String> payload) {
        String targetTimeStr = payload.get("targetTime"); // Bóc tách chuỗi thời gian muốn tua tới
        if (targetTimeStr == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Missing targetTime")); // Nếu gửi thiếu thì
                                                                                                   // chửi lỗi 400
        }
        LocalDateTime targetTime = LocalDateTime.parse(targetTimeStr); // Đổi từ chuỗi sang kiểu Ngày-Giờ của Java
        try {
            LocalDateTime oldTime = TimeProvider.now(); // Lấy giờ hiện tại trước khi tua
            TimeProvider.fastForwardTo(targetTime); // Chạy hàm tua thời gian

            // Tính số giây bị lệch so với giờ thực tế, và lưu vào Cấu hình (SystemConfig)
            // Database
            long offsetSeconds = TimeProvider.getSimulatedOffset().getSeconds();
            systemConfigService.saveOrUpdateConfigValue("TIME_SIMULATED_OFFSET_SECONDS", String.valueOf(offsetSeconds));

            // Gửi tin nhắn qua WebSocket báo cho tất cả màn hình Web cập nhật đồng hồ hiển
            // thị
            Map<String, Object> wsPayload = new HashMap<>();
            wsPayload.put("offsetSeconds", offsetSeconds);
            messagingTemplate.convertAndSend("/topic/time-sync", (Object) wsPayload);

            // Bắn một phát súng (Event) thông báo trong nội bộ hệ thống Backend: "Giờ đã
            // thay đổi, các JOB dọn dẹp data hãy chạy đi!"
            eventPublisher.publishEvent(new TimeFastForwardedEvent(this, oldTime, TimeProvider.now()));

            // Trả về chữ OK và giờ sau khi tua
            return ResponseEntity
                    .ok(ApiResponse.success("Current Time: " + TimeProvider.now(), "Time fast-forwarded successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 1: NHẬN TÍN HIỆU TỪ CỔNG VÀO (CHECK-IN)
     * =========================================================================
     * MỤC ĐÍCH:
     * Khi xe tới cổng vào, thiết bị Camera hoặc Đầu đọc thẻ sẽ gửi 1 API (POST) đến
     * đây.
     * Hàm này sẽ hứng dữ liệu và chuyển cho bộ não (Service) xử lý, sau đó báo lại
     * cho máy quét.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Lắng nghe HTTP POST tại đường dẫn /gates/checkin.
     * 2. Phân tích gói JSON được gửi tới (body) thành đối tượng CheckInRequestDTO.
     * 3. Gọi GateOperationService.triggerScanCheckIn() truyền gói dữ liệu vào để xử
     * lý luồng lõi.
     * 4. Lấy kết quả (GateResponseDTO) từ Service.
     * 5. Bọc kết quả vào ApiResponse, đánh mã code HTTP 200 (OK) và trả về cho
     * thiết bị.
     */
    @PostMapping("/gates/checkin") // Dòng này chỉ định: Chỉ chấp nhận method POST, nối vào sau đường dẫn gốc
                                   // thành: /api/v1/operation/iot/hardware/gates/checkin
    // @RequestBody: Cú pháp báo cho Spring Boot biết phải dịch chuỗi JSON gửi lên
    // thành class CheckInRequestDTO (lưu vào biến `request`)
    public ResponseEntity<ApiResponse<GateResponseDTO>> simulateCheckIn(@RequestBody CheckInRequestDTO request) {

        // Bước 1: Gọi hàm xử lý lõi.
        // Hàm triggerScanCheckIn sẽ làm các việc (nằm ở file GateOperationService):
        // Tìm xem khách này là vé tháng hay vé lượt -> Có Booking không -> Tìm tuyến
        // đường bãi trống -> Bắn WebSocket lên giao diện cho bảo vệ xem.
        // Kết quả xử lý xong sẽ được lưu vào biến `response`.
        GateResponseDTO response = gateOperationService.triggerScanCheckIn(request);

        // Bước 2: Trả kết quả về cho thiết bị IoT (camera/đầu đọc).
        // ApiResponse.success(): Bọc dữ liệu `response` vào trong một chuẩn JSON chung
        // của team, kèm câu thông báo "Check-in triggered".
        // ResponseEntity.ok(): Tạo một gói tin HTTP có mã 200 (Nghĩa là Thành công),
        // chứa nội dung ApiResponse ở trên.
        return ResponseEntity.ok(ApiResponse.success(response, "Check-in triggered"));
    }

    /**
     * =========================================================================
     * API 2: NHẬN TÍN HIỆU TỪ CỔNG RA (CHECK-OUT)
     * =========================================================================
     * MỤC ĐÍCH:
     * Tương tự như lúc vào, khi khách quét thẻ/biển số lúc ra về, thiết bị gọi API
     * này.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Lắng nghe HTTP POST tại đường dẫn /gates/checkout.
     * 2. Bắt lấy gói dữ liệu JSON và gán vào biến CheckOutRequestDTO.
     * 3. Đẩy dữ liệu sang GateOperationService.triggerScanCheckOut() để nó tìm giờ
     * vào, tính tiền, và bắn WebSocket lên màn hình thu ngân.
     * 4. Bọc kết quả và trả về HTTP 200 OK.
     */
    @PostMapping("/gates/checkout") // Lắng nghe method POST tại endpoint /gates/checkout
    public ResponseEntity<ApiResponse<GateResponseDTO>> simulateCheckOut(@RequestBody CheckOutRequestDTO request) {

        // Gọi Service lõi xử lý việc xe quét thẻ/biển số ở cổng ra.
        // Bên trong hàm này sẽ tính toán xem xe này phải đóng bao nhiêu tiền (nếu là vé
        // lượt) và bắn lên màn hình cổng ra.
        GateResponseDTO response = gateOperationService.triggerScanCheckOut(request);

        // Trả về cho thiết bị quét tín hiệu 200 OK, ngụ ý: "Server đã nhận và xử lý tín
        // hiệu thành công".
        return ResponseEntity.ok(ApiResponse.success(response, "Check-out triggered"));
    }

    /**
     * =========================================================================
     * API: ĐỒNG BỘ DỮ LIỆU CHUNG (DATA SYNC) CHO TOOL SIMULATOR
     * =========================================================================
     * MỤC ĐÍCH VÀ CƠ CHẾ HOẠT ĐỘNG:
     * 
     * 1. Ý nghĩa kinh doanh:
     *    - Giao diện IoT Simulator (và Màn hình giám sát) là một bản đồ 2D phức tạp. 
     *      Để vẽ được bản đồ này, nó cần biết toàn bộ thực trạng của bãi xe ngay tại giây phút này: 
     *      có bao nhiêu ô đỗ, xe nào đang đậu ở ô nào, có bao nhiêu cổng đang mở.
     *    - Hàm này đóng vai trò như một "Chiếc phễu khổng lồ", gom dữ liệu từ 9 bảng
     *      Database khác nhau và trả về cho Client trong 1 lần gọi API duy nhất (Bulk Fetch) 
     *      để giảm thiểu tối đa số lượng HTTP Request, tối ưu hóa băng thông mạng.
     * 
     * 2. Phân tích kỹ thuật (Tại sao phải biến đổi dữ liệu?):
     *    - Thay vì trả về thẳng các Entity thô (ví dụ: `return slotRepository.findAll()`), 
     *      bạn sẽ thấy code bên dưới dùng hàm `.map(...)` để tự tay bóc tách từng trường (ID, Name) 
     *      nhét vào các `HashMap` nhỏ (kiến trúc DTO on-the-fly). Tại sao phải vất vả vậy?
     *    - LÝ DO 1 (Tránh Infinite Recursion - Vòng lặp vô tận): 
     *      Trong CSDL (Hibernate), bảng Slot nối với bảng Zone. Nếu quăng thẳng Entity Slot cho 
     *      Jackson ép kiểu sang JSON, Jackson sẽ nhảy vào đọc Zone. Nhưng rủi thay, trong Zone 
     *      lại chứa danh sách Slot... Jackson lại nhảy vào Slot, rồi lại sang Zone... Gây ra 
     *      vòng lặp vô tận làm treo máy chủ (StackOverflow).
     *    - LÝ DO 2 (Tránh LazyInitializationException): 
     *      Hibernate mặc định chỉ tải dữ liệu cơ bản (Lazy Loading). Khi hàm này kết thúc, kết 
     *      nối Database lập tức bị ngắt. Nếu lúc này Jackson mới bắt đầu cố lôi dữ liệu của bảng 
     *      VehicleType (nằm trong Session) ra để dịch, hệ thống sẽ văng lỗi vì Database đã đóng.
     *      Việc dùng `HashMap` ép Java phải móc dữ liệu ra ngay lập tức khi kết nối DB đang còn mở.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC THỰC THI:
     * 1. Khởi tạo một cái túi lớn `Map<String, Object> data = new HashMap<>()`.
     * 2. Lấy Giờ hệ thống hiện tại (Có tính cả độ lệch nếu giảng viên đang dùng chức năng Fast-Forward).
     * 3. Quét bảng Slot: Lọc bỏ các ô đỗ đã xóa, chỉ lấy các trường an toàn (id, name, status, zoneId) 
     *    bỏ vào túi nhỏ, rồi gom nhét vào cái túi lớn.
     * 4. Quét bảng Session (Xe đang đậu): Bóc tách biển số, ảnh chụp panorama, ID thẻ RFID... nhét vào túi lớn.
     * 5. Quét lần lượt các bảng Reservation (Đặt chỗ), Gate (Cổng), Monthly Ticket (Vé tháng), 
     *    Vehicle Type (Phân loại xe), Thẻ RFID rảnh, Tầng hầm, Khu vực đỗ xe...
     * 6. Trả về toàn bộ túi dữ liệu khổng lồ đó bằng mã HTTP 200 OK.
     */
    @GetMapping("/data-sync")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncData() {
        Map<String, Object> data = new HashMap<>();

        /* 
         * =========================================================
         * PHẦN 1: ĐỒNG BỘ GIỜ HỆ THỐNG
         * =========================================================
         * Mục đích: Tool Simulator cần biết giờ của Backend (có thể đang bị tua nhanh).
         * Hành động: Lấy giờ từ TimeProvider và nhét vào key "currentTime".
         */
        data.put("currentTime", TimeProvider.now());

        /*
         * =========================================================
         * PHẦN 2: DANH SÁCH Ô ĐẬU XE (SLOTS)
         * =========================================================
         * Hành động: Quét toàn bộ bảng Slot trong DB.
         * Bộ lọc (Filter): Bỏ qua các Slot mồ côi (không có Zone) hoặc Zone đã bị xóa ("DELETED").
         * Map (Ép kiểu): Nhặt ra ID, tên ô đỗ (A1, A2), trạng thái (trống, có xe).
         * Ràng buộc: Chỉ lấy ra `zone.getId()` chứ tuyệt đối không lấy nguyên object `zone` để tránh Infinite Recursion.
         */
        data.put("slots", slotRepository.findAll().stream()
                .filter(s -> s.getZone() != null && !"DELETED".equals(s.getZone().getStatus()))
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", s.getId());
                    map.put("slotName", s.getSlotName());
                    map.put("status", s.getStatus());
                    if (s.getZone() != null) {
                        map.put("zoneId", s.getZone().getId());
                    }
                    return map;
                }).toList());

        /*
         * =========================================================
         * PHẦN 3: DANH SÁCH XE ĐANG TRONG BÃI (ACTIVE SESSIONS)
         * =========================================================
         * Hành động: Quét bảng ParkingSession.
         * Bộ lọc: Chỉ lấy xe đang đậu ("ACTIVE") hoặc xe bị khóa ("LOCKED"). Xe đã ra khỏi bãi (COMPLETED) thì vứt.
         * Map (Bóc tách dữ liệu sâu):
         * - Nhặt các trường cơ bản: Biển số, giờ vào, ảnh Base64.
         * - Flatten (làm phẳng) dữ liệu tầng (Floor): Lấy `gateIn.getFloor().getId()`.
         * - Làm phẳng dữ liệu Thẻ RFID: Thay vì trả object RfidCard, ta tự tạo `rfidMap` chỉ chứa 2 mã Code và ID.
         * - Nếu không bóc tách thủ công thế này, Jackson sẽ cố đọc `GateIn` -> đọc `Floor` -> gây tràn RAM.
         */
        data.put("activeSessions", sessionRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus()))
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", s.getId());
                    map.put("plate", s.getPlate());
                    map.put("timeIn", s.getTimeIn());
                    map.put("status", s.getStatus());
                    map.put("suggestedZoneId", s.getSuggestedZoneId());
                    map.put("picInPanorama", s.getPicInPanorama());
                    map.put("picInFace", s.getPicInFace());
                    if (s.getGateIn() != null && s.getGateIn().getFloor() != null) {
                        map.put("floorId", s.getGateIn().getFloor().getId());
                    }
                    if (s.getVehicleType() != null) {
                        map.put("vehicleTypeId", s.getVehicleType().getId());
                    }
                    if (s.getRfidCard() != null) {
                        Map<String, Object> rfidMap = new HashMap<>();
                        rfidMap.put("cardCode", s.getRfidCard().getCardCode());
                        rfidMap.put("cardId", s.getRfidCard().getCardId());
                        map.put("rfidCard", rfidMap);
                    }

                    if (s.getVehicleType() != null) {
                        Map<String, Object> vMap = new HashMap<>();
                        vMap.put("id", s.getVehicleType().getId());
                        vMap.put("typeName", s.getVehicleType().getTypeName());
                        map.put("vehicleType", vMap);
                    }
                    return map;
                })
                .toList());

        /*
         * =========================================================
         * PHẦN 4: DANH SÁCH ĐẶT CHỖ TRƯỚC (RESERVATIONS)
         * =========================================================
         * Hành động: Quét bảng Reservation.
         * Bộ lọc: Khách đang chờ ("PENDING") hoặc đang sử dụng ("ACTIVE").
         * Map: Bóc tách thông tin khách đã đặt cọc bao nhiêu (reservationFee), biển số xe đã đăng ký (vehicle.plateNumber),
         * và khu vực họ muốn đậu (zoneName).
         */
        data.put("reservations", reservationRepository.findAll().stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()) || "PENDING".equals(r.getStatus()))
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", r.getId());
                    map.put("expectedEntryTime", r.getExpectedEntryTime());
                    map.put("reservationFee", r.getReservationFee());
                    map.put("status", r.getStatus());
                    if (r.getVehicle() != null) {
                        Map<String, Object> vMap = new HashMap<>();
                        vMap.put("id", r.getVehicle().getId());
                        vMap.put("plateNumber", r.getVehicle().getPlateNumber());
                        if (r.getVehicle().getVehicleType() != null) {
                            Map<String, Object> vtMap = new HashMap<>();
                            vtMap.put("id", r.getVehicle().getVehicleType().getId());
                            vtMap.put("typeName", r.getVehicle().getVehicleType().getTypeName());
                            vMap.put("vehicleType", vtMap);
                        }
                        map.put("vehicle", vMap);
                    }
                    if (r.getZone() != null) {
                        Map<String, Object> zMap = new HashMap<>();
                        zMap.put("id", r.getZone().getId());
                        zMap.put("zoneName", r.getZone().getZoneName());
                        if (r.getZone().getFloor() != null) {
                            zMap.put("floorId", r.getZone().getFloor().getId());
                        }
                        map.put("zone", zMap);
                    }
                    return map;
                })
                .toList());

        /*
         * =========================================================
         * PHẦN 5: DANH SÁCH CÁC CỔNG (GATES) - KÈM TRẠNG THÁI NHÂN VIÊN
         * =========================================================
         * Hành động: Quét bảng Gate.
         * Logic nghiệp vụ quan trọng: Simulator cần biết cổng nào đang mở và có bảo vệ trực.
         * - Gọi thêm `staffWorkSessionRepository` để dò xem có Ca làm việc nào đang "ACTIVE" tại cái cổng này không.
         * - Nếu có (`isPresent()`), gán cờ `hasStaff = true` và báo loại cổng (Vào hay Ra).
         * - Nếu không, gán `gateType = NONE` để vẽ cổng màu xám (tắt).
         */
        data.put("gates", gateRepository.findAll().stream().map(g -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", g.getId());
            map.put("gateName", g.getGateName());
            var activeSessionOpt = staffWorkSessionRepository.findByGateIdAndStatus(g.getId(), "ACTIVE");
            map.put("hasStaff", activeSessionOpt.isPresent());
            if (activeSessionOpt.isPresent()) {
                map.put("gateType", activeSessionOpt.get().getWorkGateType());
            } else {
                map.put("gateType", "NONE"); // Indicate inactive gate
            }
            if (g.getFloor() != null) {
                map.put("floorType", g.getFloor().getFloorType());
                map.put("floorId", g.getFloor().getId());
            }
            // Tọa độ + góc xoay trên sơ đồ — trước đây bị thiếu ở đây (khác với
            // khối dựng "zones" ngay bên dưới, vốn luôn có đủ 3 trường này), khiến
            // SimulatorMap.tsx (IoT Simulator) không có gì để đọc và mọi Gate bị vẽ
            // dồn về tọa độ mặc định (0,0), chồng lên nhau thay vì đúng vị trí thật
            // đã cấu hình trên Space Map.
            map.put("layoutX", g.getLayoutX());
            map.put("layoutY", g.getLayoutY());
            map.put("rotation", g.getRotation());
            return map;
        }).toList());

        /*
         * =========================================================
         * PHẦN 6: DANH SÁCH VÉ THÁNG (MONTHLY TICKETS)
         * =========================================================
         * Lấy danh sách biển số xe đã đăng ký vé tháng còn hiệu lực ("ACTIVE").
         * Bóc tách thêm Tên khách hàng (FullName) để nếu xe này vào bãi, Frontend hiện được chữ "Xin chào anh Nguyễn Văn A".
         */
        data.put("monthlyTickets", monthlyTicketRepository.findAll().stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", m.getId());
                    map.put("plate", m.getPlateNumber());
                    if (m.getUser() != null) {
                        map.put("customerName", m.getUser().getFullName());
                    }
                    map.put("validFrom", m.getValidFrom());
                    map.put("validUntil", m.getValidUntil());
                    map.put("status", m.getStatus());
                    if (m.getVehicleType() != null) {
                        Map<String, Object> vMap = new HashMap<>();
                        vMap.put("id", m.getVehicleType().getId());
                        vMap.put("typeName", m.getVehicleType().getTypeName());
                        map.put("vehicleType", vMap);
                    }
                    return map;
                }).toList());

        /*
         * =========================================================
         * PHẦN 7: DANH SÁCH PHÂN LOẠI XE (VEHICLE TYPES)
         * =========================================================
         * Quét bảng VehicleType. Nhặt ra thông số kích thước ô đỗ (matrixWidth, matrixHeight) 
         * để Simulator tính toán xem xe to (như xe khách) có nhét vừa ô đỗ nhỏ (xe máy) hay không.
         */
        data.put("vehicleTypes", vehicleTypeRepository.findAll().stream().map(v -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", v.getId());
            map.put("typeName", v.getTypeName());
            map.put("category", v.getCategory());
            map.put("matrixWidth", v.getMatrixWidth());
            map.put("matrixHeight", v.getMatrixHeight());
            return map;
        }).toList());

        /*
         * =========================================================
         * PHẦN 8: DANH SÁCH THẺ RFID ĐANG RẢNH (AVAILABLE CARDS)
         * =========================================================
         * Mục đích: Simulator cần 1 danh sách Dropdown chứa các thẻ nhựa chưa ai dùng 
         * để người test chọn và giả lập quẹt thẻ cho khách vãng lai.
         * Dùng hàm `.map(c -> c.getCardCode())` để biến một Object cồng kềnh thành 1 Mảng chuỗi (String Array) đơn giản.
         */
        data.put("availableCards", rfidCardRepository.findByStatus("AVAILABLE").stream()
                .map(c -> c.getCardCode())
                .toList());

        /*
         * =========================================================
         * PHẦN 9: DANH SÁCH TẦNG (FLOORS) VÀ KÍCH THƯỚC BẢN ĐỒ
         * =========================================================
         * Nhặt ra số lượng Cột (mapCols) và Hàng (mapRows).
         * Đây là số liệu cực kỳ quan trọng để Frontend vẽ một cái lưới Grid 2D (ví dụ 10x20 ô) để thả các Slot vào.
         */
        data.put("floors", floorRepository.findAll().stream()
                .filter(f -> !"DELETED".equals(f.getStatus()))
                .map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("floorName", f.getFloorName());
            map.put("floorType", f.getFloorType());
            map.put("mapCols", f.getMapCols());
            map.put("mapRows", f.getMapRows());
            return map;
        }).toList());

        /*
         * =========================================================
         * PHẦN 10: DANH SÁCH KHU VỰC (ZONES) VÀ TỌA ĐỘ BẢN ĐỒ
         * =========================================================
         * Nhặt ra Tọa độ X (layoutX), Tọa độ Y (layoutY) và Góc xoay (rotation).
         * Dữ liệu này dùng để Frontend đặt chính xác khu vực A, B, C vào đúng tọa độ trên lưới Grid 2D.
         */
        data.put("zones", zoneRepository.findAll().stream()
                .filter(z -> !"DELETED".equals(z.getStatus()))
                .map(z -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", z.getId());
                    map.put("zoneName", z.getZoneName());
                    if (z.getFloor() != null) {
                        map.put("floorId", z.getFloor().getId());
                    }
                    map.put("layoutX", z.getLayoutX());
                    map.put("layoutY", z.getLayoutY());
                    map.put("rotation", z.getRotation());
                    map.put("functionType", z.getFunctionType());
                    if (z.getVehicleType() != null) {
                        map.put("vehicleTypeId", z.getVehicleType().getId());
                    }
                    return map;
                }).toList());

        return ResponseEntity.ok(ApiResponse.success(data, "Synced"));
    }
}
