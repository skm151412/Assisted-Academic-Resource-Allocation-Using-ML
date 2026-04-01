package com.college.ara.controller;

import com.college.ara.model.UsageLog;
import com.college.ara.service.UsageLogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final UsageLogService usageLogService;

    public AnalyticsController(UsageLogService usageLogService) {
        this.usageLogService = usageLogService;
    }

    @GetMapping("/usage-logs")
    public List<UsageLog> listUsageLogs() {
        return usageLogService.listUsageLogs();
    }
}
