type FrontendErrorPayload = {
  source: 'react-error-boundary' | 'window-error' | 'unhandled-rejection';
  message: string;
  stack?: string;
  componentStack?: string;
  route?: string;
  userAgent?: string;
  timestamp: string;
};

type FrontendErrorInput = {
  source: FrontendErrorPayload['source'];
  message: string;
  stack?: string;
  componentStack?: string;
};

const FRONTEND_ERROR_ENDPOINT = '/api/telemetry/frontend-errors';

export function reportFrontendError(input: FrontendErrorInput): void {
  const payload: FrontendErrorPayload = {
    source: input.source,
    message: input.message,
    stack: input.stack,
    componentStack: input.componentStack,
    route: typeof window !== 'undefined' ? window.location.pathname : undefined,
    userAgent:
      typeof navigator !== 'undefined' ? navigator.userAgent : undefined,
    timestamp: new Date().toISOString(),
  };

  // Fail-open telemetry: never throw in the UI path.
  try {
    if (
      typeof navigator !== 'undefined' &&
      typeof navigator.sendBeacon === 'function'
    ) {
      const body = JSON.stringify(payload);
      const blob = new Blob([body], { type: 'application/json' });
      navigator.sendBeacon(FRONTEND_ERROR_ENDPOINT, blob);
      return;
    }

    void fetch(FRONTEND_ERROR_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      keepalive: true,
    });
  } catch {
    // Intentionally no-op.
  }
}
