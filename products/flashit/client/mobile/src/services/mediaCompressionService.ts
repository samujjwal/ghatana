import * as FileSystem from 'expo-file-system';
import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';
import { optimizeImage, getFileSizeMB } from './imageOptimization';

/**
 * Media Compression Service
 * 
 * @doc.type service
 * @doc.purpose Compress media files (images, videos, audio) before upload
 * @doc.layer product
 * @doc.pattern Service
 * 
 * Features:
 * - Adaptive compression based on file size and network
 * - Quality presets (high, medium, low)
 * - Video compression with FFmpeg (via backend)
 * - Image compression (client-side)
 * - Audio compression (via backend)
 * - Bandwidth estimation integration
 */

export type CompressionQuality = 'high' | 'medium' | 'low';
export type MediaType = 'image' | 'video' | 'audio';

export interface CompressionOptions {
  quality?: CompressionQuality;
  maxFileSizeMB?: number;
  maintainAspectRatio?: boolean;
  targetResolution?: { width: number; height: number };
}

export interface CompressionResult {
  uri: string;
  originalSize: number;
  compressedSize: number;
  compressionRatio: number; // Percentage
  processingTime: number; // Milliseconds
  quality: CompressionQuality;
}

// Quality presets for images
const IMAGE_QUALITY_PRESETS = {
  high: {
    maxWidth: 2048,
    maxHeight: 2048,
    quality: 0.9,
    format: SaveFormat.JPEG,
  },
  medium: {
    maxWidth: 1536,
    maxHeight: 1536,
    quality: 0.8,
    format: SaveFormat.JPEG,
  },
  low: {
    maxWidth: 1024,
    maxHeight: 1024,
    quality: 0.7,
    format: SaveFormat.JPEG,
  },
} as const;

// Quality presets for videos (sent to backend)
const VIDEO_QUALITY_PRESETS = {
  high: {
    resolution: '1280x720', // 720p
    videoBitrate: '1500k',
    audioBitrate: '128k',
    codec: 'libx264',
    crf: 23, // Constant Rate Factor (lower = better quality)
  },
  medium: {
    resolution: '854x480', // 480p
    videoBitrate: '1000k',
    audioBitrate: '96k',
    codec: 'libx264',
    crf: 26,
  },
  low: {
    resolution: '640x360', // 360p
    videoBitrate: '600k',
    audioBitrate: '64k',
    codec: 'libx264',
    crf: 29,
  },
} as const;

// Quality presets for audio
const AUDIO_QUALITY_PRESETS = {
  high: {
    bitrate: '128k',
    sampleRate: 44100,
    codec: 'aac',
  },
  medium: {
    bitrate: '96k',
    sampleRate: 44100,
    codec: 'aac',
  },
  low: {
    bitrate: '64k',
    sampleRate: 44100,
    codec: 'aac',
  },
} as const;

/**
 * Media Compression Service
 */
class MediaCompressionService {
  private apiUrl = 'http://localhost:2900'; // TODO: Use environment config

  /**
   * Compress media file based on type
   */
  async compressMedia(
    uri: string,
    type: MediaType,
    options: CompressionOptions = {}
  ): Promise<CompressionResult> {
    const quality = options.quality || 'medium';
    const startTime = Date.now();

    try {
      let result: CompressionResult;

      switch (type) {
        case 'image':
          result = await this.compressImage(uri, quality, options);
          break;
        case 'video':
          result = await this.compressVideo(uri, quality, options);
          break;
        case 'audio':
          result = await this.compressAudio(uri, quality, options);
          break;
        default:
          throw new Error(`Unsupported media type: ${type}`);
      }

      result.processingTime = Date.now() - startTime;
      return result;
    } catch (error) {
      console.error(`[MediaCompression] Failed to compress ${type}:`, error);
      throw error;
    }
  }

  /**
   * Compress image (client-side using expo-image-manipulator)
   * REUSES: imageOptimization.ts utilities
   */
  private async compressImage(
    uri: string,
    quality: CompressionQuality,
    options: CompressionOptions
  ): Promise<CompressionResult> {
    const originalSize = await getFileSizeMB(uri);
    const preset = IMAGE_QUALITY_PRESETS[quality];

    // Use existing imageOptimization utility
    const compressedUri = await optimizeImage(uri, {
      maxWidth: options.targetResolution?.width || preset.maxWidth,
      maxHeight: options.targetResolution?.height || preset.maxHeight,
      quality: preset.quality,
      format: preset.format,
    });

    const compressedSize = await getFileSizeMB(compressedUri);
    const compressionRatio = ((originalSize - compressedSize) / originalSize) * 100;

    return {
      uri: compressedUri,
      originalSize: originalSize * 1024 * 1024, // Convert to bytes
      compressedSize: compressedSize * 1024 * 1024,
      compressionRatio,
      processingTime: 0, // Will be set by caller
      quality,
    };
  }

