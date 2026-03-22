/**
 * @tutorputor/animator - Core Animation Engine
 * 
 * A comprehensive animation library for creating, sequencing, and rendering
 * educational animations. Integrates with React Router v7 for navigation-aware
 * animations and provides hooks for animation authoring and automatic generation.
 * 
 * @example
 * ```tsx
 * import { Timeline, AnimationTrack, useAnimator } from '@tutorputor/animator';
 * 
 * function MyAnimation() {
 *   const animator = useAnimator({
 *     duration: 5000,
 *     easing: 'ease-in-out',
 *   });
 * 
 *   return (
 *     <Timeline animator={animator}>
 *       <AnimationTrack
 *         target="circle"
 *         property="x"
 *         from={0}
 *         to={100}
 *         duration={1000}
 *       />
 *     </Timeline>
 *   );
 * }
 * ```
 */

// =============================================================================
// Core Types
// =============================================================================

export interface AnimationKeyframe {
  /** Time offset in milliseconds */
  time: number;
  /** Property value at this keyframe */
  value: number | string | object;
  /** Easing function name or cubic-bezier */
  easing?: EasingFunction | string;
}

export interface AnimationTrack {
  /** Unique track identifier */
  id: string;
  /** Target element ID or selector */
  target: string;
  /** CSS property or custom property being animated */
  property: string;
  /** Starting value (optional, uses computed style if omitted) */
  from?: number | string | object;
  /** Ending value */
  to: number | string | object;
  /** Duration in milliseconds */
  duration: number;
  /** Delay before animation starts */
  delay?: number;
  /** Easing function */
  easing?: EasingFunction | string;
  /** Keyframes for complex animations */
  keyframes?: AnimationKeyframe[];
  /** Whether to reverse on complete */
  yoyo?: boolean;
  /** Number of times to repeat (-1 for infinite) */
  repeat?: number;
  /** Callback when track completes */
  onComplete?: () => void;
  /** Callback on each frame */
  onUpdate?: (progress: number) => void;
}

export interface Timeline {
  /** Timeline ID */
  id: string;
  /** Total duration in milliseconds */
  duration: number;
  /** Animation tracks */
  tracks: AnimationTrack[];
  /** Global timeline callbacks */
  onPlay?: () => void;
  onPause?: () => void;
  onComplete?: () => void;
  onSeek?: (time: number) => void;
}

export type EasingFunction = 
  | 'linear'
  | 'ease'
  | 'ease-in'
  | 'ease-out'
  | 'ease-in-out'
  | 'cubic-bezier'
  | 'spring'
  | 'bounce'
  | 'elastic'
  | 'back-in'
  | 'back-out'
  | 'circ-in'
  | 'circ-out'
  | 'expo-in'
  | 'expo-out'
  | 'quad-in'
  | 'quad-out'
  | 'quart-in'
  | 'quart-out'
  | 'sine-in'
  | 'sine-out';

export interface AnimatorState {
  /** Current playback time in milliseconds */
  currentTime: number;
  /** Total timeline duration */
  totalDuration: number;
  /** Whether animation is playing */
  isPlaying: boolean;
  /** Whether animation is paused */
  isPaused: boolean;
  /** Whether animation has completed */
  isComplete: boolean;
  /** Current playback speed (1.0 = normal) */
  playbackSpeed: number;
  /** Current loop iteration */
  currentIteration: number;
  /** Total iterations to perform */
  iterations: number;
}

export interface AnimatorConfig {
  /** Timeline duration in milliseconds */
  duration: number;
  /** Global easing function */
  easing?: EasingFunction | string;
  /** Whether to loop */
  loop?: boolean;
  /** Number of iterations (-1 for infinite) */
  iterations?: number;
  /** Whether to reverse direction on loop */
  yoyo?: boolean;
  /** Delay before starting */
  delay?: number;
  /** FPS cap (0 for unlimited) */
  fps?: number;
  /** Whether to use RAF (true) or setTimeout (false) */
  useRAF?: boolean;
  /** Auto-play on mount */
  autoplay?: boolean;
}

// =============================================================================
// Easing Functions
// =============================================================================

