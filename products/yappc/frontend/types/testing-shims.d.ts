// Ambient type shims for test helpers and third-party modules (project-local)
// This file helps the compiler when external test-related packages lack types.

declare module 'jest-axe' {
  // Minimal shim for the jest-axe helpers used in tests. Keep intentionally permissive.
  export const axe: unknown;
  export const toHaveNoViolations: unknown;
  const _default: { axe: unknown; toHaveNoViolations: any };
  export default _default;
}

// Augment vitest types with the custom matchers used across the repo (accessibility and image snapshot)
declare module 'vitest' {
  // Extend existing matcher interfaces with the helpers used in tests
  /**
   *
   */
  interface Assertion<T = unknown> {
    toHaveNoViolations(): void;
    toMatchImageSnapshot(): void;
  }

  /**
   *
   */
  interface JestMatchers<T = unknown> {
    toHaveNoViolations(): void;
    toMatchImageSnapshot(): void;
  }

  // Minimal shims for vitest top-level helpers (used in many test files)
  export const vi: unknown;
}

// Provide global top-level test helpers for environments that don't provide
// the typed globals. Keep permissive to avoid collisions with real @types.
declare global {
  const describe: unknown;
  const it: unknown;
  const test: unknown;
  const expect: unknown;
  const beforeEach: unknown;
  const afterEach: unknown;
  const beforeAll: unknown;
  const afterAll: unknown;
}

export {};

// Minimal shim for @faker-js/faker used in factories when types are not installed
declare module '@faker-js/faker' {
  export const faker: unknown;
}

// Minimal shims for Capacitor platform packages used in mobile-cap and web mobile routes
declare module '@capacitor/filesystem' {
  export const Directory: unknown;
  export const Filesystem: unknown;
  export const Encoding: unknown;
}

declare module '@capacitor/camera' {
  export const Camera: unknown;
  export const CameraDirection: unknown;
}

declare module '@capacitor/haptics' {
  export const Haptics: unknown;
  export const ImpactStyle: unknown;
}

declare module '@capacitor/local-notifications' {
  export const LocalNotifications: unknown;
}

declare module '@capacitor/share' {
  export const Share: unknown;
}

declare module '@capacitor/network' {
  export const Network: unknown;
}

// Shim for tanstack react query import typo / presence
declare module '@tanstack/react-query' {
  export const QueryClient: unknown;
  export const QueryClientProvider: unknown;
}

declare module 'web-vitals' {
  export function onCLS(cb: (...args: unknown[]) => void): void;
  export function onFID(cb: (...args: unknown[]) => void): void;
  export function onFCP(cb: (...args: unknown[]) => void): void;
  export function onLCP(cb: (...args: unknown[]) => void): void;
  export function onTTFB(cb: (...args: unknown[]) => void): void;
}

// Simple static asset modules
declare module '*.module.css';
declare module '*.module.scss';
declare module '*.png';
declare module '*.svg';
