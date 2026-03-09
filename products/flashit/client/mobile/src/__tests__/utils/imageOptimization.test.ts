/**
 * @jest-environment node
 */

import * as ImageManipulator from 'expo-image-manipulator';
import * as FileSystem from 'expo-file-system';
import { optimizeImage, getFileSizeMB, compressToSize } from '../../utils/imageOptimization';

// Mock Expo modules
jest.mock('expo-image-manipulator', () => ({
  manipulateAsync: jest.fn(),
  SaveFormat: {
    JPEG: 'jpeg',
    PNG: 'png',
  },
}));

jest.mock('expo-file-system', () => ({
  getInfoAsync: jest.fn(),
  readAsStringAsync: jest.fn(),
  EncodingType: {
    Base64: 'base64',
  },
}));

describe('imageOptimization', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('optimizeImage', () => {
    it('should resize image when larger than max dimensions', async () => {
      const mockResult = {
        uri: 'file:///optimized.jpg',
        width: 2048,
        height: 1536,
      };

      (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue(mockResult);

      const result = await optimizeImage('file:///large-image.jpg', {
        maxWidth: 2048,
        maxHeight: 2048,
        quality: 0.9,
      });

      expect(ImageManipulator.manipulateAsync).toHaveBeenCalledWith(
        'file:///large-image.jpg',
        [{ resize: { width: 2048 } }],
        { compress: 0.9, format: 'jpeg' }
      );

      expect(result.uri).toBe('file:///optimized.jpg');
    });

    it('should use default options when not provided', async () => {
      const mockResult = {
        uri: 'file:///optimized.jpg',
        width: 2048,
        height: 1536,
      };

      (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue(mockResult);

      await optimizeImage('file:///image.jpg');

      expect(ImageManipulator.manipulateAsync).toHaveBeenCalledWith(
        'file:///image.jpg',
        [{ resize: { width: 2048 } }],
        { compress: 0.9, format: 'jpeg' }
      );
    });

    it('should respect custom quality setting', async () => {
      const mockResult = {
        uri: 'file:///optimized.jpg',
        width: 1024,
        height: 768,
      };

      (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue(mockResult);

      await optimizeImage('file:///image.jpg', { quality: 0.7 });

      expect(ImageManipulator.manipulateAsync).toHaveBeenCalledWith(
        'file:///image.jpg',
        [{ resize: { width: 2048 } }],
        { compress: 0.7, format: 'jpeg' }
      );
    });

    it('should handle errors gracefully', async () => {
      (ImageManipulator.manipulateAsync as jest.Mock).mockRejectedValue(
        new Error('Manipulation failed')
      );

      await expect(optimizeImage('file:///invalid.jpg')).rejects.toThrow('Manipulation failed');
    });
  });

  describe('getFileSizeMB', () => {
    it('should return file size in MB', async () => {
      (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
        exists: true,
        size: 2097152, // 2 MB in bytes
        uri: 'file:///test.jpg',
        modificationTime: Date.now(),
        isDirectory: false,
      });

      const sizeMB = await getFileSizeMB('file:///test.jpg');

      expect(sizeMB).toBeCloseTo(2, 2);
    });

    it('should return 0 if file does not exist', async () => {
      (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
        exists: false,
      });

      const sizeMB = await getFileSizeMB('file:///nonexistent.jpg');

      expect(sizeMB).toBe(0);
    });

    it('should handle errors gracefully', async () => {
      (FileSystem.getInfoAsync as jest.Mock).mockRejectedValue(new Error('File access error'));

      const sizeMB = await getFileSizeMB('file:///error.jpg');

      expect(sizeMB).toBe(0);
    });
  });

  describe('compressToSize', () => {
    it('should compress image until target size is reached', async () => {
      let callCount = 0;
      const mockResults = [
        { uri: 'file:///compressed1.jpg', width: 2048, height: 1536 },
        { uri: 'file:///compressed2.jpg', width: 2048, height: 1536 },
        { uri: 'file:///compressed3.jpg', width: 2048, height: 1536 },
      ];

      (ImageManipulator.manipulateAsync as jest.Mock).mockImplementation(() => {
        return Promise.resolve(mockResults[callCount++]);
      });

      (FileSystem.getInfoAsync as jest.Mock).mockImplementation((uri) => {
        // First call: 3MB, second: 2.5MB, third: 1.8MB
        const sizes = [3145728, 2621440, 1887437];
        return Promise.resolve({
          exists: true,
          size: sizes[callCount - 1],
          uri,
          modificationTime: Date.now(),
          isDirectory: false,
        });
      });

      const result = await compressToSize('file:///large.jpg', 2);

      expect(ImageManipulator.manipulateAsync).toHaveBeenCalledTimes(3);
      expect(result.uri).toBe('file:///compressed3.jpg');
    });

    it('should return after max attempts even if target not reached', async () => {
      (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue({
        uri: 'file:///compressed.jpg',
        width: 2048,
        height: 1536,
      });

      (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
        exists: true,
        size: 5242880, // Always 5MB
        uri: 'file:///compressed.jpg',
        modificationTime: Date.now(),
        isDirectory: false,
      });

      const result = await compressToSize('file:///huge.jpg', 2, { maxAttempts: 5 });

      expect(ImageManipulator.manipulateAsync).toHaveBeenCalledTimes(5);
      expect(result.uri).toBe('file:///compressed.jpg');
    });

    it('should use custom quality step', async () => {
      let callCount = 0;
      (ImageManipulator.manipulateAsync as jest.Mock).mockImplementation(() => {
        callCount++;
        return Promise.resolve({
          uri: `file:///compressed${callCount}.jpg`,
          width: 2048,
          height: 1536,
        });
      });

      (FileSystem.getInfoAsync as jest.Mock).mockImplementation(() => {
        const sizes = [3145728, 2097152]; // 3MB, 2MB
        return Promise.resolve({
          exists: true,
          size: sizes[callCount - 1],
          uri: `file:///compressed${callCount}.jpg`,
          modificationTime: Date.now(),
          isDirectory: false,
        });
      });

      await compressToSize('file:///large.jpg', 2, { qualityStep: 0.05 });

      // Should reduce quality by 0.05 each attempt
      expect(ImageManipulator.manipulateAsync).toHaveBeenNthCalledWith(
        1,
        'file:///large.jpg',
        [],
        { compress: 0.9, format: 'jpeg' }
      );

      expect(ImageManipulator.manipulateAsync).toHaveBeenNthCalledWith(
        2,
        expect.any(String),
        [],
        { compress: 0.85, format: 'jpeg' }
      );
    });

    it('should handle errors gracefully', async () => {
      (ImageManipulator.manipulateAsync as jest.Mock).mockRejectedValue(
        new Error('Compression failed')
      );

      await expect(compressToSize('file:///error.jpg', 2)).rejects.toThrow('Compression failed');
    });
  });
});