const EASING: Record<EasingFunction, (t: number) => number> = {
  'linear': (t) => t,
  'ease': (t) => cubicBezier(0.25, 0.1, 0.25, 1)(t),
  'ease-in': (t) => cubicBezier(0.42, 0, 1, 1)(t),
  'ease-out': (t) => cubicBezier(0, 0, 0.58, 1)(t),
  'ease-in-out': (t) => cubicBezier(0.42, 0, 0.58, 1)(t),
  'cubic-bezier': (t) => t, // Placeholder, requires parameters
  'spring': (t) => springEasing(1, 100, 10)(t),
  'bounce': (t) => bounceOut(t),
  'elastic': (t) => elasticOut(t),
  'back-in': (t) => backIn(1.7)(t),
  'back-out': (t) => backOut(1.7)(t),
  'circ-in': (t) => 1 - Math.sqrt(1 - Math.pow(t, 2)),
  'circ-out': (t) => Math.sqrt(1 - Math.pow(t - 1, 2)),
  'expo-in': (t) => t === 0 ? 0 : Math.pow(2, 10 * (t - 1)),
  'expo-out': (t) => t === 1 ? 1 : 1 - Math.pow(2, -10 * t),
  'quad-in': (t) => t * t,
  'quad-out': (t) => 1 - (1 - t) * (1 - t),
  'quart-in': (t) => t * t * t * t,
  'quart-out': (t) => 1 - Math.pow(1 - t, 4),
  'sine-in': (t) => 1 - Math.cos((t * Math.PI) / 2),
  'sine-out': (t) => Math.sin((t * Math.PI) / 2),
};

export function getEasingFunction(name: EasingFunction | string): (t: number) => number {
  if (typeof name === 'string' && name.startsWith('cubic-bezier')) {
    const match = name.match(/cubic-bezier\(([^)]+)\)/);
    if (match) {
      const [x1, y1, x2, y2] = match[1]!.split(',').map(Number) as [number, number, number, number];
      return cubicBezier(x1, y1, x2, y2);
    }
  }
  return EASING[name as EasingFunction] || EASING['ease'];
}

function cubicBezier(x1: number, y1: number, x2: number, y2: number): (t: number) => number {
  return (t: number): number => {
    const cx = 3 * x1;
    const bx = 3 * (x2 - x1) - cx;
    const ax = 1 - cx - bx;
    const cy = 3 * y1;
    const by = 3 * (y2 - y1) - cy;
    const ay = 1 - cy - by;

    const sampleCurveX = (t: number) => ((ax * t + bx) * t + cx) * t;
    const sampleCurveY = (t: number) => ((ay * t + by) * t + cy) * t;
    const solveCurveX = (x: number) => {
      let t = x;
      for (let i = 0; i < 8; i++) {
        const x2 = sampleCurveX(t) - x;
        if (Math.abs(x2) < 1e-6) return t;
        const d2 = (3 * ax * t + 2 * bx) * t + cx;
        if (Math.abs(d2) < 1e-6) break;
        t -= x2 / d2;
      }
      return t;
    };

    return sampleCurveY(solveCurveX(t));
  };
}

function springEasing(mass: number, stiffness: number, damping: number): (t: number) => number {
  return (t: number): number => {
    const w0 = Math.sqrt(stiffness / mass);
    const zeta = damping / (2 * Math.sqrt(stiffness * mass));
    const wd = zeta < 1 ? w0 * Math.sqrt(1 - zeta * zeta) : 0;
    const b = zeta < 1 ? (zeta * w0) / wd : w0;

    if (zeta < 1) {
      return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + b * Math.sin(wd * t));
    }
    return 1 - (1 + w0 * t) * Math.exp(-w0 * t);
  };
}

function bounceOut(t: number): number {
  const n1 = 7.5625;
  const d1 = 2.75;
  if (t < 1 / d1) {
    return n1 * t * t;
  } else if (t < 2 / d1) {
    return n1 * (t -= 1.5 / d1) * t + 0.75;
  } else if (t < 2.5 / d1) {
    return n1 * (t -= 2.25 / d1) * t + 0.9375;
  }
  return n1 * (t -= 2.625 / d1) * t + 0.984375;
}

