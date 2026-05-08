/**
 * Import Review Decision Persistence Service
 *
 * Persists import review queue decisions for decompile loss points and residual
 * islands to the backend before PageDesigner marks them decided locally.
 *
 * @doc.type service
 * @doc.purpose Persist import review queue decisions with backend audit evidence
 * @doc.layer product
 * @doc.pattern Repository Pattern
 * @doc.security Cookie-based auth, no localStorage tokens
 */

export type ImportReviewItemKind = 'loss-point' | 'residual-island';
export type ImportReviewDecision = 'applied' | 'skipped' | 'promoted';

export interface PersistImportReviewDecisionRequest {
  readonly artifactId: string;
  readonly reviewItemId: string;
  readonly kind: ImportReviewItemKind;
  readonly decision: ImportReviewDecision;
  readonly label?: string;
  readonly details?: string;
  readonly notes?: string;
}

export interface PersistImportReviewDecisionResponse {
  readonly artifactId: string;
  readonly reviewItemId: string;
  readonly kind: ImportReviewItemKind;
  readonly decision: ImportReviewDecision;
  readonly auditRecordId: string;
  readonly auditRecorded: true;
  readonly reviewedAt: string;
}

const IMPORT_REVIEW_DECISION_ENDPOINT_TEMPLATE =
  '/api/v1/yappc/artifacts/:artifactId/import-review-decisions';

function buildEndpoint(artifactId: string): string {
  return IMPORT_REVIEW_DECISION_ENDPOINT_TEMPLATE.replace(':artifactId', encodeURIComponent(artifactId));
}

function isPersistImportReviewDecisionResponse(
  value: unknown,
): value is PersistImportReviewDecisionResponse {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as Record<string, unknown>;
  return (
    typeof record['artifactId'] === 'string' &&
    typeof record['reviewItemId'] === 'string' &&
    (record['kind'] === 'loss-point' || record['kind'] === 'residual-island') &&
    (record['decision'] === 'applied' || record['decision'] === 'skipped' || record['decision'] === 'promoted') &&
    typeof record['auditRecordId'] === 'string' &&
    record['auditRecorded'] === true &&
    typeof record['reviewedAt'] === 'string'
  );
}

export async function persistImportReviewDecision(
  request: PersistImportReviewDecisionRequest,
): Promise<PersistImportReviewDecisionResponse> {
  const { artifactId, reviewItemId, kind, decision, label, details, notes } = request;

  if (!artifactId || !reviewItemId) {
    throw new Error('persistImportReviewDecision: artifactId and reviewItemId are required');
  }

  let response: Response;
  try {
    response = await fetch(buildEndpoint(artifactId), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        reviewItemId,
        kind,
        decision,
        ...(label ? { label } : {}),
        ...(details ? { details } : {}),
        ...(notes ? { notes } : {}),
      }),
    });
  } catch (networkError) {
    throw new Error(
      `Import review decision POST failed due to a network error: ${
        networkError instanceof Error ? networkError.message : String(networkError)
      }`,
    );
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`Import review decision request failed (HTTP ${response.status}): ${body}`);
  }

  const payload: unknown = await response.json();
  if (!isPersistImportReviewDecisionResponse(payload)) {
    throw new Error('Import review decision response had an unexpected shape');
  }

  return payload;
}
