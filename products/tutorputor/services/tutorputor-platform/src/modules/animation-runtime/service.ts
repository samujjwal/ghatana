/**
 * @doc.type module
 * @doc.purpose Animation Runtime with keyframe execution engine
 * @doc.layer product
 * @doc.pattern Service
 */

// Import easing functions from the sim-renderer library
import {
  applyEasing,
  lerp,
  lerpColor,
  clamp,
} from "@ghatana/tutorputor-sim-renderer";

type FrameRequestCallback = (timestamp: number) => void;

const raf: (callback: FrameRequestCallback) => number =
  typeof (globalThis as { requestAnimationFrame?: unknown }).requestAnimationFrame === "function"
    ? (
        globalThis as {
          requestAnimationFrame: (callback: FrameRequestCallback) => number;
        }
      ).requestAnimationFrame.bind(globalThis)
    : (callback: FrameRequestCallback): number =>
        setTimeout(() => callback(Date.now()), 16) as unknown as number;

const caf: (id: number) => void =
  typeof (globalThis as { cancelAnimationFrame?: unknown }).cancelAnimationFrame === "function"
    ? (
        globalThis as {
          cancelAnimationFrame: (id: number) => void;
        }
      ).cancelAnimationFrame.bind(globalThis)
    : (id: number): void => {
        clearTimeout(id as unknown as NodeJS.Timeout);
      };

// ============================================================================
// Types
// ============================================================================

export interface AnimationKeyframe {
  timeMs: number;
  description: string;
  properties: Record<string, string | number>;
  easing?: string; // Easing function to use for this keyframe
}

export interface AnimationSpec {
  animationId: string;
  title: string;
  description: string;
  type: "2d" | "3d" | "timeline";
  durationSeconds: number;
  keyframes: AnimationKeyframe[];
  config: Record<string, any>;
  loop?: boolean;
  autoplay?: boolean;
}

export interface AnimationState {
  isPlaying: boolean;
  isPaused: boolean;
  isCompleted: boolean;
  currentTime: number;
  duration: number;
  currentKeyframeIndex: number;
  progress: number; // 0-1
  loop: boolean;
}

export interface AnimationRenderer {
  setProperty(property: string, value: any): void;
  getProperty(property: string): any;
  applyTransform(transform: Record<string, any>): void;
  render(): void;
}

export interface AnimationEvent {
  type: "start" | "pause" | "resume" | "complete" | "keyframe" | "loop";
  timestamp: number;
  keyframeIndex?: number;
  keyframe?: AnimationKeyframe;
  data?: any;
}

export type AnimationEventListener = (event: AnimationEvent) => void;

// ============================================================================
// Animation Runtime Engine
// ============================================================================

export class AnimationRuntime {
  private animation: AnimationSpec | null = null;
  private renderer: AnimationRenderer | null = null;
  private state: AnimationState = {
    isPlaying: false,
    isPaused: false,
    isCompleted: false,
    currentTime: 0,
    duration: 0,
    currentKeyframeIndex: 0,
    progress: 0,
    loop: false,
  };

  private startTime: number = 0;
  private pauseTime: number = 0;
  private animationFrameId: number | null = null;
  private listeners: Map<string, AnimationEventListener[]> = new Map();

  constructor() {
    this.setupEventListeners();
  }

  // ===========================================================================
  // Public API
  // ===========================================================================

  /**
   * Load an animation specification
   */
  loadAnimation(animation: AnimationSpec): void {
    this.stop();
    this.animation = animation;
    this.state.duration = animation.durationSeconds * 1000;
    this.state.loop = animation.loop || false;
    this.state.currentTime = 0;
    this.state.progress = 0;
    this.state.currentKeyframeIndex = 0;
    this.state.isCompleted = false;
  }

  /**
   * Set the renderer for the animation
   */
  setRenderer(renderer: AnimationRenderer): void {
    this.renderer = renderer;
  }

  /**
   * Play the animation
   */
  play(): void {
    if (!this.animation || !this.renderer) {
      throw new Error("Animation and renderer must be set before playing");
    }

    if (this.state.isPlaying) return;

    if (this.state.isPaused) {
      // Resume from pause
      this.resume();
      return;
    }

    this.state.isPlaying = true;
    this.state.isPaused = false;
    this.startTime = performance.now() - this.state.currentTime;

    this.emitEvent({ type: "start", timestamp: this.startTime });
    this.animate();
  }

  /**
   * Pause the animation
   */
  pause(): void {
    if (!this.state.isPlaying || this.state.isPaused) return;

    this.state.isPaused = true;
    this.state.isPlaying = false;
    this.pauseTime = performance.now();

    if (this.animationFrameId) {
      caf(this.animationFrameId);
      this.animationFrameId = null;
    }

    this.emitEvent({ type: "pause", timestamp: this.pauseTime });
  }

  /**
   * Resume the animation
   */
  resume(): void {
    if (!this.state.isPaused) return;

    this.state.isPaused = false;
    this.state.isPlaying = true;

    // Adjust start time to account for pause duration
    const pauseDuration = performance.now() - this.pauseTime;
    this.startTime += pauseDuration;

    this.emitEvent({ type: "resume", timestamp: performance.now() });
    this.animate();
  }

