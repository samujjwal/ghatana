export interface FlashItSharedDiagnosticEvent {
  level: 'warn' | 'error';
  component: string;
  message: string;
  error?: unknown;
}

export function emitFlashItSharedDiagnostic(event: FlashItSharedDiagnosticEvent): void {
  if (
    typeof globalThis.dispatchEvent !== 'function' ||
    typeof CustomEvent === 'undefined'
  ) {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent<FlashItSharedDiagnosticEvent>('flashit-shared-diagnostic', {
      detail: event,
    })
  );
}
