/**
 * Device capability detection and offline capture components
 * Production-grade device capability checks and offline queue management
 *
 * @doc.type components
 * @doc.purpose Device capability detection and offline capture management
 * @doc.layer platform
 * @doc.pattern ReactComponent
 */

import React, { useState, useEffect, useCallback } from 'react';
import { getDeviceCapabilities, DeviceCapabilities } from './media';

// Device capability state
export interface DeviceCapabilityState {
  capabilities: DeviceCapabilities | null;
  isLoading: boolean;
  hasCamera: boolean;
  hasMicrophone: boolean;
  isOnline: boolean;
  permissions: {
    camera: PermissionState;
    microphone: PermissionState;
  };
  error: string | null;
}

// Offline capture queue item
export interface OfflineCaptureItem {
  id: string;
  type: 'audio' | 'video' | 'text';
  data: Blob | string;
  metadata: {
    capturedAt: string;
    sphereId: string;
    emotions: string[];
    tags: string[];
    importance: number;
    fileName?: string;
    mimeType?: string;
    size: number;
  };
  status: 'pending' | 'uploading' | 'completed' | 'failed';
  attempts: number;
  lastAttempt?: string;
  error?: string;
}

// Offline queue manager
class OfflineCaptureQueue {
  private queue: OfflineCaptureItem[] = [];
  private isProcessing = false;
  private maxRetries = 3;
  private retryDelay = 5000; // 5 seconds

  constructor() {
    this.loadQueue();
    this.setupNetworkListener();
  }

  private loadQueue() {
    try {
      // Check if we're in a browser environment
      if (typeof window !== 'undefined' && window.localStorage) {
        const stored = localStorage.getItem('flashit_offline_queue');
        if (stored) {
          this.queue = JSON.parse(stored);
        }
      }
    } catch (error) {
      console.error('Failed to load offline queue:', error);
    }
  }

