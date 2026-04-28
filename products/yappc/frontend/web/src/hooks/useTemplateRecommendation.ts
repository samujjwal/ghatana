/**
 * useTemplateRecommendation — AI-Y9
 *
 * Fetches AI-generated project template recommendations based on
 * the user's onboarding context (role/goal).
 *
 * ## Data contract
 * `GET /api/onboarding/template-recommendations?role=:role&goal=:goal`
 * ```json
 * {
 *   "recommendations": [
 *     {
 *       "templateId": "web-saas",
 *       "name": "SaaS Web App",
 *       "description": "Full-stack template for SaaS products.",
 *       "tags": ["react", "api", "auth"],
 *       "confidence": 0.91
 *     }
 *   ]
 * }
 * ```
 *
 * @doc.type hook
 * @doc.purpose Fetch AI-generated template recommendations for onboarding
 * @doc.layer product
 * @doc.pattern Data Fetching
 */

import { useQuery } from '@tanstack/react-query';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface TemplateRecommendation {
  templateId: string;
  name: string;
  description: string;
  tags: string[];
  /** 0–1 confidence score from the recommendation model. */
  confidence: number;
}

export interface TemplateRecommendationResponse {
  recommendations: TemplateRecommendation[];
}

export interface TemplateRecommendationContext {
  /** User's self-described role, e.g. "frontend-engineer". */
  role?: string;
  /** Stated goal, e.g. "ship a saas product". */
  goal?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

function buildUrl(ctx: TemplateRecommendationContext): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  const params = new URLSearchParams();
  if (ctx.role) params.set('role', ctx.role);
  if (ctx.goal) params.set('goal', ctx.goal);
  return `${base}/api/onboarding/template-recommendations?${params.toString()}`;
}

async function fetchRecommendations(ctx: TemplateRecommendationContext): Promise<TemplateRecommendation[]> {
  const res = await fetch(buildUrl(ctx), { credentials: 'include' });
  if (!res.ok) throw new Error(`Template recommendations failed: ${res.status}`);
  const body = (await res.json()) as TemplateRecommendationResponse;
  return body.recommendations;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Returns AI-generated template recommendations for the current onboarding context.
 * Disabled when both `role` and `goal` are absent (e.g. before the user
 * has filled in any context).
 */
export function useTemplateRecommendation(ctx: TemplateRecommendationContext): {
  recommendations: TemplateRecommendation[];
  isLoading: boolean;
  isError: boolean;
} {
  const enabled = Boolean(ctx.role ?? ctx.goal);

  const { data, isLoading, isError } = useQuery<TemplateRecommendation[]>({
    queryKey: ['template-recommendations', ctx.role, ctx.goal],
    queryFn: () => fetchRecommendations(ctx),
    enabled,
    staleTime: 5 * 60_000, // recommendations are stable for the session
  });

  return {
    recommendations: data ?? [],
    isLoading: enabled && isLoading,
    isError,
  };
}
