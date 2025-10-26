package com.example.FileStorage.dto;

import com.example.FileStorage.entity.FileEntity;
import com.example.FileStorage.entity.Share;

import java.time.LocalDateTime;

public class FileWithShareInfo {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String storagePath;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;
    private Long userId;
    private String ownerUsername;
    
    // Thông tin chia sẻ
    private boolean isShared;
    private String sharePermission;
    private String sharedBy;

    public FileWithShareInfo() {}

    public FileWithShareInfo(FileEntity file) {
        this.id = file.getId();
        this.fileName = file.getFileName();
        this.fileType = file.getFileType();
        this.fileSize = file.getFileSize();
        this.storagePath = file.getStoragePath();
        this.uploadedAt = file.getUploadedAt();
        this.updatedAt = file.getUpdatedAt();
        this.userId = file.getUser().getId();
        this.ownerUsername = file.getUser().getUsername();
        this.isShared = false;
    }

    public FileWithShareInfo(FileEntity file, Share share) {
        this(file);
        this.isShared = true;
        this.sharePermission = share.getPermission();
        this.sharedBy = share.getOwner().getUsername();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { isShared = shared; }
    public String getSharePermission() { return sharePermission; }
    public void setSharePermission(String sharePermission) { this.sharePermission = sharePermission; }
    public String getSharedBy() { return sharedBy; }
    public void setSharedBy(String sharedBy) { this.sharedBy = sharedBy; }
}
