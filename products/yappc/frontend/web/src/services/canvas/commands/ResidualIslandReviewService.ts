/**
 * Residual Island Review Persistence Service
 *
 * Persists reviewer decisions (accept / reject) for residual island nodes to the backend.
 * Each call results in an immutable audit record on the server-side artifact store.
 *
 * @doc.type service
 * @doc.purpose Persist residual island review decisions to backend artifact store
 * @doc.layer product
 * @doc.pattern Repository Pattern
 * @doc.security Cookie-based auth, no localStorage tokens
 */

/** Decision options for a residual island review action. */
export type ResidualIslandDecision = 'ACCEPTED' | 'REJECTED' | 'PROMOTED';

/** Input to a single residual island review operation. */
export interface ResidualIslandReviewRequest {
  /** Artifact (page document) that owns the island. */
  readonly artifactId: string;
  /** Stable identifier for the residual island node. */
  readonly residualIslandId: string;
  /** Reviewer decision: accept the island as-is or reject (require rework). */
  readonly decision: ResidualIslandDecision;
  /** Optional reviewer note explaining the decision rationale. */
  readonly notes?: string;
  /** Clear documentation of downstream impact of this decision. */
  readonly downstreamImpact?: string;
}

/** Response from the backend after persisting the review. */
export interface ResidualIslandReviewResponse {
  /** Echo of the artifact id. */
  readonly artifactId: string;
  /** Echo of the island id. */
  readonly residualIslandId: string;
  /** Persisted decision. */
  readonly decision: ResidualIslandDecision;
  /** Server-assigned audit record identifier for the review. */
  readonly auditRecordId: string;
  /** ISO-8601 timestamp at which the decision was recorded. */
  readonly reviewedAt: string;
  /** Echoed downstream impact documentation. */
  readonly downstreamImpact?: string;
}

const REVIEW_ENDPOINT_TEMPLATE =
  '/api/v1/yappc/artifacts/:artifactId/residual-islands/:residualIslandId/review';

function buildEndpoint(artifactId: string, residualIslandId: string): string {
  return REVIEW_ENDPOINT_TEMPLATE.replace(':artifactId', encodeURIComponent(artifactId)).replace(
    ':residualIslandId',
    encodeURIComponent(residualIslandId),
  );
}

function isResidualIslandReviewResponse(value: unknown): value is ResidualIslandReviewResponse {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['artifactId'] === 'string' &&
    typeof v['residualIslandId'] === 'string' &&
    (v['decision'] === 'ACCEPTED' || v['decision'] === 'REJECTED' || v['decision'] === 'PROMOTED') &&
    typeof v['auditRecordId'] === 'string' &&
    typeof v['reviewedAt'] === 'string' &&
    (v['downstreamImpact'] === undefined || typeof v['downstreamImpact'] === 'string')
  );
}

/**
 * Persist a residual island review decision to the artifact backend.
 *
 * @throws {Error} When the server returns a non-2xx status or an unexpected payload shape.
 */
export async function persistResidualIslandReview(
  request: ResidualIslandReviewRequest,
): Promise<ResidualIslandReviewResponse> {
  const { artifactId, residualIslandId, decision, notes, downstreamImpact } = request;

  if (!artifactId || !residualIslandId) {
    throw new Error(
      'persistResidualIslandReview: artifactId and residualIslandId are required',
    );
  }

  // Require downstream impact documentation for PROMOTED decisions
  if (decision === 'PROMOTED' && !downstreamImpact) {
    throw new Error(
      'persistResidualIslandReview: downstreamImpact is required for PROMOTED decisions',
    );
  }

  const endpoint = buildEndpoint(artifactId, residualIslandId);

  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ decision, notes, downstreamImpact }),
    });
  } catch (networkError) {
    throw new Error(
      `Residual island review POST failed due to a network error: ${
        networkError instanceof Error ? networkError.message : String(networkError)
      }`,
    );
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(
      `Residual island review request failed (HTTP ${response.status}): ${body}`,
    );
  }

  const payload: unknown = await response.json();
  if (!isResidualIslandReviewResponse(payload)) {
    throw new Error('Residual island review response had an unexpected shape');
  }

  return payload;
}

/**
 * Generate default downstream impact descriptions for residual island decisions.
 */
export function generateDefaultDownstreamImpact(decision: ResidualIslandDecision): string {
  switch (decision) {
    case 'ACCEPTED':
      return 'Residual island accepted as-is. Island will be included in the page document without further governance.';
    case 'REJECTED':
      return 'Residual island rejected. Island will be removed from the page document and not included in production artifacts.';
    case 'PROMOTED':
      return 'Residual island promoted to registry candidate. Will undergo review for inclusion in the component registry as a reusable contract.';
  }
}
