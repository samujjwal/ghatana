/**
 * @ghatana/audio-video-ui
 *
 * React components and hooks for integrating Ghatana STT, TTS, and Vision
 * services into web applications.
 */

// Components
export { AudioPlayer } from './components/AudioPlayer.js';
export type { AudioPlayerProps } from './components/AudioPlayer.js';

export { VideoPlayer } from './components/VideoPlayer.js';
export type { VideoPlayerProps } from './components/VideoPlayer.js';

export { WaveformVisualizer } from './components/WaveformVisualizer.js';
export type { WaveformVisualizerProps } from './components/WaveformVisualizer.js';

export { TranscriptionDisplay } from './components/TranscriptionDisplay.js';
export type { TranscriptionDisplayProps } from './components/TranscriptionDisplay.js';

export { VoiceSelector } from './components/VoiceSelector.js';
export type { VoiceSelectorProps } from './components/VoiceSelector.js';

// Hooks
export { useAudioCapture } from './hooks/useAudioCapture.js';
export type { AudioCaptureOptions, AudioCaptureResult, CaptureState } from './hooks/useAudioCapture.js';
