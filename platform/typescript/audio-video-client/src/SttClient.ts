import type {
  STTRequest,
  STTResult,
  STTUtterance,
  PaginatedResponse,
  ServiceHealth,
} from '@ghatana/audio-video-types';
import { BaseHttpClient, type ClientConfig } from './BaseHttpClient.js';

/** Wire format returned by the REST gateway for a transcription request. */
interface STTResponseWire {
  fullTranscript: string;
  utterances: STTUtterance[];
  languageDetected?: string;
  processingTimeMs: number;
  audioDurationMs: number;
  engine: string;
}

/**
 * REST client for the Ghatana Speech-to-Text (STT) service.
 *
 * Supports both single-request transcription (`transcribe`) and browser-side
 * streaming where audio chunks are uploaded as a multipart stream while partial
 * results are consumed via a `ReadableStream`.
 *
 * @example
 * ```ts
 * const client = new SttClient({ baseUrl: 'https://api.ghatana.com/audio-video', getToken });
 * const result = await client.transcribe(request);
 * console.log(result.fullTranscript);
 * ```
 */
export class SttClient extends BaseHttpClient {
  constructor(config: ClientConfig) {
    super(config);
  }

  /**
   * Transcribe an audio clip.
   *
   * If `request.audio` is an `ArrayBuffer` the bytes are sent as `multipart/form-data`.
   * If it is a `string` (URL) the server fetches the audio itself.
   */
  async transcribe(request: STTRequest, signal?: AbortSignal): Promise<STTResult> {
    let body: BodyInit;
    const extraHeaders: Record<string, string> = {};

    if (typeof request.audio === 'string') {
      // URL-based request — send as JSON
      body = JSON.stringify({
        audioUrl: request.audio,
        format: request.format,
        sampleRate: request.sampleRate,
        language: request.language,
        options: request.options ?? {},
      });
      extraHeaders['Content-Type'] = 'application/json';
    } else {
      // Raw bytes — multipart/form-data so large payloads stream efficiently
      const form = new FormData();
      form.append('audio', new Blob([request.audio], { type: 'application/octet-stream' }), 'audio');
      form.append('format', request.format);
      form.append('sampleRate', String(request.sampleRate));
      form.append('language', request.language);
      if (request.options) {
        form.append('options', JSON.stringify(request.options));
      }
      body = form;
    }

    const wire = await this.fetch<STTResponseWire>('/v1/stt/transcribe', {
      method: 'POST',
      headers: extraHeaders,
      body,
      signal,
    });

    return wire as STTResult;
  }

  /**
   * Stream partial transcription results using the browser `EventSource` API
   * (server-sent events).  Yields partial `STTUtterance` objects as they arrive.
   *
   * The server must support `POST /v1/stt/transcribe/stream` with SSE response.
   */
  async *transcribeStream(
    audio: ArrayBuffer,
    request: Omit<STTRequest, 'audio'>,
    signal?: AbortSignal,
  ): AsyncGenerator<STTUtterance> {
    const token = await (this as unknown as { getToken: () => string | Promise<string> }).getToken?.() ?? '';
    const form = new FormData();
    form.append('audio', new Blob([audio]), 'audio');
    form.append('format', request.format);
    form.append('sampleRate', String(request.sampleRate));
    form.append('language', request.language);
    if (request.options) form.append('options', JSON.stringify(request.options));

    const response = await fetch(`${this.baseUrl}/v1/stt/transcribe/stream`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
      signal,
    });

    if (!response.ok || !response.body) {
      throw new Error(`SSE stream failed: ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const lines = buffer.split('\n\n');
      buffer = lines.pop() ?? '';

      for (const chunk of lines) {
        const dataLine = chunk.split('\n').find((l) => l.startsWith('data:'));
        if (!dataLine) continue;
        const json = dataLine.slice(5).trim();
        if (json === '[DONE]') return;
        const utterance = JSON.parse(json) as STTUtterance;
        yield utterance;
      }
    }
  }

  /** List supported languages for STT. */
  async listLanguages(): Promise<PaginatedResponse<string>> {
    return this.fetch<PaginatedResponse<string>>('/v1/stt/languages');
  }

  /** Health check for the STT service. */
  async health(): Promise<ServiceHealth> {
    return this.fetch<ServiceHealth>('/v1/stt/health');
  }
}
