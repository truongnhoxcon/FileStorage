package com.example.FileStorage.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shares")
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="share_link", unique = true, nullable = true, length = 255)
    private String shareLink;

    @Column(name="permission", length = 20, nullable = false)
    private String permission; // VIEW, DOWNLOAD, EDIT, ALL

    @Column(name="password")
    private String password;

    @Column(name="expire_at")
    private LocalDateTime expireAt;

    @Column(name="created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Quan hệ N-1 với File
    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    // Quan hệ N-1 với User (owner)
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Quan hệ N-1 với User (recipient)
    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    // getter, setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShareLink() { return shareLink; }
    public void setShareLink(String shareLink) { this.shareLink = shareLink; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public FileEntity getFile() { return file; }
    public void setFile(FileEntity file) { this.file = file; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }
}

