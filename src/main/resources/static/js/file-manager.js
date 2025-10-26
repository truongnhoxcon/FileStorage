// File Manager JavaScript
let currentUser = null;
let currentFiles = [];
let currentCategory = 'all';
let selectedFiles = new Set();
let currentFolder = null; // giữ thư mục đang mở (FileEntity có fileType='directory')

document.addEventListener('DOMContentLoaded', function() {
    checkAuth();
    setupEventListeners();
    setupTabs();
});

// Check authentication
function checkAuth() {
    const token = localStorage.getItem('token');
    if (!token) {
        window.location.href = '/login';
        return;
    }

    // Get user info
    fetch('/api/users/me', {
        headers: {
            'Authorization': 'Bearer ' + token
        }
    })
    .then(response => {
        if (response.ok) {
            return response.json();
        } else {
            throw new Error('Unauthorized');
        }
    })
    .then(user => {
        currentUser = user;
        window.currentUser = user; // Make available globally for WebSocket
        document.getElementById('userName').textContent = user.username;
        loadFiles();
        
        // Show live indicator when WebSocket connects
        if (wsClient && wsClient.isConnected) {
            document.getElementById('liveIndicator').style.display = 'inline-flex';
        }
    })
    .catch(() => {
        localStorage.removeItem('token');
        window.location.href = '/login';
    });
}

