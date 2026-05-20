export type DesignSystemDiagnosticLevel = "debug" | "info" | "warn" | "error";

export interface DesignSystemDiagnosticEvent {
  source: string;
  level: DesignSystemDiagnosticLevel;
  message: string;
  context?: Record<string, unknown>;
  timestamp: string;
}

export function emitDesignSystemDiagnostic(
  source: string,
  level: DesignSystemDiagnosticLevel,
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
    new CustomEvent<DesignSystemDiagnosticEvent>("design-system-diagnostic", {
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
