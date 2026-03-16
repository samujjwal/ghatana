import type { AudioFormat } from './audio.js';

// ---------------------------------------------------------------------------
// Language / Locale
// ---------------------------------------------------------------------------

/**
 * BCP-47 language tag used to configure STT recognition.
 * Common values: 'en-US', 'hi-IN', 'es-ES', 'fr-FR', 'de-DE', 'ja-JP'
 */
export type STTLanguage = string;

// ---------------------------------------------------------------------------
// Request
// ---------------------------------------------------------------------------

/** Options that tune STT recognition behaviour without changing the request body. */
export interface STTOptions {
  /** If true the service returns partial/interim results before finalising. */
  readonly streamingPartials?: boolean;
  /** Maximum number of alternative transcripts per utterance (1–10). */
  readonly maxAlternatives?: number;
  /** Capture word-level start/end timestamps in the result. */
  readonly enableWordTimestamps?: boolean;
  /** Speaker diarization: split audio by speaker if supported by the engine. */
  readonly enableDiarization?: boolean;
  /** Number of distinct speakers expected (hint). 0 = auto-detect. */
  readonly speakerCount?: number;
  /** Custom vocabulary hints to boost recognition for domain terms. */
  readonly boostWords?: readonly string[];
  /** Recognition model variant; if omitted the server picks the best default. */
  readonly model?: string;
}

/** A single speech-to-text recognition request. */
export interface STTRequest {
  /** Raw audio bytes or a URL to fetch from. */
  readonly audio: ArrayBuffer | string;
  readonly format: AudioFormat;
  readonly sampleRate: number;
  /** BCP-47 language tag, e.g. 'en-US'. */
  readonly language: STTLanguage;
  readonly options?: STTOptions;
}

// ---------------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------------

/**
 * Fine-grained timing for a single recognised word.
 * All times are milliseconds relative to the start of the audio clip.
 */
export interface WordTimestamp {
  readonly word: string;
  readonly startMs: number;
  readonly endMs: number;
  readonly confidence: number;
}

/**
 * A single speaker segment produced when diarization is enabled.
 */
export interface SpeakerSegment {
  /** Zero-based speaker index (0 = first speaker). */
  readonly speakerIndex: number;
  readonly startMs: number;
  readonly endMs: number;
}

/**
 * One alternative interpretation of the spoken audio.
 * The first alternative is always the highest-confidence result.
 */
export interface AlternativeTranscription {
  readonly transcript: string;
  /** Aggregate confidence across all words (0.0 – 1.0). */
  readonly confidence: number;
  readonly words?: readonly WordTimestamp[];
}

/**
 * A single stable utterance result.  When streaming, multiple partial results
 * may be emitted before `isFinal` is true.
 */
export interface STTUtterance {
  readonly alternatives: readonly AlternativeTranscription[];
  /** If provided, the utterance occured at this offset in the audio. */
  readonly offsetMs?: number;
  readonly durationMs?: number;
  readonly isFinal: boolean;
  readonly speakers?: readonly SpeakerSegment[];
}

/**
 * The top-level result returned by a completed STT request.
 * For streaming use-cases individual `STTUtterance` events are emitted; this
 * structure holds the final aggregated view.
 */
export interface STTResult {
  /** Concatenated final transcript of all utterances. */
  readonly fullTranscript: string;
  readonly utterances: readonly STTUtterance[];
  readonly languageDetected?: STTLanguage;
  readonly processingTimeMs: number;
  readonly audioDurationMs: number;
  readonly engine: string;
}
