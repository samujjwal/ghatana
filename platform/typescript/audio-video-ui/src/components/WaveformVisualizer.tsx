import React, { useEffect, useRef, useCallback } from 'react';

export interface WaveformVisualizerProps {
  /**
   * Live audio stream source — if provided the visualiser reads from the Web
   * Audio API directly.  Either `stream` or `audioBuffer` must be supplied.
   */
  stream?: MediaStream;
  /**
   * Pre-recorded audio buffer for static waveform rendering.
   */
  audioBuffer?: AudioBuffer;
  /** Current playback position in milliseconds (for cursor). */
  currentMs?: number;
  /** Total duration in milliseconds. */
  durationMs?: number;
  width?: number;
  height?: number;
  /** Bar colour (CSS colour string). */
  barColor?: string;
  /** Number of FFT frequency bins (power of 2, 32–32768). */
  fftSize?: number;
  className?: string;
}

/**
 * Canvas-based real-time waveform / frequency bar visualiser.
 *
 * When a `MediaStream` is provided it renders live FFT bars.
 * When an `AudioBuffer` is provided it renders the static waveform with a
 * playback cursor overlay.
 */
export function WaveformVisualizer({
  stream,
  audioBuffer,
  currentMs = 0,
  durationMs = 0,
  width = 400,
  height = 80,
  barColor = 'rgb(59,130,246)',
  fftSize = 256,
  className,
}: WaveformVisualizerProps): React.ReactElement {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const animRef = useRef<number | undefined>(undefined);
  const audioCtxRef = useRef<AudioContext | undefined>(undefined);
  const analyserRef = useRef<AnalyserNode | undefined>(undefined);

  // ---------- Live FFT mode ----------
  const startLiveFFT = useCallback(() => {
    if (!stream) return;

    const ctx = new AudioContext();
    audioCtxRef.current = ctx;
    const source = ctx.createMediaStreamSource(stream);
    const analyser = ctx.createAnalyser();
    analyser.fftSize = fftSize;
    source.connect(analyser);
    analyserRef.current = analyser;

    const data = new Uint8Array(analyser.frequencyBinCount);
    const canvas = canvasRef.current;
    if (!canvas) return;
    const drawCtx = canvas.getContext('2d');
    if (!drawCtx) return;

    const draw = (): void => {
      analyser.getByteFrequencyData(data);
      drawCtx.clearRect(0, 0, width, height);

      const barWidth = (width / data.length) * 2.5;
      let x = 0;
      for (const value of data) {
        const barHeight = (value / 255) * height;
        drawCtx.fillStyle = barColor;
        drawCtx.fillRect(x, height - barHeight, barWidth, barHeight);
        x += barWidth + 1;
        if (x > width) break;
      }

      animRef.current = requestAnimationFrame(draw);
    };
    draw();
  }, [stream, fftSize, width, height, barColor]);

  // ---------- Static buffer waveform ----------
  const drawStaticWaveform = useCallback(() => {
    if (!audioBuffer) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const channelData = audioBuffer.getChannelData(0);
    const step = Math.ceil(channelData.length / width);
    ctx.clearRect(0, 0, width, height);

    ctx.beginPath();
    const mid = height / 2;
    for (let i = 0; i < width; i++) {
      let min = 1.0;
      let max = -1.0;
      for (let j = 0; j < step; j++) {
        const datum = channelData[i * step + j] ?? 0;
        if (datum < min) min = datum;
        if (datum > max) max = datum;
      }
      ctx.fillStyle = barColor;
      ctx.fillRect(i, Math.round(mid + min * mid), 1, Math.round((max - min) * mid));
    }

    // Playback cursor
    if (durationMs > 0) {
      const cursorX = (currentMs / durationMs) * width;
      ctx.strokeStyle = 'rgba(239,68,68,0.9)';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(cursorX, 0);
      ctx.lineTo(cursorX, height);
      ctx.stroke();
    }
  }, [audioBuffer, currentMs, durationMs, width, height, barColor]);

  useEffect(() => {
    if (stream) {
      startLiveFFT();
    }
    return () => {
      if (animRef.current !== undefined) cancelAnimationFrame(animRef.current);
      void audioCtxRef.current?.close();
    };
  }, [stream, startLiveFFT]);

  useEffect(() => {
    if (!stream) drawStaticWaveform();
  }, [stream, drawStaticWaveform]);

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      className={`waveform-visualizer rounded ${className ?? ''}`}
      aria-label="Audio waveform visualizer"
      role="img"
    />
  );
}
