/**
 * Sentry Instrumentation — Release Tagging and Source Maps (F-Y049)
 *
 * This module initialises Sentry with:
 * - `release` set from `VITE_APP_VERSION` (injected at build time)
 * - `environment` set from `VITE_APP_ENV`
 * - Source maps uploaded via `@sentry/vite-plugin` in vite.config.ts
 *
 * **Dependencies** (add to package.json when wiring in):
 * ```
 * "@sentry/react": "^9"
 * "@sentry/vite-plugin": "^3"
 * ```
 *
 * **Vite config addition** (see comment block in vite.config.ts):
 * ```ts
 * import { sentryVitePlugin } from '@sentry/vite-plugin';
 * // ...inside plugins array:
 * sentryVitePlugin({
 *   org: process.env.SENTRY_ORG,
 *   project: process.env.SENTRY_PROJECT,
 *   authToken: process.env.SENTRY_AUTH_TOKEN,
 *   release: { name: process.env.VITE_APP_VERSION },
 *   sourcemaps: { assets: './dist/**' },
 * }),
 * ```
 *
 * **CI environment variables** required:
 * - `VITE_APP_VERSION`  — semver + git SHA, e.g. `1.4.2-abc1234`
 * - `VITE_APP_ENV`      — `production` | `staging` | `development`
 * - `SENTRY_DSN`        — DSN for the yappc-web project
 * - `SENTRY_AUTH_TOKEN` — CI secret for source map uploads
 * - `SENTRY_ORG`        — Sentry organisation slug
 * - `SENTRY_PROJECT`    — Sentry project slug
 *
 * @doc.type service
 * @doc.purpose Sentry initialisation with mandatory release tagging
 * @doc.layer product
 * @doc.pattern Observability Initialisation
 */

// ─────────────────────────────────────────────────────────────────────────────
// Env vars (injected by Vite at build time via import.meta.env)
// ─────────────────────────────────────────────────────────────────────────────

const SENTRY_DSN: string = import.meta.env.VITE_SENTRY_DSN ?? '';
const APP_VERSION: string = import.meta.env.VITE_APP_VERSION ?? 'unknown';
const APP_ENV: string = import.meta.env.VITE_APP_ENV ?? 'development';

// ─────────────────────────────────────────────────────────────────────────────
// Initialisation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Initialise Sentry.
 *
 * Must be called once, before the React tree mounts (i.e. in main.tsx).
 * Safe to call in non-production environments — Sentry is a no-op when DSN is
 * absent.
 *
 * @example
 * ```ts
 * // main.tsx
 * import { initSentry } from './lib/sentry';
 * initSentry();
 * ```
 */
export function initSentry(): void {
  if (!SENTRY_DSN) {
    // No DSN configured — skip initialisation in local dev / test.
    return;
  }

  // Dynamic import avoids bundling Sentry in environments where it isn't used.
  void import('@sentry/react').then((Sentry) => {
    Sentry.init({
      dsn: SENTRY_DSN,
      // release MUST be set — this is the F-Y049 contract requirement.
      release: APP_VERSION,
      environment: APP_ENV,
      // Sample 100 % in dev/staging, 10 % in prod to keep cost in check.
      tracesSampleRate: APP_ENV === 'production' ? 0.1 : 1.0,
      // Replay 1 % of sessions in prod, 10 % on errors.
      replaysSessionSampleRate: APP_ENV === 'production' ? 0.01 : 0.1,
      replaysOnErrorSampleRate: 1.0,
      integrations: [
        Sentry.browserTracingIntegration(),
        Sentry.replayIntegration(),
      ],
    });
  });
}

/**
 * Capture a handled error with optional context.
 *
 * Use this for errors that are caught and handled but are worth tracking
 * (e.g. API failures that fall back gracefully).
 */
export function captureHandledException(
  error: unknown,
  context?: Record<string, unknown>
): void {
  if (!SENTRY_DSN) return;

  void import('@sentry/react').then((Sentry) => {
    Sentry.withScope((scope) => {
      if (context) {
        scope.setExtras(context);
      }
      Sentry.captureException(error);
    });
  });
}