function elasticOut(t: number): number {
  if (t === 0) return 0;
  if (t === 1) return 1;
  const c4 = (2 * Math.PI) / 3;
  return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
}

function backIn(overshoot: number): (t: number) => number {
  return (t: number) => t * t * ((overshoot + 1) * t - overshoot);
}

function backOut(overshoot: number): (t: number) => number {
  return (t: number) => 1 - backIn(overshoot)(1 - t);
}

// =============================================================================
// Core Animator Class
// =============================================================================

export class Animator {
  private config: AnimatorConfig;
  private state: AnimatorState;
  private tracks: AnimationTrack[] = [];
  private rafId: number | null = null;
  private startTime: number = 0;
  private lastTime: number = 0;
  private callbacks: {
    onPlay?: () => void;
    onPause?: () => void;
    onComplete?: () => void;
    onUpdate?: (state: AnimatorState) => void;
  } = {};

  constructor(config: AnimatorConfig) {
    this.config = {
      easing: 'ease-in-out',
      loop: false,
      iterations: 1,
      yoyo: false,
      delay: 0,
      fps: 60,
      useRAF: true,
      autoplay: false,
      ...config,
    };

    this.state = {
      currentTime: 0,
      totalDuration: config.duration,
      isPlaying: false,
      isPaused: false,
      isComplete: false,
      playbackSpeed: 1,
      currentIteration: 0,
      iterations: config.iterations || 1,
    };

    if (this.config.autoplay) {
      setTimeout(() => this.play(), this.config.delay);
    }
  }

  addTrack(track: AnimationTrack): void {
    this.tracks.push(track);
    this.recalculateDuration();
  }

  removeTrack(trackId: string): void {
    this.tracks = this.tracks.filter(t => t.id !== trackId);
    this.recalculateDuration();
  }

  play(): void {
    if (this.state.isPlaying) return;
    
    this.state.isPlaying = true;
    this.state.isPaused = false;
    this.startTime = performance.now() - this.state.currentTime;
    this.lastTime = performance.now();
    
    this.callbacks.onPlay?.();
    this.tick();
  }

  pause(): void {
    if (!this.state.isPlaying) return;
    
    this.state.isPlaying = false;
    this.state.isPaused = true;
    
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    
    this.callbacks.onPause?.();
  }

