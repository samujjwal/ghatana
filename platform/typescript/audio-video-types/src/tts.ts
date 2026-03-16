import type { AudioFormat } from './audio.js';

// ---------------------------------------------------------------------------
// Voice configuration
// ---------------------------------------------------------------------------

export type VoiceGender = 'male' | 'female' | 'neutral';

/**
 * Describes a synthesiser voice available on the platform.
 * Returned by the voice-listing endpoint and referenced in requests.
 */
export interface VoiceInfo {
  readonly id: string;
  readonly name: string;
  /** BCP-47 language tag, e.g. 'en-US'. */
  readonly language: string;
  readonly gender: VoiceGender;
  /** Human-readable description of the voice style or persona. */
  readonly description?: string;
  /** Whether this voice supports real-time streaming synthesis. */
  readonly supportsStreaming: boolean;
  /** Whether this voice supports SSML markup. */
  readonly supportsSSML: boolean;
  /** Sample audio URL for preview. */
  readonly previewUrl?: string;
}

/**
 * Per-request voice tuning parameters applied on top of the base voice.
 */
export interface VoiceConfig {
  readonly voiceId: string;
  /** Speaking rate relative to the voice default (0.5 = half speed, 2.0 = double). */
  readonly speed?: number;
  /** Pitch shift in semitones relative to default (-12 to +12). */
  readonly pitchSemitones?: number;
  /** Volume gain in dB (-6 to +6). */
  readonly volumeGainDb?: number;
}

// ---------------------------------------------------------------------------
// Request
// ---------------------------------------------------------------------------

/** Controls synthesis behaviour without altering the text payload. */
export interface TTSOptions {
  /** Output sample rate in Hz (8000, 16000, 22050, 24000, 44100, 48000). */
  readonly sampleRateHz?: number;
  /**
   * If true the server streams audio chunks back as they are synthesised.
   * The result type changes to an `AsyncIterable<AudioChunk>`.
   */
  readonly streaming?: boolean;
  /** Include word-boundary timing events aligned to the output audio. */
  readonly enableWordBoundaries?: boolean;
}

/** A single text-to-speech synthesis request. */
export interface TTSRequest {
  /** Plain text or SSML markup to synthesize. */
  readonly text: string;
  readonly voice: VoiceConfig;
  readonly outputFormat: AudioFormat;
  readonly options?: TTSOptions;
}

// ---------------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------------

/**
 * Timing information for one word in the synthesised audio.
 * Milliseconds relative to the start of the returned audio.
 */
export interface WordBoundary {
  readonly word: string;
  readonly startMs: number;
  readonly endMs: number;
}

/** The result of a completed (non-streaming) TTS synthesis request. */
export interface TTSResult {
  /** Synthesised audio bytes in the format requested. */
  readonly audio: ArrayBuffer;
  readonly format: AudioFormat;
  readonly sampleRateHz: number;
  readonly durationMs: number;
  readonly wordBoundaries?: readonly WordBoundary[];
  readonly processingTimeMs: number;
  readonly engine: string;
}

/** A single streamed audio chunk emitted during streaming synthesis. */
export interface TTSChunk {
  readonly sequenceNumber: number;
  readonly audio: Uint8Array;
  readonly isLast: boolean;
  /** Present when `enableWordBoundaries` is true. */
  readonly wordBoundaries?: readonly WordBoundary[];
}