  private saveQueue() {
    try {
      // Check if we're in a browser environment
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem('flashit_offline_queue', JSON.stringify(this.queue));
      }
    } catch (error) {
      console.error('Failed to save offline queue:', error);
    }
  }

  private setupNetworkListener() {
    // Check if we're in a browser environment
    if (typeof window !== 'undefined' && window.addEventListener) {
      window.addEventListener('online', () => {
        console.log('Network online - processing offline queue');
        this.processQueue();
      });
    }
  }

  addItem(item: Omit<OfflineCaptureItem, 'id' | 'status' | 'attempts'>): string {
    const queueItem: OfflineCaptureItem = {
      ...item,
      id: `offline_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      status: 'pending',
      attempts: 0,
    };

    this.queue.push(queueItem);
    this.saveQueue();

    // Process immediately if online
    if (navigator.onLine) {
      this.processQueue();
    }

    return queueItem.id;
  }

  removeItem(id: string) {
    this.queue = this.queue.filter(item => item.id !== id);
    this.saveQueue();
  }

  getQueue(): OfflineCaptureItem[] {
    return [...this.queue];
  }

  getQueueStats() {
    return {
      total: this.queue.length,
      pending: this.queue.filter(item => item.status === 'pending').length,
      uploading: this.queue.filter(item => item.status === 'uploading').length,
      failed: this.queue.filter(item => item.status === 'failed').length,
      completed: this.queue.filter(item => item.status === 'completed').length,
    };
  }

  async processQueue() {
    if (this.isProcessing || !navigator.onLine) return;

    this.isProcessing = true;

    const pendingItems = this.queue.filter(
      item => item.status === 'pending' ||
        (item.status === 'failed' && item.attempts < this.maxRetries)
    );

    for (const item of pendingItems) {
      try {
        await this.processItem(item);
      } catch (error) {
        console.error('Failed to process queue item:', error);
      }
    }

    this.isProcessing = false;
  }

  private async processItem(item: OfflineCaptureItem) {
    item.status = 'uploading';
    item.attempts += 1;
    item.lastAttempt = new Date().toISOString();
    this.saveQueue();

    try {
      // First create the moment
      const momentResponse = await fetch('/api/moments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${typeof window !== 'undefined' && window.localStorage ? localStorage.getItem('flashit_token') : null}`,
        },
        body: JSON.stringify({
          sphereId: item.metadata.sphereId,
          content: {
            text: typeof item.data === 'string' ? item.data : `${item.type} captured offline`,
            type: item.type === 'text' ? 'TEXT' : item.type.toUpperCase(),
          },
          signals: {
            emotions: item.metadata.emotions,
            tags: [...item.metadata.tags, 'offline-captured'],
            importance: item.metadata.importance,
          },
          capturedAt: item.metadata.capturedAt,
        }),
      });

      if (!momentResponse.ok) {
        throw new Error(`Failed to create moment: ${momentResponse.status}`);
      }

      const { moment } = await momentResponse.json();

      // If it's media, upload the file
      if (item.type !== 'text' && item.data instanceof Blob) {
        await this.uploadMedia(moment.id, item);
      }

      item.status = 'completed';
      this.saveQueue();

      // Remove completed items after a delay
      setTimeout(() => this.removeItem(item.id), 30000); // 30 seconds

    } catch (error: any) {
      item.status = 'failed';
      item.error = error.message;

      if (item.attempts >= this.maxRetries) {
        console.error(`Failed to upload offline item after ${this.maxRetries} attempts:`, error);
      } else {
        // Retry after delay
        setTimeout(() => {
          item.status = 'pending';
          this.saveQueue();
        }, this.retryDelay * item.attempts);
      }

      this.saveQueue();
    }
  }

  private async uploadMedia(momentId: string, item: OfflineCaptureItem) {
    // Request presigned URL
    const presignedResponse = await fetch('/api/upload/presigned-url', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${typeof window !== 'undefined' && window.localStorage ? localStorage.getItem('flashit_token') : null}`,
      },
      body: JSON.stringify({
        fileName: item.metadata.fileName || `${item.type}_${item.id}.${item.type === 'audio' ? 'webm' : 'mp4'}`,
        fileType: item.metadata.mimeType || `${item.type}/webm`,
        fileSize: item.metadata.size,
        momentId,
      }),
    });

    if (!presignedResponse.ok) {
      throw new Error('Failed to get presigned URL');
    }

    const { uploadId, presignedUrl, s3Key } = await presignedResponse.json();

    // Upload to S3
    const s3Response = await fetch(presignedUrl, {
      method: 'PUT',
      body: item.data,
      headers: {
        'Content-Type': item.metadata.mimeType || `${item.type}/webm`,
      },
    });

    if (!s3Response.ok) {
      throw new Error('Failed to upload to S3');
    }

    // Complete upload
    const completeResponse = await fetch('/api/upload/complete', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${typeof window !== 'undefined' && window.localStorage ? localStorage.getItem('flashit_token') : null}`,
      },
      body: JSON.stringify({
        uploadId,
        s3Key,
        actualSize: item.metadata.size,
      }),
    });

    if (!completeResponse.ok) {
      throw new Error('Failed to complete upload');
    }
  }
}

// Singleton instance
const offlineQueue = new OfflineCaptureQueue();

/**
 * Device capability detection hook
 */
export function useDeviceCapabilities() {
  const [state, setState] = useState<DeviceCapabilityState>({
    capabilities: null,
    isLoading: true,
    hasCamera: false,
    hasMicrophone: false,
    isOnline: navigator.onLine,
    permissions: {
      camera: 'prompt',
      microphone: 'prompt',
    },
    error: null,
  });

  const checkCapabilities = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const capabilities = await getDeviceCapabilities();

      // Check permissions
      let cameraPermission: PermissionState = 'prompt';
      let microphonePermission: PermissionState = 'prompt';

      if ('permissions' in navigator) {
        try {
          const cameraResult = await navigator.permissions.query({ name: 'camera' as PermissionName });
          cameraPermission = cameraResult.state;
        } catch (e) {
          // Permission API not supported for camera
        }

        try {
          const microphoneResult = await navigator.permissions.query({ name: 'microphone' as PermissionName });
          microphonePermission = microphoneResult.state;
        } catch (e) {
          // Permission API not supported for microphone
        }
      }

      setState({
        capabilities,
        isLoading: false,
        hasCamera: capabilities.hasCamera,
        hasMicrophone: capabilities.hasMicrophone,
        isOnline: navigator.onLine,
        permissions: {
          camera: cameraPermission,
          microphone: microphonePermission,
        },
        error: null,
      });

    } catch (error: any) {
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error.message || 'Failed to detect device capabilities',
      }));
    }
  }, []);

  useEffect(() => {
    checkCapabilities();

    // Listen for network changes
    const handleOnline = () => setState(prev => ({ ...prev, isOnline: true }));
    const handleOffline = () => setState(prev => ({ ...prev, isOnline: false }));

    // Only add event listeners if we're in a browser environment
    if (typeof window !== 'undefined' && window.addEventListener) {
      window.addEventListener('online', handleOnline);
      window.addEventListener('offline', handleOffline);

      return () => {
        window.removeEventListener('online', handleOnline);
        window.removeEventListener('offline', handleOffline);
      };
    }
  }, [checkCapabilities]);

  const requestPermissions = useCallback(async (type: 'camera' | 'microphone' | 'both' = 'both') => {
    try {
      const constraints: MediaStreamConstraints = {};

      if (type === 'camera' || type === 'both') {
        constraints.video = true;
      }
      if (type === 'microphone' || type === 'both') {
        constraints.audio = true;
      }

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      stream.getTracks().forEach(track => track.stop());

      // Refresh capabilities after permission granted
      await checkCapabilities();

      return true;
    } catch (error) {
      console.error('Permission request failed:', error);
      return false;
    }
  }, [checkCapabilities]);

  return {
    ...state,
    requestPermissions,
    refresh: checkCapabilities,
  };
}