  stop(): void {
    this.state.isPlaying = false;
    this.state.isPaused = false;
    this.state.currentTime = 0;
    this.state.currentIteration = 0;
    
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  seek(time: number): void {
    this.state.currentTime = Math.max(0, Math.min(time, this.state.totalDuration));
    this.updateTracks(this.state.currentTime);
  }

  setSpeed(speed: number): void {
    this.state.playbackSpeed = Math.max(0.1, Math.min(speed, 10));
  }

  onPlay(callback: () => void): void {
    this.callbacks.onPlay = callback;
  }

  onPause(callback: () => void): void {
    this.callbacks.onPause = callback;
  }

  onComplete(callback: () => void): void {
    this.callbacks.onComplete = callback;
  }

  onUpdate(callback: (state: AnimatorState) => void): void {
    this.callbacks.onUpdate = callback;
  }

  getState(): AnimatorState {
    return { ...this.state };
  }

  private recalculateDuration(): void {
    const maxDuration = this.tracks.reduce((max, track) => {
      return Math.max(max, (track.delay || 0) + track.duration);
    }, 0);
    this.state.totalDuration = Math.max(this.config.duration, maxDuration);
  }

  private tick(): void {
    if (!this.state.isPlaying) return;

    const now = performance.now();
    const delta = (now - this.lastTime) * this.state.playbackSpeed;
    this.lastTime = now;

    this.state.currentTime += delta;

    if (this.state.currentTime >= this.state.totalDuration) {
      this.handleComplete();
    } else {
      this.updateTracks(this.state.currentTime);
      this.callbacks.onUpdate?.({ ...this.state });
    }

    if (this.state.isPlaying) {
      this.rafId = requestAnimationFrame(() => this.tick());
    }
  }

  private handleComplete(): void {
    this.state.currentIteration++;
    
    if (this.config.yoyo) {
      this.state.playbackSpeed = -this.state.playbackSpeed;
      this.state.currentTime = this.state.playbackSpeed > 0 ? 0 : this.state.totalDuration;
    }

    if (this.state.currentIteration >= this.state.iterations && this.state.iterations !== -1) {
      this.state.isPlaying = false;
      this.state.isComplete = true;
      this.state.currentTime = this.state.totalDuration;
      this.callbacks.onComplete?.();
    } else {
      this.state.currentTime = 0;
      this.startTime = performance.now();
    }
  }

  private updateTracks(currentTime: number): void {
    for (const track of this.tracks) {
      const trackTime = currentTime - (track.delay || 0);
      if (trackTime < 0 || trackTime > track.duration) continue;

      const progress = trackTime / track.duration;
      const easingFn = getEasingFunction(track.easing || this.config.easing || 'ease-in-out');
      const easedProgress = easingFn(progress);

      const value = this.interpolateValue(track.from, track.to, easedProgress);
      track.onUpdate?.(easedProgress);

      // Apply to DOM or callback
      this.applyValue(track.target, track.property, value);
    }
  }

  private interpolateValue(from: any, to: any, progress: number): any {
    if (typeof from === 'number' && typeof to === 'number') {
      return from + (to - from) * progress;
    }
    
    if (typeof from === 'string' && typeof to === 'string') {
      // Handle color interpolation
      if (from.startsWith('#') && to.startsWith('#')) {
        return this.interpolateColor(from, to, progress);
      }
      // Handle CSS values with units (e.g., "100px")
      const fromMatch = from.match(/^([\d.]+)(.*)$/);
      const toMatch = to.match(/^([\d.]+)(.*)$/);
      if (fromMatch && toMatch && fromMatch[2] === toMatch[2]) {
        const val = parseFloat(fromMatch[1]!) + (parseFloat(toMatch[1]!) - parseFloat(fromMatch[1]!)) * progress;
        return `${val}${fromMatch[2]}`;
      }
    }

    return progress < 0.5 ? from : to;
  }

  private interpolateColor(from: string, to: string, progress: number): string {
    const fromRGB = this.hexToRGB(from);
    const toRGB = this.hexToRGB(to);
    
    const r = Math.round(fromRGB.r + (toRGB.r - fromRGB.r) * progress);
    const g = Math.round(fromRGB.g + (toRGB.g - fromRGB.g) * progress);
    const b = Math.round(fromRGB.b + (toRGB.b - fromRGB.b) * progress);
    
    return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
  }

  private hexToRGB(hex: string): { r: number; g: number; b: number } {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
      r: parseInt(result[1]!, 16),
      g: parseInt(result[2]!, 16),
      b: parseInt(result[3]!, 16),
    } : { r: 0, g: 0, b: 0 };
  }

  private applyValue(target: string, property: string, value: any): void {
    // This is a simplified implementation - in practice, you'd integrate with
    // React state, CSS-in-JS, or direct DOM manipulation
    if (typeof window !== 'undefined') {
      const elements = document.querySelectorAll(target);
      elements.forEach(el => {
        if (property.startsWith('--')) {
          (el as HTMLElement).style.setProperty(property, String(value));
        } else if (property in (el as HTMLElement).style) {
          (el as HTMLElement).style[property as any] = value;
        } else {
          (el as HTMLElement).setAttribute(property, String(value));
        }
      });
    }
  }
}

// =============================================================================
// React Hooks
// =============================================================================

import { useState, useCallback, useRef, useEffect } from 'react';

