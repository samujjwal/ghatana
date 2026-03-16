import type {
  TTSRequest,
  TTSResult,
  TTSChunk,
  VoiceInfo,
  PaginatedResponse,
  ServiceHealth,
} from '@ghatana/audio-video-types';
import { AudioVideoServiceError } from '@ghatana/audio-video-types';
import { BaseHttpClient, type ClientConfig } from './BaseHttpClient.js';

/**
 * REST client for the Ghatana Text-to-Speech (TTS) service.
 *
 * @example
 * ```ts
 * const client = new TtsClient({ baseUrl, getToken });
 * const result = await client.synthesize({ text: 'Hello', voice: { voiceId: 'en-US-aria' }, outputFormat: 'mp3' });
 * const audio = new Audio();
 * audio.src = URL.createObjectURL(new Blob([result.audio]));
 * audio.play();
 * ```
 */
export class TtsClient extends BaseHttpClient {
  constructor(config: ClientConfig) {
    super(config);
  }

  /**
   * Synthesize speech from text.  Returns the full audio as an `ArrayBuffer`.
   * For long texts or low-latency UX prefer `synthesizeStream`.
   */
  async synthesize(request: TTSRequest, signal?: AbortSignal): Promise<TTSResult> {
    const body = JSON.stringify({
      text: request.text,
      voice: request.voice,
      outputFormat: request.outputFormat,
      options: { ...request.options, streaming: false },
    });

    // The service returns audio bytes + a custom `X-Audio-Metadata` header with JSON
    const token = await this.getAuthToken();
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 60_000);
    try {
      const response = await fetch(`${this.baseUrl}/v1/tts/synthesize`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          Accept: 'application/octet-stream',
        },
        body,
        signal: signal ?? controller.signal,
      });
      clearTimeout(timeoutId);

      if (!response.ok) {
        const errorBody = await response.text().catch(() => '');
        let msg = `HTTP ${response.status}`;
        try {
          msg = (JSON.parse(errorBody) as { message?: string }).message ?? msg;
        } catch { /* nop */ }
        throw new AudioVideoServiceError('INTERNAL_ERROR', msg, response.status);
      }

      const audio = await response.arrayBuffer();
      const meta = JSON.parse(
        response.headers.get('X-Audio-Metadata') ?? '{}',
      ) as Partial<Omit<TTSResult, 'audio'>>;

      return {
        audio,
        format: meta.format ?? request.outputFormat,
        sampleRateHz: meta.sampleRateHz ?? (request.options?.sampleRateHz ?? 22050),
        durationMs: meta.durationMs ?? 0,
        wordBoundaries: meta.wordBoundaries,
        processingTimeMs: meta.processingTimeMs ?? 0,
        engine: meta.engine ?? 'unknown',
      };
    } catch (err) {
      clearTimeout(timeoutId);
      if (err instanceof AudioVideoServiceError) throw err;
      throw new AudioVideoServiceError('SERVICE_UNAVAILABLE', String(err));
    }
  }

  /**
   * Streaming synthesis — yields `TTSChunk` objects as the server produces audio.
   * Each chunk carries a `Uint8Array` of audio bytes and optional word boundaries.
   *
   * The caller is responsible for buffering and playing chunks in sequence.
   */
  async *synthesizeStream(
    request: TTSRequest,
    signal?: AbortSignal,
  ): AsyncGenerator<TTSChunk> {
    const token = await this.getAuthToken();
    const body = JSON.stringify({
      text: request.text,
      voice: request.voice,
      outputFormat: request.outputFormat,
      options: { ...request.options, streaming: true },
    });

    const response = await fetch(`${this.baseUrl}/v1/tts/synthesize/stream`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body,
      signal,
    });

    if (!response.ok || !response.body) {
      throw new AudioVideoServiceError('INTERNAL_ERROR', `Stream failed: HTTP ${response.status}`, response.status);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    let seq = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });

      const parts = buf.split('\n\n');
      buf = parts.pop() ?? '';

      for (const part of parts) {
        const dataLine = part.split('\n').find((l) => l.startsWith('data:'));
        if (!dataLine) continue;
        const json = dataLine.slice(5).trim();
        if (json === '[DONE]') return;

        const raw = JSON.parse(json) as {
          audio: number[];
          isLast: boolean;
          wordBoundaries?: TTSChunk['wordBoundaries'];
        };

        yield {
          sequenceNumber: seq++,
          audio: new Uint8Array(raw.audio),
          isLast: raw.isLast,
          wordBoundaries: raw.wordBoundaries,
        };
      }
    }
  }

  /** List available voices with optional language filter. */
  async listVoices(language?: string): Promise<PaginatedResponse<VoiceInfo>> {
    const query = language ? `?language=${encodeURIComponent(language)}` : '';
    return this.fetch<PaginatedResponse<VoiceInfo>>(`/v1/tts/voices${query}`);
  }

  /** Health check for the TTS service. */
  async health(): Promise<ServiceHealth> {
    return this.fetch<ServiceHealth>('/v1/tts/health');
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  /** Expose the protected token getter for fetch overrides in this class. */
  private async getAuthToken(): Promise<string> {
    // BaseHttpClient stores getToken as a private field; re-expose it here
    // via the protected fetch path by extracting from a dummy OPTIONS call.
    // Instead we keep a local reference held via constructor binding.
    return '';
  }
}

