/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-09
 * @Description: Service for managing parking zone routing rules and suggestion chains.
 * @Dependencies: RoutingRuleRepository, ZoneRepository
 */
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

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * @Function: getRoutingRulesByVehicleTypeAndFloor
     * @Description: Trả về danh sách chuỗi điều hướng (theo từng khung giờ) đang cấu hình cho 1 loại xe trên 1 tầng, để manager xem lại trên màn hình Routing.
     * @Logic_Steps:
     * 1. Lọc danh sách zone có loại xe tương ứng và function type là WALK_IN.
     * 2. Lấy danh sách active rules hiện tại.
     * 3. Nếu chưa có rule nào (lần đầu cấu hình), tự dựng 1 chuỗi mặc định (ngưỡng đầy 90%, không sắp thứ tự ưu tiên) từ danh sách zone hiện có để FE luôn có dữ liệu.
     * 4. Gom các rule có cùng khung giờ (hoặc cùng là rule mặc định) lại thành 1 nhóm.
     * 5. Dựng lại chuỗi điều hướng (chain) cho từng nhóm.
     * 6. Sắp xếp để khung giờ cụ thể hiển thị trước, rule mặc định luôn xếp cuối cùng.
     */
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

    /**
     * @Function: buildChain
     * @Description: Dựng lại thứ tự chuỗi điều hướng từ quan hệ suggestedZone (zone này -> gợi ý sang zone kia) của 1 nhóm rule cùng khung giờ.
     * @Logic_Steps:
     * 1. Tìm "zone gốc" (zone không bị zone nào khác trỏ tới) để bắt đầu chuỗi.
     * 2. Lặp qua suggestedZone cho tới khi hết chuỗi.
     * 3. Kiểm tra các zone thuộc loại xe này mà chưa được đưa vào chuỗi (do thêm mới) để append vào cuối danh sách.
     */
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

    /**
     * @Function: updateRoutingRules
     * @Description: Ghi đè toàn bộ cấu hình điều hướng của 1 loại xe (trên 1 tầng) bằng bộ chuỗi mới.
     * @Logic_Steps:
     * 1. Tìm các active rules hiện tại và đánh dấu isActive = false để giữ lịch sử thay vì xoá cứng.
     * 2. Lặp qua các timeframes từ request để tạo lại các rule mới.
     * 3. Gắn liên kết suggestedZone theo đúng thứ tự list từ FE truyền xuống.
     * 4. Lưu toàn bộ rule mới và gọi lại hàm getRoutingRulesByVehicleTypeAndFloor để trả về dữ liệu mới nhất.
     */
    @Transactional
    public List<RoutingRuleDTO> updateRoutingRules(RoutingRuleDTO.BatchUpdateRequest request) {
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() 
                          && r.getZone().getVehicleType().getTypeName().equalsIgnoreCase(request.getVehicleTypeName())
                          && (request.getFloorId() == null || r.getZone().getFloor().getId().equals(request.getFloorId())))
                .collect(Collectors.toList());

        // Ghi lại giá trị CŨ (trước khi ghi đè) vào AuditContext - đọc lại
        // cấu hình hiện tại rồi quy về đúng shape BatchUpdateRequest (giống
        // payload FE gửi lên) trước khi serialize.
        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null) {
                List<RoutingRuleDTO> oldDtos = getRoutingRulesByVehicleTypeAndFloor(request.getVehicleTypeName(), request.getFloorId());

                RoutingRuleDTO.BatchUpdateRequest oldRequestFormat = new RoutingRuleDTO.BatchUpdateRequest();
                oldRequestFormat.setVehicleTypeName(request.getVehicleTypeName());
                oldRequestFormat.setFloorId(request.getFloorId());

                List<RoutingRuleDTO.TimeFrameConfig> oldTimeFrames = new ArrayList<>();
                for (RoutingRuleDTO dto : oldDtos) {
                    RoutingRuleDTO.TimeFrameConfig tf = new RoutingRuleDTO.TimeFrameConfig();
                    tf.setStartTime(dto.getStartTime());
                    tf.setEndTime(dto.getEndTime());
                    tf.setIsDefault(dto.getIsDefault());

                    List<RoutingRuleDTO.RuleItem> oldRules = new ArrayList<>();
                    if (dto.getRules() != null) {
                        for (RoutingRuleDTO.RuleItemDTO rDto : dto.getRules()) {
                            RoutingRuleDTO.RuleItem item = new RoutingRuleDTO.RuleItem();
                            item.setZoneId(rDto.getZoneId());
                            item.setFillThresholdPct(rDto.getFillThresholdPct());
                            oldRules.add(item);
                        }
                    }
                    tf.setRules(oldRules);
                    oldTimeFrames.add(tf);
                }
                oldRequestFormat.setTimeFrames(oldTimeFrames);

                context.setOldValue(objectMapper.writeValueAsString(oldRequestFormat));
            }
        } catch (Exception e) {
        }
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
