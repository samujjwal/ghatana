/**
 * AI Voice Production Studio - Waveform Display
 * 
 * @doc.type component
 * @doc.purpose Audio waveform visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useRef, useEffect, useMemo } from 'react';
import type { AudioFile } from '../../types';

interface WaveformDisplayProps {
  audioFile: AudioFile;
  currentTime: number;
  duration: number;
  height?: number;
  color?: string;
  backgroundColor?: string;
  onSeek?: (time: number) => void;
}

export const WaveformDisplay: React.FC<WaveformDisplayProps> = ({
  audioFile,
  currentTime,
  duration,
  height = 100,
  color = '#3b82f6',
  backgroundColor = '#1f2937',
  onSeek,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const waveformData = useMemo(() => {
    if (audioFile.waveformData) return audioFile.waveformData;
    // Generate placeholder waveform if no data
    return Array.from({ length: 200 }, () => Math.random() * 0.8 + 0.1);
  }, [audioFile.waveformData]);

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

    // Draw waveform
    const barWidth = rect.width / waveformData.length;
    const centerY = height / 2;

    ctx.fillStyle = color;
    waveformData.forEach((value, i) => {
      const barHeight = value * (height * 0.8);
      const x = i * barWidth;
      const y = centerY - barHeight / 2;
      ctx.fillRect(x, y, Math.max(1, barWidth - 1), barHeight);
    });

    // Draw playhead
    if (duration > 0) {
      const playheadX = (currentTime / duration) * rect.width;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(playheadX - 1, 0, 2, height);
    }
  }, [waveformData, currentTime, duration, height, color, backgroundColor]);

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!onSeek || !containerRef.current || duration <= 0) return;
    
    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const time = (x / rect.width) * duration;
    onSeek(Math.max(0, Math.min(duration, time)));
  };

  return (
    <div
      ref={containerRef}
      className="relative w-full cursor-pointer rounded-lg overflow-hidden"
      onClick={handleClick}
    >
      <canvas ref={canvasRef} className="w-full" />
      
      {/* Time display */}
      <div className="absolute bottom-2 left-2 text-xs text-gray-400 bg-gray-900/80 px-2 py-1 rounded">
        {formatTime(currentTime)} / {formatTime(duration)}
      </div>
    </div>
  );
};

function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}
