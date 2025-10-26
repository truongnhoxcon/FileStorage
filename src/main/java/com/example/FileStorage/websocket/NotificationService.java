package com.example.FileStorage.websocket;

import com.example.FileStorage.entity.User;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Send notification to specific user
    public void sendNotificationToUser(Long userId, String type, String title, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", LocalDateTime.now().toString());
        notification.put("userId", userId);

        messagingTemplate.convertAndSendToUser(
                userId.toString(), 
                "/queue/notifications", 
                notification
        );
    }

    // Send notification to all users
    public void sendBroadcastNotification(String type, String title, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    // File operation notifications
    public void notifyFileUploaded(User user, String fileName) {
        sendNotificationToUser(
                user.getId(),
                "success",
                "File Uploaded",
                "File '" + fileName + "' has been uploaded successfully"
        );
    }

    public void notifyFileDeleted(User user, String fileName) {
        sendNotificationToUser(
                user.getId(),
                "info",
                "File Deleted",
                "File '" + fileName + "' has been deleted"
        );
    }

    public void notifyFileDownloaded(User user, String fileName) {
        sendNotificationToUser(
                user.getId(),
                "info",
                "File Downloaded",
                "File '" + fileName + "' has been downloaded"
        );
    }

    public void notifyFileShared(User user, String fileName, String shareLink) {
        sendNotificationToUser(
                user.getId(),
                "success",
                "File Shared",
                "File '" + fileName + "' has been shared. Link: " + shareLink
        );
    }

    // System notifications
    public void notifySystemMaintenance() {
        sendBroadcastNotification(
                "warning",
                "System Maintenance",
                "The system will undergo maintenance in 30 minutes. Please save your work."
        );
    }

    public void notifyNewUserRegistered(String username) {
        sendBroadcastNotification(
                "info",
                "New User",
                "New user '" + username + "' has joined the system"
        );
    }

    // Real-time file updates
    public void broadcastFileUpdate(Long userId, String action, String fileName) {
        Map<String, Object> update = new HashMap<>();
        update.put("userId", userId);
        update.put("action", action);
        update.put("fileName", fileName);
        update.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/file-updates", update);
    }

    // User activity notifications
    public void notifyUserActivity(Long userId, String activity) {
        Map<String, Object> activityNotification = new HashMap<>();
        activityNotification.put("userId", userId);
        activityNotification.put("activity", activity);
        activityNotification.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/user-activity", activityNotification);
    }
}

