package com.example.FileStorage.controller;

import com.example.FileStorage.entity.FileEntity;
import com.example.FileStorage.entity.User;
import com.example.FileStorage.repository.UserRepository;
import com.example.FileStorage.service.FileService;
import com.example.FileStorage.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${file.upload-dir}")   // Lấy từ application.properties
    private String uploadDir;

    public FileController(FileService fileService, UserRepository userRepository, NotificationService notificationService) {
        this.fileService = fileService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // 🔹 Lấy danh sách file theo userId
    @GetMapping("/user/{userId}")
    public List<FileEntity> getFilesByUser(@PathVariable Long userId) {
        return fileService.getFilesByUser(userId);
    }

    // 🔹 Lấy metadata chi tiết file
    @GetMapping("/{id}")
    public ResponseEntity<FileEntity> getFileById(@PathVariable Long id) {
        return fileService.getFileById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 Upload file
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("❌ File is empty!");
        }

        // Kiểm tra user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("❌ User not found with id: " + userId));

        // Xác định thư mục lưu trữ tuyệt đối và đảm bảo tồn tại
        Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(baseUploadPath);

        // Tạo tên file duy nhất và loại bỏ mọi path traversal
        String originalFileName = file.getOriginalFilename();
        String safeFileName = originalFileName == null ? "file" : Paths.get(originalFileName).getFileName().toString();
        String uniqueFileName = System.currentTimeMillis() + "_" + safeFileName;
        Path destinationPath = baseUploadPath.resolve(uniqueFileName).normalize();

        // Lưu file bằng NIO để tránh phụ thuộc đường dẫn tạm của Tomcat
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Tạo metadata và lưu DB
        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(originalFileName);
        fileEntity.setFileType(file.getContentType());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setStoragePath(destinationPath.toString());
        fileEntity.setUser(user);
        fileEntity.setUploadedAt(LocalDateTime.now());

        FileEntity savedFile = fileService.saveFile(fileEntity);

        // Send real-time notification
        notificationService.notifyFileUploaded(user, originalFileName);
        notificationService.broadcastFileUpdate(userId, "upload", originalFileName);

        return ResponseEntity.ok(savedFile);
    }

    // 🔹 Tạo thư mục
    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            @RequestParam("name") String folderName,
            @RequestParam("userId") Long userId) throws IOException {

        // Kiểm tra user tồn tại
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("❌ User not found with id: " + userId));

        // Validate tên thư mục tối thiểu
        if (folderName == null || folderName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("❌ Folder name is required");
        }

        // Loại bỏ path traversal và ký tự nguy hiểm, chỉ giữ tên cuối cùng
        String safeFolderName = Paths.get(folderName.trim()).getFileName().toString();
        // Không cho phép các tên đặc biệt có thể gây lỗi trên Windows/Linux
        if (".".equals(safeFolderName) || "..".equals(safeFolderName)) {
            return ResponseEntity.badRequest().body("❌ Invalid folder name");
        }

        Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(baseUploadPath);

        Path folderPath = baseUploadPath.resolve(safeFolderName).normalize();
        // Đảm bảo vẫn nằm trong thư mục gốc
        if (!folderPath.startsWith(baseUploadPath)) {
            return ResponseEntity.badRequest().body("❌ Invalid folder path");
        }

        // Tạo thư mục nếu chưa tồn tại
        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        } else if (!Files.isDirectory(folderPath)) {
            return ResponseEntity.status(409).body("❌ A file with the same name already exists");
        } else {
            // Nếu thư mục đã tồn tại, trả về 409 để client xử lý (hoặc có thể coi là OK idempotent)
            return ResponseEntity.status(409).body("❌ Folder already exists");
        }

        // Lưu metadata thư mục vào DB
        FileEntity folderEntity = new FileEntity();
        folderEntity.setFileName(safeFolderName);
        folderEntity.setFileType("directory");
        folderEntity.setFileSize(0L);
        folderEntity.setStoragePath(folderPath.toString());
        folderEntity.setUser(user);
        folderEntity.setUploadedAt(LocalDateTime.now());

        FileEntity savedFolder = fileService.saveFile(folderEntity);

        // Gửi thông báo realtime
        notificationService.broadcastFileUpdate(userId, "create-folder", safeFolderName);

        return ResponseEntity.ok(savedFolder);
    }

    // 🔹 Upload file vào thư mục
    @PostMapping("/upload-to-folder")
    public ResponseEntity<?> uploadFileToFolder(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folderId") Long folderId,
            @RequestParam("userId") Long userId
    ) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("❌ File is empty!");
        }

        // Lấy thông tin thư mục
        FileEntity folderEntity = fileService.getFileById(folderId)
                .orElseThrow(() -> new RuntimeException("❌ Folder not found with id: " + folderId));

        // Kiểm tra thư mục hợp lệ
        if (!"directory".equalsIgnoreCase(folderEntity.getFileType())) {
            return ResponseEntity.badRequest().body("❌ Target is not a directory");
        }

        // Kiểm tra sở hữu: thư mục phải thuộc về user
        if (folderEntity.getUser() == null || !folderEntity.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).body("❌ Folder does not belong to the user");
        }

        // Xác thực user tồn tại
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("❌ User not found with id: " + userId));

        // Đảm bảo đường dẫn thư mục nằm trong uploadDir
        Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetFolderPath = Paths.get(folderEntity.getStoragePath()).toAbsolutePath().normalize();
        if (!targetFolderPath.startsWith(baseUploadPath)) {
            return ResponseEntity.badRequest().body("❌ Invalid folder path");
        }

        // Sanitized file name
        String originalFileName = file.getOriginalFilename();
        String safeFileName = originalFileName == null ? "file" : Paths.get(originalFileName).getFileName().toString();
        String uniqueFileName = System.currentTimeMillis() + "_" + safeFileName;
        Path destinationPath = targetFolderPath.resolve(uniqueFileName).normalize();

        // Ghi file
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Lưu metadata file
        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(originalFileName);
        fileEntity.setFileType(file.getContentType());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setStoragePath(destinationPath.toString());
        fileEntity.setUser(user);
        fileEntity.setUploadedAt(LocalDateTime.now());

        FileEntity savedFile = fileService.saveFile(fileEntity);

        // Thông báo realtime
        notificationService.notifyFileUploaded(user, originalFileName);
        notificationService.broadcastFileUpdate(userId, "upload", originalFileName);

        return ResponseEntity.ok(savedFile);
    }

    // 🔹 Download file
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        FileEntity fileEntity = fileService.getFileById(id)
                .orElseThrow(() -> new RuntimeException("❌ File not found with id: " + id));

        File file = new File(fileEntity.getStoragePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        // Send real-time notification for download
        notificationService.notifyFileDownloaded(fileEntity.getUser(), fileEntity.getFileName());
        notificationService.broadcastFileUpdate(fileEntity.getUser().getId(), "download", fileEntity.getFileName());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileEntity.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(fileEntity.getFileType()))
                .contentLength(file.length())
                .body(resource);
    }

    // 🔹 Xóa file
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id) {
        Optional<FileEntity> fileEntityOpt = fileService.getFileById(id);
        if (fileEntityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileEntity fileEntity = fileEntityOpt.get();

        // Xóa file trên server
        File file = new File(fileEntity.getStoragePath());
        if (file.exists()) {
            file.delete();
        }

        // Send real-time notification for deletion
        notificationService.notifyFileDeleted(fileEntity.getUser(), fileEntity.getFileName());
        notificationService.broadcastFileUpdate(fileEntity.getUser().getId(), "delete", fileEntity.getFileName());

        // Xóa metadata trong DB
        fileService.deleteFile(id);
        return ResponseEntity.ok("✅ File deleted successfully");
    }
}
