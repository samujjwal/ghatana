export type CanvasDiagnosticLevel = "debug" | "info" | "warn" | "error";

export interface CanvasDiagnosticEvent {
  source: string;
  level: CanvasDiagnosticLevel;
  message: string;
  context?: Record<string, unknown>;
  timestamp: string;
}

export function emitCanvasDiagnostic(
  source: string,
  level: CanvasDiagnosticLevel,
  message: string,
  context?: Record<string, unknown>,
): void {
  if (
    typeof globalThis.dispatchEvent !== "function" ||
    typeof CustomEvent === "undefined"
  ) {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent<CanvasDiagnosticEvent>("canvas-diagnostic", {
      detail: {
        source,
        level,
        message,
        context,
        timestamp: new Date().toISOString(),
      },
    }),
  );
}
