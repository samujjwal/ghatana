/**
 * Shared browser/platform runtime utilities for audio-video speech hooks.
 */

export type AudioVideoProvider = 'none' | 'browser' | 'platform';

export interface AudioVideoRuntimeConfig {
  languageTag: string;
  requestTimeoutMs: number;
  recognitionMimeType: string;
  ttsVoiceId: string;
  syncToleranceMs: number;
  metricsEnabled: boolean;
}

export interface AudioVideoRuntimeMetricsSnapshot {
  browserRecognitionSessions: number;
  browserSynthesisRequests: number;
  sttFallbackRequests: number;
  ttsFallbackRequests: number;
  fallbackFailures: number;
}

export interface AudioVideoPlatformFallbackConfig {
  sttEndpoint?: string;
  ttsEndpoint?: string;
  headers?: HeadersInit;
  preferPlatform?: boolean;
  fetchImpl?: typeof fetch;
}

export interface AudioVideoSpeechHookConfig {
  fallback?: AudioVideoPlatformFallbackConfig;
  runtimeConfig?: Partial<AudioVideoRuntimeConfig>;
}

export interface AudioVideoPlatformError extends Error {
  code: string;
  category: 'validation' | 'runtime';
  retryable: boolean;
}

const defaultRuntimeConfig: AudioVideoRuntimeConfig = {
  languageTag: typeof navigator !== 'undefined' ? navigator.language || 'en-US' : 'en-US',
  requestTimeoutMs: 30_000,
  recognitionMimeType: 'audio/webm',
  ttsVoiceId: 'default',
  syncToleranceMs: 40,
  metricsEnabled: true,
};

const metrics: AudioVideoRuntimeMetricsSnapshot = {
  browserRecognitionSessions: 0,
  browserSynthesisRequests: 0,
  sttFallbackRequests: 0,
  ttsFallbackRequests: 0,
  fallbackFailures: 0,
};

export function normalizeAudioVideoRuntimeConfig(
  overrides?: Partial<AudioVideoRuntimeConfig>,
): AudioVideoRuntimeConfig {
  return {
    ...defaultRuntimeConfig,
    ...overrides,
    requestTimeoutMs: Math.max(1000, overrides?.requestTimeoutMs ?? defaultRuntimeConfig.requestTimeoutMs),
    syncToleranceMs: Math.max(20, overrides?.syncToleranceMs ?? defaultRuntimeConfig.syncToleranceMs),
    languageTag: overrides?.languageTag?.trim() || defaultRuntimeConfig.languageTag,
    ttsVoiceId: overrides?.ttsVoiceId?.trim() || defaultRuntimeConfig.ttsVoiceId,
  };
}

export function createPlatformError(
  code: string,
  category: 'validation' | 'runtime',
  retryable: boolean,
  message: string,
): AudioVideoPlatformError {
  const error = new Error(message) as AudioVideoPlatformError;
  error.code = code;
  error.category = category;
  error.retryable = retryable;
  return error;
}

export function incrementAudioVideoPlatformMetric(
  key: keyof AudioVideoRuntimeMetricsSnapshot,
): void {
  metrics[key] += 1;
}

export function getAudioVideoPlatformMetrics(): AudioVideoRuntimeMetricsSnapshot {
  return { ...metrics };
}

export function isPlatformSpeechRecognitionAvailable(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    typeof window !== 'undefined' &&
    'mediaDevices' in navigator &&
    typeof navigator.mediaDevices?.getUserMedia === 'function' &&
    'MediaRecorder' in window
  );
}

export async function transcribeWithPlatformFallback(
  audioBlob: Blob,
  fallbackConfig: AudioVideoPlatformFallbackConfig,
  languageTag: string,
): Promise<{ transcript: string; confidence: number }> {
  if (!fallbackConfig.sttEndpoint) {
    throw createPlatformError(
      'media.platform_unavailable',
      'runtime',
      false,
      'No STT fallback endpoint has been configured.',
    );
  }
  const formData = new FormData();
  formData.append('audio', audioBlob, 'speech.webm');
  formData.append('languageTag', languageTag);

  const response = await (fallbackConfig.fetchImpl ?? fetch)(fallbackConfig.sttEndpoint, {
    method: 'POST',
    headers: fallbackConfig.headers,
    body: formData,
  });

  if (!response.ok) {
    throw createPlatformError(
      'media.temporarily_unavailable',
      'runtime',
      response.status >= 500,
      `STT fallback request failed with status ${response.status}.`,
    );
  }

  const body = (await response.json()) as {
    transcript?: string;
    transcription?: string;
    confidence?: number;
  };

  const transcript = body.transcript ?? body.transcription ?? '';
  if (!transcript) {
    throw createPlatformError(
      'media.processing_failed',
      'runtime',
      true,
      'STT fallback response did not contain a transcript.',
    );
  }

  return {
    transcript,
    confidence: Math.max(0, Math.min(1, body.confidence ?? 0.0)),
  };
}

