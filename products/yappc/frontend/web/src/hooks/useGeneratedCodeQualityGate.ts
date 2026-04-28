/**
 * Generated-Code Quality Gate Hook (F-Y016 / AI-Y5)
 *
 * Fetches and polls quality-gate results for a generated-code artifact from
 * `/api/generated-code/:artifactId/quality`.
 *
 * The hook returns a `canAccept` flag that is `true` only when all three
 * checks — `compile`, `lint`, and `test` — have a `'PASSED'` status.
 *
 * @doc.type hook
 * @doc.purpose Quality gate enforcement for generated code artifacts
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useQuery } from '@tanstack/react-query';
import { parseJsonResponse } from '@/lib/http';

// ── Types ──────────────────────────────────────────────────────────────────────

export type QualityCheckStatus = 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED';

export interface QualityCheckResult {
  status: QualityCheckStatus;
  /** Human-readable detail (error output, lint issues, test summary) */
  detail?: string;
  /** AI-generated remediation suggestion when status is FAILED */
  remediationSuggestion?: string;
}

export interface GeneratedCodeQuality {
  artifactId: string;
  compile: QualityCheckResult;
  lint: QualityCheckResult;
  test: QualityCheckResult;
  /** Backend-resolved flag — true when all three checks pass */
  allPassed: boolean;
}

// ── Fetch ─────────────────────────────────────────────────────────────────────

const importMetaEnv = import.meta as ImportMeta & {
  env?: { DEV?: boolean; VITE_API_ORIGIN?: string };
};

const API_BASE_URL = importMetaEnv.env?.DEV
  ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

async function fetchGeneratedCodeQuality(
  artifactId: string
): Promise<GeneratedCodeQuality> {
  const response = await fetch(
    `${API_BASE_URL}/generated-code/${encodeURIComponent(artifactId)}/quality`,
    { credentials: 'include' }
  );
  return parseJsonResponse<GeneratedCodeQuality>(response);
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export interface UseGeneratedCodeQualityGateOptions {
  artifactId: string;
  /** Whether to enable the gate check. Pass false when no artifact is present. */
  enabled?: boolean;
}

export interface UseGeneratedCodeQualityGateReturn {
  /** True only when compile + lint + test all pass */
  canAccept: boolean;
  isLoading: boolean;
  isError: boolean;
  quality: GeneratedCodeQuality | undefined;
  /** Refetch manually (e.g. after a fix) */
  refetch: () => void;
}

/**
 * Poll the quality gate endpoint until all checks have a terminal status
 * (PASSED or FAILED). Refetches every 3 s while any check is still PENDING
 * or RUNNING.
 *
 * @example
 * ```tsx
 * const { canAccept, quality, isLoading } = useGeneratedCodeQualityGate({ artifactId });
 * <Button disabled={!canAccept}>Accept</Button>
 * ```
 */
export function useGeneratedCodeQualityGate({
  artifactId,
  enabled = true,
}: UseGeneratedCodeQualityGateOptions): UseGeneratedCodeQualityGateReturn {
  const isRunning = (q: GeneratedCodeQuality | undefined): boolean => {
    if (!q) return true;
    const checks = [q.compile, q.lint, q.test];
    return checks.some((c) => c.status === 'PENDING' || c.status === 'RUNNING');
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['generated-code-quality', artifactId],
    queryFn: () => fetchGeneratedCodeQuality(artifactId),
    enabled: enabled && Boolean(artifactId),
    staleTime: 0,
    // Poll while any check is still in-progress
    refetchInterval: (query) => (isRunning(query.state.data) ? 3_000 : false),
  });

  const canAccept = Boolean(data?.allPassed);

  return { canAccept, isLoading, isError, quality: data, refetch };
}
