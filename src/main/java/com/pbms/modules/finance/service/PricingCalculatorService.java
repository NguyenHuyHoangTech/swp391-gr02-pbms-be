package com.pbms.modules.finance.service;

import com.pbms.modules.finance.domain.PricingBlock;
import com.pbms.modules.finance.domain.PricingPolicy;
import com.pbms.modules.finance.domain.PricingShift;
import com.pbms.modules.finance.repository.PricingPolicyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PricingCalculatorService chịu trách nhiệm tính toán số tiền phải trả (parking fee).
 * Thuật toán tính tiền dựa trên 3 yếu tố:
 * 1. Global Base Mins: Số phút đỗ miễn phí hoặc có giá sàn (ví dụ 15 phút đầu miễn phí).
 * 2. Shift (Khung giờ): Mỗi loại xe có thể có giá khác nhau theo thời điểm trong ngày (Vd: Ban ngày, Ban đêm).
 * 3. Block (Khối thời gian): Trong mỗi khung giờ, giá được tính lũy tiến theo từng block (vd: block 60 phút, block 30 phút).
 */
@Service
public class PricingCalculatorService {

    private final PricingPolicyRepository policyRepository;

    public PricingCalculatorService(PricingPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /**
     * Hàm tính tiền công khai được gọi bởi hệ thống khi xe ra (Checkout).
     * Sẽ lấy chính sách giá đang ACTIVE của loại xe tương ứng để tiến hành tính toán.
     */
    public BigDecimal calculateTotalFee(Long vehicleTypeId, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        PricingPolicy policy = policyRepository.findByVehicleTypeIdAndStatus(vehicleTypeId, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("No active pricing policy for vehicle type: " + vehicleTypeId));

        return calculate(policy, checkInTime, checkOutTime);
    }

    /**
     * Thuật toán tính tiền cốt lõi. Gồm 3 bước:
     * 1. Lọc qua Global Base (Số phút đỗ miễn phí / giá sàn).
     * 2. BƯỚC 1: Cắt thời gian đỗ thành các lát (slices) vừa khít với các Ca (Shifts) cấu hình.
     * 3. BƯỚC 2: Trượt qua từng Block trong ca đó để cộng tiền.
     * 4. BƯỚC 3: Áp dụng giá trần (Max Cap) nếu tổng tiền vượt quá giới hạn.
     */
    public BigDecimal calculate(PricingPolicy policy, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        if (checkOutTime.isBefore(checkInTime)) {
            // Prevent exception if OS time goes backwards during testing
            checkOutTime = checkInTime;
        }

        long totalMinutes = Duration.between(checkInTime, checkOutTime).toMinutes();

        // Lá»šP TIá»€N Xá»¬ LÃ: Bá»˜ Lá»ŒC CÆ  Báº¢N TOÃ€N Cáº¢NH (GLOBAL BASE INTERCEPTOR)
        if (totalMinutes <= policy.getGlobalBaseMins()) {
            return policy.getGlobalBaseFee();
        }

        // BÆ¯á»šC 1: MÃY Cáº®T THEO CA (Helper_SliceByShift)
        List<ShiftSlice> slices = sliceByShift(policy.getShifts(), checkInTime, checkOutTime);

        // BÆ¯á»šC 2: Cá»– MÃY TRÆ¯á»¢T BLOCK (Helper_SlideBlocks)
        BigDecimal totalFee = BigDecimal.ZERO;
        for (ShiftSlice slice : slices) {
            BigDecimal sliceFee = slideBlocks(slice.shift, slice.durationMins);
            totalFee = totalFee.add(sliceFee);
        }

        // BÆ¯á»šC 3: Tá»”NG Há»¢P VÃ€ ÃP TRáº¦N (Main_CalculateTotalFee)
        if (totalFee.compareTo(policy.getMaxParkingCap()) > 0) {
            return policy.getMaxParkingCap();
        }

        return totalFee;
    }

    /**
     * Hàm phụ trợ BƯỚC 1: Chia khoảng thời gian đỗ xe thực tế thành các "lát cắt" (slices)
     * tương ứng với từng khung giờ cấu hình (Shift). 
     * Rất quan trọng để xử lý việc xe đỗ xuyên qua nhiều ca khác nhau hoặc đỗ vắt qua đêm (qua 00:00).
     */
    private List<ShiftSlice> sliceByShift(List<PricingShift> shifts, LocalDateTime checkIn, LocalDateTime checkOut) {
        List<ShiftSlice> slices = new ArrayList<>();
        LocalDateTime current = checkIn;

        while (current.isBefore(checkOut)) {
            PricingShift currentShift = findShiftForTime(shifts, current.toLocalTime());
            if (currentShift == null) {
                // Náº¿u khÃ´ng tÃ¬m tháº¥y ca nÃ o (Cáº¥u hÃ¬nh há»•ng), tÃ­nh theo giá» máº·c Ä‘á»‹nh hoáº·c bá» qua
                current = current.plusMinutes(60);
                continue;
            }

            // TÃ­nh thá»i Ä‘iá»ƒm káº¿t thÃºc cá»§a Ca nÃ y trong ngÃ y hiá»‡n táº¡i
            LocalDateTime shiftEnd = LocalDateTime.of(current.toLocalDate(), currentShift.getEndTime());
            if (currentShift.getEndTime().isBefore(currentShift.getStartTime())) {
                // Ca váº¯t qua Ä‘Ãªm (vÃ­ dá»¥: 18:00 - 06:00)
                if (current.toLocalTime().isBefore(currentShift.getEndTime())) {
                    // Äang á»Ÿ ráº¡ng sÃ¡ng (sau ná»­a Ä‘Ãªm)
                } else {
                    // Äang á»Ÿ buá»•i tá»‘i (trÆ°á»›c ná»­a Ä‘Ãªm), káº¿t thÃºc ca lÃ  sÃ¡ng hÃ´m sau
                    shiftEnd = shiftEnd.plusDays(1);
                }
            }

            // Thá»i Ä‘iá»ƒm káº¿t thÃºc cá»§a lÃ¡t cáº¯t lÃ  min(thá»i Ä‘iá»ƒm ra, thá»i Ä‘iá»ƒm káº¿t thÃºc ca)
            LocalDateTime sliceEnd = checkOut.isBefore(shiftEnd) ? checkOut : shiftEnd;

            long durationMins = Duration.between(current, sliceEnd).toMinutes();
            if (durationMins > 0) {
                slices.add(new ShiftSlice(currentShift, (int) durationMins));
            }

            current = sliceEnd;
        }

        return slices;
    }

    /**
     * Hàm phụ trợ để tìm xem một thời điểm cụ thể (LocalTime) đang nằm trọn trong ca (Shift) nào.
     * Hỗ trợ tìm kiếm cả cho ca vắt qua đêm (ví dụ Ca đêm: 18:00 - 06:00).
     */
    private PricingShift findShiftForTime(List<PricingShift> shifts, LocalTime time) {
        for (PricingShift shift : shifts) {
            LocalTime s = shift.getStartTime();
            LocalTime e = shift.getEndTime();
            if (s.isBefore(e)) {
                if (!time.isBefore(s) && time.isBefore(e)) {
                    return shift;
                }
            } else { // Váº¯t qua Ä‘Ãªm
                if (!time.isBefore(s) || time.isBefore(e)) {
                    return shift;
                }
            }
        }
        return null;
    }

    /**
     * Hàm phụ trợ BƯỚC 2: Trượt qua các khối thời gian (Blocks) trong một ca để tính tiền.
     * Ví dụ ca Ngày có: Block 1 (60p) = 10k, Block 2 (30p) = 5k.
     * Nếu đỗ quá thời gian tổng của các block, block cuối cùng sẽ được lặp lại liên tục.
     */
    private BigDecimal slideBlocks(PricingShift shift, int durationMins) {
        BigDecimal fee = BigDecimal.ZERO;
        int remainingMins = durationMins;

        List<PricingBlock> blocks = shift.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return fee;
        }

        int blockIndex = 0;
        while (remainingMins > 0) {
            PricingBlock block;
            if (blockIndex < blocks.size()) {
                block = blocks.get(blockIndex);
                blockIndex++;
            } else {
                // Láº·p láº¡i block cuá»‘i cÃ¹ng náº¿u thá»i gian Ä‘á»— vÆ°á»£t quÃ¡ tá»•ng thá»i gian cáº¥u hÃ¬nh cá»§a cÃ¡c block
                block = blocks.get(blocks.size() - 1);
            }
            fee = fee.add(block.getFee());
            remainingMins -= block.getDurationMins();
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

