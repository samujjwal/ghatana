/**
 * Image Capture Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Test image capture and upload functionality
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { useImageCapture } from '../useImageCapture';

// Mock MediaStream
const mockMediaStream = {
  getTracks: jest.fn(() => [{ stop: jest.fn() }]),
  getVideoTracks: jest.fn(() => [{ 
    stop: jest.fn(),
    getSettings: () => ({ deviceId: 'camera-1' }) 
  }]),
};

describe('useImageCapture', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    
    // Mock navigator.mediaDevices
    Object.defineProperty(navigator, 'mediaDevices', {
      value: {
        getUserMedia: jest.fn().mockResolvedValue(mockMediaStream),
        enumerateDevices: jest.fn().mockResolvedValue([
          { deviceId: 'camera-1', kind: 'videoinput', label: 'Camera 1' },
          { deviceId: 'camera-2', kind: 'videoinput', label: 'Camera 2' },
        ]),
      },
      writable: true,
    });

    // Mock URL.createObjectURL
    (global as any).URL.createObjectURL = jest.fn(() => 'blob:mock-image-url');
    (global as any).URL.revokeObjectURL = jest.fn();

    // Mock Image
    (global as any).Image = class {
      onload: () => void = () => {};
      onerror: () => void = () => {};
      src: string = '';
      width: number = 1920;
      height: number = 1080;
      
      set source(val: string) {
        this.src = val;
        setTimeout(() => this.onload(), 0);
      }
    };

    // Mock canvas
    HTMLCanvasElement.prototype.getContext = jest.fn(() => ({
      drawImage: jest.fn(),
    }));
    HTMLCanvasElement.prototype.toBlob = jest.fn((callback) => {
      callback(new Blob(['test'], { type: 'image/jpeg' }));
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('initialization', () => {
    it('should initialize with default state', () => {
      const { result } = renderHook(() => useImageCapture());

      expect(result.current.state.imageUrl).toBeNull();
      expect(result.current.state.imageBlob).toBeNull();
      expect(result.current.state.isCapturing).toBe(false);
      expect(result.current.state.isCameraActive).toBe(false);
      expect(result.current.state.isCameraSupported).toBe(true);
      expect(result.current.state.error).toBeNull();
    });

    it('should enumerate available cameras', async () => {
      const { result } = renderHook(() => useImageCapture());

      await waitFor(() => {
        expect(result.current.state.availableCameras).toHaveLength(2);
      });
    });
  });

  describe('requestPermission', () => {
    it('should request camera permission', async () => {
      const { result } = renderHook(() => useImageCapture());

      let granted: boolean;
      await act(async () => {
        granted = await result.current.controls.requestPermission();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ video: true });
      expect(granted!).toBe(true);
      expect(result.current.state.permissionStatus).toBe('granted');
    });

    it('should handle permission denied', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new DOMException('Permission denied', 'NotAllowedError')
      );

      const onError = jest.fn();
      const { result } = renderHook(() => useImageCapture({ onError }));

      let granted: boolean;
      await act(async () => {
        granted = await result.current.controls.requestPermission();
      });

      expect(granted!).toBe(false);
      expect(result.current.state.permissionStatus).toBe('denied');
      expect(onError).toHaveBeenCalledWith('Camera permission denied');
    });
  });

  describe('startCamera', () => {
    it('should start camera with specified facing mode', async () => {
      const { result } = renderHook(() => useImageCapture());

      await act(async () => {
        await result.current.controls.startCamera('user');
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith(
        expect.objectContaining({
          video: expect.objectContaining({
            facingMode: 'user',
          }),
        })
      );
      expect(result.current.state.isCameraActive).toBe(true);
    });

    it('should handle camera start error', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new Error('Camera not available')
      );

      const onError = jest.fn();
      const { result } = renderHook(() => useImageCapture({ onError }));

      await act(async () => {
        await result.current.controls.startCamera();
      });

      expect(result.current.state.error).toBe('Camera not available');
      expect(result.current.state.isCameraActive).toBe(false);
      expect(onError).toHaveBeenCalledWith('Camera not available');
    });
  });

  describe('stopCamera', () => {
    it('should stop camera and release resources', async () => {
      const { result } = renderHook(() => useImageCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      act(() => {
        result.current.controls.stopCamera();
      });

      expect(result.current.state.isCameraActive).toBe(false);
      expect(mockMediaStream.getTracks()[0].stop).toHaveBeenCalled();
    });
  });

  describe('processFile', () => {
    it('should process valid image file', async () => {
      const onImageCapture = jest.fn();
      const { result } = renderHook(() => useImageCapture({ onImageCapture }));

      const mockFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });

      await act(async () => {
        await result.current.controls.processFile(mockFile);
      });

      await waitFor(() => {
        expect(result.current.state.imageUrl).toBeTruthy();
        expect(result.current.state.imageBlob).toBeTruthy();
        expect(result.current.state.originalFile).toBe(mockFile);
      });
    });

    it('should reject invalid file type', async () => {
      const onError = jest.fn();
      const { result } = renderHook(() => useImageCapture({ onError }));

      const mockFile = new File(['test'], 'test.txt', { type: 'text/plain' });

      await act(async () => {
        await result.current.controls.processFile(mockFile);
      });

      expect(result.current.state.error).toContain('Invalid file type');
      expect(onError).toHaveBeenCalled();
    });

    it('should reject file exceeding max size', async () => {
      const onError = jest.fn();
      const { result } = renderHook(() => 
        useImageCapture({ maxSizeBytes: 100, onError })
      );

      const mockFile = new File(['a'.repeat(200)], 'test.jpg', { type: 'image/jpeg' });

      await act(async () => {
        await result.current.controls.processFile(mockFile);
      });

      expect(result.current.state.error).toContain('File too large');
      expect(onError).toHaveBeenCalled();
    });
  });

  describe('resetImage', () => {
    it('should reset image state', async () => {
      const { result } = renderHook(() => useImageCapture());

      const mockFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });

      await act(async () => {
        await result.current.controls.processFile(mockFile);
      });

      act(() => {
        result.current.controls.resetImage();
      });

      expect(result.current.state.imageUrl).toBeNull();
      expect(result.current.state.imageBlob).toBeNull();
      expect(result.current.state.originalFile).toBeNull();
      expect(result.current.state.error).toBeNull();
      expect(URL.revokeObjectURL).toHaveBeenCalled();
    });
  });

  describe('switchCamera', () => {
    it('should switch between available cameras', async () => {
      const { result } = renderHook(() => useImageCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      await act(async () => {
        await result.current.controls.switchCamera();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledTimes(2);
    });
  });
});
