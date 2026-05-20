export interface DataCloudUiComponentDiagnosticEvent {
  level: 'warn' | 'error';
  component: string;
  message: string;
  context?: Record<string, unknown>;
  error?: unknown;
}

export function emitDataCloudUiComponentDiagnostic(
  event: DataCloudUiComponentDiagnosticEvent,
): void {
  if (
    typeof globalThis.dispatchEvent !== 'function' ||
    typeof CustomEvent === 'undefined'
  ) {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent<DataCloudUiComponentDiagnosticEvent>(
      'data-cloud-ui-component-diagnostic',
      { detail: event },
    ),
  );
}
