/**
 * GovernanceExportService
 *
 * Fetches the page artifact audit trail from the backend and triggers a
 * browser download. Supports JSON and CSV export formats.
 *
 * Endpoint:
 *   GET /api/v1/yappc/artifacts/:artifactId/audit-export?format=json|csv
 *
 * The service returns raw bytes from the server; it does not transform them
 * so the server remains the authoritative format provider.
 *
 * @doc.type service
 * @doc.purpose Governance export for page artifact audit trail
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export type GovernanceExportFormat = 'json' | 'csv';

export interface GovernanceExportResult {
  /** Artifact ID that was exported */
  readonly artifactId: string;
  /** Format that was exported */
  readonly format: GovernanceExportFormat;
  /** Number of audit records exported */
  readonly recordCount: number;
}

export class GovernanceExportError extends Error {
  constructor(
    message: string,
    public readonly statusCode?: number
  ) {
    super(message);
    this.name = 'GovernanceExportError';
  }
}

// ============================================================================
// Service
// ============================================================================

/**
 * Fetches the audit export from the server and returns the Blob for download.
 * Throws GovernanceExportError on non-2xx responses.
 */
export async function fetchGovernanceExport(
  artifactId: string,
  format: GovernanceExportFormat
): Promise<Blob> {
  if (!artifactId) {
    throw new GovernanceExportError('artifactId is required');
  }

  const url = `/api/v1/yappc/artifacts/${encodeURIComponent(artifactId)}/audit-export?format=${format}`;

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: format === 'csv' ? 'text/csv' : 'application/json',
    },
    credentials: 'same-origin',
  });

  if (!response.ok) {
    throw new GovernanceExportError(
      `Governance export failed: ${response.status} ${response.statusText}`,
      response.status
    );
  }

  return response.blob();
}

/**
 * Triggers a browser download for the governance audit export.
 *
 * @param artifactId  - The page artifact to export
 * @param format      - 'json' or 'csv'
 * @param filename    - Optional override for the downloaded file name
 * @returns           GovernanceExportResult on success
 */
export async function downloadGovernanceExport(
  artifactId: string,
  format: GovernanceExportFormat,
  filename?: string
): Promise<GovernanceExportResult> {
  const blob = await fetchGovernanceExport(artifactId, format);

  const derivedFilename =
    filename ?? `artifact-${artifactId}-audit.${format}`;

  const objectUrl = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = derivedFilename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }

  // Parse record count from blob if JSON; fall back to 0 for CSV
  let recordCount = 0;
  if (format === 'json') {
    try {
      const text = await blob.text();
      const parsed = JSON.parse(text) as { records?: unknown[] };
      recordCount = Array.isArray(parsed.records) ? parsed.records.length : 0;
    } catch {
      // Non-fatal — record count is informational only
      recordCount = 0;
    }
  }

  return { artifactId, format, recordCount };
}
