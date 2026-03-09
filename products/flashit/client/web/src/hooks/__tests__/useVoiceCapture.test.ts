/**
 * Voice Capture Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Test voice recording functionality
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { useVoiceCapture, formatDuration } from '../useVoiceCapture';

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

const mockMediaStream = {
  getTracks: jest.fn(() => [{ stop: jest.fn() }]),
  getVideoTracks: jest.fn(() => []),
  getAudioTracks: jest.fn(() => [{ getSettings: () => ({}) }]),
};

// Mock AudioContext
const mockAudioContext = {
  createAnalyser: jest.fn(() => ({
    fftSize: 256,
    frequencyBinCount: 128,
    getByteFrequencyData: jest.fn((arr: Uint8Array) => {
      arr.fill(128);
    }),
    connect: jest.fn(),
  })),
  createMediaStreamSource: jest.fn(() => ({
    connect: jest.fn(),
  })),
  close: jest.fn(),
};

describe('useVoiceCapture', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    
    // Mock navigator.mediaDevices
    Object.defineProperty(navigator, 'mediaDevices', {
      value: {
        getUserMedia: jest.fn().mockResolvedValue(mockMediaStream),
        enumerateDevices: jest.fn().mockResolvedValue([]),
      },
      writable: true,
    });

    // Mock navigator.permissions
    Object.defineProperty(navigator, 'permissions', {
      value: {
        query: jest.fn().mockResolvedValue({
          state: 'prompt',
          addEventListener: jest.fn(),
        }),
      },
      writable: true,
    });

    // Mock MediaRecorder
    (global as any).MediaRecorder = jest.fn(() => mockMediaRecorder);
    (global as any).MediaRecorder.isTypeSupported = jest.fn(() => true);

    // Mock AudioContext
    (global as any).AudioContext = jest.fn(() => mockAudioContext);

    // Mock URL.createObjectURL
    (global as any).URL.createObjectURL = jest.fn(() => 'blob:mock-url');
    (global as any).URL.revokeObjectURL = jest.fn();

    // Mock requestAnimationFrame
    jest.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      return setTimeout(cb, 16) as unknown as number;
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('initialization', () => {
    it('should initialize with default state', () => {
      const { result } = renderHook(() => useVoiceCapture());

      expect(result.current.state.isRecording).toBe(false);
      expect(result.current.state.isPaused).toBe(false);
      expect(result.current.state.duration).toBe(0);
      expect(result.current.state.audioUrl).toBeNull();
      expect(result.current.state.audioBlob).toBeNull();
      expect(result.current.state.error).toBeNull();
      expect(result.current.state.isSupported).toBe(true);
    });

    it('should detect unsupported browsers', () => {
      delete (global as any).MediaRecorder;

      const { result } = renderHook(() => useVoiceCapture());

      expect(result.current.state.isSupported).toBe(false);
    });
  });

  describe('requestPermission', () => {
    it('should request microphone permission', async () => {
      const { result } = renderHook(() => useVoiceCapture());

      let granted: boolean;
      await act(async () => {
        granted = await result.current.controls.requestPermission();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true });
      expect(granted!).toBe(true);
      expect(result.current.state.permissionStatus).toBe('granted');
    });

    it('should handle permission denied', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new Error('Permission denied')
      );

      const onError = jest.fn();
      const { result } = renderHook(() => useVoiceCapture({ onError }));

      let granted: boolean;
      await act(async () => {
        granted = await result.current.controls.requestPermission();
      });

      expect(granted!).toBe(false);
      expect(result.current.state.permissionStatus).toBe('denied');
      expect(onError).toHaveBeenCalledWith('Microphone permission denied');
    });
  });

  describe('startRecording', () => {
    it('should start recording successfully', async () => {
      const { result } = renderHook(() => useVoiceCapture());

      await act(async () => {
        await result.current.controls.startRecording();
      });

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      expect(mockMediaRecorder.start).toHaveBeenCalledWith(1000);
      expect(result.current.state.isRecording).toBe(true);
      expect(result.current.state.isPaused).toBe(false);
    });

    it('should handle recording error', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new Error('Device busy')
      );

      const onError = jest.fn();
      const { result } = renderHook(() => useVoiceCapture({ onError }));

      await act(async () => {
        await result.current.controls.startRecording();
      });

      expect(result.current.state.error).toBe('Device busy');
      expect(result.current.state.isRecording).toBe(false);
      expect(onError).toHaveBeenCalledWith('Device busy');
    });
  });

  describe('stopRecording', () => {
    it('should stop recording and create audio blob', async () => {
      const onRecordingComplete = jest.fn();
      const { result } = renderHook(() => useVoiceCapture({ onRecordingComplete }));

      // Start recording
      await act(async () => {
        await result.current.controls.startRecording();
      });

      mockMediaRecorder.state = 'recording';

      // Stop recording
      await act(async () => {
        await result.current.controls.stopRecording();
      });

      // Simulate data available
      await act(async () => {
        mockMediaRecorder.ondataavailable?.({ data: new Blob(['test'], { type: 'audio/webm' }) });
        mockMediaRecorder.onstop?.();
      });

      expect(mockMediaRecorder.stop).toHaveBeenCalled();
      expect(result.current.state.audioUrl).toBe('blob:mock-url');
      expect(result.current.state.audioBlob).toBeTruthy();
    });
  });

  describe('pauseRecording', () => {
    it('should pause recording', async () => {
      const { result } = renderHook(() => useVoiceCapture());

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
      const { result } = renderHook(() => useVoiceCapture());

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
      const { result } = renderHook(() => useVoiceCapture());

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
      expect(result.current.state.audioUrl).toBeNull();
      expect(result.current.state.audioBlob).toBeNull();
      expect(result.current.state.audioLevels).toEqual([]);
    });
  });

  describe('max duration', () => {
    it('should stop recording when max duration reached', async () => {
      jest.useFakeTimers();
      
      const { result } = renderHook(() => 
        useVoiceCapture({ maxDurationMs: 1000 })
      );

      await act(async () => {
        await result.current.controls.startRecording();
      });

      mockMediaRecorder.state = 'recording';

      // Advance time past max duration
      act(() => {
        jest.advanceTimersByTime(1100);
      });

      expect(mockMediaRecorder.stop).toHaveBeenCalled();

      jest.useRealTimers();
    });
  });
});

describe('formatDuration', () => {
  it('should format seconds correctly', () => {
    expect(formatDuration(0)).toBe('00:00');
    expect(formatDuration(1000)).toBe('00:01');
    expect(formatDuration(30000)).toBe('00:30');
    expect(formatDuration(60000)).toBe('01:00');
    expect(formatDuration(90000)).toBe('01:30');
    expect(formatDuration(300000)).toBe('05:00');
    expect(formatDuration(3661000)).toBe('61:01');
  });
});