  /**
   * Stop the animation and reset to beginning
   */
  stop(): void {
    if (this.animationFrameId) {
      caf(this.animationFrameId);
      this.animationFrameId = null;
    }

    this.state.isPlaying = false;
    this.state.isPaused = false;
    this.state.currentTime = 0;
    this.state.progress = 0;
    this.state.currentKeyframeIndex = 0;
    this.state.isCompleted = false;
    this.startTime = 0;
    this.pauseTime = 0;

    if (this.renderer && this.animation) {
      this.applyKeyframe(this.animation.keyframes[0], 0);
    }
  }

  /**
   * Seek to a specific time
   */
  seek(timeMs: number): void {
    timeMs = clamp(timeMs, 0, this.state.duration);
    this.state.currentTime = timeMs;
    this.state.progress = timeMs / this.state.duration;

    this.updateKeyframes();

    if (this.renderer && this.animation) {
      const interpolatedProps = this.interpolateProperties(timeMs);
      this.renderer.applyTransform(interpolatedProps);
      this.renderer.render();
    }
  }

  /**
   * Get current animation state
   */
  getState(): AnimationState {
    return { ...this.state };
  }

  /**
   * Get current animation spec
   */
  getAnimation(): AnimationSpec | null {
    return this.animation;
  }

  // ===========================================================================
  // Event System
  // ===========================================================================

  addEventListener(eventType: string, listener: AnimationEventListener): void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, []);
    }
    this.listeners.get(eventType)!.push(listener);
  }

  removeEventListener(
    eventType: string,
    listener: AnimationEventListener,
  ): void {
    const listeners = this.listeners.get(eventType);
    if (listeners) {
      const index = listeners.indexOf(listener);
      if (index > -1) {
        listeners.splice(index, 1);
      }
    }
  }

  private emitEvent(event: AnimationEvent): void {
    const listeners = this.listeners.get(event.type);
    if (listeners) {
      listeners.forEach((listener) => listener(event));
    }
  }

  private setupEventListeners(): void {
    // Auto-advance keyframes
    this.addEventListener("keyframe", (event) => {
      if (event.keyframeIndex !== undefined) {
        this.state.currentKeyframeIndex = event.keyframeIndex;
      }
    });

    // Handle completion
    this.addEventListener("complete", () => {
      if (this.state.loop && this.animation) {
        this.emitEvent({ type: "loop", timestamp: performance.now() });
        this.seek(0);
        this.play();
      } else {
        this.state.isCompleted = true;
        this.state.isPlaying = false;
      }
    });
  }

  // ===========================================================================
  // Animation Loop
  // ===========================================================================

  private animate(): void {
    if (!this.state.isPlaying) return;

    const currentTime = performance.now();
    this.state.currentTime = currentTime - this.startTime;
    this.state.progress = this.state.currentTime / this.state.duration;

    // Check for completion
    if (this.state.currentTime >= this.state.duration) {
      this.state.currentTime = this.state.duration;
      this.state.progress = 1;
      this.emitEvent({ type: "complete", timestamp: currentTime });
      return;
    }

    this.updateKeyframes();

    if (this.renderer && this.animation) {
      const interpolatedProps = this.interpolateProperties(
        this.state.currentTime,
      );
      this.renderer.applyTransform(interpolatedProps);
      this.renderer.render();
    }

    this.animationFrameId = raf(() => this.animate());
  }

  private updateKeyframes(): void {
    if (!this.animation) return;

    const keyframes = this.animation.keyframes;
    const currentTime = this.state.currentTime;

    // Find current keyframe index
    let newKeyframeIndex = 0;
    for (let i = 0; i < keyframes.length; i++) {
      if (keyframes[i].timeMs <= currentTime) {
        newKeyframeIndex = i;
      } else {
        break;
      }
    }

    // Emit keyframe event if changed
    if (newKeyframeIndex !== this.state.currentKeyframeIndex) {
      this.emitEvent({
        type: "keyframe",
        timestamp: performance.now(),
        keyframeIndex: newKeyframeIndex,
        keyframe: keyframes[newKeyframeIndex],
      });
    }
  }

  private interpolateProperties(timeMs: number): Record<string, any> {
    if (!this.animation) return {};

    const keyframes = this.animation.keyframes;

    // Find surrounding keyframes
    let prevKeyframe = keyframes[0];
    let nextKeyframe = keyframes[keyframes.length - 1];

    for (let i = 0; i < keyframes.length - 1; i++) {
      if (keyframes[i].timeMs <= timeMs && keyframes[i + 1].timeMs > timeMs) {
        prevKeyframe = keyframes[i];
        nextKeyframe = keyframes[i + 1];
        break;
      }
    }

    // Calculate interpolation progress
    const segmentDuration = nextKeyframe.timeMs - prevKeyframe.timeMs;
    const segmentProgress =
      segmentDuration > 0
        ? (timeMs - prevKeyframe.timeMs) / segmentDuration
        : 1;

    // Apply easing
    const easing = nextKeyframe.easing || "linear";
    const easedProgress = applyEasing(segmentProgress, easing as any);

    // Interpolate properties
    const interpolated: Record<string, any> = {};

    for (const [property, endValue] of Object.entries(
      nextKeyframe.properties,
    )) {
      const startValue = prevKeyframe.properties[property];

      if (startValue !== undefined) {
        interpolated[property] = this.interpolateValue(
          startValue,
          endValue,
          easedProgress,
        );
      } else {
        interpolated[property] = endValue;
      }
    }

    return interpolated;
  }

  private interpolateValue(start: any, end: any, progress: number): any {
    // Handle different value types
    if (typeof start === "number" && typeof end === "number") {
      return lerp(start, end, progress);
    }

    if (typeof start === "string" && typeof end === "string") {
      // Check if it's a color
      if (start.startsWith("#") && end.startsWith("#")) {
        return lerpColor(start, end, progress);
      }

      // For other strings, just return the end value when progress > 0.5
      return progress > 0.5 ? end : start;
    }

    // For objects, recursively interpolate
    if (
      typeof start === "object" &&
      typeof end === "object" &&
      start !== null &&
      end !== null
    ) {
      const result: any = {};
      for (const key of Object.keys(end)) {
        result[key] = this.interpolateValue(start[key], end[key], progress);
      }
      return result;
    }

    return end;
  }

  private applyKeyframe(keyframe: AnimationKeyframe, progress: number): void {
    if (!this.renderer) return;

    const easedProgress = applyEasing(progress, "linear");
    const interpolated = this.interpolateValue(
      keyframe.properties,
      keyframe.properties,
      easedProgress,
    );
    this.renderer.applyTransform(interpolated);
    this.renderer.render();
  }
}

