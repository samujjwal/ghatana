import { useState, useRef, useCallback, useEffect } from 'react';
import type { AudioFormat } from '@ghatana/audio-video-types';

export type CaptureState = 'idle' | 'requesting' | 'recording' | 'paused' | 'stopped';

export interface AudioCaptureOptions {
  /** Preferred sample rate in Hz; browser may choose a different value. */
  sampleRateHz?: number;
  /** Mono (1) or stereo (2). */
  channelCount?: 1 | 2;
  /** MIME type hint for `MediaRecorder` (e.g. `'audio/webm;codecs=opus'`). */
  mimeType?: string;
  /** Emit intermediate `onChunk` callbacks every N milliseconds. */
  timesliceMs?: number;
}

export interface AudioCaptureResult {
  state: CaptureState;
  stream: MediaStream | null;
  /** Recorded chunks so far (grows during recording). */
  chunks: readonly Blob[];
  /** Combined audio `Blob` when stopped; null while recording. */
  recording: Blob | null;
  /** Detected media format based on MediaRecorder support. */
  detectedFormat: AudioFormat;
  /** Error if permission was denied or MediaRecorder threw. */
  error: Error | null;
  start: () => Promise<void>;
  pause: () => void;
  resume: () => void;
  stop: () => void;
  reset: () => void;
}

const PREFERRED_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/ogg;codecs=opus',
  'audio/webm',
  'audio/ogg',
] as const;

function detectFormat(mimeType: string): AudioFormat {
  if (mimeType.includes('ogg')) return 'ogg_opus';
  if (mimeType.includes('webm')) return 'webm_opus';
  return 'wav';
}

function chooseMimeType(hint?: string): string {
  if (hint && MediaRecorder.isTypeSupported(hint)) return hint;
  for (const type of PREFERRED_MIME_TYPES) {
    if (MediaRecorder.isTypeSupported(type)) return type;
  }
  return '';
}

/**
 * React hook that encapsulates the full browser audio-capture lifecycle:
 * getUserMedia → MediaRecorder → chunked Blobs → final recording Blob.
 *
 * The returned `stream` is also exposed so consumers can feed it directly into
 * `WaveformVisualizer` for real-time FFT feedback.
 *
 * @example
 * ```tsx
 * const capture = useAudioCapture({ timesliceMs: 500 });
 * <WaveformVisualizer stream={capture.stream ?? undefined} />
 * <button onClick={capture.start}>Start</button>
 * <button onClick={capture.stop}>Stop</button>
 * ```
 */
export function useAudioCapture(options: AudioCaptureOptions = {}): AudioCaptureResult {
  const { sampleRateHz, channelCount = 1, mimeType, timesliceMs = 500 } = options;

  const [state, setState] = useState<CaptureState>('idle');
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [chunks, setChunks] = useState<Blob[]>([]);
  const [recording, setRecording] = useState<Blob | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [detectedFormat, setDetectedFormat] = useState<AudioFormat>('webm_opus');

  const recorderRef = useRef<MediaRecorder | null>(null);

  const start = useCallback(async (): Promise<void> => {
    try {
      setState('requesting');
      setError(null);
      setChunks([]);
      setRecording(null);

      const constraints: MediaStreamConstraints = {
        audio: {
          ...(sampleRateHz !== undefined && { sampleRate: sampleRateHz }),
          channelCount,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      };

      const mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      setStream(mediaStream);

      const resolvedMime = chooseMimeType(mimeType);
      setDetectedFormat(detectFormat(resolvedMime));

      const recorder = new MediaRecorder(
        mediaStream,
        resolvedMime ? { mimeType: resolvedMime } : undefined,
      );
      recorderRef.current = recorder;

      const localChunks: Blob[] = [];

      recorder.ondataavailable = (e: BlobEvent) => {
        if (e.data.size > 0) {
          localChunks.push(e.data);
          setChunks([...localChunks]);
        }
      };

      recorder.onstop = () => {
        const blob = new Blob(localChunks, {
          type: resolvedMime || 'audio/webm',
        });
        setRecording(blob);
        setState('stopped');
        mediaStream.getTracks().forEach((t) => t.stop());
        setStream(null);
      };

      recorder.onerror = (e) => {
        const err = new Error(`MediaRecorder error: ${(e as ErrorEvent).message ?? 'unknown'}`);
        setError(err);
        setState('idle');
      };

      recorder.start(timesliceMs);
      setState('recording');
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
      setState('idle');
    }
  }, [sampleRateHz, channelCount, mimeType, timesliceMs]);

  const pause = useCallback((): void => {
    if (recorderRef.current?.state === 'recording') {
      recorderRef.current.pause();
      setState('paused');
    }
  }, []);

  const resume = useCallback((): void => {
    if (recorderRef.current?.state === 'paused') {
      recorderRef.current.resume();
      setState('recording');
    }
  }, []);

  const stop = useCallback((): void => {
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop();
    }
  }, []);

  const reset = useCallback((): void => {
    stop();
    setChunks([]);
    setRecording(null);
    setError(null);
    setState('idle');
  }, [stop]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (recorderRef.current && recorderRef.current.state !== 'inactive') {
        recorderRef.current.stop();
      }
      stream?.getTracks().forEach((t) => t.stop());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return {
    state,
    stream,
    chunks,
    recording,
    detectedFormat,
    error,
    start,
    pause,
    resume,
    stop,
    reset,
  };
}