// Load files based on current category
function loadFiles(category = currentCategory) {
    if (!currentUser) {
        console.log('No current user, cannot load files');
        return;
    }
    
    currentCategory = category;
    updateSidebar();
    
    console.log('Loading files for user:', currentUser);
    console.log('User ID:', currentUser.id);
    
    const token = localStorage.getItem('token');
    console.log('Token:', token ? 'Present' : 'Missing');
    
    const endpoint = (currentCategory === 'trash')
        ? `/api/files/user/${currentUser.id}/trash`
        : `/api/files/user/${currentUser.id}/with-shared`;
    fetch(endpoint, {
        headers: {
            'Authorization': 'Bearer ' + token
        }
    })
    .then(response => {
        console.log('Files response status:', response.status);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(files => {
        console.log('Files loaded:', files);
        currentFiles = files;
        filterAndDisplayFiles();
    })
    .catch(error => {
        console.error('Error loading files:', error);
        displayFiles([]);
    });
}

// Filter files by category
function filterAndDisplayFiles() {
    let filteredFiles = currentFiles;
    
    if (currentCategory !== 'all' && currentCategory !== 'trash') {
        filteredFiles = currentFiles.filter(file => {
            const fileType = file.fileType || '';
            switch (currentCategory) {
                case 'images':
                    return fileType.startsWith('image/');
                case 'documents':
                    return fileType.includes('pdf') || fileType.includes('document') || fileType.includes('text');
                case 'videos':
                    return fileType.startsWith('video/');
                case 'audio':
                    return fileType.startsWith('audio/');
                default:
                    return true;
            }
        });
    }
    
    // Nếu đang mở thư mục, chỉ hiển thị file/thư mục bên trong thư mục đó (không áp dụng cho trash)
    if (currentCategory !== 'trash' && currentFolder && currentFolder.storagePath) {
        const basePath = normalizePath(currentFolder.storagePath);
        filteredFiles = filteredFiles.filter(file => {
            if (!file || !file.storagePath) return false;
            const p = normalizePath(file.storagePath);
            // loại trừ chính thư mục
            if (file.id === currentFolder.id) return false;
            return p.startsWith(basePath + '/');
        });
    } else if (currentCategory !== 'trash') {
        // Ở chế độ gốc (không mở thư mục), ẩn các file nằm bên trong bất kỳ thư mục nào
        const folderPaths = currentFiles
            .filter(f => f && f.fileType === 'directory' && f.storagePath)
            .map(f => normalizePath(f.storagePath) + '/');
        if (folderPaths.length > 0) {
            filteredFiles = filteredFiles.filter(file => {
                if (!file || !file.storagePath) return false;
                if (file.fileType === 'directory') return true; // luôn hiển thị thư mục ở cấp gốc
                const p = normalizePath(file.storagePath);
                // nếu file nằm dưới bất kỳ thư mục nào, ẩn ở chế độ gốc
                return !folderPaths.some(fp => p.startsWith(fp));
            });
        }
    }

    // Trash: chỉ hiển thị item cấp cao nhất (không hiển thị file con bên trong thư mục đã xóa)
    if (currentCategory === 'trash') {
        const items = filteredFiles.filter(f => f && f.originalPath);
        filteredFiles = items.filter(f => {
            const p = normalizePath(f.originalPath || '');
            // Loại bỏ nếu originalPath của f nằm bên trong originalPath của item khác
            return !items.some(g => {
                if (!g || g.id === f.id) return false;
                const gPath = normalizePath(g.originalPath || '');
                return p.startsWith(gPath + '/');
            });
        });
    }

    // Apply search filter
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();
    if (searchTerm) {
        filteredFiles = filteredFiles.filter(file => 
            file.fileName.toLowerCase().includes(searchTerm)
        );
    }
    
    // Apply sort
    const sortBy = document.getElementById('sortSelect').value;
    filteredFiles.sort((a, b) => {
        switch (sortBy) {
            case 'name':
                return a.fileName.localeCompare(b.fileName);
            case 'date':
                return new Date(b.uploadedAt) - new Date(a.uploadedAt);
            case 'size':
                return b.fileSize - a.fileSize;
            case 'type':
                return (a.fileType || '').localeCompare(b.fileType || '');
            default:
                return 0;
        }
    });
    
    displayFiles(filteredFiles);
}

// Display files in grid
function displayFiles(files) {
    const fileGrid = document.getElementById('fileGrid');
    const emptyState = document.getElementById('emptyState');
    
    if (files.length === 0) {
        fileGrid.style.display = 'none';
        emptyState.style.display = 'block';
        return;
    }
    
    fileGrid.style.display = 'grid';
    emptyState.style.display = 'none';
    
    fileGrid.innerHTML = files.map(file => createFileItem(file)).join('');
}

// Create file item HTML
function createFileItem(file) {
    const fileIcon = getFileIcon(file.fileType);
    const fileSize = formatFileSize(file.fileSize);
    const uploadDate = new Date(file.uploadedAt).toLocaleDateString('vi-VN');
    
    const isDirectory = (file.fileType === 'directory');
    const isShared = file.isShared || false;
    const isTrash = (currentCategory === 'trash');
    const onClick = (!isTrash && isDirectory) ? `openFolder(${file.id})` : `selectFile(${file.id})`;
    
    // Chỉ hiển thị nút chia sẻ cho thư mục của chính user (không phải thư mục được chia sẻ)
    const showShareButton = !isTrash && isDirectory && !isShared;

    return `
        <div class="file-item ${isShared ? 'shared-item' : ''}" data-file-id="${file.id}" onclick="${onClick}">
            <div class="file-icon ${fileIcon.class} ${isShared ? 'shared-icon' : ''}">
                <i class="${fileIcon.icon}"></i>
                ${isShared ? '<div class="shared-badge"><i class="fas fa-share-alt"></i></div>' : ''}
            </div>
            <div class="file-name" title="${file.fileName}">
                ${file.fileName}
                ${isShared ? `<span class="shared-label">(Chia sẻ bởi ${file.sharedBy})</span>` : ''}
            </div>
            <div class="file-size">${fileSize}</div>
            <div class="file-date">${uploadDate}</div>
            <div class="file-actions">
                ${isTrash ? `
                <button class="file-action-btn" onclick="event.stopPropagation(); restoreFile(${file.id})" title="Khôi phục">
                    <i class="fas fa-undo"></i>
                </button>
                <button class="file-action-btn delete" onclick="event.stopPropagation(); purgeFile(${file.id})" title="Xóa vĩnh viễn">
                    <i class="fas fa-times"></i>
                </button>
                ` : `
                ${showShareButton ? `
                <button class="file-action-btn share" onclick="event.stopPropagation(); showShareModal(${file.id})" title="Chia sẻ">
                    <i class="fas fa-share-alt"></i>
                </button>
                ` : ''}
                <button class="file-action-btn download" onclick="event.stopPropagation(); downloadFile(${file.id})" title="Tải xuống">
                    <i class="fas fa-download"></i>
                </button>
                <button class="file-action-btn info" onclick="event.stopPropagation(); showFileDetails(${file.id})" title="Chi tiết">
                    <i class="fas fa-info"></i>
                </button>
                ${!isShared ? `
                <button class="file-action-btn delete" onclick="event.stopPropagation(); deleteFile(${file.id})" title="Xóa">
                    <i class="fas fa-trash"></i>
                </button>
                ` : ''}
                `}
            </div>
        </div>
    `;
}

// Get file icon based on type
function getFileIcon(fileType) {
    if (fileType === 'directory') return { class: 'folder', icon: 'fas fa-folder' };
    if (!fileType) return { class: 'default', icon: 'fas fa-file' };
    
    if (fileType.startsWith('image/')) {
        return { class: 'image', icon: 'fas fa-image' };
    } else if (fileType.startsWith('video/')) {
        return { class: 'video', icon: 'fas fa-video' };
    } else if (fileType.startsWith('audio/')) {
        return { class: 'audio', icon: 'fas fa-music' };
    } else if (fileType.includes('pdf')) {
        return { class: 'pdf', icon: 'fas fa-file-pdf' };
    } else if (fileType.includes('document') || fileType.includes('text')) {
        return { class: 'doc', icon: 'fas fa-file-alt' };
    } else {
        return { class: 'default', icon: 'fas fa-file' };
    }
}

// Format file size
function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// Select file
function selectFile(fileId) {
    const fileItem = document.querySelector(`[data-file-id="${fileId}"]`);
    if (selectedFiles.has(fileId)) {
        selectedFiles.delete(fileId);
        fileItem.classList.remove('selected');
    } else {
        selectedFiles.add(fileId);
        fileItem.classList.add('selected');
    }
}

// Download file
function downloadFile(fileId) {
	const token = localStorage.getItem('token');
	if (!token) {
		alert('Bạn cần đăng nhập để tải file.');
		window.location.href = '/login';
		return;
	}

	fetch(`/api/files/download/${fileId}`, {
		headers: {
			'Authorization': 'Bearer ' + token
		}
	})
	.then(response => {
		if (!response.ok) {
			if (response.status === 401 || response.status === 403) {
				throw new Error('Không có quyền tải xuống file này.');
			}
			throw new Error('Tải xuống thất bại.');
		}

		// Lấy tên file từ header nếu có
		const disposition = response.headers.get('Content-Disposition') || '';
		let filename = 'download';
		const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
		if (match) {
			filename = decodeURIComponent(match[1] || match[2]);
		}

		return response.blob().then(blob => ({ blob, filename }));
	})
	.then(({ blob, filename }) => {
		const url = window.URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = filename;
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
		window.URL.revokeObjectURL(url);
	})
	.catch(err => {
		console.error('Download error:', err);
		alert(err.message || 'Không thể tải xuống file.');
	});
}

// Show file details
function showFileDetails(fileId) {
    const file = currentFiles.find(f => f.id === fileId);
    if (!file) return;
    
    const modal = new bootstrap.Modal(document.getElementById('fileDetailsModal'));
    const content = document.getElementById('fileDetailsContent');
    
    content.innerHTML = `
        <div class="row">
            <div class="col-md-4 text-center">
                <div class="file-icon ${getFileIcon(file.fileType).class}" style="width: 80px; height: 80px; margin: 0 auto 20px; font-size: 32px;">
                    <i class="${getFileIcon(file.fileType).icon}"></i>
                </div>
            </div>
            <div class="col-md-8">
                <h6><strong>Tên file:</strong></h6>
                <p class="mb-3">${file.fileName}</p>
                
                <h6><strong>Loại file:</strong></h6>
                <p class="mb-3">${file.fileType || 'Không xác định'}</p>
                
                <h6><strong>Kích thước:</strong></h6>
                <p class="mb-3">${formatFileSize(file.fileSize)}</p>
                
                <h6><strong>Ngày tải lên:</strong></h6>
                <p class="mb-3">${new Date(file.uploadedAt).toLocaleString('vi-VN')}</p>
                
                <h6><strong>Đường dẫn:</strong></h6>
                <p class="mb-0 text-muted small">${file.storagePath}</p>
            </div>
        </div>
    `;
    
    // Set download button action
    document.getElementById('downloadFileBtn').onclick = () => downloadFile(fileId);
    
    modal.show();
}

// Delete file
function deleteFile(fileId) {
    if (!confirm('Bạn có chắc chắn muốn xóa file này?')) return;
    
    const token = localStorage.getItem('token');
    fetch(`/api/files/${fileId}`, {
        method: 'DELETE',
        headers: {
            'Authorization': 'Bearer ' + token
        }
    })
    .then(response => {
        if (response.ok) {
            loadFiles();
            showNotification('info', 'Đã chuyển vào thùng rác', 'Mục đã được đưa vào thùng rác.');
        } else {
            throw new Error('Failed to delete file');
        }
    })
    .catch(error => {
        console.error('Error deleting file:', error);
        alert('Có lỗi xảy ra khi xóa file!');
    });
}

// Show upload modal
function showUploadModal() {
    const modal = new bootstrap.Modal(document.getElementById('uploadModal'));
    document.getElementById('fileInput').value = '';
    document.getElementById('confirmUpload').disabled = true;
    document.getElementById('uploadProgress').style.display = 'none';
    document.getElementById('selectedFiles').style.display = 'none';
    document.getElementById('uploadArea').style.display = 'block';
    modal.show();
}

// Create new folder
function createFolder() {
    if (!currentUser) {
        alert('Bạn cần đăng nhập.');
        return;
    }
    
    // Show the create folder modal
    const modal = new bootstrap.Modal(document.getElementById('createFolderModal'));
    const folderNameInput = document.getElementById('folderNameInput');
    const confirmBtn = document.getElementById('confirmCreateFolder');
    
    // Reset form
    folderNameInput.value = '';
    confirmBtn.disabled = true;
    
    // Focus on input when modal is shown
    modal.show();
    setTimeout(() => folderNameInput.focus(), 500);
}

// Handle folder name input validation
function setupCreateFolderModal() {
    const folderNameInput = document.getElementById('folderNameInput');
    const confirmBtn = document.getElementById('confirmCreateFolder');
    
    // Input validation
    folderNameInput.addEventListener('input', function() {
        const name = this.value.trim();
        const isValid = name.length > 0 && isValidFolderName(name);
        
        confirmBtn.disabled = !isValid;
        
        // Visual feedback
        if (name.length > 0 && !isValidFolderName(name)) {
            this.classList.add('is-invalid');
        } else {
            this.classList.remove('is-invalid');
        }
    });
    
    // Handle Enter key
    folderNameInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !confirmBtn.disabled) {
            confirmCreateFolder();
        }
    });
    
    // Confirm button click
    confirmBtn.addEventListener('click', confirmCreateFolder);
}

