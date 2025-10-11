package com.example.FileStorage.service;

import com.example.FileStorage.dto.FileWithShareInfo;
import com.example.FileStorage.entity.FileEntity;
import com.example.FileStorage.entity.Share;
import com.example.FileStorage.repository.FileRepository;
import com.example.FileStorage.repository.ShareRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        
        // Tạo danh sách kết quả
        List<FileWithShareInfo> result = userFiles.stream()
                .map(file -> {
                    Share share = sharedFilesMap.get(file.getId());
                    return share != null ? new FileWithShareInfo(file, share) : new FileWithShareInfo(file);
                })
                .collect(Collectors.toList());
        
        // Thêm các file được chia sẻ mà user chưa có
        List<FileEntity> sharedFiles = sharedShares.stream()
                .map(Share::getFile)
                .filter(file -> !userFiles.contains(file))
                .collect(Collectors.toList());
        
        for (FileEntity file : sharedFiles) {
            Share share = sharedFilesMap.get(file.getId());
            result.add(new FileWithShareInfo(file, share));
        }
        
        return result;
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