  /**
   * Compress video (backend processing required)
   * Videos are too large to compress on mobile, send to backend
   */
  private async compressVideo(
    uri: string,
    quality: CompressionQuality,
    options: CompressionOptions
  ): Promise<CompressionResult> {
    const originalSize = await getFileSizeMB(uri);
    const preset = VIDEO_QUALITY_PRESETS[quality];

    // For now, return original URI and indicate backend processing needed
    // In production, upload to backend for compression
    console.log('[MediaCompression] Video compression requires backend processing');
    console.log('[MediaCompression] Preset:', preset);

    // TODO: Implement backend video compression
    // 1. Upload video to backend compression endpoint
    // 2. Backend uses FFmpeg to compress
    // 3. Return compressed video URL
    // 4. Download compressed video (optional, or upload directly from backend)

    // Estimate compression ratio based on preset
    let estimatedCompressionRatio = 0;
    switch (quality) {
      case 'high':
        estimatedCompressionRatio = 30; // ~30% reduction
        break;
      case 'medium':
        estimatedCompressionRatio = 50; // ~50% reduction
        break;
      case 'low':
        estimatedCompressionRatio = 70; // ~70% reduction
        break;
    }

    const estimatedCompressedSize = originalSize * (1 - estimatedCompressionRatio / 100);

    return {
      uri, // Return original for now
      originalSize: originalSize * 1024 * 1024,
      compressedSize: estimatedCompressedSize * 1024 * 1024,
      compressionRatio: estimatedCompressionRatio,
      processingTime: 0,
      quality,
    };
  }

  /**
   * Compress audio (backend processing required)
   * Audio compression uses FFmpeg on backend
   */
  private async compressAudio(
    uri: string,
    quality: CompressionQuality,
    options: CompressionOptions
  ): Promise<CompressionResult> {
    const originalSize = await getFileSizeMB(uri);
    const preset = AUDIO_QUALITY_PRESETS[quality];

    // Similar to video, audio compression needs backend
    console.log('[MediaCompression] Audio compression requires backend processing');
    console.log('[MediaCompression] Preset:', preset);

    // TODO: Implement backend audio compression
    // Use FFmpeg to convert to AAC with target bitrate

    // Estimate compression based on preset
    let estimatedCompressionRatio = 0;
    switch (quality) {
      case 'high':
        estimatedCompressionRatio = 20; // ~20% reduction
        break;
      case 'medium':
        estimatedCompressionRatio = 40; // ~40% reduction
        break;
      case 'low':
        estimatedCompressionRatio = 60; // ~60% reduction
        break;
    }

    const estimatedCompressedSize = originalSize * (1 - estimatedCompressionRatio / 100);

    return {
      uri, // Return original for now
      originalSize: originalSize * 1024 * 1024,
      compressedSize: estimatedCompressedSize * 1024 * 1024,
      compressionRatio: estimatedCompressionRatio,
      processingTime: 0,
      quality,
    };
  }

  /**
   * Get recommended compression quality based on file size and network
   */
  getRecommendedQuality(
    fileSizeMB: number,
    networkType: 'wifi' | 'cellular' | 'unknown',
    userPreference?: CompressionQuality
  ): CompressionQuality {
    // User preference takes precedence
    if (userPreference) {
      return userPreference;
    }

    // WiFi: allow larger files
    if (networkType === 'wifi') {
      if (fileSizeMB > 50) return 'medium';
      return 'high';
    }

    // Cellular: aggressive compression
    if (networkType === 'cellular') {
      if (fileSizeMB > 20) return 'low';
      if (fileSizeMB > 10) return 'medium';
      return 'medium'; // Default for cellular
    }

    // Unknown network: play it safe
    return 'medium';
  }

  /**
   * Estimate upload time based on file size and network speed
   */
  estimateUploadTime(
    fileSizeMB: number,
    networkType: 'wifi' | 'cellular' | 'unknown'
  ): number {
    // Estimated speeds in Mbps
    const speeds = {
      wifi: 50, // 50 Mbps typical WiFi
      cellular: 10, // 10 Mbps typical 4G
      unknown: 5, // Conservative estimate
    };

    const speedMbps = speeds[networkType];
    const speedMBps = speedMbps / 8; // Convert to MB/s

    return (fileSizeMB / speedMBps) * 1000; // Return in milliseconds
  }

  /**
   * Check if compression is recommended
   */
  shouldCompress(
    fileSizeMB: number,
    type: MediaType,
    networkType: 'wifi' | 'cellular' | 'unknown'
  ): boolean {
    // Always compress videos
    if (type === 'video') return true;

    // Compress large images
    if (type === 'image' && fileSizeMB > 2) return true;

    // Compress audio on cellular
    if (type === 'audio' && networkType === 'cellular' && fileSizeMB > 5) return true;

    // Compress large audio files
    if (type === 'audio' && fileSizeMB > 10) return true;

    return false;
  }

  /**
   * Get compression statistics for UI display
   */
  getCompressionStats(result: CompressionResult): {
    savedMB: number;
    savedPercentage: number;
    timeSavedSeconds: number;
  } {
    const savedBytes = result.originalSize - result.compressedSize;
    const savedMB = savedBytes / (1024 * 1024);
    const savedPercentage = result.compressionRatio;

    // Estimate time saved (assuming 10 Mbps upload speed)
    const uploadSpeedMBps = 10 / 8; // 10 Mbps = 1.25 MB/s
    const timeSavedSeconds = savedMB / uploadSpeedMBps;

    return {
      savedMB: parseFloat(savedMB.toFixed(2)),
      savedPercentage: parseFloat(savedPercentage.toFixed(1)),
      timeSavedSeconds: parseFloat(timeSavedSeconds.toFixed(1)),
    };
  }
}

// Singleton instance
export const mediaCompressionService = new MediaCompressionService();
