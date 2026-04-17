import { LifecyclePhase } from '../../types/lifecycle';
import { parseJsonResponse } from '@/lib/http';

const importMetaEnv = import.meta as ImportMeta & {
  env?: {
    DEV?: boolean;
    VITE_API_ORIGIN?: string;
  };
};

const API_BASE_URL = importMetaEnv.env?.DEV
  ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

export interface PhaseTransitionPreview {
  projectId: string;
  currentPhase: LifecyclePhase;
  nextPhase: LifecyclePhase | null;
  canAdvance: boolean;
  readiness: number;
  blockers: string[];
  requiredArtifacts: string[];
  completedArtifacts: string[];
  estimatedReadyIn: string | null;
  estimatedReadyInHours: number | null;
  predictionConfidence: number | null;
  checkedAt: string;
}

class PhaseTransitionApiError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = 'PhaseTransitionApiError';
  }
}

async function readPhaseTransitionError(
  response: Response
): Promise<{ error?: string; message?: string }> {
  const raw = await response.text();
  if (!raw) {
    return {};
  }

  try {
    return JSON.parse(raw) as { error?: string; message?: string };
  } catch {
    return { message: raw.trim() || undefined };
  }
}

async function fetchPhaseTransitionPreview(
  currentPhase: LifecyclePhase,
  projectId: string
): Promise<PhaseTransitionPreview> {
  const response = await fetch(
    `${API_BASE_URL}/phases/${encodeURIComponent(currentPhase)}/next?projectId=${encodeURIComponent(projectId)}`,
    {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    }
  );

  if (!response.ok) {
    const error = await readPhaseTransitionError(response);

    throw new PhaseTransitionApiError(
      error.message ?? error.error ?? 'Failed to load phase transition preview',
      response.status
    );
  }

  return parseJsonResponse<PhaseTransitionPreview>(
    response,
    'fetch phase transition preview'
  );
}

export const phaseTransitionAPI = {
  getNextPhase: fetchPhaseTransitionPreview,
};

export { PhaseTransitionApiError };