// Validate folder name
function isValidFolderName(name) {
    // Check for invalid characters
    const invalidChars = /[<>:"/\\|?*]/;
    if (invalidChars.test(name)) {
        return false;
    }
    
    // Check for reserved names (Windows)
    const reservedNames = ['CON', 'PRN', 'AUX', 'NUL', 'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9', 'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9'];
    if (reservedNames.includes(name.toUpperCase())) {
        return false;
    }
    
    // Check length
    if (name.length === 0 || name.length > 100) {
        return false;
    }
    
    return true;
}

// Confirm create folder
function confirmCreateFolder() {
    const folderNameInput = document.getElementById('folderNameInput');
    const name = folderNameInput.value.trim();
    
    if (!name || !isValidFolderName(name)) {
        return;
    }
    
    const form = new URLSearchParams();
    form.append('name', name);
    form.append('userId', currentUser.id);

    // Disable button during request
    const confirmBtn = document.getElementById('confirmCreateFolder');
    confirmBtn.disabled = true;
    confirmBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Đang tạo...';

    fetch('/api/files/folder', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + localStorage.getItem('token'),
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: form.toString()
    })
    .then(resp => {
        if (!resp.ok) {
            return resp.text().then(t => { throw new Error(t || 'Tạo thư mục thất bại'); });
        }
        return resp.json();
    })
    .then(() => {
        // Close modal
        bootstrap.Modal.getInstance(document.getElementById('createFolderModal')).hide();
        
        // Show success notification
        showNotification('success', 'Thành công', `Đã tạo thư mục "${name}" thành công.`);
        
        // Refresh file list
        refreshFiles();
    })
    .catch(err => {
        console.error('Create folder error:', err);
        alert('Không thể tạo thư mục: ' + (err.message || 'Lỗi không xác định'));
        
        // Re-enable button
        confirmBtn.disabled = false;
        confirmBtn.innerHTML = '<i class="fas fa-folder-plus me-1"></i>Tạo thư mục';
    });
}

