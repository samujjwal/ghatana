import type { ServiceHealth } from '@ghatana/audio-video-types';
import { BaseHttpClient, type ClientConfig } from './BaseHttpClient.js';
import { SttClient } from './SttClient.js';
import { TtsClient } from './TtsClient.js';
import { VisionClient } from './VisionClient.js';

/**
 * Unified entry-point client for all Ghatana Audio-Video services.
 *
 * Instantiates `SttClient`, `TtsClient`, and `VisionClient` with the same
 * configuration so callers can access all capabilities from one import.
 *
 * @example
 * ```ts
 * const av = new AudioVideoClient({
 *   baseUrl: 'https://api.ghatana.com/audio-video',
 *   getToken: () => authStore.getAccessToken(),
 * });
 *
 * // STT
 * const transcript = await av.stt.transcribe({ audio, format: 'wav', ... });
 *
 * // TTS
 * const speech = await av.tts.synthesize({ text: 'Hello', voice: { voiceId: 'en-US-aria' }, outputFormat: 'mp3' });
 *
 * // Vision
 * const analysis = await av.vision.analyze({ media: imageBuffer, mediaType: 'jpeg', task: 'object_detection' });
 * ```
 */
export class AudioVideoClient extends BaseHttpClient {
  /** Speech-to-Text client. */
  readonly stt: SttClient;
  /** Text-to-Speech client. */
  readonly tts: TtsClient;
  /** Computer Vision client. */
  readonly vision: VisionClient;

  constructor(config: ClientConfig) {
    super(config);
    this.stt = new SttClient(config);
    this.tts = new TtsClient(config);
    this.vision = new VisionClient(config);
  }

  /**
   * Aggregate health from all three services.
   * Returns a partial result even if some services are unavailable.
   */
  async healthAll(): Promise<{
    stt: ServiceHealth | null;
    tts: ServiceHealth | null;
    vision: ServiceHealth | null;
  }> {
    const [stt, tts, vision] = await Promise.allSettled([
      this.stt.health(),
      this.tts.health(),
      this.vision.health(),
    ]);

    return {
      stt: stt.status === 'fulfilled' ? stt.value : null,
      tts: tts.status === 'fulfilled' ? tts.value : null,
      vision: vision.status === 'fulfilled' ? vision.value : null,
    };
  }
}
