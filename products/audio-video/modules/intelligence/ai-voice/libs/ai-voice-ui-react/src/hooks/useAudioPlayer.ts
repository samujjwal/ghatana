/**
 * @ghatana/ai-voice-ui-react - useAudioPlayer
 * 
 * Hook for audio playback control.
 * 
 * @doc.type function
 * @doc.purpose Audio playback hook
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import type { PlaybackState } from '../types';

export interface UseAudioPlayerOptions {
  /** Audio file path */
  audioPath?: string;
  
  /** Auto-play on load */
  autoPlay?: boolean;
  
  /** Loop playback */
  loop?: boolean;
  
  /** Callback when playback ends */
  onEnd?: () => void;
  
  /** Callback on time update */
  onTimeUpdate?: (time: number) => void;
}

export interface UseAudioPlayerResult {
  /** Current playback state */
  state: PlaybackState;
  
  /** Play audio */
  play: () => void;
  
  /** Pause audio */
  pause: () => void;
  
  /** Toggle play/pause */
  toggle: () => void;
  
  /** Seek to time */
  seek: (time: number) => void;
  
  /** Set loop region */
  setLoop: (start: number, end: number) => void;
  
  /** Clear loop region */
  clearLoop: () => void;
  
  /** Set volume (0-1) */
  setVolume: (volume: number) => void;
}

/**
 * Hook for audio playback control.
 */
export function useAudioPlayer(options: UseAudioPlayerOptions = {}): UseAudioPlayerResult {
  const { audioPath, autoPlay = false, loop = false, onEnd, onTimeUpdate } = options;
  
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const animationRef = useRef<number | null>(null);
  
  const [state, setState] = useState<PlaybackState>({
    isPlaying: false,
    currentTime: 0,
    duration: 0,
    isLooping: loop,
  });

  // Create audio element
  useEffect(() => {
    if (!audioPath) return;
    
    const audio = new Audio(audioPath);
    audioRef.current = audio;
    
    audio.addEventListener('loadedmetadata', () => {
      setState(prev => ({ ...prev, duration: audio.duration }));
    });
    
    audio.addEventListener('ended', () => {
      setState(prev => ({ ...prev, isPlaying: false, currentTime: 0 }));
      onEnd?.();
    });
    
    if (autoPlay) {
      audio.play().catch(() => {});
    }
    
    return () => {
      audio.pause();
      audio.src = '';
      audioRef.current = null;
    };
  }, [audioPath, autoPlay, onEnd]);

  // Animation loop for time updates
  useEffect(() => {
    const updateTime = () => {
      if (audioRef.current) {
        const time = audioRef.current.currentTime;
        setState(prev => ({ ...prev, currentTime: time }));
        onTimeUpdate?.(time);
        
        // Check loop bounds
        if (state.isLooping && state.loopEnd && time >= state.loopEnd) {
          audioRef.current.currentTime = state.loopStart || 0;
        }
      }
      animationRef.current = requestAnimationFrame(updateTime);
    };
    
    if (state.isPlaying) {
      animationRef.current = requestAnimationFrame(updateTime);
    }
    
    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [state.isPlaying, state.isLooping, state.loopStart, state.loopEnd, onTimeUpdate]);

  const play = useCallback(() => {
    audioRef.current?.play().then(() => {
      setState(prev => ({ ...prev, isPlaying: true }));
    }).catch(() => {});
  }, []);

  const pause = useCallback(() => {
    audioRef.current?.pause();
    setState(prev => ({ ...prev, isPlaying: false }));
  }, []);

  const toggle = useCallback(() => {
    if (state.isPlaying) {
      pause();
    } else {
      play();
    }
  }, [state.isPlaying, play, pause]);

  const seek = useCallback((time: number) => {
    if (audioRef.current) {
      audioRef.current.currentTime = Math.max(0, Math.min(time, state.duration));
      setState(prev => ({ ...prev, currentTime: time }));
    }
  }, [state.duration]);

  const setLoop = useCallback((start: number, end: number) => {
    setState(prev => ({
      ...prev,
      isLooping: true,
      loopStart: start,
      loopEnd: end,
    }));
  }, []);

  const clearLoop = useCallback(() => {
    setState(prev => ({
      ...prev,
      isLooping: false,
      loopStart: undefined,
      loopEnd: undefined,
    }));
  }, []);

  const setVolume = useCallback((volume: number) => {
    if (audioRef.current) {
      audioRef.current.volume = Math.max(0, Math.min(1, volume));
    }
  }, []);

  return {
    state,
    play,
    pause,
    toggle,
    seek,
    setLoop,
    clearLoop,
    setVolume,
  };
}