// Show share folder modal
function showShareModal(folderId) {
    if (!currentUser) {
        alert('Bạn cần đăng nhập.');
        return;
    }
    
    const folder = currentFiles.find(f => f.id === folderId);
    if (!folder || folder.fileType !== 'directory') {
        alert('Chỉ có thể chia sẻ thư mục.');
        return;
    }
    
    // Show the share folder modal
    const modal = new bootstrap.Modal(document.getElementById('shareFolderModal'));
    const folderNameElement = document.getElementById('shareFolderName');
    const emailInput = document.getElementById('recipientEmailInput');
    const confirmBtn = document.getElementById('confirmShareFolder');
    
    // Set folder name
    folderNameElement.textContent = `Chia sẻ thư mục: ${folder.fileName}`;
    
    // Reset form
    emailInput.value = '';
    confirmBtn.disabled = true;
    
    // Store current folder ID for sharing
    window.currentShareFolderId = folderId;
    
    // Focus on email input when modal is shown
    modal.show();
    setTimeout(() => emailInput.focus(), 500);
}

// Setup share folder modal
function setupShareFolderModal() {
    const emailInput = document.getElementById('recipientEmailInput');
    const confirmBtn = document.getElementById('confirmShareFolder');
    
    // Email validation
    emailInput.addEventListener('input', function() {
        const email = this.value.trim();
        const isValid = email.length > 0 && isValidEmail(email);
        
        confirmBtn.disabled = !isValid;
        
        // Visual feedback
        if (email.length > 0 && !isValidEmail(email)) {
            this.classList.add('is-invalid');
        } else {
            this.classList.remove('is-invalid');
        }
    });
    
    // Handle Enter key
    emailInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !confirmBtn.disabled) {
            confirmShareFolder();
        }
    });
    
    // Confirm button click
    confirmBtn.addEventListener('click', confirmShareFolder);
}

