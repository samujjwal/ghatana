function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

export function isRecoverableMswStartupError(error: unknown): boolean {
  const message = getErrorMessage(error);

  return (
    message.includes("Failed to register the Service Worker") ||
    message.includes("unsupported MIME type") ||
    message.includes("mockServiceWorker.js") ||
    message.includes("ServiceWorker")
  );
}
