package com.pbms.modules.operation.service;

import com.pbms.modules.operation.dto.CameraScanDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IotService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastCameraScan(CameraScanDTO payload) {
        String destination = "/user/queue/gates/" + payload.getGateId() + "/scans";
        messagingTemplate.convertAndSend(destination, payload);
    }
}

