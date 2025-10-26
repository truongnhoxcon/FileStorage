package com.example.FileStorage.service;

import com.example.FileStorage.entity.ActivityLog;
import com.example.FileStorage.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public ActivityLog saveLog(ActivityLog log) {
        return activityLogRepository.save(log);
    }

    public List<ActivityLog> getLogsByUser(Long userId) {
        return activityLogRepository.findByUserId(userId);
    }

    public List<ActivityLog> getAllLogs() {
        return activityLogRepository.findAll();
    }
}

