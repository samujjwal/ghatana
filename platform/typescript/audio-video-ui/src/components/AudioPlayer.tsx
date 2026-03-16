import React, { useRef, useState, useCallback, useEffect } from 'react';
import type { AudioFormat } from '@ghatana/audio-video-types';

export interface AudioPlayerProps {
  /** Audio bytes to play, or a URL string. */
  src: ArrayBuffer | string;
  format?: AudioFormat;
  /** Millisecond-precision word boundaries for synchronized caption highlight. */
  wordBoundaries?: ReadonlyArray<{ word: string; startMs: number; endMs: number }>;
  autoPlay?: boolean;
  loop?: boolean;
  onTimeUpdate?: (currentMs: number) => void;
  onEnded?: () => void;
  onError?: (error: Error) => void;
  className?: string;
}

/** Returns a `data:audio/*;base64,...` URL from an ``ArrayBuffer``. */
function arrayBufferToDataUrl(buffer: ArrayBuffer, format: AudioFormat): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const b of bytes) {
    binary += String.fromCharCode(b);
  }
  const base64 = btoa(binary);
  const mime =
    format === 'mp3'
      ? 'audio/mpeg'
      : format === 'ogg_opus'
        ? 'audio/ogg'
        : format === 'flac'
          ? 'audio/flac'
          : format === 'aac'
            ? 'audio/aac'
            : format === 'webm_opus'
              ? 'audio/webm'
              : 'audio/wav';
  return `data:${mime};base64,${base64}`;
}

/**
 * A fully accessible HTML5 audio player with optional word-boundary highlight
 * support for TTS playback.
 *
 * Emits `onTimeUpdate` every animation frame while playing so consumers can
 * highlight captions in sync.
 */
export function AudioPlayer({
  src,
  format = 'wav',
  wordBoundaries,
  autoPlay = false,
  loop = false,
  onTimeUpdate,
  onEnded,
  onError,
  className,
}: AudioPlayerProps): React.ReactElement {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentMs, setCurrentMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [volume, setVolume] = useState(1);
  const rafRef = useRef<number | undefined>(undefined);

  const srcUrl = React.useMemo(
    () =>
      typeof src === 'string' ? src : arrayBufferToDataUrl(src, format),
    [src, format],
  );

  const tick = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    const ms = audio.currentTime * 1000;
    setCurrentMs(ms);
    onTimeUpdate?.(ms);
    if (!audio.paused) {
      rafRef.current = requestAnimationFrame(tick);
    }
  }, [onTimeUpdate]);

  const togglePlayPause = useCallback(async () => {
    const audio = audioRef.current;
    if (!audio) return;
    try {
      if (audio.paused) {
        await audio.play();
      } else {
        audio.pause();
      }
    } catch (err) {
      onError?.(err instanceof Error ? err : new Error(String(err)));
    }
  }, [onError]);

  const handlePlay = useCallback(() => {
    setIsPlaying(true);
    rafRef.current = requestAnimationFrame(tick);
  }, [tick]);

  const handlePause = useCallback(() => {
    setIsPlaying(false);
    if (rafRef.current !== undefined) {
      cancelAnimationFrame(rafRef.current);
    }
  }, []);

  const handleEnded = useCallback(() => {
    setIsPlaying(false);
    onEnded?.();
  }, [onEnded]);

  const handleError = useCallback(() => {
    onError?.(new Error('Audio element playback error'));
  }, [onError]);

  const handleLoadedMetadata = useCallback(() => {
    const audio = audioRef.current;
    if (audio) setDurationMs(audio.duration * 1000);
  }, []);

  const handleVolumeChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const v = Number(e.target.value);
    setVolume(v);
    if (audioRef.current) audioRef.current.volume = v;
  }, []);

  const handleSeek = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const ms = Number(e.target.value);
    if (audioRef.current) audioRef.current.currentTime = ms / 1000;
    setCurrentMs(ms);
  }, []);

  useEffect(() => {
    return () => {
      if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  const activeWord = wordBoundaries?.find(
    (w) => currentMs >= w.startMs && currentMs <= w.endMs,
  );

  const fmt = (ms: number): string => {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  };

  return (
    <div
      className={`audio-player flex flex-col gap-2 p-3 rounded-lg bg-gray-100 dark:bg-gray-800 ${className ?? ''}`}
      role="region"
      aria-label="Audio player"
    >
      {/* Hidden native element */}
      <audio
        ref={audioRef}
        src={srcUrl}
        autoPlay={autoPlay}
        loop={loop}
        onPlay={handlePlay}
        onPause={handlePause}
        onEnded={handleEnded}
        onError={handleError}
        onLoadedMetadata={handleLoadedMetadata}
        className="sr-only"
      />

      {/* Word highlight display */}
      {wordBoundaries && (
        <div className="text-sm text-center text-gray-700 dark:text-gray-300 min-h-6">
          {wordBoundaries.map((wb, i) => (
            <span
              key={i}
              className={
                activeWord?.word === wb.word && activeWord.startMs === wb.startMs
                  ? 'font-bold text-blue-600 dark:text-blue-400'
                  : ''
              }
            >
              {wb.word}{' '}
            </span>
          ))}
        </div>
      )}

      {/* Progress */}
      <input
        type="range"
        min={0}
        max={durationMs}
        value={currentMs}
        onChange={handleSeek}
        className="w-full accent-blue-600"
        aria-label="Seek"
      />

      <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
        <span>{fmt(currentMs)}</span>
        <span>{fmt(durationMs)}</span>
      </div>

      {/* Controls */}
      <div className="flex items-center gap-3">
        <button
          onClick={togglePlayPause}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          className="flex items-center justify-center w-9 h-9 rounded-full bg-blue-600 text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-blue-400 transition"
        >
          {isPlaying ? (
            <span aria-hidden>⏸</span>
          ) : (
            <span aria-hidden>▶</span>
          )}
        </button>

        <label className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
          <span aria-hidden>🔊</span>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={volume}
            onChange={handleVolumeChange}
            className="w-20 accent-blue-600"
            aria-label="Volume"
          />
        </label>
      </div>
    </div>
  );
}