export function useAnimator(config: AnimatorConfig) {
  const animatorRef = useRef<Animator | null>(null);
  const [state, setState] = useState<AnimatorState>({
    currentTime: 0,
    totalDuration: config.duration,
    isPlaying: false,
    isPaused: false,
    isComplete: false,
    playbackSpeed: 1,
    currentIteration: 0,
    iterations: config.iterations || 1,
  });

  if (!animatorRef.current) {
    animatorRef.current = new Animator(config);
    animatorRef.current.onUpdate((newState) => {
      setState({ ...newState });
    });
  }

  const play = useCallback(() => animatorRef.current?.play(), []);
  const pause = useCallback(() => animatorRef.current?.pause(), []);
  const stop = useCallback(() => animatorRef.current?.stop(), []);
  const seek = useCallback((time: number) => animatorRef.current?.seek(time), []);
  const setSpeed = useCallback((speed: number) => animatorRef.current?.setSpeed(speed), []);
  const addTrack = useCallback((track: AnimationTrack) => animatorRef.current?.addTrack(track), []);
  const removeTrack = useCallback((trackId: string) => animatorRef.current?.removeTrack(trackId), []);

  useEffect(() => {
    return () => {
      animatorRef.current?.stop();
    };
  }, []);

  return {
    state,
    play,
    pause,
    stop,
    seek,
    setSpeed,
    addTrack,
    removeTrack,
    animator: animatorRef.current,
  };
}

export function useTimeline(tracks: AnimationTrack[], config: Partial<AnimatorConfig> = {}) {
  const duration = Math.max(
    5000,
    ...tracks.map(t => (t.delay || 0) + t.duration)
  );
  
  const animator = useAnimator({
    duration,
    ...config,
  });

  useEffect(() => {
    tracks.forEach(track => animator.addTrack(track));
    return () => {
      tracks.forEach(track => animator.removeTrack(track.id));
    };
  }, [tracks]);

  return animator;
}

// =============================================================================
// Value Interpolation Utilities
// =============================================================================

export function lerp(start: number, end: number, t: number): number {
  return start + (end - start) * t;
}

export function lerpColor(from: string, to: string, t: number): string {
  const fromRGB = hexToRGB(from);
  const toRGB = hexToRGB(to);
  
  const r = Math.round(lerp(fromRGB.r, toRGB.r, t));
  const g = Math.round(lerp(fromRGB.g, toRGB.g, t));
  const b = Math.round(lerp(fromRGB.b, toRGB.b, t));
  
  return `rgb(${r}, ${g}, ${b})`;
}

function hexToRGB(hex: string): { r: number; g: number; b: number } {
  const clean = hex.replace('#', '');
  const bigint = parseInt(clean, 16);
  return {
    r: (bigint >> 16) & 255,
    g: (bigint >> 8) & 255,
    b: bigint & 255,
  };
}

// =============================================================================
// Preset Animations
// =============================================================================

export const AnimationPresets = {
  fadeIn: (target: string, duration: number = 500): AnimationTrack => ({
    id: `fade-in-${target}-${Date.now()}`,
    target,
    property: 'opacity',
    from: 0,
    to: 1,
    duration,
    easing: 'ease-out',
  }),

  fadeOut: (target: string, duration: number = 500): AnimationTrack => ({
    id: `fade-out-${target}-${Date.now()}`,
    target,
    property: 'opacity',
    from: 1,
    to: 0,
    duration,
    easing: 'ease-in',
  }),

  slideIn: (target: string, direction: 'left' | 'right' | 'up' | 'down' = 'left', distance: number = 100, duration: number = 500): AnimationTrack => {
    const axis = direction === 'left' || direction === 'right' ? 'x' : 'y';
    const from = direction === 'left' || direction === 'up' ? -distance : distance;
    return {
      id: `slide-in-${target}-${Date.now()}`,
      target,
      property: 'transform',
      from: `translate${axis.toUpperCase()}(${from}px)`,
      to: `translate${axis.toUpperCase()}(0px)`,
      duration,
      easing: 'ease-out',
    };
  },

  scale: (target: string, from: number = 0.5, to: number = 1, duration: number = 500): AnimationTrack => ({
    id: `scale-${target}-${Date.now()}`,
    target,
    property: 'transform',
    from: `scale(${from})`,
    to: `scale(${to})`,
    duration,
    easing: 'spring',
  }),

  rotate: (target: string, from: number = 0, to: number = 360, duration: number = 1000): AnimationTrack => ({
    id: `rotate-${target}-${Date.now()}`,
    target,
    property: 'transform',
    from: `rotate(${from}deg)`,
    to: `rotate(${to}deg)`,
    duration,
    easing: 'ease-in-out',
  }),

  bounce: (target: string, duration: number = 1000): AnimationTrack => ({
    id: `bounce-${target}-${Date.now()}`,
    target,
    property: 'transform',
    from: 'translateY(0px)',
    to: 'translateY(-30px)',
    duration,
    easing: 'bounce',
    yoyo: true,
    repeat: 1,
  }),

  pulse: (target: string, duration: number = 1000): AnimationTrack => ({
    id: `pulse-${target}-${Date.now()}`,
    target,
    property: 'opacity',
    from: 1,
    to: 0.5,
    duration: duration / 2,
    easing: 'sine-in-out',
    yoyo: true,
    repeat: -1,
  }),

  shake: (target: string, intensity: number = 10, duration: number = 500): AnimationTrack => ({
    id: `shake-${target}-${Date.now()}`,
    target,
    property: 'transform',
    keyframes: [
      { time: 0, value: 'translateX(0px)' },
      { time: duration * 0.1, value: `translateX(-${intensity}px)` },
      { time: duration * 0.2, value: `translateX(${intensity}px)` },
      { time: duration * 0.3, value: `translateX(-${intensity}px)` },
      { time: duration * 0.4, value: `translateX(${intensity}px)` },
      { time: duration * 0.5, value: `translateX(-${intensity}px)` },
      { time: duration * 0.6, value: `translateX(${intensity}px)` },
      { time: duration * 0.7, value: `translateX(-${intensity}px)` },
      { time: duration * 0.8, value: `translateX(${intensity}px)` },
      { time: duration * 0.9, value: `translateX(-${intensity}px)` },
      { time: duration, value: 'translateX(0px)' },
    ],
    from: 'translateX(0px)',
    to: 'translateX(0px)',
    duration,
  }),

  colorShift: (target: string, from: string, to: string, duration: number = 1000): AnimationTrack => ({
    id: `color-${target}-${Date.now()}`,
    target,
    property: 'backgroundColor',
    from,
    to,
    duration,
    easing: 'linear',
  }),
};

