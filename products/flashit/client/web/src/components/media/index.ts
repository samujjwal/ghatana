/**
 * Media Components Barrel Export
 * 
 * @doc.type module
 * @doc.purpose Export all media capture and playback components
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Capture components
export { VoiceRecorder } from './VoiceRecorder';
export { ImageCapture } from './ImageCapture';
export { VideoRecorder } from './VideoRecorder';

// Unified uploader
export { MediaUploader } from './MediaUploader';
export type { MediaType, CapturedMedia, MediaUploaderProps } from './MediaUploader';

// Playback components
export { AudioPlayer, VideoPlayer } from './MediaPlayer';

// Re-export types
export type { 
  VoiceCaptureState, 
  VoiceCaptureControls, 
  UseVoiceCaptureOptions 
} from '../../hooks/useVoiceCapture';

export type { 
  ImageCaptureState, 
  ImageCaptureControls, 
  UseImageCaptureOptions 
} from '../../hooks/useImageCapture';

export type { 
  VideoCaptureState, 
  VideoCaptureControls, 
  UseVideoCaptureOptions 
} from '../../hooks/useVideoCapture';
