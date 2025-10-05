// File Manager JavaScript
let currentUser = null;
let currentFiles = [];
let currentCategory = 'all';
let selectedFiles = new Set();

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
    
    fetch(`/api/files/user/${currentUser.id}`, {
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
    
    if (currentCategory !== 'all') {
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
    
    return `
        <div class="file-item" data-file-id="${file.id}" onclick="selectFile(${file.id})">
            <div class="file-icon ${fileIcon.class}">
                <i class="${fileIcon.icon}"></i>
            </div>
            <div class="file-name" title="${file.fileName}">${file.fileName}</div>
            <div class="file-size">${fileSize}</div>
            <div class="file-date">${uploadDate}</div>
            <div class="file-actions">
                <button class="file-action-btn download" onclick="event.stopPropagation(); downloadFile(${file.id})" title="Tải xuống">
                    <i class="fas fa-download"></i>
                </button>
                <button class="file-action-btn info" onclick="event.stopPropagation(); showFileDetails(${file.id})" title="Chi tiết">
                    <i class="fas fa-info"></i>
                </button>
                <button class="file-action-btn delete" onclick="event.stopPropagation(); deleteFile(${file.id})" title="Xóa">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        </div>
    `;
}

// Get file icon based on type
function getFileIcon(fileType) {
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
            loadFiles(); // Reload files
            showNotification('info', 'File đã xóa', 'File đã được xóa thành công!');
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
    modal.show();
}

// Setup event listeners
function setupEventListeners() {
    // Upload area click
    document.getElementById('uploadArea').addEventListener('click', () => {
        document.getElementById('fileInput').click();
    });
    
    // File input change
    document.getElementById('fileInput').addEventListener('change', (e) => {
        const files = e.target.files;
        if (files.length > 0) {
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
    document.getElementById('uploadProgress').style.display = 'block';
    const progressBar = document.querySelector('.progress-bar');
    
    fetch('/api/files/upload', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + localStorage.getItem('token')
        },
        body: formData
    })
    .then(response => {
        console.log('Upload response status:', response.status);
        console.log('Upload response headers:', response.headers);
        
        if (!response.ok) {
            // Try to get error message from response
            return response.text().then(text => {
                console.error('Upload error response:', text);
                throw new Error(`HTTP error! status: ${response.status}, message: ${text}`);
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('Upload successful:', data);
        loadFiles(); // Reload file list
        bootstrap.Modal.getInstance(document.getElementById('uploadModal')).hide();
        
        // Show success notification
        showNotification('success', 'Upload thành công!', `File "${data.fileName}" đã được tải lên thành công.`);
    })
    .catch(error => {
        console.error('Upload error:', error);
        alert('Có lỗi xảy ra khi upload file: ' + error.message);
    })
    .finally(() => {
        document.getElementById('uploadProgress').style.display = 'none';
        progressBar.style.width = '0%';
    });
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
        'delete': 'đã xóa'
    };
    
    const message = `File "${update.fileName}" ${actionText[update.action] || update.action}`;
    showNotification('info', 'File Update', message);
}

// WebSocket connection status handler
function handleWebSocketStatus(connected) {
    const liveIndicator = document.getElementById('liveIndicator');
    if (liveIndicator) {
        liveIndicator.style.display = connected ? 'inline-flex' : 'none';
    }
}
