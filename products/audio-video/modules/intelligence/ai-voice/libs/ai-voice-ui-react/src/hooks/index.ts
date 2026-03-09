/**
 * @ghatana/ai-voice-ui-react - Hooks
 * 
 * AI Voice Production Studio hooks.
 */

export { useAudioPlayer } from './useAudioPlayer';
export type { UseAudioPlayerOptions, UseAudioPlayerResult } from './useAudioPlayer';

export { useStemMixer } from './useStemMixer';
export type { UseStemMixerOptions, UseStemMixerResult } from './useStemMixer';

// Re-export shared hooks
export { useSpeechClientBase } from '@ghatana/speech-ui-react';
