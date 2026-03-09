/**
 * Media playback components for Flashit web app
 * Production-grade audio and video players
 */

import React from 'react';
import { useAudioPlayer, useVideoPlayer, formatTime, formatFileSize } from '@ghatana/flashit-shared';

interface MediaPlayerProps {
  src: string;
  fileName?: string;
  fileSize?: number;
  className?: string;
  autoPlay?: boolean;
  onError?: (error: string) => void;
}

/**
 * Audio Player Component
 */
export function AudioPlayer({
  src,
  fileName,
  fileSize,
  className = '',
  autoPlay = false,
  onError
}: MediaPlayerProps) {
  const { state, controls, elementRef } = useAudioPlayer(src, { autoPlay });

  React.useEffect(() => {
    if (state.error && onError) {
      onError(state.error);
    }
  }, [state.error, onError]);

  const progress = state.duration ? (state.currentTime / state.duration) * 100 : 0;

  return (
    <div className={`bg-white rounded-lg border border-gray-200 p-4 ${className}`}>
      {/* Hidden audio element */}
      <audio ref={elementRef} src={src} preload="metadata" />

      {/* Audio player UI */}
      <div className="flex items-center space-x-4">
        {/* Play/Pause button */}
        <button
          onClick={state.isPlaying ? controls.pause : controls.play}
          disabled={state.isLoading}
          className="flex-shrink-0 w-12 h-12 bg-blue-500 hover:bg-blue-600 disabled:bg-gray-300
                     rounded-full flex items-center justify-center text-white transition-colors"
        >
          {state.isLoading ? (
            <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
          ) : state.isPlaying ? (
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
          ) : (
            <svg className="w-5 h-5 ml-0.5" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
            </svg>
          )}
        </button>

        {/* Progress and info */}
        <div className="flex-1 min-w-0">
          {/* File info */}
          <div className="flex items-center justify-between mb-2">
            <div className="truncate">
              <p className="font-medium text-gray-900 truncate">{fileName || 'Audio'}</p>
              <p className="text-sm text-gray-500">
                {fileSize ? formatFileSize(fileSize) : ''} • {formatTime(state.duration)}
              </p>
            </div>
            <div className="text-sm text-gray-500 ml-4">
              {formatTime(state.currentTime)} / {formatTime(state.duration)}
            </div>
          </div>

          {/* Progress bar */}
          <div className="relative">
            <div className="w-full bg-gray-200 rounded-full h-2 cursor-pointer"
                 onClick={(e) => {
                   const rect = e.currentTarget.getBoundingClientRect();
                   const clickX = e.clientX - rect.left;
                   const newTime = (clickX / rect.width) * state.duration;
                   controls.seek(newTime);
                 }}>
              <div
                className="bg-blue-500 h-2 rounded-full transition-all duration-100"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>

          {/* Error display */}
          {state.error && (
            <p className="text-red-500 text-sm mt-2">Error: {state.error}</p>
          )}
        </div>

        {/* Volume control */}
        <div className="flex items-center space-x-2">
          <button
            onClick={controls.toggleMute}
            className="p-2 text-gray-400 hover:text-gray-600"
          >
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.553 13H2a1 1 0 01-1-1V8a1 1 0 011-1h2.553l3.83-3.793a1 1 0 011.617.793z" clipRule="evenodd" />
            </svg>
          </button>
          <input
            type="range"
            min="0"
            max="1"
            step="0.1"
            value={state.volume}
            onChange={(e) => controls.setVolume(parseFloat(e.target.value))}
            className="w-20 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer slider"
          />
        </div>
      </div>
    </div>
  );
}

/**
 * Video Player Component
 */
