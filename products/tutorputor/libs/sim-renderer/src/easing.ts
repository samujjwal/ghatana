/**
 * Easing Functions
 *
 * @doc.type module
 * @doc.purpose Provide easing functions for animations
 * @doc.layer product
 * @doc.pattern Utility
 */

import type { EasingFunction } from "@ghatana/tutorputor-contracts/v1/simulation";

// Re-export EasingFunction type for consumer convenience
export type { EasingFunction };

/**
 * Linear easing (no easing, constant motion).
 */
export function linear(t: number): number {
    return t;
}

/**
 * Apply an easing function to a progress value (0-1).
 */
export function applyEasing(progress: number, easing: EasingFunction): number {
    switch (easing) {
        case "linear":
            return progress;

        case "easeIn":
            return easeInCubic(progress);

        case "easeOut":
            return easeOutCubic(progress);

        case "easeInOut":
            return easeInOutCubic(progress);

        case "easeInQuad":
            return easeInQuad(progress);

        case "easeOutQuad":
            return easeOutQuad(progress);

        case "easeInOutQuad":
            return easeInOutQuad(progress);

        case "easeInCubic":
            return easeInCubic(progress);

        case "easeOutCubic":
            return easeOutCubic(progress);

        case "easeInOutCubic":
            return easeInOutCubic(progress);

        case "easeInElastic":
            return easeInElastic(progress);

        case "easeOutElastic":
            return easeOutElastic(progress);

        case "easeInBounce":
            return easeInBounce(progress);

        case "easeOutBounce":
            return easeOutBounce(progress);

        case "spring":
            return spring(progress);

        default:
            return progress;
    }
}

// =============================================================================
// Quadratic Easing
// =============================================================================

export function easeInQuad(t: number): number {
    return t * t;
}

export function easeOutQuad(t: number): number {
    return t * (2 - t);
}

export function easeInOutQuad(t: number): number {
    return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
}

// =============================================================================
// Cubic Easing
// =============================================================================

export function easeInCubic(t: number): number {
    return t * t * t;
}

export function easeOutCubic(t: number): number {
    const t1 = t - 1;
    return t1 * t1 * t1 + 1;
}

export function easeInOutCubic(t: number): number {
    return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
}

// =============================================================================
// Elastic Easing
// =============================================================================

export function easeInElastic(t: number): number {
    if (t === 0) return 0;
    if (t === 1) return 1;
    const p = 0.3;
    const s = p / 4;
    return -(Math.pow(2, 10 * (t - 1)) * Math.sin(((t - 1 - s) * (2 * Math.PI)) / p));
}

export function easeOutElastic(t: number): number {
    if (t === 0) return 0;
    if (t === 1) return 1;
    const p = 0.3;
    const s = p / 4;
    return Math.pow(2, -10 * t) * Math.sin(((t - s) * (2 * Math.PI)) / p) + 1;
}

export function easeInOutElastic(t: number): number {
    if (t === 0) return 0;
    if (t === 1) return 1;
    const p = 0.3 * 1.5;
    const s = p / 4;
    if (t < 0.5) {
        const t2 = t * 2 - 1;
        return -0.5 * (Math.pow(2, 10 * t2) * Math.sin(((t2 - s) * (2 * Math.PI)) / p));
    }
    const t2 = t * 2 - 1;
    return 0.5 * (Math.pow(2, -10 * t2) * Math.sin(((t2 - s) * (2 * Math.PI)) / p)) + 1;
}

// =============================================================================
// Bounce Easing
// =============================================================================

export function easeOutBounce(t: number): number {
    if (t < 1 / 2.75) {
        return 7.5625 * t * t;
    } else if (t < 2 / 2.75) {
        const t1 = t - 1.5 / 2.75;
        return 7.5625 * t1 * t1 + 0.75;
    } else if (t < 2.5 / 2.75) {
        const t1 = t - 2.25 / 2.75;
        return 7.5625 * t1 * t1 + 0.9375;
    } else {
        const t1 = t - 2.625 / 2.75;
        return 7.5625 * t1 * t1 + 0.984375;
    }
}

export function easeInBounce(t: number): number {
    return 1 - easeOutBounce(1 - t);
}

// =============================================================================
// Spring Easing
// =============================================================================

export function spring(t: number): number {
    const stiffness = 100;
    const damping = 10;
    const mass = 1;

    const w0 = Math.sqrt(stiffness / mass);
    const zeta = damping / (2 * Math.sqrt(stiffness * mass));

    if (zeta < 1) {
        // Underdamped
        const wd = w0 * Math.sqrt(1 - zeta * zeta);
        const A = 1;
        const B = (zeta * w0) / wd;
        return 1 - Math.exp(-zeta * w0 * t) * (A * Math.cos(wd * t) + B * Math.sin(wd * t));
    } else {
        // Critically damped or overdamped
        return 1 - (1 + w0 * t) * Math.exp(-w0 * t);
    }
}

/**
 * Overshoot easing — goes past the target then snaps back.
 */
export function overshoot(t: number, amount: number = 1.70158): number {
    const s = amount;
    const t1 = t - 1;
    return t1 * t1 * ((s + 1) * t1 + s) + 1;
}

/**
 * Get an easing function by name.
 */
export function getEasingFunction(name: string): (t: number) => number {
    const map: Record<string, (t: number) => number> = {
        linear,
        easeIn: easeInCubic,
        easeOut: easeOutCubic,
        easeInOut: easeInOutCubic,
        easeInQuad,
        easeOutQuad,
        easeInOutQuad,
        easeInCubic,
        easeOutCubic,
        easeInOutCubic,
        easeInElastic,
        easeOutElastic,
        easeInOutElastic,
        easeInBounce,
        easeOutBounce,
        spring,
        overshoot,
    };
    return map[name] ?? linear;
}

// =============================================================================
// Interpolation Utilities
// =============================================================================

/**
 * Linear interpolation between two values.
 */
export function lerp(from: number, to: number, t: number): number {
    return from + (to - from) * t;
}

/**
 * Linear interpolation for colors (in hex format).
 */
export function lerpColor(from: string, to: string, t: number): string {
    const fromRgb = hexToRgb(from);
    const toRgb = hexToRgb(to);

    if (!fromRgb || !toRgb) return from;

    const r = Math.round(lerp(fromRgb.r, toRgb.r, t));
    const g = Math.round(lerp(fromRgb.g, toRgb.g, t));
    const b = Math.round(lerp(fromRgb.b, toRgb.b, t));

    return rgbToHex(r, g, b);
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result
        ? {
            r: parseInt(result[1]!, 16),
            g: parseInt(result[2]!, 16),
            b: parseInt(result[3]!, 16),
        }
        : null;
}

function rgbToHex(r: number, g: number, b: number): string {
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, "0")).join("")}`;
}

/**
 * Clamp a value between min and max.
 */
export function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}