// Validate email format
function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

// Confirm share folder
function confirmShareFolder() {
    const emailInput = document.getElementById('recipientEmailInput');
    const email = emailInput.value.trim();
    const permission = 'ALL'; // Default to full access
    
    if (!email || !isValidEmail(email)) {
        return;
    }
    
    if (!window.currentShareFolderId) {
        alert('Lỗi: Không tìm thấy thư mục cần chia sẻ.');
        return;
    }
    
    const form = new URLSearchParams();
    form.append('folderId', window.currentShareFolderId);
    form.append('ownerId', currentUser.id);
    form.append('recipientEmail', email);
    form.append('permission', permission);

    // Disable button during request
    const confirmBtn = document.getElementById('confirmShareFolder');
    confirmBtn.disabled = true;
    confirmBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Đang chia sẻ...';

    fetch('/api/shares/folder/email', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + localStorage.getItem('token'),
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: form.toString()
    })
    .then(resp => {
        if (!resp.ok) {
            return resp.text().then(t => { throw new Error(t || 'Chia sẻ thư mục thất bại'); });
        }
        return resp.json();
    })
    .then((shareResult) => {
        // Close modal
        bootstrap.Modal.getInstance(document.getElementById('shareFolderModal')).hide();
        
        // Show success notification
        showNotification('success', 'Chia sẻ thành công', 
            `Đã chia sẻ thư mục với ${email} với quyền ${getPermissionText(permission)}.`);
        
        // Clear stored folder ID
        window.currentShareFolderId = null;
    })
    .catch(err => {
        console.error('Share folder error:', err);
        alert('Không thể chia sẻ thư mục: ' + (err.message || 'Lỗi không xác định'));
        
        // Re-enable button
        confirmBtn.disabled = false;
        confirmBtn.innerHTML = '<i class="fas fa-share-alt me-1"></i>Chia sẻ';
    });
}

// Get permission text for display
function getPermissionText(permission) {
    const permissionTexts = {
        'VIEW': 'Xem',
        'DOWNLOAD': 'Tải xuống',
        'EDIT': 'Chỉnh sửa',
        'ALL': 'Toàn quyền'
    };
    return permissionTexts[permission] || permission;
}

// Setup event listeners
function setupEventListeners() {
    // Setup create folder modal
    setupCreateFolderModal();
    
    // Setup share folder modal
    setupShareFolderModal();
    
    // Upload area click
    document.getElementById('uploadArea').addEventListener('click', () => {
        document.getElementById('fileInput').click();
    });
    
    // File input change
    document.getElementById('fileInput').addEventListener('change', (e) => {
        const files = e.target.files;
        if (files.length > 0) {
            displaySelectedFiles(files);
            document.getElementById('confirmUpload').disabled = false;
            document.getElementById('confirmUpload').onclick = () => uploadFiles(files);
        }
    });
    
    // Drag and drop
    const uploadArea = document.getElementById('uploadArea');
    
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('dragover');
    });
    
    uploadArea.addEventListener('dragleave', () => {
        uploadArea.classList.remove('dragover');
    });
    
    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('dragover');
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            document.getElementById('fileInput').files = files;
            displaySelectedFiles(files);
            document.getElementById('confirmUpload').disabled = false;
            document.getElementById('confirmUpload').onclick = () => uploadFiles(files);
        }
    });
    
    // Search input
    document.getElementById('searchInput').addEventListener('input', filterAndDisplayFiles);
    
    // Sort select
    document.getElementById('sortSelect').addEventListener('change', filterAndDisplayFiles);
    
    // View mode toggle
    document.querySelectorAll('input[name="viewMode"]').forEach(radio => {
        radio.addEventListener('change', (e) => {
            const fileGrid = document.getElementById('fileGrid');
            if (e.target.id === 'listView') {
                fileGrid.classList.add('list-view');
            } else {
                fileGrid.classList.remove('list-view');
            }
        });
    });
    
    // Force list view on page load
    const fileGrid = document.getElementById('fileGrid');
    if (fileGrid) {
        fileGrid.classList.add('list-view');
    }
}

