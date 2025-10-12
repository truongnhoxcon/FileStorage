package com.example.FileStorage.service;

import com.example.FileStorage.dto.FileWithShareInfo;
import com.example.FileStorage.entity.FileEntity;
import com.example.FileStorage.entity.Share;
import com.example.FileStorage.repository.FileRepository;
import com.example.FileStorage.repository.ShareRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final ShareRepository shareRepository;

    public FileService(FileRepository fileRepository, ShareRepository shareRepository) {
        this.fileRepository = fileRepository;
        this.shareRepository = shareRepository;
    }

    public List<FileEntity> getFilesByUser(Long userId) {
        return fileRepository.findByUserId(userId);
    }

    public List<FileWithShareInfo> getFilesWithShared(Long userId) {
        // Lấy file của user
        List<FileEntity> userFiles = fileRepository.findByUserId(userId);
        
        // Lấy các thư mục được chia sẻ cho user này
        List<Share> sharedShares = shareRepository.findByRecipientId(userId);
        Map<Long, Share> sharedFilesMap = sharedShares.stream()
                .collect(Collectors.toMap(share -> share.getFile().getId(), share -> share));
        
        // Tạo Set để tránh trùng lặp file
        Set<Long> addedFileIds = new HashSet<>();
        
        // Tạo danh sách kết quả
        List<FileWithShareInfo> result = new ArrayList<>();
        
        // Thêm file của user
        for (FileEntity file : userFiles) {
            Share share = sharedFilesMap.get(file.getId());
            FileWithShareInfo fileWithShareInfo = share != null ? new FileWithShareInfo(file, share) : new FileWithShareInfo(file);
            result.add(fileWithShareInfo);
            addedFileIds.add(file.getId());
        }
        
        // Thêm các file được chia sẻ mà user chưa có
        List<FileEntity> sharedFiles = sharedShares.stream()
                .map(Share::getFile)
                .filter(file -> !addedFileIds.contains(file.getId()))
                .collect(Collectors.toList());
        
        for (FileEntity file : sharedFiles) {
            Share share = sharedFilesMap.get(file.getId());
            result.add(new FileWithShareInfo(file, share));
            addedFileIds.add(file.getId());
        }
        
        // Thêm các file bên trong folder được chia sẻ
        for (Share share : sharedShares) {
            FileEntity sharedFolder = share.getFile();
            if ("directory".equalsIgnoreCase(sharedFolder.getFileType())) {
                // Tìm tất cả file bên trong folder được chia sẻ
                List<FileEntity> filesInSharedFolder = getFilesInDirectory(sharedFolder.getStoragePath());
                
                for (FileEntity fileInFolder : filesInSharedFolder) {
                    // Chỉ thêm nếu chưa có trong danh sách
                    if (!addedFileIds.contains(fileInFolder.getId())) {
                        // Tạo FileWithShareInfo cho file bên trong folder được chia sẻ
                        FileWithShareInfo fileWithShareInfo = new FileWithShareInfo(fileInFolder);
                        fileWithShareInfo.setShared(true);
                        fileWithShareInfo.setSharePermission(share.getPermission());
                        fileWithShareInfo.setSharedBy(share.getOwner().getUsername());
                        result.add(fileWithShareInfo);
                        addedFileIds.add(fileInFolder.getId());
                    }
                }
            }
        }
        
        return result;
    }
    
    private List<FileEntity> getFilesInDirectory(String directoryPath) {
        // Tìm tất cả file có storagePath bắt đầu bằng directoryPath
        return fileRepository.findByStoragePathStartingWith(directoryPath);
    }

    public Optional<FileEntity> getFileById(Long id) {
        return fileRepository.findById(id);
    }

    public FileEntity saveFile(FileEntity file) {
        return fileRepository.save(file);
    }

    public void deleteFile(Long id) {
        fileRepository.deleteById(id);
    }
}

