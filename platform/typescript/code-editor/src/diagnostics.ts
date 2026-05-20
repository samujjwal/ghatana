export type CodeEditorDiagnosticLevel = "debug" | "info" | "warn" | "error";

export interface CodeEditorDiagnosticEvent {
  source: string;
  level: CodeEditorDiagnosticLevel;
  message: string;
  context?: Record<string, unknown>;
  timestamp: string;
}

export function emitCodeEditorDiagnostic(
  source: string,
  level: CodeEditorDiagnosticLevel,
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
    new CustomEvent<CodeEditorDiagnosticEvent>("code-editor-diagnostic", {
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
