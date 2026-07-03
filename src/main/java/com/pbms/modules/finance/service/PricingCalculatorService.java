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

@Service
public class PricingCalculatorService {

    private final PricingPolicyRepository policyRepository;

    public PricingCalculatorService(PricingPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public BigDecimal calculateTotalFee(Long vehicleTypeId, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        PricingPolicy policy = policyRepository.findByVehicleTypeIdAndStatus(vehicleTypeId, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("No active pricing policy for vehicle type: " + vehicleTypeId));

        return calculate(policy, checkInTime, checkOutTime);
    }

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

