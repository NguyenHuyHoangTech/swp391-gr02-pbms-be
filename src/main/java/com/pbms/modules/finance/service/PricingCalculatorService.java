// Author: Võ Trung Hiếu
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

    public BigDecimal calculateParkingFee(Long vehicleTypeId, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
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

