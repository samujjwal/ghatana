export type DataCloudDiagnosticLevel = "debug" | "info" | "warn" | "error";

export interface DataCloudDiagnosticEvent {
  source: string;
  level: DataCloudDiagnosticLevel;
  message: string;
  context?: Record<string, unknown>;
  timestamp: string;
}

export function emitDataCloudDiagnostic(
  source: string,
  level: DataCloudDiagnosticLevel,
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
    new CustomEvent<DataCloudDiagnosticEvent>("data-cloud-diagnostic", {
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
