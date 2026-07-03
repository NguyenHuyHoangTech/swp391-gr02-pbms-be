package com.pbms.common.utils;

import java.time.Duration;
import java.time.LocalDateTime;

public class TimeProvider {

    private static Duration simulatedOffset = Duration.ZERO;

    /**
     * Gets the current date-time. If an offset is configured,
     * it adds the offset to the actual current date-time.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now().plus(simulatedOffset);
    }

    /**
     * Fast-forwards the time to a specific target time in the future.
     * Cannot go backwards.
     */
    public static void fastForwardTo(LocalDateTime targetTime) {
        LocalDateTime actualNow = LocalDateTime.now();
        if (targetTime.isBefore(now())) {
            throw new IllegalArgumentException("Cannot travel back in time. Target time must be after the current simulated time.");
        }
        simulatedOffset = Duration.between(actualNow, targetTime);
    }

    /**
     * Resets the simulated time to the actual system time.
     */
    public static void reset() {
        simulatedOffset = Duration.ZERO;
    }

    public static Duration getSimulatedOffset() {
        return simulatedOffset;
    }

    public static void setSimulatedOffset(Duration offset) {
        simulatedOffset = offset;
    }
}

