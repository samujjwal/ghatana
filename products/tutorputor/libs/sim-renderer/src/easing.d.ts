/**
 * Easing Functions
 *
 * @doc.type module
 * @doc.purpose Provide easing functions for animations
 * @doc.layer product
 * @doc.pattern Utility
 */
import type { EasingFunction } from "@ghatana/tutorputor-contracts/v1/simulation";
/**
 * Apply an easing function to a progress value (0-1).
 */
export declare function applyEasing(progress: number, easing: EasingFunction): number;
/**
 * Linear interpolation between two values.
 */
export declare function lerp(from: number, to: number, t: number): number;
/**
 * Linear interpolation for colors (in hex format).
 */
export declare function lerpColor(from: string, to: string, t: number): string;
/**
 * Clamp a value between min and max.
 */
export declare function clamp(value: number, min: number, max: number): number;
//# sourceMappingURL=easing.d.ts.map