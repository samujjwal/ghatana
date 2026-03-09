/**
 * Voice Recorder Component for Web
 * Full-featured audio recording with waveform visualization
 * 
 * @doc.type component
 * @doc.purpose Provide voice recording UI with controls and visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useCallback } from 'react';
import { useVoiceCapture, formatDuration, VoiceCaptureState } from '../../hooks/useVoiceCapture';

interface VoiceRecorderProps {
  onRecordingComplete?: (blob: Blob, duration: number) => void;
  onError?: (error: string) => void;
  maxDurationMs?: number;
  className?: string;
}

/**
 * Waveform visualization component
 */
function WaveformVisualizer({ 
  levels, 
  isRecording,
  isPaused 
}: { 
  levels: number[]; 
  isRecording: boolean;
  isPaused: boolean;
}) {
  const bars = levels.length > 0 ? levels : Array(50).fill(0.05);
  
  return (
    <div className="flex items-center justify-center h-20 gap-0.5 px-4">
      {bars.map((level, index) => (
        <div
          key={index}
          className={`w-1 rounded-full transition-all duration-75 ${
            isRecording && !isPaused
              ? 'bg-red-500'
              : isPaused
              ? 'bg-yellow-500'
              : 'bg-gray-300'
          }`}
          style={{
            height: `${Math.max(4, Math.min(80, level * 80))}px`,
            opacity: isRecording || isPaused ? 1 : 0.5,
          }}
        />
      ))}
    </div>
  );
}

/**
 * Recording controls component
 */
function RecordingControls({
  state,
  onStart,
  onStop,
  onPause,
  onResume,
  onReset,
}: {
  state: VoiceCaptureState;
  onStart: () => void;
  onStop: () => void;
  onPause: () => void;
  onResume: () => void;
  onReset: () => void;
}) {
  const { isRecording, isPaused, duration } = state;

  return (
    <div className="flex flex-col items-center gap-4">
      {/* Duration display */}
      <div className="text-3xl font-mono font-bold text-gray-800">
        {formatDuration(duration)}
      </div>

      {/* Control buttons */}
      <div className="flex items-center gap-4">
        {!isRecording && !state.audioUrl && (
          <button
            onClick={onStart}
            className="w-16 h-16 bg-red-500 hover:bg-red-600 rounded-full flex items-center justify-center
                       text-white shadow-lg transition-all transform hover:scale-105 active:scale-95"
            aria-label="Start recording"
          >
            <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="6" />
            </svg>
          </button>
        )}

        {isRecording && (
          <>
            {/* Pause/Resume button */}
            <button
              onClick={isPaused ? onResume : onPause}
              className="w-12 h-12 bg-yellow-500 hover:bg-yellow-600 rounded-full flex items-center justify-center
                         text-white shadow-md transition-all"
              aria-label={isPaused ? 'Resume recording' : 'Pause recording'}
            >
              {isPaused ? (
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M8 5v14l11-7z" />
                </svg>
              ) : (
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
                </svg>
              )}
            </button>

            {/* Stop button */}
            <button
              onClick={onStop}
              className="w-16 h-16 bg-gray-700 hover:bg-gray-800 rounded-full flex items-center justify-center
                         text-white shadow-lg transition-all transform hover:scale-105 active:scale-95"
              aria-label="Stop recording"
            >
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                <rect x="6" y="6" width="12" height="12" rx="2" />
              </svg>
            </button>
          </>
        )}

        {state.audioUrl && !isRecording && (
          <button
            onClick={onReset}
            className="w-12 h-12 bg-gray-200 hover:bg-gray-300 rounded-full flex items-center justify-center
                       text-gray-700 shadow-md transition-all"
            aria-label="Record again"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        )}
      </div>

      {/* Recording indicator */}
      {isRecording && (
        <div className="flex items-center gap-2 text-sm text-red-500 font-medium">
          <span className={`w-2 h-2 rounded-full bg-red-500 ${isPaused ? '' : 'animate-pulse'}`} />
          {isPaused ? 'Paused' : 'Recording'}
        </div>
      )}
    </div>
  );
}

/**
 * Audio preview component
 */
