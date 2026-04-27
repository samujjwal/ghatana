/**
 * Shim: re-exports @yappc/auth/rbac so that the Vitest alias for '@yappc/auth'
 * (which is a prefix-matched alias resolving to src/) can still find the rbac
 * module at src/rbac.ts rather than src/auth/rbac.
 *
 * Root cause: vitest.config.ts lists `@yappc/auth` before `@yappc/auth/rbac`
 * in its alias array, so `@yappc/auth/rbac` is resolved via the more-general
 * rule as `<auth-src>/rbac` — which lands here.
 */
export * from './auth/rbac/index';
