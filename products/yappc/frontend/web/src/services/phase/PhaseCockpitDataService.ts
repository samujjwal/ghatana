import type {
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from './types';

export type LifecyclePhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'LEARN'
  | 'EVOLVE';

interface RawPhaseProjectSnapshot extends Omit<PhaseProjectSnapshot, 'healthScore' | 'nextActionHints'> {
  aiHealthScore?: number | null;
  aiNextActions?: string[];
}

const LIFECYCLE_PHASES: readonly LifecyclePhase[] = [
  'INTENT',
  'SHAPE',
  'VALIDATE',
  'GENERATE',
  'RUN',
  'OBSERVE',
  'LEARN',
  'EVOLVE',
];

export function isLifecyclePhase(value: string): value is LifecyclePhase {
  return (LIFECYCLE_PHASES as readonly string[]).includes(value);
}

export function formatTimestamp(timestamp: string): string {
  return new Date(timestamp).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

async function readResponseBody(response: Response): Promise<string> {
  const maybeText = (response as Response & { text?: () => Promise<string> }).text;
  if (typeof maybeText === 'function') {
    return maybeText.call(response);
  }

  const maybeJson = (response as Response & { json?: () => Promise<unknown> }).json;
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
    throw new Error(`${context} returned invalid JSON: ${detail}`, {
      cause: error,
    });
  }
}

export async function parseProjectResponse(
  response: Response,
  context: string,
): Promise<PhaseProjectSnapshot> {
  const payload = await parseJsonResponse<unknown>(response, context);

  const rawProject = (
    typeof payload === 'object' &&
    payload !== null &&
    'project' in payload
      ? (payload as { project: RawPhaseProjectSnapshot }).project
      : (payload as RawPhaseProjectSnapshot)
  );

  return {
    ...rawProject,
    healthScore: rawProject.aiHealthScore ?? null,
    nextActionHints: rawProject.aiNextActions ?? [],
  };
}

export async function fetchPhaseTransitionPreview(
  currentPhase: LifecyclePhase,
  projectId: string,
): Promise<PhaseTransitionPreviewSnapshot> {
  const response = await fetch(
    `/api/phases/${encodeURIComponent(currentPhase)}/next?projectId=${encodeURIComponent(projectId)}`,
    {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    },
  );

  if (!response.ok) {
    throw new Error('Failed to load phase transition preview.');
  }

  return parseJsonResponse<PhaseTransitionPreviewSnapshot>(
    response,
    'fetch phase transition preview',
  );
}
