/**
 * @ghatana/ai-voice-ui-react
 * 
 * React components and hooks for AI Voice Production Studio.
 * 
 * This library provides AI Voice-specific UI components while re-exporting
 * shared types and components from @ghatana/speech-ui-react and @ghatana/tts-ui-react.
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
  // Re-exported shared types
  ProfileSettings,
  AdaptationMode,
  PrivacyLevel,
  AudioChunk,
  AudioData,
  SessionState,
  EngineState,
  VoiceInfo,
  SynthesisOptions,
  SynthesisResult,
} from './types';

// Components
export {
  // AI Voice specific components
  Waveform,
  StemTrack,
  PhraseTimeline,
  TrainingProgress,
  // Re-exported shared components
  ProfileSettingsEditor,
  PrivacyConsentBlock,
  ProfileSelector,
  DashboardCard,
} from './components';

export type {
  WaveformProps,
  StemTrackProps,
  PhraseTimelineProps,
  TrainingProgressProps,
  ProfileSettingsEditorProps,
  PrivacyConsentBlockProps,
  ProfileSelectorProps,
  DashboardCardProps,
} from './components';

// Hooks
export { useAudioPlayer, useStemMixer, useSpeechClientBase } from './hooks';
export type {
  UseAudioPlayerOptions,
  UseAudioPlayerResult,
  UseStemMixerOptions,
  UseStemMixerResult,
} from './hooks';