// Display selected files
function displaySelectedFiles(files) {
    const selectedFilesDiv = document.getElementById('selectedFiles');
    const fileListDiv = document.getElementById('fileList');
    const uploadArea = document.getElementById('uploadArea');
    
    // Clear previous file list
    fileListDiv.innerHTML = '';
    
    // Show selected files section and hide upload area
    uploadArea.style.display = 'none';
    selectedFilesDiv.style.display = 'block';
    
    // Helper function to format file size
    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }
    
    // Helper function to get file icon
    function getFileIcon(fileName) {
        const ext = fileName.split('.').pop().toLowerCase();
        const iconMap = {
            // Images
            'jpg': 'fa-file-image',
            'jpeg': 'fa-file-image',
            'png': 'fa-file-image',
            'gif': 'fa-file-image',
            'bmp': 'fa-file-image',
            'svg': 'fa-file-image',
            // Videos
            'mp4': 'fa-file-video',
            'avi': 'fa-file-video',
            'mov': 'fa-file-video',
            'wmv': 'fa-file-video',
            // Audio
            'mp3': 'fa-file-audio',
            'wav': 'fa-file-audio',
            'flac': 'fa-file-audio',
            // Documents
            'pdf': 'fa-file-pdf',
            'doc': 'fa-file-word',
            'docx': 'fa-file-word',
            'xls': 'fa-file-excel',
            'xlsx': 'fa-file-excel',
            'ppt': 'fa-file-powerpoint',
            'pptx': 'fa-file-powerpoint',
            'txt': 'fa-file-alt',
            // Archives
            'zip': 'fa-file-archive',
            'rar': 'fa-file-archive',
            '7z': 'fa-file-archive',
            // Code
            'html': 'fa-file-code',
            'css': 'fa-file-code',
            'js': 'fa-file-code',
            'java': 'fa-file-code',
            'py': 'fa-file-code',
            'cpp': 'fa-file-code',
            'c': 'fa-file-code'
        };
        return iconMap[ext] || 'fa-file';
    }
    
    // Display each file
    Array.from(files).forEach((file, index) => {
        const fileItem = document.createElement('div');
        fileItem.className = 'list-group-item d-flex justify-content-between align-items-center';
        fileItem.innerHTML = `
            <div class="d-flex align-items-center">
                <i class="fas ${getFileIcon(file.name)} fa-2x me-3 text-primary"></i>
                <div>
                    <div class="fw-bold">${file.name}</div>
                    <small class="text-muted">${formatFileSize(file.size)}</small>
                </div>
            </div>
            <button class="btn btn-sm btn-outline-danger" onclick="removeSelectedFile(${index})" title="Hủy và chọn lại">
                <i class="fas fa-times"></i>
            </button>
        `;
        fileListDiv.appendChild(fileItem);
    });
}

// Remove selected file
function removeSelectedFile(index) {
    // Reset the file input and UI
    document.getElementById('fileInput').value = '';
    document.getElementById('confirmUpload').disabled = true;
    document.getElementById('selectedFiles').style.display = 'none';
    document.getElementById('uploadArea').style.display = 'block';
}

