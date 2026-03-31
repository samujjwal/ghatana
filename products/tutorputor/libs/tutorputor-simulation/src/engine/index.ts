// TutorPutor Simulation Engine - Consolidated Exports
// Legacy auto-runtime internals have been extracted to auto-retired.ts

export * from "./author";
export * from "./runtime";
export * from "./nl";

// Legacy auto-runtime compatibility - extracted to separate module
// Do not use for new development; prefer starter-catalog exports
export * from "./auto-retired";

// Modern compatibility layer - use these for preset interoperability
export * from "./auto/preset-compatibility";

export * from "./export";

// Curated starter catalog - preferred for all new simulation authoring
export * from "./starter-catalog";
export * from "./starter-packaging";