function AudioPreview({ 
  audioUrl, 
  duration 
}: { 
  audioUrl: string; 
  duration: number;
}) {
  const audioRef = React.useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = React.useState(false);
  const [currentTime, setCurrentTime] = React.useState(0);

  React.useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleTimeUpdate = () => setCurrentTime(audio.currentTime * 1000);
    const handleEnded = () => setIsPlaying(false);
    const handlePlay = () => setIsPlaying(true);
    const handlePause = () => setIsPlaying(false);

    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('play', handlePlay);
    audio.addEventListener('pause', handlePause);

    return () => {
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('play', handlePlay);
      audio.removeEventListener('pause', handlePause);
    };
  }, []);

  const togglePlayback = () => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play();
    }
  };

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="bg-gray-50 rounded-lg p-4 space-y-3">
      <audio ref={audioRef} src={audioUrl} preload="metadata" />
      
      <div className="flex items-center gap-4">
        <button
          onClick={togglePlayback}
          className="w-10 h-10 bg-blue-500 hover:bg-blue-600 rounded-full flex items-center justify-center
                     text-white shadow-md transition-all"
          aria-label={isPlaying ? 'Pause' : 'Play'}
        >
          {isPlaying ? (
            <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
              <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
            </svg>
          ) : (
            <svg className="w-4 h-4 ml-0.5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>

        <div className="flex-1">
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-500 h-2 rounded-full transition-all duration-100"
              style={{ width: `${progress}%` }}
            />
          </div>
          <div className="flex justify-between text-xs text-gray-500 mt-1">
            <span>{formatDuration(currentTime)}</span>
            <span>{formatDuration(duration)}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Permission request component
 */
function PermissionRequest({ 
  status, 
  onRequest 
}: { 
  status: string; 
  onRequest: () => void;
}) {
  return (
    <div className="text-center p-6 bg-gray-50 rounded-lg">
      <svg className="w-12 h-12 mx-auto text-gray-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
      </svg>
      
      {status === 'denied' ? (
        <>
          <h3 className="font-medium text-gray-900 mb-2">Microphone Access Denied</h3>
          <p className="text-sm text-gray-500 mb-4">
            Please enable microphone access in your browser settings to record audio.
          </p>
        </>
      ) : (
        <>
          <h3 className="font-medium text-gray-900 mb-2">Microphone Access Required</h3>
          <p className="text-sm text-gray-500 mb-4">
            We need access to your microphone to record audio.
          </p>
          <button
            onClick={onRequest}
            className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors"
          >
            Allow Microphone
          </button>
        </>
      )}
    </div>
  );
}

/**
 * Main Voice Recorder Component
 */
export function VoiceRecorder({
  onRecordingComplete,
  onError,
  maxDurationMs = 5 * 60 * 1000,
  className = '',
}: VoiceRecorderProps) {
  const { state, controls } = useVoiceCapture({
    maxDurationMs,
    onRecordingComplete,
    onError,
  });

  const handleStart = useCallback(async () => {
    await controls.startRecording();
  }, [controls]);

  const handleStop = useCallback(async () => {
    await controls.stopRecording();
  }, [controls]);

  // Show unsupported message
  if (!state.isSupported) {
    return (
      <div className={`p-6 bg-red-50 rounded-lg text-center ${className}`}>
        <svg className="w-12 h-12 mx-auto text-red-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <h3 className="font-medium text-red-800 mb-2">Recording Not Supported</h3>
        <p className="text-sm text-red-600">
          Your browser doesn't support audio recording. Please use a modern browser like Chrome, Firefox, or Safari.
        </p>
      </div>
    );
  }

  // Show permission request if needed
  if (state.permissionStatus === 'prompt' || state.permissionStatus === 'denied') {
    return (
      <div className={className}>
        <PermissionRequest
          status={state.permissionStatus}
          onRequest={controls.requestPermission}
        />
      </div>
    );
  }

  return (
    <div className={`space-y-4 ${className}`}>
      {/* Error display */}
      {state.error && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
          {state.error}
        </div>
      )}

      {/* Waveform visualizer */}
      <div className="bg-gray-100 rounded-lg py-4">
        <WaveformVisualizer
          levels={state.audioLevels}
          isRecording={state.isRecording}
          isPaused={state.isPaused}
        />
      </div>

      {/* Recording controls */}
      <RecordingControls
        state={state}
        onStart={handleStart}
        onStop={handleStop}
        onPause={controls.pauseRecording}
        onResume={controls.resumeRecording}
        onReset={controls.resetRecording}
      />

      {/* Audio preview */}
      {state.audioUrl && !state.isRecording && (
        <AudioPreview audioUrl={state.audioUrl} duration={state.duration} />
      )}

      {/* Max duration indicator */}
      {state.isRecording && (
        <div className="text-center text-xs text-gray-500">
          Max duration: {formatDuration(maxDurationMs)}
        </div>
      )}
    </div>
  );
}

export default VoiceRecorder;
