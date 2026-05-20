export interface SsoDiagnosticEvent {
  level: 'warn' | 'error';
  component: string;
  message: string;
  error?: unknown;
}

export function emitSsoDiagnostic(event: SsoDiagnosticEvent): void {
  if (
    typeof globalThis.dispatchEvent !== 'function' ||
    typeof CustomEvent === 'undefined'
  ) {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent<SsoDiagnosticEvent>('ghatana-sso-diagnostic', {
      detail: event,
    })
  );
}
