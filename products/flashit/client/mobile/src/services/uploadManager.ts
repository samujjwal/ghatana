import * as FileSystem from 'expo-file-system';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { offlineQueueService, QueuedItem } from './offlineQueue';
import { networkMonitor } from './networkMonitor';
import { mediaCompressionService } from './mediaCompressionService';

const MAX_RETRIES = 3;
const RETRY_DELAY_MS = 5000; // 5 seconds
const MAX_CONCURRENT_UPLOADS = 2;

/**
 * Upload Manager Service
 * 
 * @doc.type service
 * @doc.purpose Manage background uploads with retry logic
 * @doc.layer product
 * @doc.pattern Service
 */
class UploadManagerService {
  private isProcessing = false;
  private activeUploads = 0;

  /**
   * Start processing upload queue
   */
  async startProcessing(): Promise<void> {
    if (this.isProcessing) return;

    this.isProcessing = true;
    await this.processQueue();
  }

  /**
   * Stop processing
   */
  stopProcessing(): void {
    this.isProcessing = false;
  }

  /**
   * Process upload queue
   */
  private async processQueue(): Promise<void> {
    while (this.isProcessing) {
      // Check network connectivity
      if (!networkMonitor.isOnline()) {
        await this.delay(10000); // Wait 10s before checking again
        continue;
      }

      // Get pending items
      const pendingItems = await offlineQueueService.getPendingItems();

      if (pendingItems.length === 0) {
        await this.delay(5000); // Wait 5s before checking again
        continue;
      }

      // Process items with concurrency limit
      const itemsToProcess = pendingItems.slice(0, MAX_CONCURRENT_UPLOADS - this.activeUploads);

      await Promise.all(
        itemsToProcess.map((item) => this.uploadItem(item))
      );

      await this.delay(1000); // Short delay between batches
    }
  }

  /**
   * Upload single item
   */
  private async uploadItem(item: QueuedItem): Promise<void> {
    // Check retry limit
    if (item.metadata.retryCount >= MAX_RETRIES) {
      console.log(`Max retries reached for item ${item.id}`);
      return;
    }

    // Check retry delay
    if (item.metadata.lastAttempt) {
      const timeSinceLastAttempt = Date.now() - item.metadata.lastAttempt;
      if (timeSinceLastAttempt < RETRY_DELAY_MS) {
        return; // Too soon to retry
      }
    }

    this.activeUploads++;

    try {
      await offlineQueueService.updateItemStatus(item.id, 'uploading');

      // Upload based on type
      switch (item.type) {
        case 'audio':
          await this.uploadAudio(item);
          break;
        case 'image':
          await this.uploadImage(item);
          break;
        case 'video':
          await this.uploadVideo(item);
          break;
        case 'text':
          await this.uploadText(item);
          break;
      }

      await offlineQueueService.updateItemStatus(item.id, 'completed');
      console.log(`Successfully uploaded item ${item.id}`);
    } catch (error) {
      console.error(`Error uploading item ${item.id}:`, error);
      await offlineQueueService.updateItemStatus(item.id, 'failed', String(error));
    } finally {
      this.activeUploads--;
    }
  }

  /**
   * Upload audio file
   */
  private async uploadAudio(item: QueuedItem): Promise<void> {
    if (!item.uri) throw new Error('Audio URI is missing');

    let uploadUri = item.uri;

    // Create the moment first to get the ID
    const momentId = await this.createMoment(item);

    // Compress if enabled in settings
    const shouldAutoCompress = await AsyncStorage.getItem('settings:autoCompress');
    if (shouldAutoCompress !== null ? JSON.parse(shouldAutoCompress) : false) {
      const quality = await AsyncStorage.getItem('settings:uploadQuality') as 'high' | 'medium' | 'low' || 'medium';
      const networkType = networkMonitor.getNetworkType();

      const compressionResult = await mediaCompressionService.compressMedia(
        item.uri,
        'audio',
        quality,
        { networkType }
      );

      uploadUri = compressionResult.uri;
      console.log(`Audio compressed: ${compressionResult.originalSizeBytes} -> ${compressionResult.compressedSizeBytes} (${compressionResult.compressionRatio}%)`);
    }

    // Get file info
    const fileInfo = await FileSystem.getInfoAsync(uploadUri);
    if (!fileInfo.exists) throw new Error("File does not exist");

    // Get presigned URL
    const { uploadUrl, uploadId, s3Key } = await this.getPresignedUrl('audio', momentId, `${item.id}.m4a`);

    // Upload file
    await FileSystem.uploadAsync(presignedUrl, uploadUri, {
      httpMethod: 'PUT',
      uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
    });

    // Complete upload
    await this.completeUpload(uploadId, s3Key, fileInfo.size);
  }

