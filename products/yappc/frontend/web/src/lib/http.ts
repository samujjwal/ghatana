export async function parseJsonResponse<T>(
  response: Response,
  context: string,
): Promise<T> {
  const raw = await response.text();

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`);
  }
}

export async function parseJsonResourceResponse<T>(
  response: Response,
  context: string,
  key: string,
): Promise<T> {
  const payload = await parseJsonResponse<unknown>(response, context);

  if (
    typeof payload === 'object' &&
    payload !== null &&
    key in payload
  ) {
    return (payload as Record<string, T>)[key];
  }

  return payload as T;
}

export async function readErrorResponse(
  response: Response,
  fallback: string,
): Promise<string> {
  const raw = await response.text();

  if (!raw) {
    return fallback;
  }

  try {
    const payload = JSON.parse(raw) as { message?: unknown; error?: unknown };
    if (typeof payload.message === 'string' && payload.message.length > 0) {
      return payload.message;
    }
    if (typeof payload.error === 'string' && payload.error.length > 0) {
      return payload.error;
    }
  } catch {
    if (raw.trim().length > 0) {
      return raw.trim();
    }
  }

  return fallback;
}