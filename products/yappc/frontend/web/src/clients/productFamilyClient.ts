/**
 * Product-family control-plane API client.
 *
 * @doc.type module
 * @doc.purpose Fetch release readiness, reusable assets, doc-truth, and guided reuse from backend-owned YAPPC read models
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { z } from 'zod';

const UnknownRecordSchema = z.record(z.string(), z.unknown());

const ReleaseReadinessSchema = z.object({
  productKey: z.string(),
  status: z.string(),
  verdict: z.string(),
  gateStatus: z.array(z.unknown()),
  blockers: z.array(z.unknown()),
  evidenceRefs: z.array(z.unknown()),
  foundationReadiness: z.array(z.unknown()),
  connectorGates: z.array(z.unknown()).optional(),
  approvalGates: z.array(z.unknown()).optional(),
  aiActionGates: z.array(z.unknown()).optional(),
  docTruthWarnings: z.array(z.unknown()),
  traceId: z.string(),
  updatedAt: z.string(),
});

const ProductAssetSchema = z.object({
  assetId: z.string(),
  type: z.string(),
  sourceProduct: z.string(),
  displayName: z.string(),
  domain: z.string(),
  paths: z.array(z.unknown()),
  maturity: z.string(),
  reuseMode: z.string(),
  dependencies: z.array(z.unknown()),
  tests: z.array(z.unknown()),
  productUsage: z.array(z.unknown()),
  owner: z.string(),
  promotionTarget: z.string(),
  promotionState: z.string(),
  compatibility: UnknownRecordSchema,
});

const AssetRegistrySchema = z.object({
  status: z.string(),
  assets: z.array(ProductAssetSchema),
  warnings: z.array(z.string()),
  appliedFilters: z.record(z.string(), z.string()).optional(),
});

const DocTruthSchema = z.object({
  status: z.string(),
  warnings: z.array(UnknownRecordSchema),
});

const GuidedReuseSchema = z.object({
  targetProduct: z.string(),
  status: z.string(),
  recommendations: z.array(UnknownRecordSchema),
});

const KernelTimelineSchema = z.object({
  productUnitId: z.string(),
  status: z.string(),
  timeline: z.array(UnknownRecordSchema),
  rollbackVisibility: UnknownRecordSchema,
});

const AssetPromotionSchema = z.object({
  status: z.string(),
  asset: ProductAssetSchema,
  promotion: UnknownRecordSchema,
});

export type ReleaseReadiness = z.infer<typeof ReleaseReadinessSchema>;
export type ProductAsset = z.infer<typeof ProductAssetSchema>;
export type AssetRegistry = z.infer<typeof AssetRegistrySchema>;
export type DocTruthWarnings = z.infer<typeof DocTruthSchema>;
export type GuidedReuseRecommendations = z.infer<typeof GuidedReuseSchema>;
export type KernelTimeline = z.infer<typeof KernelTimelineSchema>;
export type AssetPromotion = z.infer<typeof AssetPromotionSchema>;

export interface ProductAssetFilters {
  readonly search?: string;
  readonly product?: string;
  readonly domain?: string;
  readonly type?: string;
  readonly maturity?: string;
  readonly reuseMode?: string;
  readonly compatibility?: string;
}

export interface ProductAssetPromotionRequest {
  readonly targetState: 'hardened' | 'production' | 'shared-package' | 'plugin' | 'template' | 'schema';
  readonly promotionTarget?: string;
  readonly evidenceRefs?: readonly unknown[];
  readonly reason?: string;
}

const BASE_URL = '/api/v1/yappc/product-family';

async function apiFetch<T>(url: string, schema: z.ZodType<T>): Promise<T> {
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Product-family API error: ${response.status} ${response.statusText}`);
  }

  const raw: unknown = await response.json();
  return schema.parse(raw);
}

async function apiJson<T>(url: string, body: unknown, schema: z.ZodType<T>): Promise<T> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(`Product-family API error: ${response.status} ${response.statusText}`);
  }

  const raw: unknown = await response.json();
  return schema.parse(raw);
}

export function getReleaseReadiness(productKey: 'phr' | 'digital-marketing'): Promise<ReleaseReadiness> {
  return apiFetch(`${BASE_URL}/releases/${encodeURIComponent(productKey)}`, ReleaseReadinessSchema);
}

export function listProductAssets(filters: ProductAssetFilters = {}): Promise<AssetRegistry> {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  const query = params.toString();
  return apiFetch(`${BASE_URL}/assets${query ? `?${query}` : ''}`, AssetRegistrySchema);
}

export function promoteProductAsset(
  assetId: string,
  request: ProductAssetPromotionRequest,
): Promise<AssetPromotion> {
  return apiJson(
    `${BASE_URL}/assets/${encodeURIComponent(assetId)}/promotions`,
    request,
    AssetPromotionSchema,
  );
}

export function listDocTruthWarnings(): Promise<DocTruthWarnings> {
  return apiFetch(`${BASE_URL}/doc-truth`, DocTruthSchema);
}

export function listGuidedReuse(targetProduct: 'tutorputor' | 'flashit'): Promise<GuidedReuseRecommendations> {
  return apiFetch(`${BASE_URL}/reuse-recommendations/${encodeURIComponent(targetProduct)}`, GuidedReuseSchema);
}

export function getKernelTimeline(productUnitId: string): Promise<KernelTimeline> {
  return apiFetch(`${BASE_URL}/kernel-timeline/${encodeURIComponent(productUnitId)}`, KernelTimelineSchema);
}
