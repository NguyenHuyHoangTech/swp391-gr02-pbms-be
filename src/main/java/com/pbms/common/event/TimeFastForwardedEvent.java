package com.pbms.common.event;

import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

public class TimeFastForwardedEvent extends ApplicationEvent {
    
    private final LocalDateTime newSimulatedTime;
    private final LocalDateTime oldSimulatedTime;

    public TimeFastForwardedEvent(Object source, LocalDateTime oldSimulatedTime, LocalDateTime newSimulatedTime) {
        super(source);
        this.oldSimulatedTime = oldSimulatedTime;
        this.newSimulatedTime = newSimulatedTime;
    }

    public LocalDateTime getNewSimulatedTime() {
        return newSimulatedTime;
    }

    public LocalDateTime getOldSimulatedTime() {
        return oldSimulatedTime;
    }
}

