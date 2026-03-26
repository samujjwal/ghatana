/**
 * @audio-video/ui — Speech hooks barrel export.
 */

export { useSpeechSynthesis } from './useSpeechSynthesis';
export type {
  UseSpeechSynthesisResult,
  SpeechSynthesisOptions,
} from './useSpeechSynthesis';

export { useSpeechRecognition } from './useSpeechRecognition';
export type {
  UseSpeechRecognitionResult,
  SpeechRecognitionOptions,
  SpeechRecognitionCallbacks,
} from './useSpeechRecognition';

export { useMemoryPressure } from './useMemoryPressure';
export type {
  UseMemoryPressureResult,
  UseMemoryPressureOptions,
  MemoryPressureLevel,
  MemoryInfo,
} from './useMemoryPressure';
