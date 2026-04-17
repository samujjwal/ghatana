import * as Sentry from "@sentry/browser";

let initialized = false;

function getEnvironment(): string {
  return import.meta.env.MODE || "development";
}

export function initializeErrorTracking(): void {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn || initialized) {
    return;
  }

  Sentry.init({
    dsn,
    environment: getEnvironment(),
    tracesSampleRate: Number(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE ?? "0.1"),
    beforeSend(event): Sentry.Event | null {
      if (event.request?.headers) {
        delete event.request.headers.Authorization;
        delete event.request.headers.authorization;
        delete event.request.headers.Cookie;
        delete event.request.headers.cookie;
      }
      return event;
    },
  });

  initialized = true;
}

export function captureClientError(
  error: unknown,
  context?: Record<string, unknown>,
): void {
  if (!initialized) {
    return;
  }

  Sentry.withScope((scope) => {
    if (context) {
      scope.setContext("client", context);
    }

    if (error instanceof Error) {
      Sentry.captureException(error);
      return;
    }

    Sentry.captureMessage(String(error), "error");
  });
}

export function captureClientMessage(
  level: "debug" | "info" | "warn" | "error",
  message: string,
  context?: Record<string, unknown>,
): void {
  if (!initialized) {
    return;
  }

  Sentry.withScope((scope) => {
    if (context) {
      scope.setContext("client", context);
    }

    scope.setLevel(level);
    Sentry.captureMessage(message, level);
  });
}