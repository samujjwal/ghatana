/**
 * Release gate evidence API client.
 *
 * @doc.type service
 * @doc.purpose Loads CI release-gate evidence for the admin observability surface
 * @doc.layer product
 * @doc.pattern API Client
 */

export type ReleaseGateStatus = 'healthy' | 'degraded' | 'down';

export interface ReleaseGateEvidenceRecord {
  readonly id: string;
  readonly label: string;
  readonly category: string;
  readonly status: ReleaseGateStatus;
  readonly evidenceHref: string;
  readonly refreshedAt: string;
  readonly summary: string;
}

interface ReleaseGateDescriptor {
  readonly id: string;
  readonly label: string;
  readonly category: string;
  readonly evidenceHref: string;
}

const RELEASE_GATE_DESCRIPTORS: readonly ReleaseGateDescriptor[] = [
  {
    id: 'product-slo-budgets',
    label: 'Product SLO budgets',
    category: 'SLO',
    evidenceHref: '/release-evidence/product-slo-budgets.json',
  },
  {
    id: 'product-cost-budgets',
    label: 'Product cost budgets',
    category: 'Cost',
    evidenceHref: '/release-evidence/product-cost-budgets.json',
  },
  {
    id: 'product-domain-invariants',
    label: 'Product domain invariants',
    category: 'Domain',
    evidenceHref: '/release-evidence/product-domain-invariants.json',
  },
  {
    id: 'openapi-breaking-changes',
    label: 'OpenAPI breaking changes',
    category: 'API',
    evidenceHref: '/release-evidence/openapi-breaking-changes.json',
  },
] as const;

const RELEASE_GATE_API = '/api/admin/observability/release-gates';

type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

async function readErrorResponse(response: Response, fallbackMessage: string): Promise<string> {
  try {
    const text = await response.text();
    if (text.trim().length > 0) {
      try {
        const parsed = JSON.parse(text) as unknown;
        if (isRecord(parsed)) {
          return stringValue(parsed.message) ?? stringValue(parsed.error) ?? text;
        }
      } catch {
        // Non-JSON error bodies are returned as-is below.
      }
      return text;
    }
  } catch {
    // Ignore secondary read errors and return fallback below.
  }
  return fallbackMessage;
}