/**
 * Offline capture queue hook
 */
export function useOfflineQueue() {
  const [queue, setQueue] = useState<OfflineCaptureItem[]>([]);
  const [isProcessing, setIsProcessing] = useState(false);

  const refreshQueue = useCallback(() => {
    setQueue(offlineQueue.getQueue());
  }, []);

  useEffect(() => {
    refreshQueue();

    // Refresh queue periodically
    const interval = setInterval(refreshQueue, 5000);
    return () => clearInterval(interval);
  }, [refreshQueue]);

  const addToQueue = useCallback((item: Omit<OfflineCaptureItem, 'id' | 'status' | 'attempts'>) => {
    const id = offlineQueue.addItem(item);
    refreshQueue();
    return id;
  }, [refreshQueue]);

  const removeFromQueue = useCallback((id: string) => {
    offlineQueue.removeItem(id);
    refreshQueue();
  }, [refreshQueue]);

  const processQueue = useCallback(async () => {
    setIsProcessing(true);
    await offlineQueue.processQueue();
    setIsProcessing(false);
    refreshQueue();
  }, [refreshQueue]);

  const clearCompleted = useCallback(() => {
    const completedItems = queue.filter(item => item.status === 'completed');
    completedItems.forEach(item => offlineQueue.removeItem(item.id));
    refreshQueue();
  }, [queue, refreshQueue]);

  return {
    queue,
    stats: offlineQueue.getQueueStats(),
    isProcessing,
    addToQueue,
    removeFromQueue,
    processQueue,
    clearCompleted,
    refresh: refreshQueue,
  };
}

/**
 * Device Capability Status Component
 */
