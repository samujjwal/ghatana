export type FlashItMobileDiagnosticLevel = 'debug' | 'info' | 'warn' | 'error';

export interface FlashItMobileDiagnosticEvent {
  level: FlashItMobileDiagnosticLevel;
  component: string;
  message: string;
  context?: Record<string, unknown>;
  error?: unknown;
  timestamp: string;
}

const MAX_DIAGNOSTICS = 100;
const diagnostics: FlashItMobileDiagnosticEvent[] = [];

export function emitFlashItMobileDiagnostic(
  event: Omit<FlashItMobileDiagnosticEvent, 'timestamp'>
): void {
  diagnostics.unshift({
    ...event,
    timestamp: new Date().toISOString(),
  });

  if (diagnostics.length > MAX_DIAGNOSTICS) {
    diagnostics.splice(MAX_DIAGNOSTICS);
  }
}

export function getFlashItMobileDiagnostics(): FlashItMobileDiagnosticEvent[] {
  return [...diagnostics];
}
