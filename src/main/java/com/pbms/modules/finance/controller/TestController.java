package com.pbms.modules.finance.controller;

import com.pbms.modules.finance.service.RevenueService;
import com.pbms.modules.finance.dto.RevenueRecordDTO;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/test-revenue")
@RequiredArgsConstructor
public class TestController {
    private final RevenueService revenueService;
    
    @GetMapping
    public List<RevenueRecordDTO> test(@RequestParam String start, @RequestParam String end) {
        return revenueService.getRevenueDashboardData(LocalDate.parse(start), LocalDate.parse(end));
    }
}
