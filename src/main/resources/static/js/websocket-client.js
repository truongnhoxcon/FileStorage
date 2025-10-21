// WebSocket Client for Real-time Features
class WebSocketClient {
    constructor() {
        this.socket = null;
        this.stomp = null;
        this.isConnected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectInterval = 3000;
        this.token = localStorage.getItem('token');
    }

    connect() {
        if (!this.token) {
            console.log('No token found, cannot connect to WebSocket');
            return;
        }

        try {
            // SockJS transport + STOMP subprotocol
            this.socket = new SockJS('/ws');
            this.stomp = Stomp.over(this.socket);
            // Optional: silence debug logs
            if (this.stomp && this.stomp.debug) {
                this.stomp.debug = null;
            }

            this.stomp.connect(
                { 'Authorization': 'Bearer ' + this.token },
                () => {
                    console.log('STOMP connected');
                    this.isConnected = true;
                    this.reconnectAttempts = 0;
                    this.onConnected();
                },
                (error) => {
                    console.error('STOMP connection error:', error);
                    this.isConnected = false;
                    this.onDisconnected();
                    this.attemptReconnect();
                }
            );

        } catch (error) {
            console.error('Failed to create WebSocket connection:', error);
        }
    }

    disconnect() {
        try {
            if (this.stomp && this.stomp.connected) {
                this.stomp.disconnect(() => {
                    this.isConnected = false;
                    this.onDisconnected();
                });
            }
        } catch (_) {}
        this.socket = null;
        this.stomp = null;
    }

    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`Attempting to reconnect... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            
            setTimeout(() => {
                this.connect();
            }, this.reconnectInterval);
        } else {
            console.error('Max reconnection attempts reached');
        }
    }

    sendMessage(destination, message) {
        if (this.stomp && this.isConnected) {
            this.stomp.send(destination, {}, JSON.stringify(message));
        } else {
            console.warn('WebSocket not connected, cannot send message');
        }
    }

    subscribeToNotifications() {
        if (this.stomp && this.isConnected) {
            this.stomp.subscribe('/user/queue/notifications', (msg) => this.handleFrame(msg));
            this.stomp.subscribe('/topic/notifications', (msg) => this.handleFrame(msg));
            this.stomp.subscribe('/topic/file-updates', (msg) => this.handleFrame(msg));
            this.stomp.subscribe('/topic/user-activity', (msg) => this.handleFrame(msg));
        }
    }

    handleFrame(frame) {
        try {
            const body = frame && frame.body ? JSON.parse(frame.body) : null;
            if (!body) return;
            this.routeMessage(body);
        } catch (e) {
            console.error('Error handling STOMP frame:', e);
        }
    }

    handleMessage(event) {
        try {
            const message = JSON.parse(event.data);
            console.log('Received WebSocket message:', message);
            this.routeMessage(message);
        } catch (error) {
            console.error('Error parsing WebSocket message:', error);
        }
    }

    routeMessage(message) {
        switch (message.type) {
            case 'notification':
                this.handleNotification(message);
                break;
            case 'file-update':
                this.handleFileUpdate(message);
                break;
            case 'user-activity':
                this.handleUserActivity(message);
                break;
            case 'chat':
                this.handleChatMessage(message);
                break;
            case 'file-status':
                this.handleFileStatus(message);
                break;
            default:
                console.log('Unknown message type:', message.type);
        }
    }

    handleNotification(notification) {
        console.log('Notification received:', notification);
        
        // Show notification in UI
        this.showNotification(notification);
        
        // Update notification count if exists
        this.updateNotificationCount();
    }

    handleFileUpdate(update) {
        console.log('File update received:', update);
        
        // Refresh file list if on file manager page
        if (typeof loadFiles === 'function') {
            loadFiles();
        }
        
        // Show file update notification
        this.showFileUpdateNotification(update);
    }

    handleUserActivity(activity) {
        console.log('User activity received:', activity);
        
        // Update online users list if exists
        this.updateOnlineUsers(activity);
    }

    handleChatMessage(message) {
        console.log('Chat message received:', message);
        
        // Display chat message if chat is open
        this.displayChatMessage(message);
    }

    handleFileStatus(status) {
        console.log('File status update:', status);
        
        // Update file upload/download progress
        this.updateFileProgress(status);
    }

    showNotification(notification) {
        // Create notification element
        const notificationElement = document.createElement('div');
        notificationElement.className = `notification notification-${notification.type}`;
        notificationElement.innerHTML = `
            <div class="notification-content">
                <div class="notification-title">${notification.title}</div>
                <div class="notification-message">${notification.message}</div>
                <div class="notification-time">${new Date(notification.timestamp).toLocaleTimeString()}</div>
            </div>
            <button class="notification-close" onclick="this.parentElement.remove()">×</button>
        `;

        // Add to notification container
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

    showFileUpdateNotification(update) {
        const actionText = {
            'upload': 'đã tải lên',
            'download': 'đã tải xuống',
            'delete': 'đã xóa'
        };

        const message = `File "${update.fileName}" ${actionText[update.action] || update.action}`;
        
        this.showNotification({
            type: 'info',
            title: 'File Update',
            message: message,
            timestamp: update.timestamp
        });
    }

    updateNotificationCount() {
        const countElement = document.getElementById('notification-count');
        if (countElement) {
            const currentCount = parseInt(countElement.textContent) || 0;
            countElement.textContent = currentCount + 1;
            countElement.style.display = currentCount + 1 > 0 ? 'block' : 'none';
        }
    }

    updateOnlineUsers(activity) {
        // This would update an online users list if implemented
        console.log('Online users update:', activity);
    }

    displayChatMessage(message) {
        // This would display chat messages if chat is implemented
        console.log('Chat message:', message);
    }

    updateFileProgress(status) {
        // Update progress bars for file operations
        const progressElement = document.querySelector('.progress-bar');
        if (progressElement && status.progress !== undefined) {
            progressElement.style.width = status.progress + '%';
        }
    }

    onConnected() {
        console.log('WebSocket connected successfully');
        this.subscribeToNotifications();
        
        // Show connection status
        if (typeof handleWebSocketStatus === 'function') {
            handleWebSocketStatus(true);
        }
        this.updateConnectionDot('connected');
    }

    onDisconnected() {
        console.log('WebSocket disconnected');
        if (typeof handleWebSocketStatus === 'function') {
            handleWebSocketStatus(false);
        }
        this.updateConnectionDot('disconnected');
    }

    onError(error) {
        console.error('WebSocket error:', error);
        if (typeof handleWebSocketStatus === 'function') {
            handleWebSocketStatus(false);
        }
        this.updateConnectionDot('disconnected');
    }

    updateConnectionDot(status) {
        const statusDot = document.querySelector('#connectionStatus .status-dot');
        if (statusDot) {
            statusDot.className = 'status-dot ' + status;
        }
    }

    // Public methods for sending messages
    sendChatMessage(message) {
        this.sendMessage('/app/chat', {
            message: message,
            sender: this.getCurrentUsername()
        });
    }

    sendFileStatus(fileName, status, progress) {
        this.sendMessage('/app/file-status', {
            fileName: fileName,
            status: status,
            progress: progress
        });
    }

    sendUserTyping(isTyping) {
        this.sendMessage('/app/user-typing', {
            userId: this.getCurrentUserId(),
            username: this.getCurrentUsername(),
            isTyping: isTyping
        });
    }

    getCurrentUsername() {
        return document.getElementById('userName')?.textContent || 'Unknown';
    }

    getCurrentUserId() {
        // This would get the current user ID from the page
        return window.currentUser?.id || null;
    }
}

// Initialize WebSocket client when page loads
let wsClient = null;

document.addEventListener('DOMContentLoaded', function() {
    const token = localStorage.getItem('token');
    if (token) {
        wsClient = new WebSocketClient();
        wsClient.connect();
    }
});

// Reconnect when token changes (after login)
window.addEventListener('storage', function(e) {
    if (e.key === 'token') {
        if (wsClient) {
            wsClient.disconnect();
        }
        if (e.newValue) {
            wsClient = new WebSocketClient();
            wsClient.connect();
        }
    }
});

