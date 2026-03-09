/**
 * @ghatana/ai-voice-ui-react - StemTrack
 * 
 * Individual stem track component with controls.
 * 
 * @doc.type component
 * @doc.purpose Stem track display
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { Stem, StemType } from '../types';
import { Waveform } from './Waveform';

export interface StemTrackProps {
  /** Stem data */
  stem: Stem;
  
  /** Current playback position (0-1) */
  position?: number;
  
  /** Volume change handler */
  onVolumeChange?: (volume: number) => void;
  
  /** Mute toggle handler */
  onMuteToggle?: () => void;
  
  /** Solo toggle handler */
  onSoloToggle?: () => void;
  
  /** Additional CSS classes */
  className?: string;
}

const stemColors: Record<StemType, string> = {
  vocals: '#3b82f6',
  drums: '#22c55e',
  bass: '#eab308',
  other: '#a855f7',
};

/**
 * Individual stem track component.
 */
export const StemTrack: React.FC<StemTrackProps> = ({
  stem,
  position = 0,
  onVolumeChange,
  onMuteToggle,
  onSoloToggle,
  className,
}) => {
  const color = stemColors[stem.type] || '#6b7280';
  
  return (
    <div className={twMerge(clsx('flex items-center gap-3 bg-gray-700/50 rounded-lg p-2'), className)}>
      {/* Controls */}
      <div className="flex items-center gap-1">
        <button
          onClick={onMuteToggle}
          className={clsx(
            'w-6 h-6 rounded text-xs font-bold transition-all',
            stem.muted
              ? 'bg-red-600 text-white'
              : 'bg-gray-600 text-gray-400 hover:bg-gray-500'
          )}
          title="Mute"
        >
          M
        </button>
        <button
          onClick={onSoloToggle}
          className={clsx(
            'w-6 h-6 rounded text-xs font-bold transition-all',
            stem.solo
              ? 'bg-yellow-600 text-white'
              : 'bg-gray-600 text-gray-400 hover:bg-gray-500'
          )}
          title="Solo"
        >
          S
        </button>
      </div>

      {/* Name */}
      <span className="w-16 text-sm font-medium text-white truncate capitalize">
        {stem.name || stem.type}
      </span>

      {/* Waveform */}
      <div className="flex-1 h-8">
        {stem.waveformData ? (
          <Waveform
            data={stem.waveformData}
            position={position}
            color={stem.muted ? '#4b5563' : color}
            progressColor={stem.muted ? '#6b7280' : color}
            height={32}
          />
        ) : (
          <div 
            className="h-full rounded"
            style={{ backgroundColor: stem.muted ? '#4b5563' : color, opacity: 0.3 }}
          />
        )}
      </div>

      {/* Volume */}
      <div className="flex items-center gap-2 w-32">
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          value={stem.volume}
          onChange={(e) => onVolumeChange?.(parseFloat(e.target.value))}
          className="flex-1 h-1 bg-gray-600 rounded-lg appearance-none cursor-pointer"
        />
        <span className="w-8 text-xs text-gray-400 text-right">
          {Math.round(stem.volume * 100)}%
        </span>
      </div>
    </div>
  );
};
