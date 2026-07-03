package com.pbms.modules.infrastructure.service;

import com.pbms.modules.infrastructure.domain.RfidCard;
import com.pbms.modules.infrastructure.dto.RfidCardDTO;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RfidCardService {

    private final RfidCardRepository rfidCardRepository;

    public List<RfidCardDTO> getAllCards() {
        return rfidCardRepository.findAll().stream()
                .map(card -> RfidCardDTO.builder()
                        .uid(card.getCardCode())
                        .visualId("CARD-VL-" + String.format("%03d", card.getId()))
                        .status(card.getStatus())
                        .location(card.getStatus().equals("IN_USE") ? "In Session" : (card.getStatus().equals("AVAILABLE") ? "Gate Staff" : "Unknown"))
                        .build())
                .collect(Collectors.toList());
    }

    public void updateStatus(String uid, String newStatus) {
        RfidCard card = rfidCardRepository.findByCardCode(uid)
                .orElseThrow(() -> new RuntimeException("RFID Card not found: " + uid));
        card.setStatus(newStatus);
        rfidCardRepository.save(card);
    }

    public int importCardsFromCsv(MultipartFile file) {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("cardCode")) continue; // skip header or empty line
                String[] columns = line.split(",");
                if (columns.length > 0) {
                    String cardCode = columns[0].trim();
                    if (!cardCode.isEmpty() && rfidCardRepository.findByCardCode(cardCode).isEmpty()) {
                        RfidCard card = new RfidCard();
                        card.setCardCode(cardCode);
                        card.setStatus(columns.length > 1 ? columns[1].trim() : "AVAILABLE");
                        card.setAssignedPlate(columns.length > 2 ? columns[2].trim() : null);
                        rfidCardRepository.save(card);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing CSV file", e);
        }
        return count;
    }
}