export function DeviceCapabilityStatus({ className = '' }: { className?: string }) {
  const { capabilities, isLoading, hasCamera, hasMicrophone, isOnline, permissions, error, requestPermissions } = useDeviceCapabilities();

  if (isLoading) {
    return (
      <div className={`bg-gray-50 rounded-lg p-4 ${className}`}>
        <div className="flex items-center space-x-2">
          <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-gray-600">Checking device capabilities...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={`bg-red-50 border border-red-200 rounded-lg p-4 ${className}`}>
        <div className="flex items-center space-x-2">
          <div className="w-5 h-5 text-red-500">⚠️</div>
          <span className="text-red-700">Error: {error}</span>
        </div>
      </div>
    );
  }

  return (
    <div className={`bg-white border border-gray-200 rounded-lg p-4 ${className}`}>
      <h3 className="font-semibold text-gray-900 mb-3">Device Status</h3>

      <div className="space-y-2">
        {/* Network Status */}
        <div className="flex items-center justify-between">
          <span className="text-gray-600">Network</span>
          <div className="flex items-center space-x-2">
            <div className={`w-2 h-2 rounded-full ${isOnline ? 'bg-green-500' : 'bg-red-500'}`} />
            <span className={`text-sm ${isOnline ? 'text-green-700' : 'text-red-700'}`}>
              {isOnline ? 'Online' : 'Offline'}
            </span>
          </div>
        </div>

        {/* Camera */}
        <div className="flex items-center justify-between">
          <span className="text-gray-600">Camera</span>
          <div className="flex items-center space-x-2">
            <div className={`w-2 h-2 rounded-full ${hasCamera && permissions.camera === 'granted' ? 'bg-green-500' :
              hasCamera ? 'bg-yellow-500' : 'bg-red-500'
              }`} />
            <span className="text-sm text-gray-700">
              {!hasCamera ? 'Not available' :
                permissions.camera === 'granted' ? 'Ready' :
                  permissions.camera === 'denied' ? 'Blocked' : 'Permission needed'}
            </span>
          </div>
        </div>

        {/* Microphone */}
        <div className="flex items-center justify-between">
          <span className="text-gray-600">Microphone</span>
          <div className="flex items-center space-x-2">
            <div className={`w-2 h-2 rounded-full ${hasMicrophone && permissions.microphone === 'granted' ? 'bg-green-500' :
              hasMicrophone ? 'bg-yellow-500' : 'bg-red-500'
              }`} />
            <span className="text-sm text-gray-700">
              {!hasMicrophone ? 'Not available' :
                permissions.microphone === 'granted' ? 'Ready' :
                  permissions.microphone === 'denied' ? 'Blocked' : 'Permission needed'}
            </span>
          </div>
        </div>

        {/* Supported Formats */}
        {capabilities && capabilities.supportedMimeTypes.length > 0 && (
          <div className="mt-3 pt-3 border-t border-gray-100">
            <span className="text-gray-600 text-sm">Supported formats:</span>
            <div className="flex flex-wrap gap-1 mt-1">
              {capabilities.supportedMimeTypes.slice(0, 3).map((type) => (
                <span key={type} className="px-2 py-1 bg-gray-100 text-gray-600 text-xs rounded">
                  {type.split('/')[1]}
                </span>
              ))}
              {capabilities.supportedMimeTypes.length > 3 && (
                <span className="px-2 py-1 bg-gray-100 text-gray-600 text-xs rounded">
                  +{capabilities.supportedMimeTypes.length - 3} more
                </span>
              )}
            </div>
          </div>
        )}

        {/* Permission Request Button */}
        {(hasCamera || hasMicrophone) &&
          (permissions.camera !== 'granted' || permissions.microphone !== 'granted') && (
            <div className="mt-3">
              <button
                onClick={() => requestPermissions('both')}
                className="w-full bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium py-2 px-4 rounded-md transition-colors"
              >
                Grant Permissions
              </button>
            </div>
          )}
      </div>
    </div>
  );
}

/**
 * Offline Queue Status Component
 */
export function OfflineQueueStatus({ className = '' }: { className?: string }) {
  const { queue, stats, isProcessing, processQueue, clearCompleted } = useOfflineQueue();

  if (stats.total === 0) {
    return null;
  }

  return (
    <div className={`bg-white border border-gray-200 rounded-lg p-4 ${className}`}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-gray-900">Offline Queue</h3>
        <span className="text-sm text-gray-500">{stats.total} items</span>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-2 mb-3">
        {stats.pending > 0 && (
          <div className="text-center">
            <div className="text-lg font-semibold text-yellow-600">{stats.pending}</div>
            <div className="text-xs text-gray-600">Pending</div>
          </div>
        )}
        {stats.uploading > 0 && (
          <div className="text-center">
            <div className="text-lg font-semibold text-blue-600">{stats.uploading}</div>
            <div className="text-xs text-gray-600">Uploading</div>
          </div>
        )}
        {stats.failed > 0 && (
          <div className="text-center">
            <div className="text-lg font-semibold text-red-600">{stats.failed}</div>
            <div className="text-xs text-gray-600">Failed</div>
          </div>
        )}
        {stats.completed > 0 && (
          <div className="text-center">
            <div className="text-lg font-semibold text-green-600">{stats.completed}</div>
            <div className="text-xs text-gray-600">Completed</div>
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex space-x-2">
        {(stats.pending > 0 || stats.failed > 0) && navigator.onLine && (
          <button
            onClick={processQueue}
            disabled={isProcessing}
            className="flex-1 bg-blue-500 hover:bg-blue-600 disabled:bg-gray-300 text-white text-sm font-medium py-2 px-3 rounded-md transition-colors"
          >
            {isProcessing ? 'Processing...' : 'Sync Now'}
          </button>
        )}
        {stats.completed > 0 && (
          <button
            onClick={clearCompleted}
            className="bg-gray-500 hover:bg-gray-600 text-white text-sm font-medium py-2 px-3 rounded-md transition-colors"
          >
            Clear
          </button>
        )}
      </div>

      {/* Network Status Warning */}
      {!navigator.onLine && stats.pending > 0 && (
        <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded text-sm text-yellow-700">
          Items will sync automatically when you're back online.
        </div>
      )}
    </div>
  );
}
