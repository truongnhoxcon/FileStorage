package com.example.FileStorage.controller;

import com.example.FileStorage.entity.ActivityLog;
import com.example.FileStorage.service.ActivityLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public List<ActivityLog> getAllLogs() {
        return activityLogService.getAllLogs();
    }

    @GetMapping("/user/{userId}")
    public List<ActivityLog> getLogsByUser(@PathVariable Long userId) {
        return activityLogService.getLogsByUser(userId);
    }

    @PostMapping
    public ActivityLog createLog(@RequestBody ActivityLog log) {
        return activityLogService.saveLog(log);
    }
}

