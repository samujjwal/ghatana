/**
 * @ghatana/ai-voice-ui-react - Waveform
 * 
 * Audio waveform visualization component.
 * 
 * @doc.type component
 * @doc.purpose Waveform visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useRef, useEffect, useMemo } from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface WaveformProps {
  /** Waveform data (normalized 0-1) */
  data: number[];
  
  /** Current playback position (0-1) */
  position?: number;
  
  /** Waveform color */
  color?: string;
  
  /** Progress color */
  progressColor?: string;
  
  /** Background color */
  backgroundColor?: string;
  
  /** Height in pixels */
  height?: number;
  
  /** Whether to show as bars or line */
  variant?: 'bars' | 'line';
  
  /** Click handler for seeking */
  onSeek?: (position: number) => void;
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Audio waveform visualization component.
 */
export const Waveform: React.FC<WaveformProps> = ({
  data,
  position = 0,
  color = '#3b82f6',
  progressColor = '#60a5fa',
  backgroundColor = '#1f2937',
  height = 80,
  variant = 'bars',
  onSeek,
  className,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const normalizedData = useMemo(() => {
    if (data.length === 0) return [];
    const max = Math.max(...data.map(Math.abs));
    return max > 0 ? data.map(v => v / max) : data;
  }, [data]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = container.getBoundingClientRect();
    
    canvas.width = rect.width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = `${rect.width}px`;
    canvas.style.height = `${height}px`;
    
    ctx.scale(dpr, dpr);

    // Clear
    ctx.fillStyle = backgroundColor;
    ctx.fillRect(0, 0, rect.width, height);

    if (normalizedData.length === 0) return;

    const barWidth = rect.width / normalizedData.length;
    const centerY = height / 2;
    const progressX = position * rect.width;

    if (variant === 'bars') {
      normalizedData.forEach((value, i) => {
        const x = i * barWidth;
        const barHeight = Math.abs(value) * (height * 0.8);
        const y = centerY - barHeight / 2;
        
        ctx.fillStyle = x < progressX ? progressColor : color;
        ctx.fillRect(x, y, Math.max(1, barWidth - 1), barHeight);
      });
    } else {
      // Line variant
      ctx.beginPath();
      ctx.strokeStyle = color;
      ctx.lineWidth = 1;
      
      normalizedData.forEach((value, i) => {
        const x = i * barWidth;
        const y = centerY - value * (height * 0.4);
        
        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      });
      
      ctx.stroke();
      
      // Progress overlay
      if (position > 0) {
        ctx.fillStyle = `${progressColor}33`;
        ctx.fillRect(0, 0, progressX, height);
      }
    }

    // Playhead
    if (position > 0 && position < 1) {
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(progressX - 1, 0, 2, height);
    }
  }, [normalizedData, position, height, color, progressColor, backgroundColor, variant]);

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!onSeek || !containerRef.current) return;
    
    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const newPosition = Math.max(0, Math.min(1, x / rect.width));
    onSeek(newPosition);
  };

  return (
    <div
      ref={containerRef}
      className={twMerge(clsx('relative w-full rounded-lg overflow-hidden', onSeek && 'cursor-pointer'), className)}
      onClick={handleClick}
    >
      <canvas ref={canvasRef} className="w-full" />
    </div>
  );
};
