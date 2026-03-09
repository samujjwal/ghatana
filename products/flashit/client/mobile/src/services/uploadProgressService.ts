import * as FileSystem from 'expo-file-system';
import { atom } from 'jotai';
import { networkMonitor } from './networkMonitor';
import { mediaCompressionService } from './mediaCompressionService';

/**
 * Upload Progress Tracking Service
 * 
 * @doc.type service
 * @doc.purpose Track individual file upload progress with ETA calculation
 * @doc.layer product
 * @doc.pattern Service
 * 
 * Features:
 * - Individual file progress tracking
 * - Estimated time remaining calculation
 * - Upload speed monitoring
 * - Pause/resume functionality
 * - Network quality badges
 */

export interface UploadProgress {
  id: string;
  fileName: string;
  fileType: 'audio' | 'image' | 'video' | 'text';
  fileSizeBytes: number;
  uploadedBytes: number;
  progress: number; // 0-100
  status: 'queued' | 'compressing' | 'uploading' | 'paused' | 'completed' | 'failed';
  startTime: number | null;
  estimatedTimeRemainingMs: number;
  uploadSpeedBps: number; // Bytes per second
  error?: string;
  isPaused: boolean;
}

export interface UploadStats {
  totalFiles: number;
  completedFiles: number;
  failedFiles: number;
  totalBytes: number;
  uploadedBytes: number;
  overallProgress: number;
  estimatedTotalTimeMs: number;
  averageSpeedBps: number;
}

// Network speed estimates (in bytes per second)
const NETWORK_SPEEDS = {
  wifi: 6_250_000, // 50 Mbps = 6.25 MB/s
  '5g': 12_500_000, // 100 Mbps = 12.5 MB/s
  '4g': 1_250_000, // 10 Mbps = 1.25 MB/s
  '3g': 125_000, // 1 Mbps = 125 KB/s
  '2g': 25_000, // 200 Kbps = 25 KB/s
  cellular: 1_250_000, // Default cellular
  unknown: 625_000, // 5 Mbps = 625 KB/s (conservative)
};

/**
 * Upload Progress Service
 */
class UploadProgressService {
  private progressMap: Map<string, UploadProgress> = new Map();
  private listeners: Set<(progress: Map<string, UploadProgress>) => void> = new Set();
  private speedSamples: number[] = [];
  private lastUpdateTime: Map<string, number> = new Map();
  private lastUploadedBytes: Map<string, number> = new Map();

  /**
   * Start tracking an upload
   */
  startTracking(
    id: string,
    fileName: string,
    fileType: UploadProgress['fileType'],
    fileSizeBytes: number
  ): void {
    const networkType = networkMonitor.getNetworkType();
    const estimatedSpeed = this.getEstimatedSpeed();

    const progress: UploadProgress = {
      id,
      fileName,
      fileType,
      fileSizeBytes,
      uploadedBytes: 0,
      progress: 0,
      status: 'queued',
      startTime: null,
      estimatedTimeRemainingMs: this.calculateETA(fileSizeBytes, 0, estimatedSpeed),
      uploadSpeedBps: estimatedSpeed,
      isPaused: false,
    };

    this.progressMap.set(id, progress);
    this.notifyListeners();
  }