// Upload files
function uploadFiles(files) {
    if (!currentUser) {
        console.error('No current user found');
        return;
    }
    
    console.log('Current user:', currentUser);
    console.log('User ID:', currentUser.id);
    console.log('User ID type:', typeof currentUser.id);
    
    // Validate user ID
    if (!currentUser.id || isNaN(currentUser.id)) {
        console.error('Invalid user ID:', currentUser.id);
        alert('Lỗi: Không thể xác định user ID');
        return;
    }
    
    const formData = new FormData();
    Array.from(files).forEach(file => {
        formData.append('file', file);
    });
    formData.append('userId', currentUser.id);
    
    console.log('FormData userId:', formData.get('userId'));

    // Show progress
    const uploadProgressDiv = document.getElementById('uploadProgress');
    const uploadArea = document.getElementById('uploadArea');
    const selectedFilesDiv = document.getElementById('selectedFiles');
    const progressBar = document.querySelector('.progress-bar');
    const progressText = document.querySelector('#uploadProgress p');
    
    // Hide upload area and selected files, show progress
    uploadArea.style.display = 'none';
    selectedFilesDiv.style.display = 'none';
    uploadProgressDiv.style.display = 'block';
    progressBar.style.width = '0%';
    progressBar.textContent = '0%';
    
    const isInFolder = !!(currentFolder && currentFolder.id);
    const url = isInFolder ? '/api/files/upload-to-folder' : '/api/files/upload';
    if (isInFolder) {
        formData.append('folderId', currentFolder.id);
    }

    // Use XMLHttpRequest to track upload progress
    const xhr = new XMLHttpRequest();
    
    // Track upload progress
    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            const percentComplete = Math.round((e.loaded / e.total) * 100);
            progressBar.style.width = percentComplete + '%';
            progressBar.textContent = percentComplete + '%';
            progressBar.setAttribute('aria-valuenow', percentComplete);
            progressText.textContent = `Đang tải lên... ${percentComplete}%`;
        }
    });
    
    // Handle completion
    xhr.addEventListener('load', () => {
        console.log('Upload response status:', xhr.status);
        
        if (xhr.status >= 200 && xhr.status < 300) {
            try {
                const data = JSON.parse(xhr.responseText);
                console.log('Upload successful:', data);
                loadFiles(); // Reload file list
                bootstrap.Modal.getInstance(document.getElementById('uploadModal')).hide();
                
                // Show success notification based on response type
                if (data.count && data.count > 1) {
                    // Multiple files uploaded
                    showNotification('success', 'Upload thành công!', `Đã tải lên ${data.count} file thành công.`);
                } else if (data.fileName) {
                    // Single file uploaded
                    showNotification('success', 'Upload thành công!', `File "${data.fileName}" đã được tải lên thành công.`);
                } else {
                    // Fallback
                    showNotification('success', 'Upload thành công!', 'File đã được tải lên thành công.');
                }
            } catch (error) {
                console.error('Error parsing response:', error);
                alert('Có lỗi xảy ra khi xử lý phản hồi từ server');
            }
        } else {
            console.error('Upload error response:', xhr.responseText);
            alert(`Có lỗi xảy ra khi upload file: ${xhr.status} - ${xhr.responseText}`);
        }
        
        // Reset progress
        uploadProgressDiv.style.display = 'none';
        uploadArea.style.display = 'block';
        progressBar.style.width = '0%';
        progressBar.textContent = '0%';
        progressText.textContent = 'Đang tải lên...';
    });
    
    // Handle errors
    xhr.addEventListener('error', () => {
        console.error('Upload error');
        alert('Có lỗi xảy ra khi upload file');
        uploadProgressDiv.style.display = 'none';
        uploadArea.style.display = 'block';
        progressBar.style.width = '0%';
        progressBar.textContent = '0%';
        progressText.textContent = 'Đang tải lên...';
    });
    
    // Handle abort
    xhr.addEventListener('abort', () => {
        console.log('Upload aborted');
        uploadProgressDiv.style.display = 'none';
        uploadArea.style.display = 'block';
        progressBar.style.width = '0%';
        progressBar.textContent = '0%';
        progressText.textContent = 'Đang tải lên...';
    });
    
    // Open and send request
    xhr.open('POST', url);
    xhr.setRequestHeader('Authorization', 'Bearer ' + localStorage.getItem('token'));
    xhr.send(formData);
}

// Refresh files
function refreshFiles() {
    loadFiles();
}

// Update sidebar active state
function updateSidebar() {
    document.querySelectorAll('.sidebar-menu li').forEach(li => {
        li.classList.remove('active');
    });
    
    const activeItem = document.querySelector(`.sidebar-menu a[onclick="loadFiles('${currentCategory}')"]`).parentElement;
    activeItem.classList.add('active');
}

// Setup tabs (if needed)
function setupTabs() {
    // Any additional tab setup can go here
}

// Logout function
function logout() {
    if (confirm('Bạn có chắc chắn muốn đăng xuất?')) {
        localStorage.removeItem('token');
        window.location.href = '/login';
    }
}

// Show account settings
function showAccountSettings() {
    alert('Tính năng cài đặt tài khoản đang được phát triển!');
}

