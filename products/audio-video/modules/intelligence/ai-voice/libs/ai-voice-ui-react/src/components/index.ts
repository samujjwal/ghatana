/**
 * @ghatana/ai-voice-ui-react - Components
 * 
 * AI Voice Production Studio components.
 */

export { Waveform } from './Waveform';
export type { WaveformProps } from './Waveform';

export { StemTrack } from './StemTrack';
export type { StemTrackProps } from './StemTrack';

export { PhraseTimeline } from './PhraseTimeline';
export type { PhraseTimelineProps } from './PhraseTimeline';

export { TrainingProgress } from './TrainingProgress';
export type { TrainingProgressProps } from './TrainingProgress';

// Re-export shared components
export {
  ProfileSettingsEditor,
  PrivacyConsentBlock,
  ProfileSelector,
  DashboardCard,
} from '@ghatana/speech-ui-react';

export type {
  ProfileSettingsEditorProps,
  PrivacyConsentBlockProps,
  ProfileSelectorProps,
  DashboardCardProps,
} from '@ghatana/speech-ui-react';
