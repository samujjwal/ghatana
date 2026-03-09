/**
 * Easing Functions for Smooth Animations
 * 
 * @doc.type module
 * @doc.purpose Provide easing functions for keyframe interpolation
 * @doc.layer product
 * @doc.pattern Utility
 */

import type { EasingFunction } from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Easing function implementation.
 */
export type EasingFn = (t: number) => number;

/**
 * Linear easing (no easing).
 */
export const linear: EasingFn = (t) => t;

/**
 * Ease in (slow start).
 */
export const easeIn: EasingFn = (t) => t * t;

/**
 * Ease out (slow end).
 */
export const easeOut: EasingFn = (t) => t * (2 - t);

/**
 * Ease in-out (slow start and end).
 */
export const easeInOut: EasingFn = (t) =>
  t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

/**
 * Quadratic ease in.
 */
export const easeInQuad: EasingFn = (t) => t * t;

/**
 * Quadratic ease out.
 */
export const easeOutQuad: EasingFn = (t) => t * (2 - t);

/**
 * Quadratic ease in-out.
 */
export const easeInOutQuad: EasingFn = (t) =>
  t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

/**
 * Cubic ease in.
 */
export const easeInCubic: EasingFn = (t) => t * t * t;

/**
 * Cubic ease out.
 */
export const easeOutCubic: EasingFn = (t) => --t * t * t + 1;

/**
 * Cubic ease in-out.
 */
export const easeInOutCubic: EasingFn = (t) =>
  t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;

/**
 * Elastic ease in.
 */
export const easeInElastic: EasingFn = (t) => {
  if (t === 0 || t === 1) return t;
  return -Math.pow(2, 10 * (t - 1)) * Math.sin((t - 1.1) * 5 * Math.PI);
};

/**
 * Elastic ease out.
 */
export const easeOutElastic: EasingFn = (t) => {
  if (t === 0 || t === 1) return t;
  return Math.pow(2, -10 * t) * Math.sin((t - 0.1) * 5 * Math.PI) + 1;
};

/**
 * Bounce ease in.
 */
export const easeInBounce: EasingFn = (t) => 1 - easeOutBounce(1 - t);

/**
 * Bounce ease out.
 */
export const easeOutBounce: EasingFn = (t) => {
  if (t < 1 / 2.75) {
    return 7.5625 * t * t;
  } else if (t < 2 / 2.75) {
    t -= 1.5 / 2.75;
    return 7.5625 * t * t + 0.75;
  } else if (t < 2.5 / 2.75) {
    t -= 2.25 / 2.75;
    return 7.5625 * t * t + 0.9375;
  } else {
    t -= 2.625 / 2.75;
    return 7.5625 * t * t + 0.984375;
  }
};

/**
 * Spring easing (overshoots and settles).
 */
export const spring: EasingFn = (t) => {
  const c4 = (2 * Math.PI) / 3;
  return t === 0
    ? 0
    : t === 1
    ? 1
    : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
};

/**
 * Easing function map.
 */
const easingMap: Record<EasingFunction, EasingFn> = {
  linear,
  easeIn,
  easeOut,
  easeInOut,
  easeInQuad,
  easeOutQuad,
  easeInOutQuad,
  easeInCubic,
  easeOutCubic,
  easeInOutCubic,
  easeInElastic,
  easeOutElastic,
  easeInBounce,
  easeOutBounce,
  spring
};

/**
 * Get easing function by name.
 */
export function getEasingFunction(name: EasingFunction): EasingFn {
  return easingMap[name] || linear;
}

/**
 * Interpolate between two values using easing.
 */
export function interpolate(
  from: number,
  to: number,
  progress: number,
  easing: EasingFunction = "linear"
): number {
  const easingFn = getEasingFunction(easing);
  const t = Math.max(0, Math.min(1, progress));
  return from + (to - from) * easingFn(t);
}

/**
 * Interpolate between two points.
 */
export function interpolatePoint(
  from: { x: number; y: number },
  to: { x: number; y: number },
  progress: number,
  easing: EasingFunction = "linear"
): { x: number; y: number } {
  return {
    x: interpolate(from.x, to.x, progress, easing),
    y: interpolate(from.y, to.y, progress, easing)
  };
}

/**
 * Interpolate colors (hex format).
 */
export function interpolateColor(
  from: string,
  to: string,
  progress: number,
  easing: EasingFunction = "linear"
): string {
  const fromRgb = hexToRgb(from);
  const toRgb = hexToRgb(to);
  
  if (!fromRgb || !toRgb) return from;
  
  const r = Math.round(interpolate(fromRgb.r, toRgb.r, progress, easing));
  const g = Math.round(interpolate(fromRgb.g, toRgb.g, progress, easing));
  const b = Math.round(interpolate(fromRgb.b, toRgb.b, progress, easing));
  
  return rgbToHex(r, g, b);
}

/**
 * Convert hex to RGB.
 */
function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result
    ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
      }
    : null;
}

/**
 * Convert RGB to hex.
 */
function rgbToHex(r: number, g: number, b: number): string {
  return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
}
