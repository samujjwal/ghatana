/**
 * Legacy simulation migration exports.
 *
 * This file is intentionally separate from the production engine barrel so
 * retired auto-runtime presets cannot leak into learner/runtime bundles.
 */

export * from "./auto-retired";
export * from "./auto";
export * from "./auto/preset-compatibility";
