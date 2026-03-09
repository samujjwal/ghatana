/**
 * Video Capture Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Test video recording functionality
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { useVideoCapture, formatVideoDuration } from '../useVideoCapture';

// Mock MediaRecorder
const mockMediaRecorder = {
  start: jest.fn(),
  stop: jest.fn(),
  pause: jest.fn(),
  resume: jest.fn(),
  ondataavailable: null as ((event: { data: Blob }) => void) | null,
  onstop: null as (() => void) | null,
  onerror: null as (() => void) | null,
  state: 'inactive' as 'inactive' | 'recording' | 'paused',
};

// Mock MediaStream
const mockMediaStream = {
  getTracks: jest.fn(() => [{ stop: jest.fn() }]),
  getVideoTracks: jest.fn(() => [{ 
    stop: jest.fn(),
    getSettings: () => ({ deviceId: 'camera-1' }) 
  }]),
  getAudioTracks: jest.fn(() => [{ 
    stop: jest.fn(),
    getSettings: () => ({}) 
  }]),
};

describe('useVideoCapture', () => {
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

    // Mock MediaRecorder
    (global as any).MediaRecorder = jest.fn(() => mockMediaRecorder);
    (global as any).MediaRecorder.isTypeSupported = jest.fn(() => true);

    // Mock URL.createObjectURL
    (global as any).URL.createObjectURL = jest.fn(() => 'blob:mock-video-url');
    (global as any).URL.revokeObjectURL = jest.fn();

    // Mock canvas for thumbnail generation
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
      const { result } = renderHook(() => useVideoCapture());

      expect(result.current.state.isRecording).toBe(false);
      expect(result.current.state.isPaused).toBe(false);
      expect(result.current.state.isCameraActive).toBe(false);
      expect(result.current.state.isProcessing).toBe(false);
      expect(result.current.state.duration).toBe(0);
      expect(result.current.state.videoUrl).toBeNull();
      expect(result.current.state.videoBlob).toBeNull();
      expect(result.current.state.thumbnailUrl).toBeNull();
      expect(result.current.state.error).toBeNull();
      expect(result.current.state.isSupported).toBe(true);
    });

    it('should detect unsupported browsers', () => {
      delete (global as any).MediaRecorder;

      const { result } = renderHook(() => useVideoCapture());

      expect(result.current.state.isSupported).toBe(false);
    });

    it('should enumerate available cameras', async () => {
      const { result } = renderHook(() => useVideoCapture());

      await waitFor(() => {
        expect(result.current.state.availableCameras).toHaveLength(2);
      });
    });
  });

  describe('requestPermission', () => {
    it('should request camera and microphone permission', async () => {
      const { result } = renderHook(() => useVideoCapture());

      let granted: boolean;
      await act(async () => {
        granted = await result.current.controls.requestPermission();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ 
        video: true, 
        audio: true 
      });
      expect(granted!).toBe(true);
      expect(result.current.state.permissionStatus).toBe('granted');
    });

    it('should handle permission denied', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new DOMException('Permission denied', 'NotAllowedError')
      );

      const onError = jest.fn();
      const { result } = renderHook(() => useVideoCapture({ onError }));

      let granted: boolean;
      await act(async () => {
        granted = await result.current.controls.requestPermission();
      });

      expect(granted!).toBe(false);
      expect(result.current.state.permissionStatus).toBe('denied');
      expect(onError).toHaveBeenCalledWith('Camera/microphone permission denied');
    });
  });

  describe('startCamera', () => {
    it('should start camera with default resolution', async () => {
      const { result } = renderHook(() => useVideoCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith(
        expect.objectContaining({
          video: expect.objectContaining({
            facingMode: 'environment',
            width: { ideal: 1280 },
            height: { ideal: 720 },
          }),
          audio: true,
        })
      );
      expect(result.current.state.isCameraActive).toBe(true);
    });

    it('should start camera with specified resolution', async () => {
      const { result } = renderHook(() => 
        useVideoCapture({ resolution: 'high' })
      );

      await act(async () => {
        await result.current.controls.startCamera();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith(
        expect.objectContaining({
          video: expect.objectContaining({
            width: { ideal: 1920 },
            height: { ideal: 1080 },
          }),
        })
      );
    });

    it('should handle camera start error', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new Error('Camera not available')
      );

      const onError = jest.fn();
      const { result } = renderHook(() => useVideoCapture({ onError }));

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
      const { result } = renderHook(() => useVideoCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      act(() => {
        result.current.controls.stopCamera();
      });

      expect(result.current.state.isCameraActive).toBe(false);
    });
  });

  describe('startRecording', () => {
    it('should start recording after camera is active', async () => {
      const { result } = renderHook(() => useVideoCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      await act(async () => {
        await result.current.controls.startRecording();
      });

      expect(mockMediaRecorder.start).toHaveBeenCalledWith(1000);
      expect(result.current.state.isRecording).toBe(true);
    });

    it('should fail if camera not active', async () => {
      const onError = jest.fn();
      const { result } = renderHook(() => useVideoCapture({ onError }));

      await act(async () => {
        await result.current.controls.startRecording();
      });

      expect(onError).toHaveBeenCalledWith('Camera not active. Start camera first.');
    });
  });

  describe('stopRecording', () => {
    it('should stop recording and create video blob', async () => {
      const onRecordingComplete = jest.fn();
      const { result } = renderHook(() => 
        useVideoCapture({ onRecordingComplete })
      );

      await act(async () => {
        await result.current.controls.startCamera();
      });

      await act(async () => {
        await result.current.controls.startRecording();
      });

      mockMediaRecorder.state = 'recording';

      await act(async () => {
        await result.current.controls.stopRecording();
      });

      // Simulate data available
      await act(async () => {
        mockMediaRecorder.ondataavailable?.({ 
          data: new Blob(['test'], { type: 'video/webm' }) 
        });
        mockMediaRecorder.onstop?.();
      });

      expect(mockMediaRecorder.stop).toHaveBeenCalled();
    });
  });

  describe('pauseRecording', () => {
    it('should pause recording', async () => {
      const { result } = renderHook(() => useVideoCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      await act(async () => {
        await result.current.controls.startRecording();
      });

      mockMediaRecorder.state = 'recording';

      act(() => {
        result.current.controls.pauseRecording();
      });

      expect(mockMediaRecorder.pause).toHaveBeenCalled();
      expect(result.current.state.isPaused).toBe(true);
    });
  });

  describe('resumeRecording', () => {
    it('should resume paused recording', async () => {
      const { result } = renderHook(() => useVideoCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      await act(async () => {
        await result.current.controls.startRecording();
      });

      mockMediaRecorder.state = 'paused';

      act(() => {
        result.current.controls.resumeRecording();
      });

      expect(mockMediaRecorder.resume).toHaveBeenCalled();
      expect(result.current.state.isPaused).toBe(false);
    });
  });

  describe('resetRecording', () => {
    it('should reset all recording state', async () => {
      const { result } = renderHook(() => useVideoCapture());

      await act(async () => {
        await result.current.controls.startCamera();
      });

      await act(async () => {
        await result.current.controls.startRecording();
      });

      mockMediaRecorder.state = 'recording';

      await act(async () => {
        await result.current.controls.stopRecording();
        mockMediaRecorder.ondataavailable?.({ data: new Blob(['test']) });
        mockMediaRecorder.onstop?.();
      });

      act(() => {
        result.current.controls.resetRecording();
      });

      expect(result.current.state.isRecording).toBe(false);
      expect(result.current.state.duration).toBe(0);
      expect(result.current.state.videoUrl).toBeNull();
      expect(result.current.state.videoBlob).toBeNull();
      expect(result.current.state.thumbnailUrl).toBeNull();
    });
  });

  describe('processVideoFile', () => {
    it('should process valid video file', async () => {
      const onRecordingComplete = jest.fn();
      const { result } = renderHook(() => 
        useVideoCapture({ onRecordingComplete })
      );

      const mockFile = new File(['test'], 'test.mp4', { type: 'video/mp4' });

      await act(async () => {
        await result.current.controls.processVideoFile(mockFile);
      });

      expect(result.current.state.videoBlob).toBe(mockFile);
      expect(result.current.state.videoUrl).toBe('blob:mock-video-url');
    });

    it('should reject invalid file type', async () => {
      const onError = jest.fn();
      const { result } = renderHook(() => useVideoCapture({ onError }));

      const mockFile = new File(['test'], 'test.txt', { type: 'text/plain' });

      await act(async () => {
        await result.current.controls.processVideoFile(mockFile);
      });

      expect(result.current.state.error).toContain('Invalid file type');
      expect(onError).toHaveBeenCalled();
    });

    it('should reject file exceeding max size', async () => {
      const onError = jest.fn();
      const { result } = renderHook(() => 
        useVideoCapture({ maxSizeBytes: 100, onError })
      );

      const mockFile = new File(['a'.repeat(200)], 'test.mp4', { type: 'video/mp4' });

      await act(async () => {
        await result.current.controls.processVideoFile(mockFile);
      });

      expect(result.current.state.error).toContain('File too large');
      expect(onError).toHaveBeenCalled();
    });
  });

  describe('switchCamera', () => {
    it('should switch between available cameras', async () => {
      const { result } = renderHook(() => useVideoCapture());

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

describe('formatVideoDuration', () => {
  it('should format duration correctly', () => {
    expect(formatVideoDuration(0)).toBe('00:00');
    expect(formatVideoDuration(1000)).toBe('00:01');
    expect(formatVideoDuration(30000)).toBe('00:30');
    expect(formatVideoDuration(60000)).toBe('01:00');
    expect(formatVideoDuration(90000)).toBe('01:30');
    expect(formatVideoDuration(180000)).toBe('03:00');
  });
});
