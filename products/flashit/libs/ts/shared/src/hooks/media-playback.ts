/**
 * Media playback components for Flashit
 * Cross-platform audio and video playback
 *
 * @doc.type components
 * @doc.purpose Provide media playback capabilities across web and mobile
 * @doc.layer platform
 * @doc.pattern ReactComponent
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';

// Platform detection
const isWeb = typeof window !== 'undefined';

// Media playback configuration
export interface MediaPlaybackConfig {
  autoPlay?: boolean;
  loop?: boolean;
  muted?: boolean;
  controls?: boolean;
  preload?: 'none' | 'metadata' | 'auto';
  volume?: number; // 0-1
  playbackRate?: number; // 0.25-4.0
}

// Playback state
export interface PlaybackState {
  isPlaying: boolean;
  isPaused: boolean;
  isLoading: boolean;
  isEnded: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  playbackRate: number;
  buffered: TimeRanges | null;
  error: string | null;
}

// Playback controls
export interface PlaybackControls {
  play: () => Promise<void>;
  pause: () => void;
  stop: () => void;
  seek: (time: number) => void;
  setVolume: (volume: number) => void;
  setPlaybackRate: (rate: number) => void;
  toggleMute: () => void;
}

// Media player hook
export interface MediaPlayerHook {
  state: PlaybackState;
  controls: PlaybackControls;
  elementRef: React.RefObject<HTMLAudioElement | HTMLVideoElement>;
}

/**
 * Audio player hook
 */
export function useAudioPlayer(src: string, config: MediaPlaybackConfig = {}): MediaPlayerHook {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [state, setState] = useState<PlaybackState>({
    isPlaying: false,
    isPaused: false,
    isLoading: true,
    isEnded: false,
    currentTime: 0,
    duration: 0,
    volume: config.volume ?? 1,
    playbackRate: config.playbackRate ?? 1,
    buffered: null,
    error: null,
  });

  // Update state from audio element
  const updateState = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;

    setState(prev => ({
      ...prev,
      isPlaying: !audio.paused && !audio.ended,
      isPaused: audio.paused && !audio.ended,
      isLoading: audio.readyState < 3,
      isEnded: audio.ended,
      currentTime: audio.currentTime,
      duration: audio.duration || 0,
      volume: audio.volume,
      playbackRate: audio.playbackRate,
      buffered: audio.buffered,
    }));
  }, []);

  // Setup event listeners
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleLoadStart = () => setState(prev => ({ ...prev, isLoading: true, error: null }));
    const handleLoadedMetadata = () => updateState();
    const handleCanPlay = () => setState(prev => ({ ...prev, isLoading: false }));
    const handlePlay = () => updateState();
    const handlePause = () => updateState();
    const handleEnded = () => updateState();
    const handleTimeUpdate = () => updateState();
    const handleVolumeChange = () => updateState();
    const handleRateChange = () => updateState();
    const handleProgress = () => updateState();
    const handleError = (e: Event) => {
      const error = (e.target as HTMLAudioElement).error;
      setState(prev => ({
        ...prev,
        error: error?.message || 'Audio playback error',
        isLoading: false
      }));
    };

    audio.addEventListener('loadstart', handleLoadStart);
    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('canplay', handleCanPlay);
    audio.addEventListener('play', handlePlay);
    audio.addEventListener('pause', handlePause);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('volumechange', handleVolumeChange);
    audio.addEventListener('ratechange', handleRateChange);
    audio.addEventListener('progress', handleProgress);
    audio.addEventListener('error', handleError);

    return () => {
      audio.removeEventListener('loadstart', handleLoadStart);
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('canplay', handleCanPlay);
      audio.removeEventListener('play', handlePlay);
      audio.removeEventListener('pause', handlePause);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('volumechange', handleVolumeChange);
      audio.removeEventListener('ratechange', handleRateChange);
      audio.removeEventListener('progress', handleProgress);
      audio.removeEventListener('error', handleError);
    };
  }, [updateState]);

  // Control functions
  const controls: PlaybackControls = {
    play: async () => {
      if (audioRef.current) {
        try {
          await audioRef.current.play();
        } catch (error: any) {
          setState(prev => ({ ...prev, error: error.message }));
        }
      }
    },

    pause: () => {
      if (audioRef.current) {
        audioRef.current.pause();
      }
    },

    stop: () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.currentTime = 0;
      }
    },

    seek: (time: number) => {
      if (audioRef.current) {
        audioRef.current.currentTime = Math.max(0, Math.min(time, audioRef.current.duration));
      }
    },

    setVolume: (volume: number) => {
      if (audioRef.current) {
        audioRef.current.volume = Math.max(0, Math.min(1, volume));
      }
    },

    setPlaybackRate: (rate: number) => {
      if (audioRef.current) {
        audioRef.current.playbackRate = Math.max(0.25, Math.min(4, rate));
      }
    },

    toggleMute: () => {
      if (audioRef.current) {
        audioRef.current.muted = !audioRef.current.muted;
      }
    },
  };

  return {
    state,
    controls,
    elementRef: audioRef as React.RefObject<HTMLAudioElement | HTMLVideoElement>,
  };
}

/**
 * Video player hook
 */