// ============================================================================
// Animation Manager
// ============================================================================

export class AnimationManager {
  private animations: Map<string, AnimationRuntime> = new Map();
  private renderers: Map<string, AnimationRenderer> = new Map();

  /**
   * Create a new animation runtime
   */
  createAnimation(id: string): AnimationRuntime {
    const animation = new AnimationRuntime();
    this.animations.set(id, animation);
    return animation;
  }

  /**
   * Get an animation runtime by ID
   */
  getAnimation(id: string): AnimationRuntime | undefined {
    return this.animations.get(id);
  }

  /**
   * Register a renderer
   */
  registerRenderer(id: string, renderer: AnimationRenderer): void {
    this.renderers.set(id, renderer);
  }

  /**
   * Get a renderer by ID
   */
  getRenderer(id: string): AnimationRenderer | undefined {
    return this.renderers.get(id);
  }

  /**
   * Play all animations
   */
  playAll(): void {
    this.animations.forEach((animation) => {
      if (animation.getAnimation()?.autoplay) {
        animation.play();
      }
    });
  }

  /**
   * Pause all animations
   */
  pauseAll(): void {
    this.animations.forEach((animation) => animation.pause());
  }

  /**
   * Stop all animations
   */
  stopAll(): void {
    this.animations.forEach((animation) => animation.stop());
  }

  /**
   * Clean up resources
   */
  dispose(): void {
    this.stopAll();
    this.animations.clear();
    this.renderers.clear();
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Create an animation spec from simple parameters
 */
export function createAnimationSpec(config: {
  id: string;
  title: string;
  description: string;
  duration: number;
  keyframes: Array<{
    time: number; // in seconds
    properties: Record<string, any>;
    easing?: string;
  }>;
  type?: "2d" | "3d" | "timeline";
  loop?: boolean;
  autoplay?: boolean;
}): AnimationSpec {
  return {
    animationId: config.id,
    title: config.title,
    description: config.description,
    type: config.type || "2d",
    durationSeconds: config.duration,
    keyframes: config.keyframes.map((kf) => ({
      timeMs: kf.time * 1000,
      description: `Keyframe at ${kf.time}s`,
      properties: kf.properties,
      easing: kf.easing,
    })),
    config: {},
    loop: config.loop,
    autoplay: config.autoplay,
  };
}

/**
 * Validate an animation spec
 */
export function validateAnimationSpec(animation: AnimationSpec): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  if (!animation.animationId) {
    errors.push("Animation ID is required");
  }

  if (animation.durationSeconds <= 0) {
    errors.push("Duration must be greater than 0");
  }

  if (!animation.keyframes || animation.keyframes.length === 0) {
    errors.push("At least one keyframe is required");
  } else {
    // Check keyframe ordering
    for (let i = 1; i < animation.keyframes.length; i++) {
      if (animation.keyframes[i].timeMs <= animation.keyframes[i - 1].timeMs) {
        errors.push(
          `Keyframe ${i} time must be greater than keyframe ${i - 1}`,
        );
      }
    }

    // Check first keyframe starts at 0
    if (animation.keyframes[0].timeMs !== 0) {
      errors.push("First keyframe must start at 0ms");
    }

    // Check last keyframe ends at duration
    const lastKeyframe = animation.keyframes[animation.keyframes.length - 1];
    if (lastKeyframe.timeMs > animation.durationSeconds * 1000) {
      errors.push("Last keyframe time cannot exceed animation duration");
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
