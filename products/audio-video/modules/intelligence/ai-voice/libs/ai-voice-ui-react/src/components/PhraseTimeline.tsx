/**
 * @ghatana/ai-voice-ui-react - PhraseTimeline
 * 
 * Timeline view of detected phrases.
 * 
 * @doc.type component
 * @doc.purpose Phrase timeline display
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { Phrase, PhraseLabel } from '../types';

export interface PhraseTimelineProps {
  /** Phrases to display */
  phrases: Phrase[];
  
  /** Total duration in seconds */
  duration: number;
  
  /** Current playback time in seconds */
  currentTime?: number;
  
  /** Selected phrase ID */
  selectedPhraseId?: string;
  
  /** Phrase click handler */
  onPhraseClick?: (phrase: Phrase) => void;
  
  /** Seek handler */
  onSeek?: (time: number) => void;
  
  /** Height in pixels */
  height?: number;
  
  /** Additional CSS classes */
  className?: string;
}

const labelColors: Record<PhraseLabel, string> = {
  verse: 'bg-blue-500',
  chorus: 'bg-purple-500',
  bridge: 'bg-green-500',
  intro: 'bg-yellow-500',
  outro: 'bg-orange-500',
  other: 'bg-gray-500',
};

/**
 * Timeline view of detected phrases.
 */
export const PhraseTimeline: React.FC<PhraseTimelineProps> = ({
  phrases,
  duration,
  currentTime = 0,
  selectedPhraseId,
  onPhraseClick,
  onSeek,
  height = 60,
  className,
}) => {
  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!onSeek || duration <= 0) return;
    
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const time = (x / rect.width) * duration;
    onSeek(Math.max(0, Math.min(duration, time)));
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div
      className={twMerge(clsx('relative bg-gray-800 rounded-lg overflow-hidden', onSeek && 'cursor-pointer'), className)}
      style={{ height }}
      onClick={handleClick}
    >
      {/* Phrases */}
      {phrases.map((phrase) => {
        const left = duration > 0 ? (phrase.startTime / duration) * 100 : 0;
        const width = duration > 0 ? ((phrase.endTime - phrase.startTime) / duration) * 100 : 0;
        const isSelected = phrase.id === selectedPhraseId;
        const hasTakes = phrase.takes && phrase.takes.length > 0;
        
        return (
          <div
            key={phrase.id}
            className={clsx(
              'absolute top-2 bottom-2 rounded transition-all',
              labelColors[phrase.label || 'other'],
              isSelected ? 'ring-2 ring-white' : 'opacity-70 hover:opacity-100',
              hasTakes && 'border-b-2 border-green-400'
            )}
            style={{ left: `${left}%`, width: `${width}%` }}
            onClick={(e) => {
              e.stopPropagation();
              onPhraseClick?.(phrase);
            }}
            title={`${phrase.label || 'Phrase'}: ${formatTime(phrase.startTime)} - ${formatTime(phrase.endTime)}`}
          />
        );
      })}

      {/* Playhead */}
      {duration > 0 && (
        <div
          className="absolute top-0 bottom-0 w-0.5 bg-white pointer-events-none"
          style={{ left: `${(currentTime / duration) * 100}%` }}
        />
      )}

      {/* Time markers */}
      <div className="absolute bottom-0 left-0 right-0 h-4 flex justify-between px-2 text-xs text-gray-500">
        <span>0:00</span>
        <span>{formatTime(duration)}</span>
      </div>
    </div>
  );
};