// Notification helper functions
function showNotification(type, title, message) {
    const notificationElement = document.createElement('div');
    notificationElement.className = `notification notification-${type}`;
    notificationElement.innerHTML = `
        <div class="notification-content">
            <div class="notification-title">${title}</div>
            <div class="notification-message">${message}</div>
            <div class="notification-time">${new Date().toLocaleTimeString()}</div>
        </div>
        <button class="notification-close" onclick="this.parentElement.remove()">×</button>
    `;

    let container = document.getElementById('notification-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'notification-container';
        container.className = 'notification-container';
        document.body.appendChild(container);
    }

    container.appendChild(notificationElement);

    // Auto remove after 5 seconds
    setTimeout(() => {
        if (notificationElement.parentElement) {
            notificationElement.remove();
        }
    }, 5000);
}

// Real-time file update handler
function handleRealtimeFileUpdate(update) {
    console.log('Real-time file update:', update);
    
    // Refresh file list
    if (typeof loadFiles === 'function') {
        loadFiles();
    }
    
    // Show update notification
    const actionText = {
        'upload': 'đã tải lên',
        'download': 'đã tải xuống', 
        'delete': 'đã chuyển vào thùng rác',
        'restore': 'đã khôi phục',
        'purge': 'đã xóa vĩnh viễn',
        'unshare': 'đã xóa khỏi mục được chia sẻ'
    };
    
    const message = `File "${update.fileName}" ${actionText[update.action] || update.action}`;
    showNotification('info', 'File Update', message);
}

function restoreFile(fileId) {
    const token = localStorage.getItem('token');
    fetch(`/api/files/${fileId}/restore`, {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token }
    })
    .then(resp => {
        if (!resp.ok) throw new Error('Restore failed');
        return resp.text();
    })
    .then(() => {
        showNotification('success', 'Khôi phục', 'Mục đã được khôi phục.');
        loadFiles('trash');
    })
    .catch(err => {
        console.error('Restore error:', err);
        alert('Không thể khôi phục.');
    });
}

function purgeFile(fileId) {
    if (!confirm('Xóa vĩnh viễn? Hành động này không thể hoàn tác.')) return;
    const token = localStorage.getItem('token');
    fetch(`/api/files/${fileId}/purge`, {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + token }
    })
    .then(resp => {
        if (!resp.ok) throw new Error('Purge failed');
        return resp.text();
    })
    .then(() => {
        showNotification('info', 'Đã xóa vĩnh viễn', 'Mục đã bị xóa khỏi hệ thống.');
        loadFiles('trash');
    })
    .catch(err => {
        console.error('Purge error:', err);
        alert('Không thể xóa vĩnh viễn.');
    });
}

// WebSocket connection status handler
function handleWebSocketStatus(connected) {
    const liveIndicator = document.getElementById('liveIndicator');
    if (liveIndicator) {
        liveIndicator.style.display = connected ? 'inline-flex' : 'none';
    }
}

// --------- Folder navigation helpers ---------
function openFolder(fileId) {
    const folder = currentFiles.find(f => f.id === fileId);
    if (!folder || folder.fileType !== 'directory') return;
    currentFolder = folder;
    updateNavigationUI();
    filterAndDisplayFiles();
}

function exitFolder() {
    currentFolder = null;
    updateNavigationUI();
    filterAndDisplayFiles();
}

// Update navigation UI (back button and breadcrumb)
function updateNavigationUI() {
    const backButton = document.getElementById('backButton');
    const breadcrumbNav = document.getElementById('breadcrumbNav');
    const currentFolderName = document.getElementById('currentFolderName');
    const pageTitle = document.getElementById('pageTitle');
    const pageDescription = document.getElementById('pageDescription');
    
    if (currentFolder) {
        // Show back button and breadcrumb
        backButton.style.display = 'block';
        breadcrumbNav.style.display = 'block';
        
        // Update breadcrumb
        currentFolderName.textContent = currentFolder.fileName;
        
        // Update page title and description
        pageTitle.innerHTML = `<i class="fas fa-folder me-2"></i>${currentFolder.fileName}`;
        pageDescription.textContent = `Nội dung trong thư mục: ${currentFolder.fileName}`;
    } else {
        // Hide back button and breadcrumb
        backButton.style.display = 'none';
        breadcrumbNav.style.display = 'none';
        
        // Reset page title and description
        pageTitle.innerHTML = '<i class="fas fa-cloud-upload-alt me-2"></i>Quản lý File';
        pageDescription.textContent = 'Quản lý và tổ chức các file của bạn';
    }
}

function normalizePath(p) {
    // chuyển về dạng với dấu gạch chéo / để so sánh ổn định (Windows -> POSIX)
    // lưu ý: cần thay thế MỘT ký tự backslash, không phải cặp backslash
    return String(p).replace(/\\/g, '/');
}