export function useVideoPlayer(src: string, config: MediaPlaybackConfig = {}): MediaPlayerHook {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [state, setState] = useState<PlaybackState>({
    isPlaying: false,
    isPaused: false,
    isLoading: true,
    isEnded: false,
    currentTime: 0,
    duration: 0,
    volume: config.volume ?? 1,
    playbackRate: config.playbackRate ?? 1,
    buffered: null,
    error: null,
  });

  // Update state from video element
  const updateState = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;

    setState(prev => ({
      ...prev,
      isPlaying: !video.paused && !video.ended,
      isPaused: video.paused && !video.ended,
      isLoading: video.readyState < 3,
      isEnded: video.ended,
      currentTime: video.currentTime,
      duration: video.duration || 0,
      volume: video.volume,
      playbackRate: video.playbackRate,
      buffered: video.buffered,
    }));
  }, []);

  // Setup event listeners (similar to audio)
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const handleLoadStart = () => setState(prev => ({ ...prev, isLoading: true, error: null }));
    const handleLoadedMetadata = () => updateState();
    const handleCanPlay = () => setState(prev => ({ ...prev, isLoading: false }));
    const handlePlay = () => updateState();
    const handlePause = () => updateState();
    const handleEnded = () => updateState();
    const handleTimeUpdate = () => updateState();
    const handleVolumeChange = () => updateState();
    const handleRateChange = () => updateState();
    const handleProgress = () => updateState();
    const handleError = (e: Event) => {
      const error = (e.target as HTMLVideoElement).error;
      setState(prev => ({
        ...prev,
        error: error?.message || 'Video playback error',
        isLoading: false
      }));
    };

    video.addEventListener('loadstart', handleLoadStart);
    video.addEventListener('loadedmetadata', handleLoadedMetadata);
    video.addEventListener('canplay', handleCanPlay);
    video.addEventListener('play', handlePlay);
    video.addEventListener('pause', handlePause);
    video.addEventListener('ended', handleEnded);
    video.addEventListener('timeupdate', handleTimeUpdate);
    video.addEventListener('volumechange', handleVolumeChange);
    video.addEventListener('ratechange', handleRateChange);
    video.addEventListener('progress', handleProgress);
    video.addEventListener('error', handleError);

    return () => {
      video.removeEventListener('loadstart', handleLoadStart);
      video.removeEventListener('loadedmetadata', handleLoadedMetadata);
      video.removeEventListener('canplay', handleCanPlay);
      video.removeEventListener('play', handlePlay);
      video.removeEventListener('pause', handlePause);
      video.removeEventListener('ended', handleEnded);
      video.removeEventListener('timeupdate', handleTimeUpdate);
      video.removeEventListener('volumechange', handleVolumeChange);
      video.removeEventListener('ratechange', handleRateChange);
      video.removeEventListener('progress', handleProgress);
      video.removeEventListener('error', handleError);
    };
  }, [updateState]);

  // Control functions (same as audio)
  const controls: PlaybackControls = {
    play: async () => {
      if (videoRef.current) {
        try {
          await videoRef.current.play();
        } catch (error: any) {
          setState(prev => ({ ...prev, error: error.message }));
        }
      }
    },

    pause: () => {
      if (videoRef.current) {
        videoRef.current.pause();
      }
    },

    stop: () => {
      if (videoRef.current) {
        videoRef.current.pause();
        videoRef.current.currentTime = 0;
      }
    },

    seek: (time: number) => {
      if (videoRef.current) {
        videoRef.current.currentTime = Math.max(0, Math.min(time, videoRef.current.duration));
      }
    },

    setVolume: (volume: number) => {
      if (videoRef.current) {
        videoRef.current.volume = Math.max(0, Math.min(1, volume));
      }
    },

    setPlaybackRate: (rate: number) => {
      if (videoRef.current) {
        videoRef.current.playbackRate = Math.max(0.25, Math.min(4, rate));
      }
    },

    toggleMute: () => {
      if (videoRef.current) {
        videoRef.current.muted = !videoRef.current.muted;
      }
    },
  };

  return {
    state,
    controls,
    elementRef: videoRef as React.RefObject<HTMLAudioElement | HTMLVideoElement>,
  };
}

/**
 * Utility functions
 */
export const formatTime = (seconds: number): string => {
  if (isNaN(seconds)) return '0:00';

  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
};

export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

/**
 * Get media info from S3 URL or blob URL
 */
export const getMediaInfo = async (src: string): Promise<{
  duration?: number;
  size?: number;
  mimeType?: string;
  dimensions?: { width: number; height: number };
}> => {
  return new Promise((resolve) => {
    if (src.startsWith('blob:') || src.includes('.mp4') || src.includes('.webm')) {
      // Video
      const video = document.createElement('video');
      video.onloadedmetadata = () => {
        resolve({
          duration: video.duration,
          dimensions: { width: video.videoWidth, height: video.videoHeight },
        });
      };
      video.onerror = () => resolve({});
      video.src = src;
    } else {
      // Audio
      const audio = document.createElement('audio');
      audio.onloadedmetadata = () => {
        resolve({
          duration: audio.duration,
        });
      };
      audio.onerror = () => resolve({});
      audio.src = src;
    }
  });
};
