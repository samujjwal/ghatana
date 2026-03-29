/**
 * @ghatana/ai-voice-ui-react
 * 
 * React components and hooks for AI Voice Production Studio.
 * 
 * This library provides AI Voice-specific UI components and types.
 * 
 * @example
 * ```tsx
 * import { 
 *   Waveform, 
 *   StemTrack,
 *   useAudioPlayer,
 *   useStemMixer,
 *   type Stem 
 * } from '@ghatana/ai-voice-ui-react';
 * ```
 * 
 * @packageDocumentation
 */

// Types
export type {
  // AI Voice specific types
  StemType,
  Stem,
  StemSet,
  PhraseLabel,
  Phrase,
  Take,
  VoiceModel,
  TrainingSample,
  TrainingStatus,
  TrainingSession,
  SeparationStatus,
  SeparationProgress,
  VoiceConversionOptions,
  ConversionResult,
  PlaybackState,
  MixerState,
  StemMixSettings,
  EQSettings,
  CompressionSettings,
  EffectType,
  Effect,
  Project,
  ModelDownloadInfo,
} from './types';

// Components
export {
  // AI Voice specific components
  Waveform,
  StemTrack,
  PhraseTimeline,
  TrainingProgress,
} from './components';

export type {
  WaveformProps,
  StemTrackProps,
  PhraseTimelineProps,
  TrainingProgressProps,
} from './components';

// Hooks
export { useAudioPlayer, useStemMixer } from './hooks';
export type {
  UseAudioPlayerOptions,
  UseAudioPlayerResult,
  UseStemMixerOptions,
  UseStemMixerResult,
} from './hooks';
