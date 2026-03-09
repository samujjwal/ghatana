/**
 * WebSocket client utilities for real-time data streaming
 * 
 * Provides reconnection logic, heartbeat monitoring, and error handling
 */

import { io, Socket } from 'socket.io-client';

export interface WebSocketConfig {
    url: string;
    namespace: string;
    reconnectionDelay?: number;
    maxReconnectionAttempts?: number;
    heartbeatInterval?: number;
}

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting' | 'error';

export class WebSocketClient {
    private socket: Socket | null = null;
    private reconnectionAttempts = 0;
    private reconnectionTimer: ReturnType<typeof setTimeout> | null = null;
    private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
    private statusCallbacks: Set<(status: ConnectionStatus) => void> = new Set();
    
    constructor(private config: WebSocketConfig) {
        this.config = {
            reconnectionDelay: 2000,
            maxReconnectionAttempts: 10,
            heartbeatInterval: 30000,
            ...config,
        };
    }

    /**
     * Connect to WebSocket server
     */
    connect(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.updateStatus('connecting');

            const fullUrl = `${this.config.url}${this.config.namespace}`;
            
            this.socket = io(fullUrl, {
                transports: ['websocket'],
                autoConnect: true,
                reconnection: false, // We'll handle reconnection manually
            });

            this.socket.on('connect', () => {
                console.log('[WebSocket] Connected to', this.config.namespace);
                this.reconnectionAttempts = 0;
                this.updateStatus('connected');
                this.startHeartbeat();
                resolve();
            });

            this.socket.on('disconnect', (reason) => {
                console.log('[WebSocket] Disconnected:', reason);
                this.updateStatus('disconnected');
                this.stopHeartbeat();
                
                // Auto-reconnect unless manually disconnected
                if (reason !== 'io client disconnect') {
                    this.scheduleReconnection();
                }
            });

            this.socket.on('connect_error', (error) => {
                console.error('[WebSocket] Connection error:', error);
                this.updateStatus('error');
                reject(error);
                this.scheduleReconnection();
            });

            this.socket.on('error', (error) => {
                console.error('[WebSocket] Socket error:', error);
                this.updateStatus('error');
            });

            // Heartbeat response
            this.socket.on('heartbeat', () => {
                this.socket?.emit('pong');
            });
        });
    }

    /**
     * Disconnect from WebSocket server
     */
    disconnect(): void {
        if (this.reconnectionTimer) {
            clearTimeout(this.reconnectionTimer);
            this.reconnectionTimer = null;
        }
        
        this.stopHeartbeat();
        
        if (this.socket) {
            this.socket.disconnect();
            this.socket = null;
        }
        
        this.updateStatus('disconnected');
    }

    /**
     * Emit an event to the server
     */
    emit(event: string, data?: unknown): void {
        if (!this.socket?.connected) {
            console.warn('[WebSocket] Cannot emit, not connected');
            return;
        }
        this.socket.emit(event, data);
    }

    /**
     * Listen to an event from the server
     */
    on(event: string, callback: (...args: any[]) => void): void {
        this.socket?.on(event, callback);
    }

    /**
     * Remove event listener
     */
    off(event: string, callback?: (...args: any[]) => void): void {
        if (callback) {
            this.socket?.off(event, callback);
        } else {
            this.socket?.off(event);
        }
    }

    /**
     * Subscribe to connection status changes
     */
    onStatusChange(callback: (status: ConnectionStatus) => void): () => void {
        this.statusCallbacks.add(callback);
        // Call immediately with current status
        callback(this.getStatus());
        
        // Return unsubscribe function
        return () => {
            this.statusCallbacks.delete(callback);
        };
    }

    /**
     * Get current connection status
     */
    getStatus(): ConnectionStatus {
        if (!this.socket) return 'disconnected';
        if (this.socket.connected) return 'connected';
        if (this.reconnectionTimer) return 'reconnecting';
        return 'disconnected';
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private scheduleReconnection(): void {
        if (this.reconnectionTimer) return;
        
        const maxAttempts = this.config.maxReconnectionAttempts || 10;
        if (this.reconnectionAttempts >= maxAttempts) {
            console.error('[WebSocket] Max reconnection attempts reached');
            this.updateStatus('error');
            return;
        }

        this.reconnectionAttempts++;
        const delay = Math.min(
            (this.config.reconnectionDelay || 2000) * Math.pow(2, this.reconnectionAttempts - 1),
            30000 // Max 30 seconds
        );

        console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${this.reconnectionAttempts}/${maxAttempts})`);
        this.updateStatus('reconnecting');

        this.reconnectionTimer = setTimeout(() => {
            this.reconnectionTimer = null;
            this.connect().catch((error) => {
                console.error('[WebSocket] Reconnection failed:', error);
            });
        }, delay);
    }

    /**
     * Start heartbeat monitoring
     */
    private startHeartbeat(): void {
        this.stopHeartbeat();
        
        this.heartbeatTimer = setInterval(() => {
            if (this.socket?.connected) {
                this.socket.emit('ping');
            }
        }, this.config.heartbeatInterval || 30000);
    }

    /**
     * Stop heartbeat monitoring
     */
    private stopHeartbeat(): void {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    /**
     * Update connection status and notify callbacks
     */
    private updateStatus(status: ConnectionStatus): void {
        this.statusCallbacks.forEach(callback => callback(status));
    }
}
