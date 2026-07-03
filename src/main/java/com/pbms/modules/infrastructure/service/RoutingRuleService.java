package com.pbms.modules.infrastructure.service;

import com.pbms.modules.infrastructure.domain.RoutingRule;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.dto.RoutingRuleDTO;
import com.pbms.modules.infrastructure.repository.RoutingRuleRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingRuleService {

    private final RoutingRuleRepository routingRuleRepository;
    private final ZoneRepository zoneRepository;

    public List<RoutingRuleDTO> getRoutingRulesByVehicleTypeAndFloor(String vehicleTypeName, Long floorId) {
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType().getTypeName().equalsIgnoreCase(vehicleTypeName) 
                          && (floorId == null || z.getFloor().getId().equals(floorId))
                          && "ACTIVE".equals(z.getStatus()) 
                          && "WALK_IN".equalsIgnoreCase(z.getFunctionType()))
                .collect(Collectors.toList());

        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() 
                          && r.getZone().getVehicleType().getTypeName().equalsIgnoreCase(vehicleTypeName) 
                          && (floorId == null || r.getZone().getFloor().getId().equals(floorId))
                          && "WALK_IN".equalsIgnoreCase(r.getZone().getFunctionType()))
                .collect(Collectors.toList());

        if (activeRules.isEmpty()) {
            List<RoutingRuleDTO.RuleItemDTO> chain = new ArrayList<>();
            for (Zone z : zones) {
                chain.add(RoutingRuleDTO.RuleItemDTO.builder()
                        .zoneId(z.getId())
                        .zoneName(z.getZoneName())
                        .fillThresholdPct(90)
                        .build());
            }
            RoutingRuleDTO dto = RoutingRuleDTO.builder()
                    .timeFrameId("tf_" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Other price brackets")
                    .startTime(null)
                    .endTime(null)
                    .isDefault(true)
                    .rules(chain)
                    .build();
            return List.of(dto);
        }

        // Group rules by their time characteristics (startTime + endTime + isDefault)
        Map<String, List<RoutingRule>> groupedRules = new HashMap<>();
        for (RoutingRule rule : activeRules) {
            String key = rule.getIsDefault() ? "DEFAULT" : (rule.getStartTime() + "-" + rule.getEndTime());
            groupedRules.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        }

        List<RoutingRuleDTO> result = new ArrayList<>();
        
        for (Map.Entry<String, List<RoutingRule>> entry : groupedRules.entrySet()) {
            List<RoutingRule> group = entry.getValue();
            RoutingRule firstRule = group.get(0);
            
            RoutingRuleDTO dto = RoutingRuleDTO.builder()
                    .timeFrameId("tf_" + UUID.randomUUID().toString().substring(0, 8))
                    .name(firstRule.getIsDefault() ? "Default rules" : ("Timeframe " + firstRule.getStartTime() + " - " + firstRule.getEndTime()))
                    .startTime(firstRule.getStartTime() != null ? firstRule.getStartTime().toString() : null)
                    .endTime(firstRule.getEndTime() != null ? firstRule.getEndTime().toString() : null)
                    .isDefault(firstRule.getIsDefault())
                    .rules(buildChain(group, zones))
                    .build();
            result.add(dto);
        }

        // Sort: specific timeframes first, default last
        result.sort((a, b) -> {
            if (a.getIsDefault() && !b.getIsDefault()) return 1;
            if (!a.getIsDefault() && b.getIsDefault()) return -1;
            if (a.getStartTime() != null && b.getStartTime() != null) {
                return a.getStartTime().compareTo(b.getStartTime());
            }
            return 0;
        });

        return result;
    }

    private List<RoutingRuleDTO.RuleItemDTO> buildChain(List<RoutingRule> rules, List<Zone> zones) {
        Map<Long, RoutingRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(r -> r.getZone().getId(), r -> r, (a, b) -> a));

        List<RoutingRuleDTO.RuleItemDTO> chain = new ArrayList<>();
        RoutingRule currentRule = null;
        
        for (RoutingRule rule : rules) {
            boolean isSuggestedByOther = rules.stream()
                    .anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
            if (!isSuggestedByOther) {
                currentRule = rule;
                break;
            }
        }
        
        while (currentRule != null) {
            chain.add(RoutingRuleDTO.RuleItemDTO.builder()
                    .id(currentRule.getId())
                    .zoneId(currentRule.getZone().getId())
                    .zoneName(currentRule.getZone().getZoneName())
                    .fillThresholdPct(currentRule.getFillThresholdPct())
                    .suggestedZoneId(currentRule.getSuggestedZone() != null ? currentRule.getSuggestedZone().getId() : null)
                    .suggestedZoneName(currentRule.getSuggestedZone() != null ? currentRule.getSuggestedZone().getZoneName() : null)
                    .build());
                    
            if (currentRule.getSuggestedZone() != null) {
                currentRule = ruleMap.get(currentRule.getSuggestedZone().getId());
            } else {
                currentRule = null;
            }
        }

        // Append any remaining active zones not in the chain
        for (Zone z : zones) {
            if (chain.stream().noneMatch(dto -> dto.getZoneId().equals(z.getId()))) {
                chain.add(RoutingRuleDTO.RuleItemDTO.builder()
                        .zoneId(z.getId())
                        .zoneName(z.getZoneName())
                        .fillThresholdPct(90)
                        .build());
            }
        }
        return chain;
    }

    @Transactional
    public List<RoutingRuleDTO> updateRoutingRules(RoutingRuleDTO.BatchUpdateRequest request) {
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() 
                          && r.getZone().getVehicleType().getTypeName().equalsIgnoreCase(request.getVehicleTypeName())
                          && (request.getFloorId() == null || r.getZone().getFloor().getId().equals(request.getFloorId())))
                .collect(Collectors.toList());
        activeRules.forEach(r -> r.setIsActive(false));
        routingRuleRepository.saveAll(activeRules);

        List<RoutingRule> newRules = new ArrayList<>();
        
        if (request.getTimeFrames() != null) {
            for (RoutingRuleDTO.TimeFrameConfig tf : request.getTimeFrames()) {
                LocalTime startTime = (tf.getStartTime() != null && !tf.getStartTime().isEmpty()) ? LocalTime.parse(tf.getStartTime()) : null;
                LocalTime endTime = (tf.getEndTime() != null && !tf.getEndTime().isEmpty()) ? LocalTime.parse(tf.getEndTime()) : null;
                Boolean isDefault = tf.getIsDefault() != null ? tf.getIsDefault() : false;
                
                List<RoutingRuleDTO.RuleItem> items = tf.getRules();
                if (items == null) continue;
                
                for (int i = 0; i < items.size(); i++) {
                    RoutingRuleDTO.RuleItem item = items.get(i);
                    Zone zone = zoneRepository.findById(item.getZoneId())
                            .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + item.getZoneId()));
                    
                    Zone suggestedZone = null;
                    if (i < items.size() - 1) {
                        Long nextZoneId = items.get(i + 1).getZoneId();
                        suggestedZone = zoneRepository.findById(nextZoneId)
                                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + nextZoneId));
                    }

                    RoutingRule newRule = RoutingRule.builder()
                            .zone(zone)
                            .ruleName("Rule for " + zone.getZoneName())
                            .fillThresholdPct(item.getFillThresholdPct() != null ? item.getFillThresholdPct() : 90)
                            .suggestedZone(suggestedZone)
                            .startTime(startTime)
                            .endTime(endTime)
                            .isDefault(isDefault)
                            .isActive(true)
                            .build();
                    
                    newRules.add(newRule);
                }
            }
        }

        routingRuleRepository.saveAll(newRules);
        return getRoutingRulesByVehicleTypeAndFloor(request.getVehicleTypeName(), request.getFloorId());
    }
}

