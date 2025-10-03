package com.example.FileStorage.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length = 50)
    private String action; // UPLOAD, DOWNLOAD, RENAME, DELETE, SHARE, UPDATE_METADATA

    @Column(length = 500)
    private String description;

    @Column(name="created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Quan hệ N-1 với User
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Quan hệ N-1 với File (có thể null nếu log không liên quan file cụ thể)
    @ManyToOne
    @JoinColumn(name = "file_id")
    private FileEntity file;

    // getter, setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public FileEntity getFile() { return file; }
    public void setFile(FileEntity file) { this.file = file; }
}