export async function synthesizeWithPlatformFallback(
  text: string,
  options: { rate?: number; pitch?: number; volume?: number; lang?: string } | undefined,
  fallbackConfig: AudioVideoPlatformFallbackConfig,
  signal?: AbortSignal,
): Promise<Blob> {
  if (!fallbackConfig.ttsEndpoint) {
    throw createPlatformError(
      'media.platform_unavailable',
      'runtime',
      false,
      'No TTS fallback endpoint has been configured.',
    );
  }

  const response = await (fallbackConfig.fetchImpl ?? fetch)(fallbackConfig.ttsEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(fallbackConfig.headers ?? {}),
    },
    body: JSON.stringify({ text, ...options }),
    signal,
  });

  if (!response.ok) {
    throw createPlatformError(
      'media.temporarily_unavailable',
      'runtime',
      response.status >= 500,
      `TTS fallback request failed with status ${response.status}.`,
    );
  }

  return response.blob();
}

/**
 * Result returned by {@link startFallbackRecording}.
 */
export interface FallbackRecordingSession {
  /**
   * Call this to stop the recording and trigger transcription.  The returned
   * promise resolves with the transcript text when the server responds.
   */
  stopAndTranscribe(): Promise<{ transcript: string; confidence: number }>;
  /** Release all media tracks without transcribing (e.g. on error or unmount). */
  discard(): void;
}

/**
 * Opens a {@link MediaRecorder} session on the supplied stream and returns a
 * handle that can be used to stop recording and retrieve a transcript from the
 * configured platform STT endpoint.
 *
 * <p>Failure behaviour:
 * <ul>
 *   <li>If the recorder fires an {@code onerror} event the recording stops
 *       automatically and the next call to {@link FallbackRecordingSession.stopAndTranscribe}
 *       rejects with a {@code media.recording_failed} error.</li>
 *   <li>If the server request fails, the error propagates as a typed
 *       {@link AudioVideoPlatformError} so callers can decide whether to retry.</li>
 * </ul>
 *
 * @param stream        Active {@link MediaStream} from {@code getUserMedia}.
 * @param mimeType      MIME type to pass to {@link MediaRecorder}; uses the
 *                      browser default when undefined or unsupported.
 * @param fallbackConfig Platform fallback configuration (must include {@code sttEndpoint}).
 * @param languageTag   BCP-47 language hint forwarded to the STT service.
 */
export function startFallbackRecording(
  stream: MediaStream,
  mimeType: string | undefined,
  fallbackConfig: AudioVideoPlatformFallbackConfig,
  languageTag: string,
): FallbackRecordingSession {
  const resolvedMime =
    mimeType && MediaRecorder.isTypeSupported(mimeType) ? mimeType : undefined;
  const recorder = new MediaRecorder(stream, resolvedMime ? { mimeType: resolvedMime } : undefined);
  const chunks: Blob[] = [];
  let recorderError: AudioVideoPlatformError | null = null;

  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) {
      chunks.push(event.data);
    }
  };

  recorder.onerror = () => {
    recorderError = createPlatformError(
      'media.recording_failed',
      'runtime',
      true,
      'Failed to capture audio for platform speech recognition.',
    );
    stream.getTracks().forEach((t) => t.stop());
  };

  recorder.start();

  const stopAndTranscribe = (): Promise<{ transcript: string; confidence: number }> =>
    new Promise((resolve, reject) => {
      if (recorderError) {
        reject(recorderError);
        return;
      }

      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        if (recorderError) {
          reject(recorderError);
          return;
        }
        const blob = new Blob(chunks, { type: resolvedMime ?? 'audio/webm' });
        try {
          const result = await transcribeWithPlatformFallback(blob, fallbackConfig, languageTag);
          resolve(result);
        } catch (err) {
          reject(err);
        }
      };

      if (recorder.state !== 'inactive') {
        recorder.stop();
      }
    });

  const discard = () => {
    if (recorder.state !== 'inactive') {
      recorder.stop();
    }
    stream.getTracks().forEach((t) => t.stop());
  };

  return { stopAndTranscribe, discard };
}