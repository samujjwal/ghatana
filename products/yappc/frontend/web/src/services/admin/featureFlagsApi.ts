/**
 * Feature Flags API — Admin Service
 *
 * Thin typed API client for tenant-scoped feature flag management.
 * Used by FeatureFlagsPage (F-Y047).
 *
 * @doc.type service
 * @doc.purpose Tenant feature flag CRUD and audit API client
 * @doc.layer product
 * @doc.pattern API Client
 */

const API_BASE = '/api/admin/feature-flags';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export interface FeatureFlag {
  id: string;
  key: string;
  description: string;
  enabled: boolean;
  tenantId: string;
  rolloutPercentage: number;
  createdAt: string;
  updatedAt: string;
  updatedBy: string;
}

export interface FeatureFlagAuditEntry {
  id: string;
  flagKey: string;
  previousValue: boolean;
  newValue: boolean;
  changedBy: string;
  reason: string;
  timestamp: string;
}

export interface SetFeatureFlagRequest {
  key: string;
  enabled: boolean;
  reason: string;
  rolloutPercentage?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// API functions
// ─────────────────────────────────────────────────────────────────────────────

export async function listTenantFeatureFlags(tenantId: string): Promise<FeatureFlag[]> {
  const response = await fetch(
    `${API_BASE}?tenantId=${encodeURIComponent(tenantId)}`,
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (!response.ok) {
    throw new Error(`Failed to fetch feature flags: ${response.status}`);
  }
  return response.json() as Promise<FeatureFlag[]>;
}

export async function setFeatureFlag(
  tenantId: string,
  req: SetFeatureFlagRequest
): Promise<FeatureFlag> {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(req.key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...req, tenantId }),
  });
  if (!response.ok) {
    throw new Error(`Failed to set feature flag: ${response.status}`);
  }
  return response.json() as Promise<FeatureFlag>;
}

export async function getFeatureFlagAuditLog(
  tenantId: string,
  flagKey: string
): Promise<FeatureFlagAuditEntry[]> {
  const response = await fetch(
    `${API_BASE}/${encodeURIComponent(flagKey)}/audit?tenantId=${encodeURIComponent(tenantId)}`,
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (!response.ok) {
    throw new Error(`Failed to fetch audit log: ${response.status}`);
  }
  return response.json() as Promise<FeatureFlagAuditEntry[]>;
}
