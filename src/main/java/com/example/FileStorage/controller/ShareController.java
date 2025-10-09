package com.example.FileStorage.controller;

import com.example.FileStorage.entity.Share;
import com.example.FileStorage.service.ShareService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public Share createShare(@RequestBody Share share) {
        return shareService.createShare(share);
    }

    @GetMapping("/{id}")
    public Optional<Share> getShareById(@PathVariable Long id) {
        return shareService.getShareById(id);
    }

    @GetMapping("/link/{link}")
    public Share getShareByLink(@PathVariable String link) {
        return shareService.getShareByLink(link);
    }

    @DeleteMapping("/{id}")
    public void deleteShare(@PathVariable Long id) {
        shareService.deleteShare(id);
    }

    // Share folder by recipient email with permission: VIEW, DOWNLOAD, EDIT, ALL
    @PostMapping("/folder/email")
    public ResponseEntity<?> shareFolderByEmail(
            @RequestParam("folderId") Long folderId,
            @RequestParam("ownerId") Long ownerId,
            @RequestParam("recipientEmail") String recipientEmail,
            @RequestParam("permission") String permission
    ) {
        if (permission == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Missing permission");
        }
        String perm = permission.trim().toUpperCase();
        if (!("VIEW".equals(perm) || "DOWNLOAD".equals(perm) || "EDIT".equals(perm) || "ALL".equals(perm))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Invalid permission");
        }
        Share result = shareService.shareFolderByEmail(folderId, ownerId, recipientEmail, perm);
        return ResponseEntity.ok(result);
    }

    // List shares for a folder
    @GetMapping("/folder/{folderId}")
    public List<Share> listFolderShares(@PathVariable Long folderId) {
        return shareService.listFolderShares(folderId);
    }

    // Update a share's permission
    @PatchMapping("/{shareId}/permission")
    public Share updateSharePermission(
            @PathVariable Long shareId,
            @RequestParam("permission") String permission
    ) {
        return shareService.updateSharePermission(shareId, permission.trim().toUpperCase());
    }
}
