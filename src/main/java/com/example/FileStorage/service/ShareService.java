package com.example.FileStorage.service;

import com.example.FileStorage.entity.Share;
import com.example.FileStorage.entity.FileEntity;
import com.example.FileStorage.entity.User;
import com.example.FileStorage.repository.UserRepository;
import com.example.FileStorage.repository.FileRepository;
import com.example.FileStorage.repository.ShareRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ShareService {

    private final ShareRepository shareRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    public ShareService(ShareRepository shareRepository, UserRepository userRepository, FileRepository fileRepository) {
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
    }

    public Share createShare(Share share) {
        return shareRepository.save(share);
    }

    public Optional<Share> getShareById(Long id) {
        return shareRepository.findById(id);
    }

    public Share getShareByLink(String link) {
        return shareRepository.findByShareLink(link);
    }

    public void deleteShare(Long id) {
        shareRepository.deleteById(id);
    }

    public Share shareFolderByEmail(Long folderId, Long ownerId, String recipientEmail, String permission) {
        User owner = userRepository.findById(ownerId).orElseThrow(() -> new RuntimeException("Owner not found"));
        User recipient = userRepository.findByEmail(recipientEmail).orElseThrow(() -> new RuntimeException("Recipient not found"));
        FileEntity folder = fileRepository.findById(folderId).orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!"directory".equalsIgnoreCase(folder.getFileType())) {
            throw new RuntimeException("Target is not a directory");
        }
        if (!folder.getUser().getId().equals(ownerId)) {
            throw new RuntimeException("Owner does not own the folder");
        }

        Share existing = shareRepository.findByFileIdAndRecipientId(folderId, recipient.getId());
        if (existing != null) {
            existing.setPermission(permission);
            // Đảm bảo share_link không null do ràng buộc DB hiện tại
            if (existing.getShareLink() == null || existing.getShareLink().isEmpty()) {
                existing.setShareLink(java.util.UUID.randomUUID().toString());
            }
            return shareRepository.save(existing);
        }

        Share share = new Share();
        share.setFile(folder);
        share.setOwner(owner);
        share.setRecipient(recipient);
        share.setPermission(permission);
        // Đảm bảo share_link không null để tương thích cấu trúc DB hiện tại
        share.setShareLink(java.util.UUID.randomUUID().toString());
        return shareRepository.save(share);
    }

    public java.util.List<Share> listFolderShares(Long folderId) {
        return shareRepository.findByFileId(folderId);
    }

    public Share updateSharePermission(Long shareId, String permission) {
        Share s = shareRepository.findById(shareId).orElseThrow(() -> new RuntimeException("Share not found"));
        s.setPermission(permission);
        return shareRepository.save(s);
    }
}

