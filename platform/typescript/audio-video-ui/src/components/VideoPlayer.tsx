import React, { useRef, useState, useCallback } from 'react';

export interface VideoPlayerProps {
  /** Video URL or a Blob/File object URL. */
  src: string;
  /** Optional poster image URL shown before play. */
  poster?: string;
  autoPlay?: boolean;
  loop?: boolean;
  muted?: boolean;
  /**
   * Overlay detections drawn on top of the video for vision analysis results.
   * Coordinates are normalised 0–1 relative to video dimensions.
   */
  detectionOverlays?: ReadonlyArray<{
    label: string;
    confidence: number;
    /** Normalised coordinates [0,1] relative to video W/H. */
    x: number;
    y: number;
    width: number;
    height: number;
  }>;
  onTimeUpdate?: (currentMs: number) => void;
  onEnded?: () => void;
  onError?: (error: Error) => void;
  className?: string;
}

/**
 * HTML5 video player with an optional bounding-box overlay canvas for
 * rendering computer-vision detection results in sync with video playback.
 */
export function VideoPlayer({
  src,
  poster,
  autoPlay = false,
  loop = false,
  muted = false,
  detectionOverlays,
  onTimeUpdate,
  onEnded,
  onError,
  className,
}: VideoPlayerProps): React.ReactElement {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);

  const drawOverlays = useCallback(() => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas || !detectionOverlays?.length) return;

    canvas.width = video.clientWidth;
    canvas.height = video.clientHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (const d of detectionOverlays) {
      const x = d.x * canvas.width;
      const y = d.y * canvas.height;
      const w = d.width * canvas.width;
      const h = d.height * canvas.height;

      ctx.strokeStyle = 'rgba(59,130,246,0.9)';
      ctx.lineWidth = 2;
      ctx.strokeRect(x, y, w, h);

      ctx.fillStyle = 'rgba(59,130,246,0.85)';
      ctx.fillRect(x, y - 20, w, 20);
      ctx.fillStyle = '#fff';
      ctx.font = '11px sans-serif';
      ctx.fillText(
        `${d.label} ${Math.round(d.confidence * 100)}%`,
        x + 4,
        y - 5,
      );
    }
  }, [detectionOverlays]);

  const handleTimeUpdate = useCallback(() => {
    const ms = (videoRef.current?.currentTime ?? 0) * 1000;
    onTimeUpdate?.(ms);
    drawOverlays();
  }, [onTimeUpdate, drawOverlays]);

  const handleEnded = useCallback(() => {
    setIsPlaying(false);
    onEnded?.();
  }, [onEnded]);

  const handleError = useCallback(() => {
    onError?.(new Error('Video element playback error'));
  }, [onError]);

  const togglePlayPause = useCallback(async () => {
    const video = videoRef.current;
    if (!video) return;
    try {
      if (video.paused) {
        await video.play();
        setIsPlaying(true);
      } else {
        video.pause();
        setIsPlaying(false);
      }
    } catch (err) {
      onError?.(err instanceof Error ? err : new Error(String(err)));
    }
  }, [onError]);

  return (
    <div
      className={`video-player relative overflow-hidden rounded-lg bg-black ${className ?? ''}`}
      role="region"
      aria-label="Video player"
    >
      <video
        ref={videoRef}
        src={src}
        poster={poster}
        autoPlay={autoPlay}
        loop={loop}
        muted={muted}
        onTimeUpdate={handleTimeUpdate}
        onEnded={handleEnded}
        onError={handleError}
        className="w-full h-full"
        playsInline
      />

      {/* Detection overlay canvas — sits exactly on top of the video */}
      {detectionOverlays && (
        <canvas
          ref={canvasRef}
          className="absolute inset-0 pointer-events-none w-full h-full"
          aria-hidden
        />
      )}

      {/* Play/pause overlay button */}
      <button
        onClick={togglePlayPause}
        aria-label={isPlaying ? 'Pause video' : 'Play video'}
        className="absolute bottom-3 left-3 w-9 h-9 flex items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80 transition focus-visible:outline focus-visible:outline-blue-400"
      >
        {isPlaying ? <span aria-hidden>⏸</span> : <span aria-hidden>▶</span>}
      </button>
    </div>
  );
}
