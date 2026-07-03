package com.pbms.modules.incident.service;

import com.pbms.modules.incident.domain.IncidentTicket;
import com.pbms.modules.incident.dto.CreateIncidentRequestDTO;
import com.pbms.modules.incident.dto.IncidentTicketDTO;
import com.pbms.modules.incident.repository.IncidentTicketRepository;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.infrastructure.domain.RfidCard;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentTicketService {

    private final IncidentTicketRepository incidentTicketRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final RfidCardRepository rfidCardRepository;

    public List<IncidentTicketDTO> getAllTickets() {
        return incidentTicketRepository.findAllByOrderByIdDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public IncidentTicketDTO createIncident(CreateIncidentRequestDTO request, String docUrl, String cardUrl) {
        IncidentTicket ticket = new IncidentTicket();
        ticket.setIssueType(request.getType());
        ticket.setDescription(request.getDescription());
        ticket.setFineAmount(request.getBaseFee());
        ticket.setPriority("MEDIUM");
        ticket.setStatus("PENDING");
        ticket.setUploadedDocUrl(docUrl);
        ticket.setUploadedCardUrl(cardUrl);
        
        // Try to find an active session if plate or rfid is provided
        if (request.getPlate() != null && !request.getPlate().isEmpty()) {
            Optional<ParkingSession> session = parkingSessionRepository.findByPlateAndStatus(request.getPlate(), "ACTIVE");
            session.ifPresent(ticket::setSession);
        } else if (request.getRfid() != null && !request.getRfid().isEmpty()) {
            Optional<ParkingSession> session = parkingSessionRepository.findByRfidCard_CardCodeAndStatus(request.getRfid(), "ACTIVE");
            session.ifPresent(ticket::setSession);
        }

        IncidentTicket saved = incidentTicketRepository.save(ticket);
        return mapToDTO(saved);
    }

    @Transactional
    public IncidentTicketDTO updateToPhase2(Long id) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        
        ticket.setStatus("WAITING_CHECKOUT");
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    @Transactional
    public IncidentTicketDTO resolveTicket(Long id, String docUrl, String cardUrl) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        
        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        if (docUrl != null) ticket.setUploadedDocUrl(docUrl);
        if (cardUrl != null) ticket.setUploadedCardUrl(cardUrl);
        
        // If lost or damaged card, update the RfidCard status
        if (ticket.getSession() != null) {
            RfidCard card = ticket.getSession().getRfidCard();
            if (card != null) {
                if ("LOST_CARD".equals(ticket.getIssueType())) {
                    card.setStatus("LOST");
                    rfidCardRepository.save(card);
                } else if ("DAMAGED_CARD".equals(ticket.getIssueType())) {
                    card.setStatus("DAMAGED");
                    rfidCardRepository.save(card);
                }
            }
        }
        
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    @Transactional
    public IncidentTicketDTO rejectTicket(Long id, String reason) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        
        ticket.setStatus("REJECTED");
        ticket.setResolutionNotes(reason);
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    private IncidentTicketDTO mapToDTO(IncidentTicket ticket) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String timeStr = ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(formatter) : com.pbms.common.utils.TimeProvider.now().format(formatter);
        
        String plate = "";
        String rfid = "";
        if (ticket.getSession() != null) {
            plate = ticket.getSession().getPlate();
            if (ticket.getSession().getRfidCard() != null) {
                rfid = ticket.getSession().getRfidCard().getCardCode();
            }
        }
        
        int phase = 1;
        if ("WAITING_CHECKOUT".equals(ticket.getStatus())) phase = 2;
        if ("RESOLVED".equals(ticket.getStatus()) || "REJECTED".equals(ticket.getStatus())) phase = 3;

        return IncidentTicketDTO.builder()
                .id(ticket.getId())
                .type(ticket.getIssueType())
                .phase(phase)
                .plate(plate)
                .rfid(rfid)
                .time(timeStr)
                .status(ticket.getStatus())
                .uploadedDocUrl(ticket.getUploadedDocUrl())
                .uploadedCardUrl(ticket.getUploadedCardUrl())
                .baseFee(ticket.getFineAmount())
                .description(ticket.getDescription())
                .build();
    }
}

