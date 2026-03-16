/**
 * Core audio data structures and format enumerations used across STT, TTS,
 * and Vision pipelines.
 */

/** Supported audio encoding formats. */
export type AudioFormat =
  | 'pcm_s16le'
  | 'pcm_f32le'
  | 'wav'
  | 'mp3'
  | 'ogg_opus'
  | 'flac'
  | 'aac'
  | 'webm_opus';

/** Supported video container formats for vision inputs. */
export type VideoFormat = 'mp4' | 'webm' | 'avi' | 'mov' | 'mkv';

/** Supported image formats for vision inputs. */
export type ImageFormat = 'jpeg' | 'png' | 'webp' | 'bmp' | 'gif';

/**
 * A chunk of raw audio data ready for streaming or batch processing.
 * `data` is always in the encoding described by `format`.
 */
export interface AudioData {
  readonly format: AudioFormat;
  readonly sampleRate: number;
  readonly channels: number;
  readonly durationMs: number;
  /** Raw encoded bytes. */
  readonly data: ArrayBuffer;
}

/**
 * Metadata describing an audio stream without its actual payload.
 * Used for pre-flight checks and progress events.
 */
export interface AudioStreamInfo {
  readonly format: AudioFormat;
  readonly sampleRate: number;
  readonly channels: number;
  /** Estimated total duration; -1 when unknown (live stream). */
  readonly estimatedDurationMs: number;
  readonly bitrateKbps?: number;
}

/**
 * A single chunk delivered during streaming audio playback or capture.
 */
export interface AudioChunk {
  readonly sequenceNumber: number;
  readonly data: Uint8Array;
  readonly timestampMs: number;
  /** True on the final chunk of a stream. */
  readonly isLast: boolean;
}
