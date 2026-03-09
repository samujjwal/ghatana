/**
 * SimulationPlayer - Playback controls for simulations.
 *
 * @doc.type component
 * @doc.purpose Provides play/pause, step, seek, and speed controls
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import type { SimulationState } from '@ghatana/tutorputor-contracts/v1/simulation';

/**
 * Player props.
 */
interface SimulationPlayerProps {
  /** Current simulation state */
  state: SimulationState;
  /** Play callback */
  onPlay: () => void;
  /** Pause callback */
  onPause: () => void;
  /** Step forward callback */
  onStepForward: () => void;
  /** Step backward callback */
  onStepBackward: () => void;
  /** Seek callback */
  onSeek: (timeMs: number) => void;
  /** Speed change callback */
  onSpeedChange: (speed: number) => void;
  /** Reset callback */
  onReset: () => void;
  /** Whether controls are disabled */
  disabled?: boolean;
  /** Show speed controls */
  showSpeedControls?: boolean;
  /** Show step counter */
  showStepCounter?: boolean;
  /** Compact mode */
  compact?: boolean;
}

/**
 * Available playback speeds.
 */
const SPEED_OPTIONS = [0.25, 0.5, 1, 1.5, 2, 4];

/**
 * Format milliseconds to MM:SS format.
 */
const formatTime = (ms: number): string => {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
};

/**
 * SimulationPlayer component.
 */
