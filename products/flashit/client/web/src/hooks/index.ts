/**
 * Hooks Barrel Export
 * 
 * @doc.type module
 * @doc.purpose Export all custom hooks for web app
 * @doc.layer product
 * @doc.pattern Barrel
 */

// API hooks
export * from './use-api';

// Media capture hooks
export { 
  useVoiceCapture, 
  formatDuration as formatAudioDuration 
} from './useVoiceCapture';
export type { 
  VoiceCaptureState, 
  VoiceCaptureControls, 
  UseVoiceCaptureOptions 
} from './useVoiceCapture';

export { useImageCapture } from './useImageCapture';
export type { 
  ImageCaptureState, 
  ImageCaptureControls, 
  UseImageCaptureOptions 
} from './useImageCapture';

export { 
  useVideoCapture, 
  formatVideoDuration 
} from './useVideoCapture';
export type { 
  VideoCaptureState, 
  VideoCaptureControls, 
  UseVideoCaptureOptions 
} from './useVideoCapture';

// Real-time hooks
export { useRealtime } from './useRealtime';
export type {
  CollaborationEventType,
  PresenceData,
  PresenceStatus,
  MomentUpdate,
  CommentUpdate,
  TypingIndicator,
  ReactionUpdate,
  ConnectionState,
  UseRealtimeOptions,
  RealtimeControls,
} from './useRealtime';
