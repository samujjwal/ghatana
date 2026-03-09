// Storybook/browser-friendly entry for @ghatana/yappc-mocks
// Re-export only browser-safe parts to avoid pulling in Node-only
// modules (for example `msw/node`) during Storybook/Vite dependency
// scanning and pre-bundling.
export * from './seed-canvas';
export * from './handlers';
export { worker } from './browser';
export * from './factories';
export * from './doubles';

// Do not export `server` (msw/node) here — it is Node-only and will
// cause resolver errors in the Storybook preview.
