/**
 * Registry Candidate Promotion Service
 *
 * Promotes a decompiled residual island into a reviewed component registry candidate.
 * The backend owns persistence and audit records; the frontend only clears pending
 * residual state after the promotion response is validated.
 *
 * @doc.type service
 * @doc.purpose Promote residual islands to component registry candidates through the artifact backend
 * @doc.layer product
 * @doc.pattern Repository Pattern
 * @doc.security Cookie-based auth, no localStorage tokens
 */

/** Source path that produced the registry candidate. */
export type RegistryCandidateSource = 'decompiled-import';

/** Review state for a newly promoted registry candidate. */
export type RegistryCandidateStatus = 'NEEDS_REVIEW';

/** Input to a residual-island promotion operation. */
export interface RegistryCandidatePromotionRequest {
  /** Artifact (page document) that owns the residual island. */
  readonly artifactId: string;
  /** Stable identifier for the residual island node. */
  readonly residualIslandId: string;
  /** Proposed component contract name for the registry review queue. */
  readonly proposedContractName: string;
  /** Source of the promoted component candidate. */
  readonly source: RegistryCandidateSource;
  /** Optional reviewer note explaining why this residual should become a contract. */
  readonly notes?: string;
}

/** Backend response after the candidate is persisted. */
export interface RegistryCandidatePromotionResponse {
  /** Server-assigned candidate identifier. */
  readonly candidateId: string;
  /** Echo of the artifact id. */
  readonly artifactId: string;
  /** Echo of the island id. */
  readonly residualIslandId: string;
  /** Proposed component contract name accepted into review. */
  readonly proposedContractName: string;
  /** Review state assigned by the registry workflow. */
  readonly status: RegistryCandidateStatus;
  /** Server-assigned audit record identifier for the promotion. */
  readonly auditRecordId: string;
  /** ISO-8601 timestamp at which the candidate was created. */
  readonly createdAt: string;
}

const PROMOTION_ENDPOINT_TEMPLATE =
  '/api/v1/yappc/artifacts/:artifactId/residual-islands/:residualIslandId/registry-candidates';

function buildEndpoint(artifactId: string, residualIslandId: string): string {
  return PROMOTION_ENDPOINT_TEMPLATE.replace(':artifactId', encodeURIComponent(artifactId)).replace(
    ':residualIslandId',
    encodeURIComponent(residualIslandId),
  );
}

function isRegistryCandidatePromotionResponse(value: unknown): value is RegistryCandidatePromotionResponse {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['candidateId'] === 'string' &&
    typeof v['artifactId'] === 'string' &&
    typeof v['residualIslandId'] === 'string' &&
    typeof v['proposedContractName'] === 'string' &&
    v['status'] === 'NEEDS_REVIEW' &&
    typeof v['auditRecordId'] === 'string' &&
    typeof v['createdAt'] === 'string'
  );
}

/**
 * Persist a registry candidate promotion through the artifact backend.
 *
 * @throws {Error} When required identifiers are missing, the server returns a non-2xx status,
 * or the response payload does not match the registry candidate contract.
 */
export async function promoteResidualIslandToRegistryCandidate(
  request: RegistryCandidatePromotionRequest,
): Promise<RegistryCandidatePromotionResponse> {
  const { artifactId, residualIslandId, proposedContractName, source, notes } = request;

  if (!artifactId || !residualIslandId || !proposedContractName) {
    throw new Error(
      'promoteResidualIslandToRegistryCandidate: artifactId, residualIslandId, and proposedContractName are required',
    );
  }

  const endpoint = buildEndpoint(artifactId, residualIslandId);

  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ proposedContractName, source, notes }),
    });
  } catch (networkError) {
    throw new Error(
      `Registry candidate promotion POST failed due to a network error: ${
        networkError instanceof Error ? networkError.message : String(networkError)
      }`,
    );
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(
      `Registry candidate promotion request failed (HTTP ${response.status}): ${body}`,
    );
  }

  const payload: unknown = await response.json();
  if (!isRegistryCandidatePromotionResponse(payload)) {
    throw new Error('Registry candidate promotion response had an unexpected shape');
  }

  return payload;
}
