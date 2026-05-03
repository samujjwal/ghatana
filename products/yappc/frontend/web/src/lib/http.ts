async function readResponseBody(response: Response): Promise<string> {
  const maybeText = (response as unknown as { text?: () => Promise<string> }).text;
  if (typeof maybeText === 'function') {
    return maybeText.call(response);
  }

  const maybeJson = (response as unknown as { json?: () => Promise<unknown> }).json;
  if (typeof maybeJson === 'function') {
    const payload = await maybeJson.call(response);
    if (typeof payload === 'string') {
      return payload;
    }
    return JSON.stringify(payload ?? {});
  }

  return '';
}

export async function parseJsonResponse<T>(
  response: Response,
  context: string,
): Promise<T> {
  const raw = await readResponseBody(response);

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
  const raw = await readResponseBody(response);

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