export function VideoPlayer({
  src,
  fileName,
  fileSize,
  className = '',
  autoPlay = false,
  onError
}: MediaPlayerProps) {
  const { state, controls, elementRef } = useVideoPlayer(src, { autoPlay });
  const [showControls, setShowControls] = React.useState(true);
  const [isFullscreen, setIsFullscreen] = React.useState(false);

  React.useEffect(() => {
    if (state.error && onError) {
      onError(state.error);
    }
  }, [state.error, onError]);

  const progress = state.duration ? (state.currentTime / state.duration) * 100 : 0;

  const toggleFullscreen = async () => {
    if (!document.fullscreenElement) {
      await elementRef.current?.requestFullscreen();
      setIsFullscreen(true);
    } else {
      await document.exitFullscreen();
      setIsFullscreen(false);
    }
  };

  React.useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  return (
    <div className={`bg-black rounded-lg overflow-hidden ${className}`}>
      {/* Video element */}
      <div
        className="relative group"
        onMouseEnter={() => setShowControls(true)}
        onMouseLeave={() => setShowControls(false)}
      >
        <video
          ref={elementRef}
          src={src}
          className="w-full aspect-video"
          preload="metadata"
          onClick={state.isPlaying ? controls.pause : controls.play}
        />

        {/* Loading overlay */}
        {state.isLoading && (
          <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
            <div className="w-8 h-8 border-2 border-white border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {/* Controls overlay */}
        <div
          className={`absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black to-transparent p-4 transition-opacity duration-200 ${
            showControls ? 'opacity-100' : 'opacity-0'
          }`}
        >
          {/* Progress bar */}
          <div className="mb-4">
            <div className="w-full bg-gray-600 rounded-full h-1 cursor-pointer"
                 onClick={(e) => {
                   const rect = e.currentTarget.getBoundingClientRect();
                   const clickX = e.clientX - rect.left;
                   const newTime = (clickX / rect.width) * state.duration;
                   controls.seek(newTime);
                 }}>
              <div
                className="bg-white h-1 rounded-full transition-all duration-100"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>

          {/* Controls */}
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              {/* Play/Pause */}
              <button
                onClick={state.isPlaying ? controls.pause : controls.play}
                disabled={state.isLoading}
                className="p-2 text-white hover:bg-white hover:bg-opacity-20 rounded-full transition-colors"
              >
                {state.isPlaying ? (
                  <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                  </svg>
                ) : (
                  <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                  </svg>
                )}
              </button>

              {/* Time */}
              <div className="text-white text-sm">
                {formatTime(state.currentTime)} / {formatTime(state.duration)}
              </div>

              {/* Volume */}
              <div className="flex items-center space-x-2">
                <button
                  onClick={controls.toggleMute}
                  className="p-1 text-white hover:bg-white hover:bg-opacity-20 rounded"
                >
                  <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.553 13H2a1 1 0 01-1-1V8a1 1 0 011-1h2.553l3.83-3.793a1 1 0 011.617.793z" clipRule="evenodd" />
                  </svg>
                </button>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.1"
                  value={state.volume}
                  onChange={(e) => controls.setVolume(parseFloat(e.target.value))}
                  className="w-20 h-1 bg-gray-600 rounded-lg appearance-none cursor-pointer"
                />
              </div>
            </div>

            <div className="flex items-center space-x-2">
              {/* Playback speed */}
              <select
                value={state.playbackRate}
                onChange={(e) => controls.setPlaybackRate(parseFloat(e.target.value))}
                className="bg-transparent text-white text-sm border border-gray-600 rounded px-2 py-1"
              >
                <option value="0.5" className="text-black">0.5x</option>
                <option value="0.75" className="text-black">0.75x</option>
                <option value="1" className="text-black">1x</option>
                <option value="1.25" className="text-black">1.25x</option>
                <option value="1.5" className="text-black">1.5x</option>
                <option value="2" className="text-black">2x</option>
              </select>

              {/* Fullscreen */}
              <button
                onClick={toggleFullscreen}
                className="p-2 text-white hover:bg-white hover:bg-opacity-20 rounded-full transition-colors"
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M3 4a1 1 0 011-1h4a1 1 0 010 2H6.414l2.293 2.293a1 1 0 11-1.414 1.414L5 6.414V8a1 1 0 01-2 0V4zm9 1a1 1 0 010-2h4a1 1 0 011 1v4a1 1 0 01-2 0V6.414l-2.293 2.293a1 1 0 11-1.414-1.414L13.586 5H12zm-9 7a1 1 0 012 0v1.586l2.293-2.293a1 1 0 111.414 1.414L6.414 15H8a1 1 0 010 2H4a1 1 0 01-1-1v-4zm13-1a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 010-2h1.586l-2.293-2.293a1 1 0 111.414-1.414L15 13.586V12a1 1 0 011-1z" clipRule="evenodd" />
                </svg>
              </button>
            </div>
          </div>

          {/* File info */}
          {fileName && (
            <div className="mt-2 text-white text-sm opacity-75">
              {fileName} {fileSize && `• ${formatFileSize(fileSize)}`}
            </div>
          )}

          {/* Error display */}
          {state.error && (
            <div className="mt-2 text-red-400 text-sm">Error: {state.error}</div>
          )}
        </div>
      </div>
    </div>
  );
}