  /**
   * Upload image file
   */
  private async uploadImage(item: QueuedItem): Promise<void> {
    if (!item.uri) throw new Error('Image URI is missing');

    let uploadUri = item.uri;

    // Create the moment first
    const momentId = await this.createMoment(item);

    // Compress if enabled in settings
    const shouldAutoCompress = await AsyncStorage.getItem('settings:autoCompress');
    if (shouldAutoCompress !== null ? JSON.parse(shouldAutoCompress) : false) {
      const quality = await AsyncStorage.getItem('settings:uploadQuality') as 'high' | 'medium' | 'low' || 'medium';
      const networkType = networkMonitor.getNetworkType();

      const compressionResult = await mediaCompressionService.compressMedia(
        item.uri,
        'image',
        quality,
        { networkType }
      );

      uploadUri = compressionResult.uri;
      console.log(`Image compressed: ${compressionResult.originalSizeBytes} -> ${compressionResult.compressedSizeBytes} (${compressionResult.compressionRatio}%)`);
    }

    // Get file info
    const fileInfo = await FileSystem.getInfoAsync(uploadUri);
    if (!fileInfo.exists) throw new Error("File does not exist");

    // Get presigned URL
    const { uploadUrl, uploadId, s3Key } = await this.getPresignedUrl('image', momentId, `${item.id}.jpg`);

    // Upload file
    await FileSystem.uploadAsync(presignedUrl, uploadUri, {
      httpMethod: 'PUT',
      uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
    });

    // Complete upload
    await this.completeUpload(uploadId, s3Key, fileInfo.size);
  }

  /**
   * Upload video file
   */
  private async uploadVideo(item: QueuedItem): Promise<void> {
    if (!item.uri) throw new Error('Video URI is missing');

    let uploadUri = item.uri;

    // Create the moment first
    const momentId = await this.createMoment(item);

    // Compress if enabled in settings
    const shouldAutoCompress = await AsyncStorage.getItem('settings:autoCompress');
    if (shouldAutoCompress !== null ? JSON.parse(shouldAutoCompress) : false) {
      const quality = await AsyncStorage.getItem('settings:uploadQuality') as 'high' | 'medium' | 'low' || 'medium';
      const networkType = networkMonitor.getNetworkType();

      const compressionResult = await mediaCompressionService.compressMedia(
        item.uri,
        'video',
        quality,
        { networkType }
      );

      uploadUri = compressionResult.uri;
      console.log(`Video compressed: ${compressionResult.originalSizeBytes} -> ${compressionResult.compressedSizeBytes} (${compressionResult.compressionRatio}%)`);
    }

    // Get file info
    const fileInfo = await FileSystem.getInfoAsync(uploadUri);
    if (!fileInfo.exists) throw new Error("File does not exist");

    // Get presigned URL
    const { uploadUrl, uploadId, s3Key } = await this.getPresignedUrl('video', momentId, `${item.id}.mp4`);

    // Upload file
    await FileSystem.uploadAsync(presignedUrl, uploadUri, {
      httpMethod: 'PUT',
      uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
    });

    // Complete upload
    await this.completeUpload(uploadId, s3Key, fileInfo.size);
  }

  /**
   * Upload text content
   */
  private async uploadText(item: QueuedItem): Promise<void> {
    await this.createMoment(item);
  }

  /**
   * Helper: Create backend moment
   */
  private async createMoment(item: QueuedItem): Promise<string> {
    const token = await AsyncStorage.getItem('auth.token');
    const response = await fetch(`${this.getApiUrl()}/api/moments`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        content: {
          text: item.content || "(Media Note)", // Placeholder for media moments
          type: item.type.toUpperCase(),
        },
        sphereId: item.metadata.sphereId,
        signals: {
          tags: item.metadata.tags,
          emotions: item.metadata.emotion ? [item.metadata.emotion] : undefined,
        },
        capturedAt: new Date(item.metadata.timestamp).toISOString(),
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Failed to create moment: ${response.status} ${errorText}`);
    }

    const data = await response.json();
    return data.id;
  }

  /**
   * Get presigned upload URL
   */
  private async getPresignedUrl(type: string, momentId: string, filename: string): Promise<{ uploadUrl: string, uploadId: string, s3Key: string }> {
    const token = await AsyncStorage.getItem('auth.token');
    const response = await fetch(`${this.getApiUrl()}/api/upload/presigned-url`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        momentId,
        fileType: type === 'audio' ? 'audio/m4a' : type === 'image' ? 'image/jpeg' : 'video/mp4', // Simple mapping
        fileName: filename,
        fileSize: 1024, // TODO: Get real file size
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Failed to get presigned URL: ${response.status} ${errorText}`);
    }

    const data = await response.json();
    return {
      uploadUrl: data.uploadUrl,
      uploadId: data.uploadId,
      s3Key: data.fileKey
    };
  }

  /**
   * Complete upload
   */
  private async completeUpload(uploadId: string, s3Key: string, size: number): Promise<void> {
    const token = await AsyncStorage.getItem('auth.token');
    const response = await fetch(`${this.getApiUrl()}/api/upload/complete`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        uploadId,
        s3Key,
        actualSize: size
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Failed to complete upload: ${response.status} ${errorText}`);
    }
  }

  /**
   * Get API URL (should be from config)
   */
  private getApiUrl(): string {
    // TODO: Get from environment config
    return 'http://localhost:2900';
  }

  /**
   * Get file extension for type
   */
  private getFileExtension(type: string): string {
    const extensions: Record<string, string> = {
      audio: 'm4a',
      image: 'jpg',
      video: 'mp4',
    };
    return extensions[type] || 'bin';
  }

  /**
   * Delay helper
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Get upload progress statistics
   */
  async getProgress(): Promise<{
    activeUploads: number;
    queueLength: number;
  }> {
    const pendingItems = await offlineQueueService.getPendingItems();
    return {
      activeUploads: this.activeUploads,
      queueLength: pendingItems.length,
    };
  }
}

export const uploadManager = new UploadManagerService();
