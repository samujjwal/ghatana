let initialized = false;
type SentryBrowserModule = typeof import("@sentry/browser");
let sentryModule: SentryBrowserModule | null = null;

function getEnvironment(): string {
  return import.meta.env.MODE || "development";
}

async function loadSentry(): Promise<SentryBrowserModule | null> {
  if (sentryModule) {
    return sentryModule;
  }

  try {
    sentryModule = await import("@sentry/browser");
    return sentryModule;
  } catch {
    return null;
  }
}

export async function initializeErrorTracking(): Promise<void> {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn || initialized) {
    return;
  }

  const Sentry = await loadSentry();
  if (!Sentry) {
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
  if (!initialized || !sentryModule) {
    return;
  }

  sentryModule.withScope((scope) => {
    if (context) {
      scope.setContext("client", context);
    }

    if (error instanceof Error) {
      sentryModule.captureException(error);
      return;
    }

    sentryModule.captureMessage(String(error), "error");
  });
}

export function captureClientMessage(
  level: "debug" | "info" | "warn" | "error",
  message: string,
  context?: Record<string, unknown>,
): void {
  if (!initialized || !sentryModule) {
    return;
  }

  sentryModule.withScope((scope) => {
    if (context) {
      scope.setContext("client", context);
    }

    scope.setLevel(level);
    sentryModule.captureMessage(message, level);
  });
}