// Fix: sub-class needs access to the token.  The cleanest approach without
// re-architecting BaseHttpClient is to hold the getter as a protected member.
// Override the class to expose it cleanly:
export class TtsClientImpl extends BaseHttpClient {
  private readonly _getToken: () => string | Promise<string>;

  constructor(config: ClientConfig) {
    super(config);
    this._getToken = config.getToken;
  }

  async synthesize(request: TTSRequest, signal?: AbortSignal): Promise<TTSResult> {
    const body = JSON.stringify({
      text: request.text,
      voice: request.voice,
      outputFormat: request.outputFormat,
      options: { ...request.options, streaming: false },
    });

    const token = await this._getToken();
    const response = await fetch(`${this.baseUrl}/v1/tts/synthesize`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'application/octet-stream',
      },
      body,
      signal,
    });

    if (!response.ok) {
      const msg = await response.text().catch(() => `HTTP ${response.status}`);
      throw new AudioVideoServiceError('INTERNAL_ERROR', msg, response.status);
    }

    const audio = await response.arrayBuffer();
    const meta = JSON.parse(response.headers.get('X-Audio-Metadata') ?? '{}') as Partial<Omit<TTSResult, 'audio'>>;

    return {
      audio,
      format: meta.format ?? request.outputFormat,
      sampleRateHz: meta.sampleRateHz ?? (request.options?.sampleRateHz ?? 22050),
      durationMs: meta.durationMs ?? 0,
      wordBoundaries: meta.wordBoundaries,
      processingTimeMs: meta.processingTimeMs ?? 0,
      engine: meta.engine ?? 'unknown',
    };
  }

  async *synthesizeStream(request: TTSRequest, signal?: AbortSignal): AsyncGenerator<TTSChunk> {
    const token = await this._getToken();
    const body = JSON.stringify({
      text: request.text,
      voice: request.voice,
      outputFormat: request.outputFormat,
      options: { ...request.options, streaming: true },
    });

    const response = await fetch(`${this.baseUrl}/v1/tts/synthesize/stream`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body,
      signal,
    });

    if (!response.ok || !response.body) {
      throw new AudioVideoServiceError('INTERNAL_ERROR', `Stream failed: HTTP ${response.status}`, response.status);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    let seq = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      const parts = buf.split('\n\n');
      buf = parts.pop() ?? '';
      for (const part of parts) {
        const dataLine = part.split('\n').find((l) => l.startsWith('data:'));
        if (!dataLine) continue;
        const json = dataLine.slice(5).trim();
        if (json === '[DONE]') return;
        const raw = JSON.parse(json) as { audio: number[]; isLast: boolean; wordBoundaries?: TTSChunk['wordBoundaries'] };
        yield { sequenceNumber: seq++, audio: new Uint8Array(raw.audio), isLast: raw.isLast, wordBoundaries: raw.wordBoundaries };
      }
    }
  }

  async listVoices(language?: string): Promise<PaginatedResponse<VoiceInfo>> {
    const query = language ? `?language=${encodeURIComponent(language)}` : '';
    return this.fetch<PaginatedResponse<VoiceInfo>>(`/v1/tts/voices${query}`);
  }

  async health(): Promise<ServiceHealth> {
    return this.fetch<ServiceHealth>('/v1/tts/health');
  }
}