async function parseJsonResponse(response: Response, context: string): Promise<unknown> {
  try {
    return (await response.json()) as unknown;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to parse ${context} response: ${detail}`, {
      cause: error,
    });
  }
}

function stringValue(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

function booleanValue(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null;
}

function normalizeStatus(payload: unknown): ReleaseGateStatus {
  if (!isRecord(payload)) {
    return 'degraded';
  }

  const explicit = stringValue(payload.status)?.toLowerCase();
  if (explicit === 'healthy' || explicit === 'passed' || explicit === 'pass' || explicit === 'ok') {
    return 'healthy';
  }
  if (explicit === 'degraded' || explicit === 'warning' || explicit === 'warn') {
    return 'degraded';
  }
  if (explicit === 'down' || explicit === 'failed' || explicit === 'fail' || explicit === 'error') {
    return 'down';
  }

  const passed = booleanValue(payload.passed)
    ?? booleanValue(payload.success)
    ?? booleanValue(payload.ok);
  if (passed !== null) {
    return passed ? 'healthy' : 'down';
  }

  const violations = payload.violations;
  if (Array.isArray(violations) && violations.length > 0) {
    return 'down';
  }

  const warnings = payload.warnings;
  if (Array.isArray(warnings) && warnings.length > 0) {
    return 'degraded';
  }

  return 'healthy';
}

function normalizeSummary(payload: unknown, descriptor: ReleaseGateDescriptor): string {
  if (!isRecord(payload)) {
    return `${descriptor.label} evidence was loaded, but its payload shape was not recognized.`;
  }

  return stringValue(payload.summary)
    ?? stringValue(payload.message)
    ?? stringValue(payload.description)
    ?? `${descriptor.label} evidence loaded from CI.`;
}

function normalizeRefreshedAt(payload: unknown, fallback: string): string {
  if (!isRecord(payload)) {
    return fallback;
  }

  return stringValue(payload.refreshedAt)
    ?? stringValue(payload.generatedAt)
    ?? stringValue(payload.createdAt)
    ?? stringValue(payload.timestamp)
    ?? fallback;
}

function normalizeRecord(
  payload: unknown,
  descriptor: ReleaseGateDescriptor,
  fallbackTimestamp: string,
): ReleaseGateEvidenceRecord {
  if (isRecord(payload)) {
    const id = stringValue(payload.id) ?? descriptor.id;
    const label = stringValue(payload.label) ?? descriptor.label;
    const category = stringValue(payload.category) ?? descriptor.category;
    const evidenceHref = stringValue(payload.evidenceHref) ?? descriptor.evidenceHref;

    return {
      id,
      label,
      category,
      status: normalizeStatus(payload),
      evidenceHref,
      refreshedAt: normalizeRefreshedAt(payload, fallbackTimestamp),
      summary: normalizeSummary(payload, descriptor),
    };
  }

  return {
    id: descriptor.id,
    label: descriptor.label,
    category: descriptor.category,
    status: 'degraded',
    evidenceHref: descriptor.evidenceHref,
    refreshedAt: fallbackTimestamp,
    summary: normalizeSummary(payload, descriptor),
  };
}

function readApiItems(payload: unknown): readonly unknown[] | null {
  if (Array.isArray(payload)) {
    return payload.map((item) => item as unknown);
  }
  if (isRecord(payload)) {
    const items = payload.items;
    if (Array.isArray(items)) {
      return items.map((item) => item as unknown);
    }
  }
  if (isRecord(payload)) {
    const releaseGates = payload.releaseGates;
    if (Array.isArray(releaseGates)) {
      return releaseGates.map((item) => item as unknown);
    }
  }
  return null;
}

function descriptorForPayload(item: unknown, fallback: ReleaseGateDescriptor): ReleaseGateDescriptor {
  if (!isRecord(item)) {
    return fallback;
  }
  const id = stringValue(item.id);
  return RELEASE_GATE_DESCRIPTORS.find((descriptor) => descriptor.id === id) ?? fallback;
}

async function fetchApiReleaseGates(
  fetchImpl: FetchLike,
  fallbackTimestamp: string,
): Promise<readonly ReleaseGateEvidenceRecord[] | null> {
  const response = await fetchImpl(RELEASE_GATE_API, {
    headers: { Accept: 'application/json' },
  });

  if (response.status === 404 || response.status === 501) {
    return null;
  }

  if (!response.ok) {
    const detail = await readErrorResponse(response, `HTTP ${response.status}`);
    throw new Error(`Failed to load release gate evidence: ${detail}`);
  }

  const payload = await parseJsonResponse(response, 'release gate evidence');
  const items = readApiItems(payload);
  if (!items) {
    throw new Error('release gate evidence returned an unsupported payload shape');
  }

  return items.map((item, index) => {
    const fallback = RELEASE_GATE_DESCRIPTORS[index] ?? RELEASE_GATE_DESCRIPTORS[0];
    return normalizeRecord(item, descriptorForPayload(item, fallback), fallbackTimestamp);
  });
}

async function fetchArtifactEvidence(
  fetchImpl: FetchLike,
  descriptor: ReleaseGateDescriptor,
  fallbackTimestamp: string,
): Promise<ReleaseGateEvidenceRecord> {
  const response = await fetchImpl(descriptor.evidenceHref, {
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    const detail = await readErrorResponse(response, `HTTP ${response.status}`);
    return {
      id: descriptor.id,
      label: descriptor.label,
      category: descriptor.category,
      status: 'down',
      evidenceHref: descriptor.evidenceHref,
      refreshedAt: fallbackTimestamp,
      summary: `${descriptor.label} evidence is unavailable: ${detail}`,
    };
  }

  const payload = await parseJsonResponse(response, `${descriptor.id} evidence`);
  return normalizeRecord(payload, descriptor, fallbackTimestamp);
}

export async function loadReleaseGateEvidence(
  fetchImpl: FetchLike = fetch,
): Promise<readonly ReleaseGateEvidenceRecord[]> {
  const fallbackTimestamp = new Date().toISOString();

  const apiRecords = await fetchApiReleaseGates(fetchImpl, fallbackTimestamp);
  if (apiRecords) {
    return apiRecords;
  }

  return Promise.all(
    RELEASE_GATE_DESCRIPTORS.map((descriptor) =>
      fetchArtifactEvidence(fetchImpl, descriptor, fallbackTimestamp)
    )
  );
}

