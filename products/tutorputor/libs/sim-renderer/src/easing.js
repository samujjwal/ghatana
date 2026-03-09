/**
 * Easing Functions
 *
 * @doc.type module
 * @doc.purpose Provide easing functions for animations
 * @doc.layer product
 * @doc.pattern Utility
 */
/**
 * Apply an easing function to a progress value (0-1).
 */
export function applyEasing(progress, easing) {
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
function easeInQuad(t) {
    return t * t;
}
function easeOutQuad(t) {
    return t * (2 - t);
}
function easeInOutQuad(t) {
    return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
}
// =============================================================================
// Cubic Easing
// =============================================================================
function easeInCubic(t) {
    return t * t * t;
}
function easeOutCubic(t) {
    const t1 = t - 1;
    return t1 * t1 * t1 + 1;
}
function easeInOutCubic(t) {
    return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
}
// =============================================================================
// Elastic Easing
// =============================================================================
function easeInElastic(t) {
    if (t === 0)
        return 0;
    if (t === 1)
        return 1;
    const p = 0.3;
    const s = p / 4;
    return -(Math.pow(2, 10 * (t - 1)) * Math.sin(((t - 1 - s) * (2 * Math.PI)) / p));
}
function easeOutElastic(t) {
    if (t === 0)
        return 0;
    if (t === 1)
        return 1;
    const p = 0.3;
    const s = p / 4;
    return Math.pow(2, -10 * t) * Math.sin(((t - s) * (2 * Math.PI)) / p) + 1;
}
// =============================================================================
// Bounce Easing
// =============================================================================
function easeOutBounce(t) {
    if (t < 1 / 2.75) {
        return 7.5625 * t * t;
    }
    else if (t < 2 / 2.75) {
        const t1 = t - 1.5 / 2.75;
        return 7.5625 * t1 * t1 + 0.75;
    }
    else if (t < 2.5 / 2.75) {
        const t1 = t - 2.25 / 2.75;
        return 7.5625 * t1 * t1 + 0.9375;
    }
    else {
        const t1 = t - 2.625 / 2.75;
        return 7.5625 * t1 * t1 + 0.984375;
    }
}
function easeInBounce(t) {
    return 1 - easeOutBounce(1 - t);
}
// =============================================================================
// Spring Easing
// =============================================================================
function spring(t) {
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
    }
    else {
        // Critically damped or overdamped
        return 1 - (1 + w0 * t) * Math.exp(-w0 * t);
    }
}
// =============================================================================
// Interpolation Utilities
// =============================================================================
/**
 * Linear interpolation between two values.
 */
export function lerp(from, to, t) {
    return from + (to - from) * t;
}
/**
 * Linear interpolation for colors (in hex format).
 */
export function lerpColor(from, to, t) {
    const fromRgb = hexToRgb(from);
    const toRgb = hexToRgb(to);
    if (!fromRgb || !toRgb)
        return from;
    const r = Math.round(lerp(fromRgb.r, toRgb.r, t));
    const g = Math.round(lerp(fromRgb.g, toRgb.g, t));
    const b = Math.round(lerp(fromRgb.b, toRgb.b, t));
    return rgbToHex(r, g, b);
}
function hexToRgb(hex) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result
        ? {
            r: parseInt(result[1], 16),
            g: parseInt(result[2], 16),
            b: parseInt(result[3], 16),
        }
        : null;
}
function rgbToHex(r, g, b) {
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, "0")).join("")}`;
}
/**
 * Clamp a value between min and max.
 */
export function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}
//# sourceMappingURL=easing.js.map