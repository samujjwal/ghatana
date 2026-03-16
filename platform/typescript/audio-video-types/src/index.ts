/**
 * @ghatana/audio-video-types
 *
 * Shared TypeScript type definitions for the Ghatana Audio-Video platform:
 * Speech-to-Text (STT), Text-to-Speech (TTS), and Computer Vision services.
 *
 * All types are exported as pure interfaces / type aliases — no runtime code —
 * so this package adds zero bytes to application bundles.
 */

// Audio primitives
export type {
  AudioFormat,
  VideoFormat,
  ImageFormat,
  AudioData,
  AudioStreamInfo,
  AudioChunk,
} from './audio.js';

// Speech-to-Text
export type {
  STTLanguage,
  STTOptions,
  STTRequest,
  WordTimestamp,
  SpeakerSegment,
  AlternativeTranscription,
  STTUtterance,
  STTResult,
} from './stt.js';

// Text-to-Speech
export type {
  VoiceGender,
  VoiceInfo,
  VoiceConfig,
  TTSOptions,
  TTSRequest,
  WordBoundary,
  TTSResult,
  TTSChunk,
} from './tts.js';

// Computer Vision
export type {
  VisionTask,
  BoundingBox,
  VisionOptions,
  VisionRequest,
  Detection,
  Classification,
  KeyPoint,
  PoseEstimation,
  VisionFrame,
  OCRBlock,
  VisionResult,
} from './vision.js';

// Common / shared
export type {
  ProcessingStatus,
  ServiceErrorCode,
  HealthStatus,
  ServiceHealth,
  PaginatedResponse,
  AsyncJob,
  ProgressEvent,
} from './common.js';

// AudioVideoServiceError is a class (has runtime value) — use a regular export
export { AudioVideoServiceError } from './common.js';
