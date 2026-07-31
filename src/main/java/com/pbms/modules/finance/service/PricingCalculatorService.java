package com.pbms.modules.finance.service;

import com.pbms.modules.finance.domain.PricingBlock;
import com.pbms.modules.finance.domain.PricingPolicy;
import com.pbms.modules.finance.domain.PricingShift;
import com.pbms.modules.finance.dto.CalculationResultDTO;
import com.pbms.modules.finance.repository.PricingPolicyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PricingCalculatorService {

    private final PricingPolicyRepository policyRepository;

    public PricingCalculatorService(PricingPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /**
     * Tính toán tổng phí gửi xe dựa trên cấu hình (Policy) đang ở trạng thái ACTIVE của loại xe.
     * Sử dụng cho luồng Vận hành (Check-out thực tế).
     *
     * @param vehicleTypeId Mã loại xe (ví dụ: Xe máy, Ô tô).
     * @param checkInTime Thời gian bắt đầu đỗ xe.
     * @param checkOutTime Thời gian kết thúc đỗ xe.
     * @return Tổng số tiền phí gửi xe.
     */
    public BigDecimal calculateTotalFee(Long vehicleTypeId, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        PricingPolicy policy = policyRepository.findByVehicleTypeIdAndStatus(vehicleTypeId, "ACTIVE")
                .orElse(null);
        
        if (policy == null) {
            return BigDecimal.ZERO;
        }

        return calculateWithTrace(policy, checkInTime, checkOutTime).getFee();
    }

    public BigDecimal calculate(PricingPolicy policy, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        return calculateWithTrace(policy, checkInTime, checkOutTime).getFee();
    }

    /**
     * Hàm tính toán phí lõi, hỗ trợ trả về chi tiết (Trace Breakdown).
     * Thực hiện các bước: Bộ lọc Global Base -> Slicer (Cắt Ca) -> Slider (Trượt Block) -> Áp trần (Max Cap).
     * Giúp hiển thị minh bạch từng phép tính cấu thành nên tổng giá tiền cho người dùng xem.
     *
     * @param policy Đối tượng cấu hình chính sách giá.
     * @param checkInTime Thời gian bắt đầu.
     * @param checkOutTime Thời gian kết thúc.
     * @return Đối tượng CalculationResultDTO chứa tổng tiền và danh sách log chi tiết (Breakdown).
     */
    public CalculationResultDTO calculateWithTrace(PricingPolicy policy, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        if (checkOutTime.isBefore(checkInTime)) {
            checkOutTime = checkInTime;
        }

        long totalMinutes = Duration.between(checkInTime, checkOutTime).toMinutes();
        List<String> breakdown = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("#,###");

        // LỚP TIỀN XỬ LÝ: BỘ LỌC CƠ BẢN TOÀN CẢNH (GLOBAL BASE INTERCEPTOR)
        if (policy.getGlobalBaseMins() != null && policy.getGlobalBaseMins() > 0) {
            if (totalMinutes <= policy.getGlobalBaseMins()) {
                breakdown.add("[Pre-processing] Short parking (" + totalMinutes + "p <= " + policy.getGlobalBaseMins() + "p)");
                breakdown.add("-> Algorithm end: Calculate base price " + df.format(policy.getGlobalBaseFee()) + " VND");
                return CalculationResultDTO.builder().fee(policy.getGlobalBaseFee()).breakdown(breakdown).build();
            } else {
                breakdown.add("[Pre-processing] Exceeded base price (" + totalMinutes + "p > " + policy.getGlobalBaseMins() + "p) -> Skip base price, move to shift slicer.");
            }
        }

        // BƯỚC 1: MÁY CẮT THEO CA (Helper_SliceByShift)
        List<ShiftSlice> slices = sliceByShift(policy.getShifts(), checkInTime, checkOutTime);

        // BƯỚC 2: CỖ MÁY TRƯỢT BLOCK (Helper_SlideBlocks)
        BigDecimal totalFee = BigDecimal.ZERO;
        for (int i = 0; i < slices.size(); i++) {
            ShiftSlice slice = slices.get(i);
            breakdown.add("--- Slice " + (i + 1) + ": " + slice.shift.getShiftName() + " (Duration " + slice.durationMins + " minutes) ---");

            BigDecimal sliceFee = slideBlocksWithTrace(slice.shift, slice.durationMins, breakdown, df);
            totalFee = totalFee.add(sliceFee);
        }



        breakdown.add("FINAL RESULT => TOTAL FEE: " + df.format(totalFee) + " VND");

        return CalculationResultDTO.builder()
                .fee(totalFee)
                .breakdown(breakdown)
                .build();
    }

    /**
     * Thuật toán chẻ thời gian (Slicer): Cắt toàn bộ khoảng thời gian đậu xe thành các đoạn nhỏ (Slices).
     * Mỗi đoạn sẽ tương ứng với một Ca (Shift) cụ thể trong chính sách giá.
     * 
     * @param shifts Danh sách các Ca (Shifts) đã cấu hình.
     * @param checkIn Thời gian bắt đầu đoạn đậu xe.
     * @param checkOut Thời gian kết thúc đoạn đậu xe.
     * @return Danh sách các đoạn nhỏ (ShiftSlice) kèm theo số phút tương ứng.
     */
    private List<ShiftSlice> sliceByShift(List<PricingShift> shifts, LocalDateTime checkIn, LocalDateTime checkOut) {
        List<ShiftSlice> slices = new ArrayList<>();
        LocalDateTime current = checkIn;

        while (current.isBefore(checkOut)) {
            PricingShift currentShift = findShiftForTime(shifts, current.toLocalTime());
            if (currentShift == null) {
                current = current.plusMinutes(60);
                continue;
            }

            LocalDateTime shiftEnd = LocalDateTime.of(current.toLocalDate(), currentShift.getEndTime());
            if (currentShift.getEndTime().isBefore(currentShift.getStartTime())) {
                if (current.toLocalTime().isBefore(currentShift.getEndTime())) {
                } else {
                    shiftEnd = shiftEnd.plusDays(1);
                }
            }

            LocalDateTime sliceEnd = checkOut.isBefore(shiftEnd) ? checkOut : shiftEnd;

            long durationMins = Duration.between(current, sliceEnd).toMinutes();
            if (durationMins > 0) {
                if (!slices.isEmpty() && slices.get(slices.size() - 1).shift.getId() != null && currentShift.getId() != null && slices.get(slices.size() - 1).shift.getId().equals(currentShift.getId())) {
                    slices.get(slices.size() - 1).durationMins += (int) durationMins;
                } else {
                    slices.add(new ShiftSlice(currentShift, (int) durationMins));
                }
            }

            current = sliceEnd;
        }

        return slices;
    }

    private PricingShift findShiftForTime(List<PricingShift> shifts, LocalTime time) {
        for (PricingShift shift : shifts) {
            LocalTime s = shift.getStartTime();
            LocalTime e = shift.getEndTime();
            if (s.isBefore(e)) {
                if (!time.isBefore(s) && time.isBefore(e)) {
                    return shift;
                }
            } else {
                if (!time.isBefore(s) || time.isBefore(e)) {
                    return shift;
                }
            }
        }
        return null;
    }

    /**
     * Cỗ máy trượt Block (Slider): Tiêu thụ dần thời lượng đậu xe trong một Ca theo từng khối thời gian (Block).
     * Nếu thời gian lớn hơn tổng các block, sẽ lặp lại block cuối cùng (Latch Class).
     *
     * @param shift Ca (Shift) hiện tại đang áp dụng.
     * @param durationMins Số phút cần tính tiền.
     * @param breakdown Danh sách log (dùng để ghi lại chi tiết từng bước tính).
     * @param df Bộ định dạng tiền tệ.
     * @return Tổng số tiền của Ca (Shift) này.
     */
    private BigDecimal slideBlocksWithTrace(PricingShift shift, int durationMins, List<String> breakdown, DecimalFormat df) {
        BigDecimal fee = BigDecimal.ZERO;
        int remainingMins = durationMins;

        List<PricingBlock> blocks = shift.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return fee;
        }

        int blockIndex = 0;
        while (remainingMins > 0) {
            PricingBlock block;
            String blockName = "Layer " + (blockIndex + 1);

            if (blockIndex < blocks.size()) {
                block = blocks.get(blockIndex);
                if (blockIndex == blocks.size() - 1) {
                    blockName = "Latch Class";
                }
                blockIndex++;
            } else {
                block = blocks.get(blocks.size() - 1);
                blockName = "Latch Class";
            }

            fee = fee.add(block.getFee());

            int blockDuration = block.getDurationMins();

            if (remainingMins <= blockDuration) {
                breakdown.add("[Sliding] " + blockName + ": +" + df.format(block.getFee()) + "VND (Consumed " + remainingMins + "p, End of slice)");
                remainingMins -= blockDuration;
                break;
            } else {
                breakdown.add("[Sliding] " + blockName + ": +" + df.format(block.getFee()) + "VND (Fully consumed " + blockDuration + "p)");
                remainingMins -= blockDuration;
            }
        }

        return fee;
    }

    private static class ShiftSlice {
        PricingShift shift;
        int durationMins;

        public ShiftSlice(PricingShift shift, int durationMins) {
            this.shift = shift;
            this.durationMins = durationMins;
        }
    }
}