export const SimulationPlayer: React.FC<SimulationPlayerProps> = ({
  state,
  onPlay,
  onPause,
  onStepForward,
  onStepBackward,
  onSeek,
  onSpeedChange,
  onReset,
  disabled = false,
  showSpeedControls = true,
  showStepCounter = true,
  compact = false,
}) => {
  const [showSpeedMenu, setShowSpeedMenu] = useState(false);
  const [isDraggingSeek, setIsDraggingSeek] = useState(false);
  const seekBarRef = useRef<HTMLDivElement>(null);
  const speedMenuRef = useRef<HTMLDivElement>(null);

  const progress = state.totalDuration > 0 ? (state.currentTime / state.totalDuration) * 100 : 0;
  const isAtStart = state.currentStepIndex === 0;
  const isAtEnd = state.currentStepIndex >= state.totalSteps - 1;

  /**
   * Handle seek bar interaction.
   */
  const handleSeekInteraction = useCallback(
    (clientX: number) => {
      if (!seekBarRef.current || disabled) return;

      const rect = seekBarRef.current.getBoundingClientRect();
      const percentage = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
      const newTime = percentage * state.totalDuration;
      onSeek(newTime);
    },
    [state.totalDuration, onSeek, disabled]
  );

  /**
   * Handle seek bar mouse down.
   */
  const handleSeekMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (disabled) return;
      setIsDraggingSeek(true);
      handleSeekInteraction(e.clientX);
    },
    [handleSeekInteraction, disabled]
  );

  /**
   * Handle global mouse move during seek.
   */
  useEffect(() => {
    if (!isDraggingSeek) return;

    const handleMouseMove = (e: MouseEvent) => {
      handleSeekInteraction(e.clientX);
    };

    const handleMouseUp = () => {
      setIsDraggingSeek(false);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isDraggingSeek, handleSeekInteraction]);

  /**
   * Close speed menu on outside click.
   */
  useEffect(() => {
    if (!showSpeedMenu) return;

    const handleClickOutside = (e: MouseEvent) => {
      if (speedMenuRef.current && !speedMenuRef.current.contains(e.target as Node)) {
        setShowSpeedMenu(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showSpeedMenu]);

  /**
   * Keyboard shortcuts.
   */
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (disabled) return;

      switch (e.key) {
        case ' ':
          e.preventDefault();
          if (state.isPlaying) onPause();
          else onPlay();
          break;
        case 'ArrowLeft':
          e.preventDefault();
          onStepBackward();
          break;
        case 'ArrowRight':
          e.preventDefault();
          onStepForward();
          break;
        case 'Home':
          e.preventDefault();
          onReset();
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [disabled, state.isPlaying, onPlay, onPause, onStepForward, onStepBackward, onReset]);

  const buttonClasses = `
    flex items-center justify-center rounded-lg transition-all duration-150
    focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2
    disabled:opacity-40 disabled:cursor-not-allowed
  `;

  const primaryButtonClasses = `
    ${buttonClasses}
    ${compact ? 'w-10 h-10' : 'w-12 h-12'}
    bg-blue-600 hover:bg-blue-700 text-white
    disabled:hover:bg-blue-600
  `;

  const secondaryButtonClasses = `
    ${buttonClasses}
    ${compact ? 'w-8 h-8' : 'w-10 h-10'}
    bg-slate-100 hover:bg-slate-200 text-slate-700
    dark:bg-slate-700 dark:hover:bg-slate-600 dark:text-slate-200
    disabled:hover:bg-slate-100 dark:disabled:hover:bg-slate-700
  `;

  return (
    <div className={`bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-200 dark:border-slate-700 ${compact ? 'p-3' : 'p-4'}`}>
      {/* Seek bar */}
      <div className="mb-4">
        <div
          ref={seekBarRef}
          className={`relative w-full ${compact ? 'h-1.5' : 'h-2'} bg-slate-200 dark:bg-slate-600 rounded-full cursor-pointer group`}
          onMouseDown={handleSeekMouseDown}
          role="slider"
          aria-label="Seek bar"
          aria-valuemin={0}
          aria-valuemax={state.totalDuration}
          aria-valuenow={state.currentTime}
          tabIndex={disabled ? -1 : 0}
        >
          {/* Progress fill */}
          <div
            className="absolute left-0 top-0 h-full bg-blue-600 rounded-full transition-all duration-100"
            style={{ width: `${progress}%` }}
          />
          
          {/* Seek handle */}
          <div
            className={`absolute top-1/2 -translate-y-1/2 ${compact ? 'w-3 h-3' : 'w-4 h-4'} bg-white border-2 border-blue-600 rounded-full shadow-md transform -translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity`}
            style={{ left: `${progress}%` }}
          />
        </div>

        {/* Time display */}
        <div className="flex justify-between mt-1.5 text-xs text-slate-500 dark:text-slate-400">
          <span>{formatTime(state.currentTime)}</span>
          <span>{formatTime(state.totalDuration)}</span>
        </div>
      </div>

      {/* Controls */}
      <div className="flex items-center justify-between">
        {/* Left: Step info */}
        {showStepCounter && (
          <div className="flex-1 text-sm text-slate-600 dark:text-slate-300">
            Step {state.currentStepIndex + 1} / {state.totalSteps}
          </div>
        )}

        {/* Center: Playback controls */}
        <div className="flex items-center gap-2">
          {/* Reset */}
          <button
            className={secondaryButtonClasses}
            onClick={onReset}
            disabled={disabled || isAtStart}
            title="Reset (Home)"
            aria-label="Reset simulation"
          >
            <svg className={compact ? 'w-4 h-4' : 'w-5 h-5'} fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6" />
            </svg>
          </button>

          {/* Step backward */}
          <button
            className={secondaryButtonClasses}
            onClick={onStepBackward}
            disabled={disabled || isAtStart}
            title="Previous step (←)"
            aria-label="Previous step"
          >
            <svg className={compact ? 'w-4 h-4' : 'w-5 h-5'} fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12.066 11.2a1 1 0 000 1.6l5.334 4A1 1 0 0019 16V8a1 1 0 00-1.6-.8l-5.333 4zM4.066 11.2a1 1 0 000 1.6l5.334 4A1 1 0 0011 16V8a1 1 0 00-1.6-.8l-5.334 4z" />
            </svg>
          </button>

          {/* Play/Pause */}
          <button
            className={primaryButtonClasses}
            onClick={state.isPlaying ? onPause : onPlay}
            disabled={disabled || isAtEnd}
            title={state.isPlaying ? 'Pause (Space)' : 'Play (Space)'}
            aria-label={state.isPlaying ? 'Pause' : 'Play'}
          >
            {state.isPlaying ? (
              <svg className={compact ? 'w-5 h-5' : 'w-6 h-6'} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            ) : (
              <svg className={compact ? 'w-5 h-5' : 'w-6 h-6'} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            )}
          </button>

          {/* Step forward */}
          <button
            className={secondaryButtonClasses}
            onClick={onStepForward}
            disabled={disabled || isAtEnd}
            title="Next step (→)"
            aria-label="Next step"
          >
            <svg className={compact ? 'w-4 h-4' : 'w-5 h-5'} fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.933 12.8a1 1 0 000-1.6L6.6 7.2A1 1 0 005 8v8a1 1 0 001.6.8l5.333-4zM19.933 12.8a1 1 0 000-1.6l-5.333-4A1 1 0 0013 8v8a1 1 0 001.6.8l5.333-4z" />
            </svg>
          </button>
        </div>

        {/* Right: Speed controls */}
        {showSpeedControls && (
          <div className="flex-1 flex justify-end">
            <div className="relative" ref={speedMenuRef}>
              <button
                className={`${secondaryButtonClasses} px-3 text-sm font-medium min-w-[60px]`}
                onClick={() => setShowSpeedMenu(!showSpeedMenu)}
                disabled={disabled}
                aria-label={`Playback speed: ${state.playbackSpeed}x`}
                aria-expanded={showSpeedMenu}
              >
                {state.playbackSpeed}x
              </button>

              {/* Speed menu dropdown */}
              {showSpeedMenu && (
                <div className="absolute right-0 bottom-full mb-2 bg-white dark:bg-slate-700 rounded-lg shadow-lg border border-slate-200 dark:border-slate-600 py-1 min-w-[80px] z-10">
                  {SPEED_OPTIONS.map((speed) => (
                    <button
                      key={speed}
                      className={`w-full px-3 py-1.5 text-sm text-left hover:bg-slate-100 dark:hover:bg-slate-600 ${
                        speed === state.playbackSpeed ? 'text-blue-600 font-medium' : 'text-slate-700 dark:text-slate-200'
                      }`}
                      onClick={() => {
                        onSpeedChange(speed);
                        setShowSpeedMenu(false);
                      }}
                    >
                      {speed}x
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default SimulationPlayer;
