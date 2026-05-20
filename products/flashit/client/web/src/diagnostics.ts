export type FlashItDiagnosticLevel = 'debug' | 'info' | 'warn' | 'error';

export interface FlashItDiagnosticEvent {
  level: FlashItDiagnosticLevel;
  component: string;
  message: string;
  context?: Record<string, unknown>;
  error?: unknown;
}

export function emitFlashItDiagnostic(event: FlashItDiagnosticEvent): void {
  if (typeof window === 'undefined') {
    return;
  }

  window.dispatchEvent(
    new CustomEvent<FlashItDiagnosticEvent>('flashit-diagnostic', {
      detail: event,
    })
  );
}
