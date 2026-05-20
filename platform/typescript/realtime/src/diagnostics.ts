export type RealtimeDiagnosticLevel = 'debug' | 'info' | 'warn' | 'error';

export interface RealtimeDiagnosticEvent {
  level: RealtimeDiagnosticLevel;
  component: string;
  message: string;
  context?: Record<string, unknown>;
  error?: unknown;
}

export function emitRealtimeDiagnostic(event: RealtimeDiagnosticEvent): void {
  if (
    typeof globalThis.dispatchEvent !== 'function' ||
    typeof CustomEvent === 'undefined'
  ) {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent<RealtimeDiagnosticEvent>('ghatana-realtime-diagnostic', {
      detail: event,
    })
  );
}