  /**
   * Update upload progress
   */
  updateProgress(id: string, uploadedBytes: number): void {
    const progress = this.progressMap.get(id);
    if (!progress) return;

    const now = Date.now();
    const previousTime = this.lastUpdateTime.get(id) || now;
    const previousBytes = this.lastUploadedBytes.get(id) || 0;
    const timeDelta = (now - previousTime) / 1000; // seconds

    // Calculate current speed
    let currentSpeed = progress.uploadSpeedBps;
    if (timeDelta > 0) {
      const bytesDelta = uploadedBytes - previousBytes;
      currentSpeed = bytesDelta / timeDelta;
      
      // Update speed samples for averaging
      this.speedSamples.push(currentSpeed);
      if (this.speedSamples.length > 10) {
        this.speedSamples.shift();
      }
    }

    // Calculate average speed
    const avgSpeed = this.speedSamples.length > 0
      ? this.speedSamples.reduce((a, b) => a + b, 0) / this.speedSamples.length
      : this.getEstimatedSpeed();

    const remainingBytes = progress.fileSizeBytes - uploadedBytes;
    const eta = this.calculateETA(remainingBytes, 0, avgSpeed);

    progress.uploadedBytes = uploadedBytes;
    progress.progress = Math.min(100, (uploadedBytes / progress.fileSizeBytes) * 100);
    progress.uploadSpeedBps = avgSpeed;
    progress.estimatedTimeRemainingMs = eta;

    if (!progress.startTime && uploadedBytes > 0) {
      progress.startTime = Date.now();
      progress.status = 'uploading';
    }

    this.lastUpdateTime.set(id, now);
    this.lastUploadedBytes.set(id, uploadedBytes);
    this.progressMap.set(id, progress);
    this.notifyListeners();
  }

  /**
   * Set status for an upload
   */
  setStatus(id: string, status: UploadProgress['status'], error?: string): void {
    const progress = this.progressMap.get(id);
    if (!progress) return;

    progress.status = status;
    if (error) {
      progress.error = error;
    }
    if (status === 'completed') {
      progress.progress = 100;
      progress.uploadedBytes = progress.fileSizeBytes;
      progress.estimatedTimeRemainingMs = 0;
    }

    this.progressMap.set(id, progress);
    this.notifyListeners();
  }

  /**
   * Pause an upload
   */
  pauseUpload(id: string): void {
    const progress = this.progressMap.get(id);
    if (!progress || progress.status === 'completed' || progress.status === 'failed') return;

    progress.isPaused = true;
    progress.status = 'paused';
    this.progressMap.set(id, progress);
    this.notifyListeners();
  }

  /**
   * Resume an upload
   */
  resumeUpload(id: string): void {
    const progress = this.progressMap.get(id);
    if (!progress || !progress.isPaused) return;

    progress.isPaused = false;
    progress.status = progress.uploadedBytes > 0 ? 'uploading' : 'queued';
    this.progressMap.set(id, progress);
    this.notifyListeners();
  }

  /**
   * Stop tracking an upload
   */
  stopTracking(id: string): void {
    this.progressMap.delete(id);
    this.lastUpdateTime.delete(id);
    this.lastUploadedBytes.delete(id);
    this.notifyListeners();
  }

  /**
   * Get progress for a specific upload
   */
  getProgress(id: string): UploadProgress | undefined {
    return this.progressMap.get(id);
  }

  /**
   * Get all active uploads
   */
  getAllProgress(): UploadProgress[] {
    return Array.from(this.progressMap.values());
  }

  /**
   * Get upload statistics
   */
  getStats(): UploadStats {
    const uploads = this.getAllProgress();
    
    const totalFiles = uploads.length;
    const completedFiles = uploads.filter(u => u.status === 'completed').length;
    const failedFiles = uploads.filter(u => u.status === 'failed').length;
    const totalBytes = uploads.reduce((sum, u) => sum + u.fileSizeBytes, 0);
    const uploadedBytes = uploads.reduce((sum, u) => sum + u.uploadedBytes, 0);
    const overallProgress = totalBytes > 0 ? (uploadedBytes / totalBytes) * 100 : 0;
    
    // Calculate average speed
    const activeUploads = uploads.filter(u => u.status === 'uploading');
    const averageSpeedBps = activeUploads.length > 0
      ? activeUploads.reduce((sum, u) => sum + u.uploadSpeedBps, 0) / activeUploads.length
      : this.getEstimatedSpeed();
    
    // Calculate total ETA
    const remainingBytes = totalBytes - uploadedBytes;
    const estimatedTotalTimeMs = this.calculateETA(remainingBytes, 0, averageSpeedBps);

    return {
      totalFiles,
      completedFiles,
      failedFiles,
      totalBytes,
      uploadedBytes,
      overallProgress,
      estimatedTotalTimeMs,
      averageSpeedBps,
    };
  }

