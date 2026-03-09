/**
 * @doc.type component
 * @doc.purpose Animation Studio - Complete animation authoring environment
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useRef, useEffect, useCallback } from "react";
import { AnimationTimeline } from "./AnimationTimeline";
import {
  AnimationRuntime,
  AnimationRenderer,
  createAnimationSpec,
} from "../../../../../services/tutorputor-platform/src/modules/animation-runtime/service";
import type { AnimationSpec } from "../../../../../services/tutorputor-platform/src/modules/animation-runtime/service";
import "./AnimationStudio.css";

interface AnimationStudioProps {
  initialAnimation?: AnimationSpec;
  onSave?: (animation: AnimationSpec) => void;
  onExport?: (
    animation: AnimationSpec,
    format: "json" | "video" | "gif",
  ) => void;
}

// Simple canvas renderer for preview
class CanvasRenderer implements AnimationRenderer {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private properties: Record<string, any> = {
    x: 100,
    y: 100,
    width: 50,
    height: 50,
    rotation: 0,
    scale: 1,
    opacity: 1,
    color: "#4a9eff",
  };

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d")!;
  }

  setProperty(property: string, value: any): void {
    this.properties[property] = value;
  }

  getProperty(property: string): any {
    return this.properties[property];
  }

  applyTransform(transform: Record<string, any>): void {
    Object.assign(this.properties, transform);
  }

  render(): void {
    const { x, y, width, height, rotation, scale, opacity, color } =
      this.properties;

    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

    this.ctx.save();
    this.ctx.translate(x, y);
    this.ctx.rotate((rotation * Math.PI) / 180);
    this.ctx.scale(scale, scale);
    this.ctx.globalAlpha = opacity;

    this.ctx.fillStyle = color;
    this.ctx.fillRect(-width / 2, -height / 2, width, height);

    this.ctx.restore();

    // Draw grid
    this.ctx.strokeStyle = "#333";
    this.ctx.lineWidth = 1;
    for (let i = 0; i < this.canvas.width; i += 50) {
      this.ctx.beginPath();
      this.ctx.moveTo(i, 0);
      this.ctx.lineTo(i, this.canvas.height);
      this.ctx.stroke();
    }
    for (let i = 0; i < this.canvas.height; i += 50) {
      this.ctx.beginPath();
      this.ctx.moveTo(0, i);
      this.ctx.lineTo(this.canvas.width, i);
      this.ctx.stroke();
    }
  }
}

export const AnimationStudio: React.FC<AnimationStudioProps> = ({
  initialAnimation,
  onSave,
  onExport,
}) => {
  const [animation, setAnimation] = useState<AnimationSpec>(
    initialAnimation ||
      createAnimationSpec({
        id: `anim-${Date.now()}`,
        title: "New Animation",
        description: "Animation description",
        duration: 5,
        keyframes: [
          { time: 0, properties: { x: 100, y: 100, rotation: 0 } },
          { time: 5, properties: { x: 300, y: 100, rotation: 360 } },
        ],
        loop: false,
        autoplay: false,
      }),
  );

  const [currentTime, setCurrentTime] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [showExportDialog, setShowExportDialog] = useState(false);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const runtimeRef = useRef<AnimationRuntime | null>(null);
  const rendererRef = useRef<CanvasRenderer | null>(null);

  // Initialize animation runtime
  useEffect(() => {
    if (!canvasRef.current) return;

    const runtime = new AnimationRuntime();
    const renderer = new CanvasRenderer(canvasRef.current);

    runtime.setRenderer(renderer);
    runtime.loadAnimation(animation);

    // Listen to animation events
    runtime.addEventListener("keyframe", () => {
      const state = runtime.getState();
      setCurrentTime(state.currentTime);
    });

    runtime.addEventListener("complete", () => {
      setIsPlaying(false);
    });

    runtimeRef.current = runtime;
    rendererRef.current = renderer;

    // Initial render
    renderer.render();

    return () => {
      runtime.stop();
    };
  }, []);

  // Update animation when it changes
  useEffect(() => {
    if (runtimeRef.current) {
      runtimeRef.current.loadAnimation(animation);
      if (rendererRef.current) {
        rendererRef.current.render();
      }
    }
  }, [animation]);

  // Update current time during playback
  useEffect(() => {
    if (!isPlaying || !runtimeRef.current) return;

    const interval = setInterval(() => {
      const state = runtimeRef.current!.getState();
      setCurrentTime(state.currentTime);
    }, 16); // ~60fps

    return () => clearInterval(interval);
  }, [isPlaying]);

  const handlePlay = useCallback(() => {
    if (runtimeRef.current) {
      runtimeRef.current.play();
      setIsPlaying(true);
    }
  }, []);

  const handlePause = useCallback(() => {
    if (runtimeRef.current) {
      runtimeRef.current.pause();
      setIsPlaying(false);
    }
  }, []);

  const handleStop = useCallback(() => {
    if (runtimeRef.current) {
      runtimeRef.current.stop();
      setIsPlaying(false);
      setCurrentTime(0);
    }
  }, []);

  const handleSave = useCallback(() => {
    if (onSave) {
      onSave(animation);
    }
  }, [animation, onSave]);

  const handleExport = useCallback(
    (format: "json" | "video" | "gif") => {
      if (format === "video" && canvasRef.current) {
        // Client-side video export using MediaRecorder
        const canvas = canvasRef.current;
        const stream = canvas.captureStream(30); // 30 FPS
        const recorder = new MediaRecorder(stream, { mimeType: "video/webm" });
        const chunks: Blob[] = [];

        recorder.ondataavailable = (e) => {
          if (e.data.size > 0) chunks.push(e.data);
        };

        recorder.onstop = () => {
          const blob = new Blob(chunks, { type: "video/webm" });
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `${animation.title.replace(/\s+/g, "_")}.webm`;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          URL.revokeObjectURL(url);
        };

        // Start recording and playback
        recorder.start();
        handleStop();
        setTimeout(() => {
          handlePlay();
          // Stop after duration
          setTimeout(
            () => {
              handleStop();
              recorder.stop();
              setIsPlaying(false);
            },
            animation.durationSeconds * 1000 + 100,
          ); // Buffer
        }, 100);
      }

      if (onExport) {
        onExport(animation, format);
      }
      setShowExportDialog(false);
    },
    [animation, onExport, handlePlay, handleStop],
  );

  const handleAnimationChange = useCallback((newAnimation: AnimationSpec) => {
    setAnimation(newAnimation);
  }, []);

  return (
    <div className="animation-studio">
      {/* Header */}
      <div className="studio-header">
        <div className="studio-title">
          <input
            type="text"
            value={animation.title}
            onChange={(e) =>
              setAnimation({ ...animation, title: e.target.value })
            }
            className="title-input"
          />
        </div>
        <div className="studio-actions">
          <button onClick={handleSave} className="btn-save">
            Save
          </button>
          <button
            onClick={() => setShowExportDialog(true)}
            className="btn-export"
          >
            Export
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="studio-content">
        {/* Preview Canvas */}
        <div className="preview-panel">
          <div className="preview-header">
            <h3>Preview</h3>
            <div className="preview-info">
              <span>{animation.type.toUpperCase()}</span>
              <span>•</span>
              <span>{animation.durationSeconds}s</span>
            </div>
          </div>
          <div className="preview-canvas-container">
            <canvas
              ref={canvasRef}
              width={600}
              height={400}
              className="preview-canvas"
            />
          </div>
          <div className="preview-description">
            <textarea
              value={animation.description}
              onChange={(e) =>
                setAnimation({ ...animation, description: e.target.value })
              }
              placeholder="Animation description..."
              rows={3}
            />
          </div>
        </div>

        {/* Properties Panel */}
        <div className="properties-panel">
          <h3>Animation Settings</h3>

          <div className="setting-group">
            <label>Duration (seconds)</label>
            <input
              type="number"
              value={animation.durationSeconds}
              onChange={(e) =>
                setAnimation({
                  ...animation,
                  durationSeconds: parseFloat(e.target.value) || 1,
                })
              }
              min="0.1"
              step="0.1"
            />
          </div>

          <div className="setting-group">
            <label>Type</label>
            <select
              value={animation.type}
              onChange={(e) =>
                setAnimation({
                  ...animation,
                  type: e.target.value as "2d" | "3d" | "timeline",
                })
              }
            >
              <option value="2d">2D</option>
              <option value="3d">3D</option>
              <option value="timeline">Timeline</option>
            </select>
          </div>

          <div className="setting-group">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={animation.loop || false}
                onChange={(e) =>
                  setAnimation({ ...animation, loop: e.target.checked })
                }
              />
              Loop Animation
            </label>
          </div>

          <div className="setting-group">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={animation.autoplay || false}
                onChange={(e) =>
                  setAnimation({ ...animation, autoplay: e.target.checked })
                }
              />
              Autoplay
            </label>
          </div>

          <div className="keyframes-summary">
            <h4>Keyframes</h4>
            <div className="keyframe-count">
              {animation.keyframes.length} keyframes
            </div>
          </div>
        </div>
      </div>

      {/* Timeline */}
      <div className="studio-timeline">
        <AnimationTimeline
          animation={animation}
          onAnimationChange={handleAnimationChange}
          onPlay={handlePlay}
          onPause={handlePause}
          onStop={handleStop}
          currentTime={currentTime}
          isPlaying={isPlaying}
        />
      </div>

      {/* Export Dialog */}
      {showExportDialog && (
        <div
          className="export-dialog-overlay"
          onClick={() => setShowExportDialog(false)}
        >
          <div className="export-dialog" onClick={(e) => e.stopPropagation()}>
            <h3>Export Animation</h3>
            <div className="export-options">
              <button
                onClick={() => handleExport("json")}
                className="export-option"
              >
                <div className="export-icon">📄</div>
                <div className="export-label">JSON</div>
                <div className="export-description">
                  Animation specification
                </div>
              </button>
              <button
                onClick={() => handleExport("video")}
                className="export-option"
              >
                <div className="export-icon">🎬</div>
                <div className="export-label">Video (MP4)</div>
                <div className="export-description">Rendered video file</div>
              </button>
              <button
                onClick={() => handleExport("gif")}
                className="export-option"
              >
                <div className="export-icon">🎞️</div>
                <div className="export-label">GIF</div>
                <div className="export-description">Animated GIF</div>
              </button>
            </div>
            <button
              onClick={() => setShowExportDialog(false)}
              className="btn-cancel"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