// =============================================================================
// React Router v7 Integration
// =============================================================================

export interface RouteAnimationConfig {
  /** Routes to animate between */
  routes: string[];
  /** Enter animation */
  enter: AnimationTrack | AnimationTrack[];
  /** Exit animation */
  exit: AnimationTrack | AnimationTrack[];
  /** Whether to animate on initial load */
  animateOnMount?: boolean;
  /** Animation direction based on navigation */
  direction?: 'forward' | 'backward' | 'auto';
}

export function useRouteAnimation(config: RouteAnimationConfig) {
  // This would integrate with React Router v7's useLocation and useNavigation
  // to trigger animations on route changes
  const [isAnimating, setIsAnimating] = useState(false);
  const [animationDirection, setAnimationDirection] = useState<'forward' | 'backward'>('forward');

  const animateTransition = useCallback((from: string, to: string) => {
    const fromIndex = config.routes.indexOf(from);
    const toIndex = config.routes.indexOf(to);
    const direction = toIndex > fromIndex ? 'forward' : 'backward';
    
    setAnimationDirection(direction);
    setIsAnimating(true);

    // Trigger exit animation
    const exitTracks = Array.isArray(config.exit) ? config.exit : [config.exit];
    exitTracks.forEach(track => {
      // Apply exit animation
    });

    // Trigger enter animation after exit completes
    setTimeout(() => {
      const enterTracks = Array.isArray(config.enter) ? config.enter : [config.enter];
      enterTracks.forEach(track => {
        // Apply enter animation
      });
      setIsAnimating(false);
    }, 300);
  }, [config]);

  return {
    isAnimating,
    animationDirection,
    animateTransition,
  };
}

// =============================================================================
// Utilities
// =============================================================================

export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export function mapRange(value: number, inMin: number, inMax: number, outMin: number, outMax: number): number {
  return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
}

export function throttle<T extends (...args: any[]) => void>(fn: T, limit: number): (...args: Parameters<T>) => void {
  let inThrottle = false;
  return (...args: Parameters<T>) => {
    if (!inThrottle) {
      fn(...args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  };
}

export function debounce<T extends (...args: any[]) => void>(fn: T, delay: number): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout>;
  return (...args: Parameters<T>) => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn(...args), delay);
  };
}