  /**
   * Subscribe to progress updates
   */
  subscribe(listener: (progress: Map<string, UploadProgress>) => void): () => void {
    this.listeners.add(listener);
    listener(this.progressMap);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Get estimated network speed based on connection type
   */
  private getEstimatedSpeed(): number {
    const networkType = networkMonitor.getNetworkType();
    
    if (networkType === 'wifi') {
      return NETWORK_SPEEDS.wifi;
    }
    
    if (networkType === 'cellular') {
      const gen = networkMonitor.getCellularGeneration();
      return NETWORK_SPEEDS[gen || 'cellular'];
    }
    
    return NETWORK_SPEEDS.unknown;
  }

  /**
   * Calculate estimated time remaining
   */
  private calculateETA(remainingBytes: number, uploadedBytes: number, speedBps: number): number {
    if (speedBps <= 0 || remainingBytes <= 0) return 0;
    return (remainingBytes / speedBps) * 1000; // Convert to milliseconds
  }

  /**
   * Notify all listeners
   */
  private notifyListeners(): void {
    this.listeners.forEach(listener => listener(this.progressMap));
  }

  /**
   * Format time remaining for display
   */
  formatTimeRemaining(ms: number): string {
    if (ms <= 0) return 'Complete';
    if (ms < 1000) return 'Less than 1s';
    
    const seconds = Math.floor(ms / 1000);
    if (seconds < 60) return `${seconds}s`;
    
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) {
      return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
    }
    
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
  }

  /**
   * Format speed for display
   */
  formatSpeed(bps: number): string {
    if (bps < 1024) return `${Math.round(bps)} B/s`;
    if (bps < 1024 * 1024) return `${(bps / 1024).toFixed(1)} KB/s`;
    return `${(bps / (1024 * 1024)).toFixed(1)} MB/s`;
  }

  /**
   * Format file size for display
   */
  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }

  /**
   * Get network quality badge info
   */
  getNetworkBadge(): {
    label: string;
    color: string;
    icon: string;
  } {
    const networkType = networkMonitor.getNetworkType();
    
    if (!networkMonitor.isOnline()) {
      return { label: 'Offline', color: '#ff3b30', icon: '📵' };
    }
    
    if (networkType === 'wifi') {
      return { label: 'WiFi', color: '#34c759', icon: '📶' };
    }
    
    if (networkType === 'cellular') {
      const gen = networkMonitor.getCellularGeneration();
      switch (gen) {
        case '5g':
          return { label: '5G', color: '#34c759', icon: '📱' };
        case '4g':
          return { label: '4G', color: '#007aff', icon: '📱' };
        case '3g':
          return { label: '3G', color: '#ff9500', icon: '📱' };
        case '2g':
          return { label: '2G', color: '#ff3b30', icon: '📱' };
        default:
          return { label: 'Cellular', color: '#007aff', icon: '📱' };
      }
    }
    
    return { label: 'Unknown', color: '#888', icon: '❓' };
  }

  /**
   * Clear all tracking data
   */
  clear(): void {
    this.progressMap.clear();
    this.lastUpdateTime.clear();
    this.lastUploadedBytes.clear();
    this.speedSamples = [];
    this.notifyListeners();
  }
}

// Singleton instance
export const uploadProgressService = new UploadProgressService();

// Jotai atoms for reactive state
export const uploadProgressAtom = atom<UploadProgress[]>([]);
export const uploadStatsAtom = atom<UploadStats>({
  totalFiles: 0,
  completedFiles: 0,
  failedFiles: 0,
  totalBytes: 0,
  uploadedBytes: 0,
  overallProgress: 0,
  estimatedTotalTimeMs: 0,
  averageSpeedBps: 0,
});
