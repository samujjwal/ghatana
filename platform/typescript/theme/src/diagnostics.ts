export interface ThemeDiagnosticEvent {
  level: 'warn' | 'error';
  component: string;
  message: string;
  error?: unknown;
}

export function emitThemeDiagnostic(event: ThemeDiagnosticEvent): void {
  if (
    typeof globalThis.dispatchEvent !== 'function' ||
    typeof CustomEvent === 'undefined'
  ) {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent<ThemeDiagnosticEvent>('ghatana-theme-diagnostic', {
      detail: event,
    })
  );
}
