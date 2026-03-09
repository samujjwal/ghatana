import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system';
import { mediaCompressionService } from '../../src/services/mediaCompressionService';
import { optimizeImage, getFileSizeMB } from '../../src/utils/imageOptimization';

// Mock dependencies
jest.mock('@react-native-async-storage/async-storage');
jest.mock('expo-file-system');
jest.mock('../../src/utils/imageOptimization');

describe('MediaCompressionService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
  });

  describe('compressMedia', () => {
    describe('image compression', () => {
      it('should compress image with high quality', async () => {
        const mockUri = 'file:///path/to/image.jpg';
        const mockCompressedUri = 'file:///path/to/compressed.jpg';
        const mockOriginalSize = 5242880; // 5MB
        const mockCompressedSize = 2097152; // 2MB

        (FileSystem.getInfoAsync as jest.Mock).mockResolvedValueOnce({
          exists: true,
          size: mockOriginalSize,
        });

        (optimizeImage as jest.Mock).mockResolvedValue(mockCompressedUri);

        (FileSystem.getInfoAsync as jest.Mock).mockResolvedValueOnce({
          exists: true,
          size: mockCompressedSize,
        });

        const result = await mediaCompressionService.compressMedia(
          mockUri,
          'image',
          'high'
        );

        expect(result).toEqual({
          uri: mockCompressedUri,
          originalSizeBytes: mockOriginalSize,
          compressedSizeBytes: mockCompressedSize,
          compressionRatio: 60, // (5MB - 2MB) / 5MB * 100
        });

        expect(optimizeImage).toHaveBeenCalledWith(mockUri, {
          quality: 0.9,
          maxWidth: 1920,
          maxHeight: 1920,
        });
      });

      it('should compress image with medium quality', async () => {
        const mockUri = 'file:///path/to/image.jpg';
        const mockCompressedUri = 'file:///path/to/compressed.jpg';

        (FileSystem.getInfoAsync as jest.Mock)
          .mockResolvedValueOnce({ exists: true, size: 5242880 })
          .mockResolvedValueOnce({ exists: true, size: 1048576 });

        (optimizeImage as jest.Mock).mockResolvedValue(mockCompressedUri);

        await mediaCompressionService.compressMedia(mockUri, 'image', 'medium');

        expect(optimizeImage).toHaveBeenCalledWith(mockUri, {
          quality: 0.8,
          maxWidth: 1280,
          maxHeight: 1280,
        });
      });

      it('should compress image with low quality', async () => {
        const mockUri = 'file:///path/to/image.jpg';
        const mockCompressedUri = 'file:///path/to/compressed.jpg';

        (FileSystem.getInfoAsync as jest.Mock)
          .mockResolvedValueOnce({ exists: true, size: 5242880 })
          .mockResolvedValueOnce({ exists: true, size: 524288 });

        (optimizeImage as jest.Mock).mockResolvedValue(mockCompressedUri);

        await mediaCompressionService.compressMedia(mockUri, 'image', 'low');

        expect(optimizeImage).toHaveBeenCalledWith(mockUri, {
          quality: 0.6,
          maxWidth: 854,
          maxHeight: 854,
        });
      });

      it('should handle image compression failure', async () => {
        const mockUri = 'file:///path/to/image.jpg';

        (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
          exists: true,
          size: 5242880,
        });

        (optimizeImage as jest.Mock).mockRejectedValue(
          new Error('Compression failed')
        );

        await expect(
          mediaCompressionService.compressMedia(mockUri, 'image', 'high')
        ).rejects.toThrow('Compression failed');
      });
    });

    describe('video compression', () => {
      it('should prepare video for backend compression', async () => {
        const mockUri = 'file:///path/to/video.mp4';
        const mockSize = 20971520; // 20MB

        (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
          exists: true,
          size: mockSize,
        });

        const result = await mediaCompressionService.compressMedia(
          mockUri,
          'video',
          'high'
        );

        expect(result).toEqual({
          uri: mockUri,
          originalSizeBytes: mockSize,
          compressedSizeBytes: mockSize,
          compressionRatio: 0,
        });
      });

      it('should handle different video quality presets', async () => {
        const mockUri = 'file:///path/to/video.mp4';
        const mockSize = 20971520;

        (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
          exists: true,
          size: mockSize,
        });

        await mediaCompressionService.compressMedia(mockUri, 'video', 'medium');
        await mediaCompressionService.compressMedia(mockUri, 'video', 'low');

        // All should return original for now (backend will compress)
        expect(FileSystem.getInfoAsync).toHaveBeenCalledTimes(2);
      });
    });

    describe('audio compression', () => {
      it('should prepare audio for backend compression', async () => {
        const mockUri = 'file:///path/to/audio.m4a';
        const mockSize = 5242880; // 5MB

        (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
          exists: true,
          size: mockSize,
        });

        const result = await mediaCompressionService.compressMedia(
          mockUri,
          'audio',
          'high'
        );

        expect(result).toEqual({
          uri: mockUri,
          originalSizeBytes: mockSize,
          compressedSizeBytes: mockSize,
          compressionRatio: 0,
        });
      });
    });
  });

  describe('shouldCompress', () => {
    it('should recommend compression for large files on WiFi', () => {
      const result = mediaCompressionService.shouldCompress(
        10485760, // 10MB
        'image',
        'wifi'
      );

      expect(result.shouldCompress).toBe(true);
      expect(result.reason).toContain('File size exceeds threshold');
    });

    it('should recommend compression for large files on cellular', () => {
      const result = mediaCompressionService.shouldCompress(
        10485760,
        'image',
        'cellular'
      );

      expect(result.shouldCompress).toBe(true);
      expect(result.recommendedQuality).toBe('medium');
    });

    it('should not recommend compression for small files', () => {
      const result = mediaCompressionService.shouldCompress(
        524288, // 0.5MB
        'image',
        'wifi'
      );

      expect(result.shouldCompress).toBe(false);
      expect(result.reason).toContain('File is small enough');
    });

    it('should recommend low quality on cellular for large files', () => {
      const result = mediaCompressionService.shouldCompress(
        20971520, // 20MB
        'video',
        'cellular'
      );

      expect(result.shouldCompress).toBe(true);
      expect(result.recommendedQuality).toBe('low');
    });

    it('should recommend high quality on WiFi', () => {
      const result = mediaCompressionService.shouldCompress(
        10485760,
        'image',
        'wifi'
      );

      expect(result.shouldCompress).toBe(true);
      expect(result.recommendedQuality).toBe('high');
    });

    it('should handle text files', () => {
      const result = mediaCompressionService.shouldCompress(
        1024,
        'text',
        'wifi'
      );

      expect(result.shouldCompress).toBe(false);
      expect(result.reason).toContain('File is small enough');
    });
  });

  describe('getRecommendedQuality', () => {
    it('should recommend high quality on WiFi', () => {
      const quality = mediaCompressionService.getRecommendedQuality('wifi');
      expect(quality).toBe('high');
    });

    it('should recommend medium quality on cellular', () => {
      const quality = mediaCompressionService.getRecommendedQuality('cellular');
      expect(quality).toBe('medium');
    });

    it('should recommend low quality on 2g', () => {
      const quality = mediaCompressionService.getRecommendedQuality('2g');
      expect(quality).toBe('low');
    });

    it('should recommend low quality on unknown network', () => {
      const quality = mediaCompressionService.getRecommendedQuality('unknown');
      expect(quality).toBe('low');
    });
  });

  describe('estimateUploadTime', () => {
    it('should estimate upload time correctly on WiFi', () => {
      const estimate = mediaCompressionService.estimateUploadTime(
        10485760, // 10MB
        'wifi'
      );

      expect(estimate.estimatedSeconds).toBeGreaterThan(0);
      expect(estimate.formattedTime).toContain('seconds');
    });

    it('should estimate upload time correctly on cellular', () => {
      const estimate = mediaCompressionService.estimateUploadTime(
        10485760,
        'cellular'
      );

      expect(estimate.estimatedSeconds).toBeGreaterThan(0);
      expect(estimate.formattedTime).toContain('seconds');
    });

    it('should format time correctly for long uploads', () => {
      const estimate = mediaCompressionService.estimateUploadTime(
        104857600, // 100MB
        '2g'
      );

      expect(estimate.formattedTime).toContain('minutes');
    });

    it('should format time correctly for very long uploads', () => {
      const estimate = mediaCompressionService.estimateUploadTime(
        1048576000, // 1GB
        '2g'
      );

      expect(estimate.formattedTime).toContain('hours');
    });
  });

  describe('getCompressionStats', () => {
    it('should return compression statistics', async () => {
      const mockStats = {
        totalSaved: 10485760, // 10MB
        averageRatio: 65,
      };

      (AsyncStorage.getItem as jest.Mock)
        .mockResolvedValueOnce(mockStats.totalSaved.toString())
        .mockResolvedValueOnce(mockStats.averageRatio.toString());

      const stats = await mediaCompressionService.getCompressionStats();

      expect(stats).toEqual(mockStats);
    });

    it('should return zero stats when no data available', async () => {
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);

      const stats = await mediaCompressionService.getCompressionStats();

      expect(stats).toEqual({
        totalSaved: 0,
        averageRatio: 0,
      });
    });
  });

  describe('clearCompressionStats', () => {
    it('should clear compression statistics', async () => {
      await mediaCompressionService.clearCompressionStats();

      expect(AsyncStorage.removeItem).toHaveBeenCalledWith(
        '@compression_totalSaved'
      );
      expect(AsyncStorage.removeItem).toHaveBeenCalledWith(
        '@compression_averageRatio'
      );
    });
  });

  describe('integration scenarios', () => {
    it('should handle full compression workflow', async () => {
      const mockUri = 'file:///path/to/large-image.jpg';
      const mockCompressedUri = 'file:///path/to/compressed.jpg';
      const originalSize = 10485760; // 10MB
      const compressedSize = 3145728; // 3MB

      (FileSystem.getInfoAsync as jest.Mock)
        .mockResolvedValueOnce({ exists: true, size: originalSize })
        .mockResolvedValueOnce({ exists: true, size: compressedSize });

      (optimizeImage as jest.Mock).mockResolvedValue(mockCompressedUri);

      // Check if compression is needed
      const shouldCompress = mediaCompressionService.shouldCompress(
        originalSize,
        'image',
        'wifi'
      );

      expect(shouldCompress.shouldCompress).toBe(true);

      // Compress the image
      const result = await mediaCompressionService.compressMedia(
        mockUri,
        'image',
        shouldCompress.recommendedQuality
      );

      expect(result.compressionRatio).toBeGreaterThan(0);
      expect(result.compressedSizeBytes).toBeLessThan(result.originalSizeBytes);

      // Estimate upload time
      const uploadEstimate = mediaCompressionService.estimateUploadTime(
        result.compressedSizeBytes,
        'wifi'
      );

      expect(uploadEstimate.estimatedSeconds).toBeGreaterThan(0);
    });

    it('should handle compression on cellular network', async () => {
      const mockUri = 'file:///path/to/video.mp4';
      const fileSize = 52428800; // 50MB

      (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
        exists: true,
        size: fileSize,
      });

      // Check compression recommendation
      const shouldCompress = mediaCompressionService.shouldCompress(
        fileSize,
        'video',
        'cellular'
      );

      expect(shouldCompress.shouldCompress).toBe(true);
      expect(shouldCompress.recommendedQuality).toBe('low');

      // Estimate upload time with compression
      const uploadEstimate = mediaCompressionService.estimateUploadTime(
        fileSize,
        'cellular'
      );

      expect(uploadEstimate.estimatedSeconds).toBeGreaterThan(0);
    });
  });
});
