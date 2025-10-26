package com.example.FileStorage.controller;

import com.example.FileStorage.entity.FileEntity;
import com.example.FileStorage.entity.Share;
import com.example.FileStorage.entity.User;
import com.example.FileStorage.repository.ShareRepository;
import com.example.FileStorage.repository.UserRepository;
import com.example.FileStorage.service.FileService;
import com.example.FileStorage.service.ShareService;
import com.example.FileStorage.security.JwtService;
import com.example.FileStorage.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ShareService shareService;
    private final ShareRepository shareRepository;
    private final JwtService jwtService;

    @Value("${file.upload-dir}")   // Lấy từ application.properties
    private String uploadDir;

    public FileController(FileService fileService, UserRepository userRepository, NotificationService notificationService, ShareService shareService, ShareRepository shareRepository, JwtService jwtService) {
        this.fileService = fileService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.shareService = shareService;
        this.shareRepository = shareRepository;
        this.jwtService = jwtService;
    }

    // 🔹 Lấy danh sách file theo userId
    @GetMapping("/user/{userId}")
    public List<FileEntity> getFilesByUser(@PathVariable Long userId) {
        return fileService.getFilesByUser(userId);
    }

    // 🔹 Lấy danh sách file của user và thư mục được chia sẻ
    @GetMapping("/user/{userId}/with-shared")
    public List<com.example.FileStorage.dto.FileWithShareInfo> getFilesWithShared(@PathVariable Long userId) {
        return fileService.getFilesWithShared(userId);
    }

    // 🔹 Lấy metadata chi tiết file
    @GetMapping("/{id}")
    public ResponseEntity<FileEntity> getFileById(@PathVariable Long id) {
        return fileService.getFileById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 Upload file (hỗ trợ nhiều file)
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile[] files,
            @RequestParam("userId") Long userId) throws IOException {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("❌ No files provided!");
        }

        // Kiểm tra user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("❌ User not found with id: " + userId));

        // Xác định thư mục lưu trữ tuyệt đối và đảm bảo tồn tại
        Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(baseUploadPath);

        List<FileEntity> uploadedFiles = new ArrayList<>();

        // Xử lý từng file
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue; // Bỏ qua file rỗng
            }

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
            uploadedFiles.add(savedFile);

            // Send real-time notification
            notificationService.notifyFileUploaded(user, originalFileName);
            notificationService.broadcastFileUpdate(userId, "upload", originalFileName);

            // Thêm delay nhỏ để tránh trùng timestamp
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Trả về kết quả
        if (uploadedFiles.size() == 1) {
            return ResponseEntity.ok(uploadedFiles.get(0));
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Upload thành công " + uploadedFiles.size() + " file");
            response.put("files", uploadedFiles);
            response.put("count", uploadedFiles.size());
            return ResponseEntity.ok(response);
        }
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

    // 🔹 Upload file vào thư mục (hỗ trợ nhiều file)
    @PostMapping("/upload-to-folder")
    public ResponseEntity<?> uploadFileToFolder(
            @RequestParam("file") MultipartFile[] files,
            @RequestParam("folderId") Long folderId,
            @RequestParam("userId") Long userId
    ) throws IOException {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("❌ No files provided!");
        }

        // Lấy thông tin thư mục
        FileEntity folderEntity = fileService.getFileById(folderId)
                .orElseThrow(() -> new RuntimeException("❌ Folder not found with id: " + folderId));

        // Kiểm tra thư mục hợp lệ
        if (!"directory".equalsIgnoreCase(folderEntity.getFileType())) {
            return ResponseEntity.badRequest().body("❌ Target is not a directory");
        }

        // Lấy user hiện tại
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("❌ User not found with id: " + userId));
        
        // Kiểm tra sở hữu hoặc có quyền truy cập vào folder được share
        boolean hasAccess = false;
        
        // Kiểm tra nếu folder thuộc về user
        if (folderEntity.getUser() != null && folderEntity.getUser().getId().equals(userId)) {
            hasAccess = true;
        } else {
            // Kiểm tra xem folder có được share cho user không
            Share shareRecord = shareService.findByFileIdAndRecipientId(folderId, userId);
            if (shareRecord != null) {
                // Kiểm tra permission: phải có quyền EDIT hoặc ALL để upload
                String permission = shareRecord.getPermission();
                if ("EDIT".equals(permission) || "ALL".equals(permission)) {
                    hasAccess = true;
                }
            }
        }
        
        if (!hasAccess) {
            return ResponseEntity.status(403).body("❌ You don't have permission to upload to this folder");
        }

        // Đảm bảo đường dẫn thư mục nằm trong uploadDir
        Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetFolderPath = Paths.get(folderEntity.getStoragePath()).toAbsolutePath().normalize();
        if (!targetFolderPath.startsWith(baseUploadPath)) {
            return ResponseEntity.badRequest().body("❌ Invalid folder path");
        }

        List<FileEntity> uploadedFiles = new ArrayList<>();

        // Xử lý từng file
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue; // Bỏ qua file rỗng
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
            fileEntity.setUser(currentUser);
            fileEntity.setUploadedAt(LocalDateTime.now());

            FileEntity savedFile = fileService.saveFile(fileEntity);
            uploadedFiles.add(savedFile);

            // Thông báo realtime
            notificationService.notifyFileUploaded(currentUser, originalFileName);
            notificationService.broadcastFileUpdate(userId, "upload", originalFileName);

            // Thêm delay nhỏ để tránh trùng timestamp
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Trả về kết quả
        if (uploadedFiles.size() == 1) {
            return ResponseEntity.ok(uploadedFiles.get(0));
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Upload thành công " + uploadedFiles.size() + " file vào thư mục");
            response.put("files", uploadedFiles);
            response.put("count", uploadedFiles.size());
            return ResponseEntity.ok(response);
        }
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

        // Nếu là thư mục: nén ZIP tạm thời và trả về file ZIP
        if ("directory".equalsIgnoreCase(fileEntity.getFileType()) || file.isDirectory()) {
            try {
                Path zipPath = createZipFromDirectory(Paths.get(file.getAbsolutePath()), fileEntity.getFileName());
                File zipFile = zipPath.toFile();
                Resource zipResource = new FileSystemResource(zipFile);

                // Thông báo realtime
                notificationService.notifyFileDownloaded(fileEntity.getUser(), fileEntity.getFileName());
                notificationService.broadcastFileUpdate(fileEntity.getUser().getId(), "download", fileEntity.getFileName());

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + fileEntity.getFileName() + ".zip\"")
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .contentLength(zipFile.length())
                        .body(zipResource);
            } catch (IOException ex) {
                return ResponseEntity.internalServerError().build();
            }
        }

        Resource resource = new FileSystemResource(file);

        // Send real-time notification for download
        notificationService.notifyFileDownloaded(fileEntity.getUser(), fileEntity.getFileName());
        notificationService.broadcastFileUpdate(fileEntity.getUser().getId(), "download", fileEntity.getFileName());

        MediaType contentType;
        try {
            contentType = fileEntity.getFileType() != null ? MediaType.parseMediaType(fileEntity.getFileType()) : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileEntity.getFileName() + "\"")
                .contentType(contentType)
                .contentLength(file.length())
                .body(resource);
    }

    private Path createZipFromDirectory(Path sourceDir, String folderName) throws IOException {
        Path tempZip = Files.createTempFile(folderName + "_", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip.toFile()))) {
            Path basePath = sourceDir;
            Files.walk(basePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String entryName = basePath.relativize(path).toString().replace('\\', '/');
                        try (InputStream in = new FileInputStream(path.toFile())) {
                            ZipEntry zipEntry = new ZipEntry(entryName);
                            zos.putNextEntry(zipEntry);
                            in.transferTo(zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return tempZip;
    }

    // 🔹 Xóa mềm (đưa vào thùng rác)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        Optional<FileEntity> fileEntityOpt = fileService.getFileById(id);
        if (fileEntityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileEntity fileEntity = fileEntityOpt.get();
        
        // Lấy user hiện tại từ token
        String token = authHeader.replace("Bearer ", "");
        String username = jwtService.extractUsername(token);
        User currentUser = userRepository.findByUsername(username).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Unauthorized");
        }
        
        // Kiểm tra xem file có phải được share trực tiếp cho user hiện tại không
        Share shareRecord = shareService.findByFileIdAndRecipientId(id, currentUser.getId());
        if (shareRecord != null) {
            // File được share → chỉ xóa bản ghi share (unshare), không xóa file gốc
            shareService.deleteShare(shareRecord.getId());
            notificationService.broadcastFileUpdate(currentUser.getId(), "unshare", fileEntity.getFileName());
            return ResponseEntity.ok("✅ Removed from your shared items");
        }
        
        // Kiểm tra xem file có phải nằm trong folder được share không
        boolean isInSharedFolder = isFileInSharedFolder(fileEntity, currentUser.getId());
        
        // File thuộc sở hữu của user hoặc nằm trong folder được share → xóa mềm bình thường
        if (!fileEntity.getUser().getId().equals(currentUser.getId()) && !isInSharedFolder) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("❌ You don't have permission to delete this file");
        }

        try {
            Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path currentPath = Paths.get(fileEntity.getStoragePath()).toAbsolutePath().normalize();

            if (!currentPath.startsWith(baseUploadPath)) {
                return ResponseEntity.badRequest().body("❌ Invalid file path");
            }

            // Tính đường dẫn thùng rác: <uploadDir>/.trash/<userId>/... (giữ nguyên tên hiện tại)
            Long uid = fileEntity.getUser().getId();
            Path trashBase = baseUploadPath.resolve(".trash").resolve(String.valueOf(uid)).normalize();
            Files.createDirectories(trashBase);

            String name = fileEntity.getFileName();
            Path trashTarget = trashBase.resolve(name).normalize();

            // Tránh đè nếu đã tồn tại trong thùng rác
            trashTarget = ensureNonConflictPath(trashTarget);

            // Di chuyển vào thùng rác (file hoặc thư mục)
            Files.move(currentPath, trashTarget);

            // Cập nhật DB: set deletedAt + originalPath + storagePath mới (trong thùng rác)
            LocalDateTime deletedAt = LocalDateTime.now();
            fileService.updateStorageAndMarkDeleted(
                    id,
                    trashTarget.toString(),
                    currentPath.toString(),
                    deletedAt
            );

            // Nếu là thư mục, cascade đánh dấu xóa mềm cho tất cả phần tử con với prefix mới
            if ("directory".equalsIgnoreCase(fileEntity.getFileType())) {
                fileService.cascadeMarkDeletedForTree(
                        id,
                        currentPath.toString(),
                        trashTarget.toString(),
                        deletedAt
                );
            }

            // Thông báo realtime
            notificationService.notifyFileDeleted(fileEntity.getUser(), fileEntity.getFileName());
            notificationService.broadcastFileUpdate(fileEntity.getUser().getId(), "delete", fileEntity.getFileName());

            return ResponseEntity.ok("✅ Moved to trash");
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body("❌ Failed to move to trash: " + ex.getMessage());
        }
    }

    // 🔹 Danh sách thùng rác của user
    @GetMapping("/user/{userId}/trash")
    public ResponseEntity<List<FileEntity>> getTrash(@PathVariable Long userId) {
        List<FileEntity> list = fileService.getTrashByUser(userId);
        return ResponseEntity.ok(list);
    }

    // 🔹 Khôi phục từ thùng rác
    @PostMapping("/{id}/restore")
    public ResponseEntity<String> restoreFile(@PathVariable Long id) {
        Optional<FileEntity> opt = fileService.getFileById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        FileEntity e = opt.get();
        if (e.getDeletedAt() == null) {
            return ResponseEntity.badRequest().body("❌ Item is not in trash");
        }

        try {
            Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path trashPath = Paths.get(e.getStoragePath()).toAbsolutePath().normalize();
            Path original = e.getOriginalPath() != null
                    ? Paths.get(e.getOriginalPath()).toAbsolutePath().normalize()
                    : baseUploadPath.resolve(e.getFileName()).toAbsolutePath().normalize();

            if (!trashPath.startsWith(baseUploadPath)) {
                return ResponseEntity.badRequest().body("❌ Invalid trash path");
            }

            // Nếu original tồn tại, tạo tên không xung đột
            Path target = ensureNonConflictPath(original);
            Files.createDirectories(target.getParent() != null ? target.getParent() : baseUploadPath);
            Files.move(trashPath, target);

            fileService.clearDeletedAndSetStorage(id, target.toString());

            // Nếu là thư mục: khôi phục cả cây con theo originalPath
            if ("directory".equalsIgnoreCase(e.getFileType())) {
                // Dùng trashPath làm prefix để tìm toàn bộ phần tử con đang ở trong thùng rác
                fileService.cascadeRestoreForTree(id, trashPath.toString());
            }

            notificationService.broadcastFileUpdate(e.getUser().getId(), "restore", e.getFileName());
            return ResponseEntity.ok("✅ Restored");
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body("❌ Failed to restore: " + ex.getMessage());
        }
    }

    // 🔹 Xóa vĩnh viễn (purge)
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<String> purge(@PathVariable Long id) {
        Optional<FileEntity> fileEntityOpt = fileService.getFileById(id);
        if (fileEntityOpt.isEmpty()) return ResponseEntity.notFound().build();
        FileEntity fileEntity = fileEntityOpt.get();

        try {
            Path baseUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetPath = Paths.get(fileEntity.getStoragePath()).toAbsolutePath().normalize();
            if (!targetPath.startsWith(baseUploadPath)) {
                return ResponseEntity.badRequest().body("❌ Invalid file path");
            }

            File file = targetPath.toFile();
            if ("directory".equalsIgnoreCase(fileEntity.getFileType()) || file.isDirectory()) {
                if (Files.exists(targetPath)) {
                    Files.walk(targetPath)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ex) { throw new RuntimeException(ex); }
                            });
                }
                fileService.deleteByStoragePathPrefix(targetPath.toString());
            } else {
                Files.deleteIfExists(targetPath);
                fileService.deleteFile(id);
            }

            notificationService.broadcastFileUpdate(fileEntity.getUser().getId(), "purge", fileEntity.getFileName());
            return ResponseEntity.ok("✅ Permanently deleted");
        } catch (RuntimeException re) {
            return ResponseEntity.internalServerError().body("❌ Failed to purge: " + re.getCause());
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body("❌ Failed to purge: " + ex.getMessage());
        }
    }

    private Path ensureNonConflictPath(Path desired) throws IOException {
        if (!Files.exists(desired)) return desired;
        String fileName = desired.getFileName().toString();
        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        int i = 1;
        Path parent = desired.getParent();
        while (true) {
            Path candidate = (parent == null)
                    ? Paths.get(base + " (" + i + ")" + ext)
                    : parent.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
            i++;
        }
    }
    
    /**
     * Kiểm tra xem file có nằm trong folder được share không
     */
    private boolean isFileInSharedFolder(FileEntity file, Long recipientId) {
        String filePath = file.getStoragePath();
        
        // Lấy tất cả các folder được share cho recipient
        List<Share> sharedShares = shareRepository.findByRecipientId(recipientId);
        
        for (Share share : sharedShares) {
            FileEntity sharedFolder = share.getFile();
            
            // Chỉ kiểm tra nếu là folder
            if (!"directory".equalsIgnoreCase(sharedFolder.getFileType())) {
                continue;
            }
            
            String folderPath = sharedFolder.getStoragePath();
            
            // Kiểm tra xem file có nằm bên trong folder được share không
            // File phải có storagePath bắt đầu bằng folderPath
            if (filePath.startsWith(folderPath) && !filePath.equals(folderPath)) {
                return true;
            }
        }
        
        return false;
    }
}
