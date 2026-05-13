import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  kernelProductConformanceSuite,
  runProductConformanceSuite,
  type ProductConformanceContext,
} from '../index';
import type { ProductTelemetryFlowFixture } from '../../telemetry/index';

const testDir = fileURLToPath(new URL('.', import.meta.url));
const repoRoot = resolve(testDir, '../../../../../..');
const observabilityFlowManifest = JSON.parse(
  readFileSync(resolve(repoRoot, 'config/observability/product-observability-flows.json'), 'utf8'),
) as unknown;

const productIds = ['phr', 'finance', 'digital-marketing', 'flashit'] as const;
type ActiveProductId = (typeof productIds)[number];

const telemetryFixtures: readonly ProductTelemetryFlowFixture[] = productIds.map((product) => ({
  product,
  flow: `${product}-kernel-conformance`,
  execute(capture) {
    capture.recordTrace(`${product}.trace`);
    capture.recordTenantContext(`${product}.tenant`);
    capture.recordMetric(`${product}.metric`);
    capture.recordAudit(`${product}.audit`);
    capture.recordSafeLog(`${product}.safe-log`);
    capture.recordRedaction(`${product}.redaction`);
  },
}));

function createContext(productId: ActiveProductId): ProductConformanceContext {
  return {
    productId,
    manifest: {
      schemaVersion: '1.0.0',
      product: productId,
      kind: 'domain-pack',
      capabilities: [],
      policies: {},
      surfaces: [],
      runtimeServices: [],
    },
    dataAccessContextSnapshots: [
      {
        tenantId: `${productId}:tenant`,
        principalId: `${productId}:principal`,
        correlationId: `${productId}:correlation`,
        auditClassification: `${productId}:audit`,
        dataOwnerScope: `${productId}:owner`,
        idempotencyKey: `${productId}:idempotency`,
        metadata: { productId },
      },
    ],
    idempotencyObservations: [
      {
        operation: `${productId}:write`,
        key: `${productId}:idempotency`,
        fingerprint: `${productId}:fingerprint`,
        status: 'miss',
        replayed: false,
        expired: false,
        principalId: `${productId}:principal`,
        tenantId: `${productId}:tenant`,
        correlationId: `${productId}:correlation`,
      },
    ],
    routeEntitlementPayloads: [
      {
        product: productId,
        principalId: `${productId}:principal`,
        tenantId: `${productId}:tenant`,
        role: 'viewer',
        routes: [
          {
            path: `/${productId}`,
            label: productId,
            actions: [`${productId}:read`],
            cards: [`${productId}:summary`],
          },
        ],
        actions: [
          {
            id: `${productId}:read`,
            label: 'Read',
            routePath: `/${productId}`,
          },
        ],
        cards: [
          {
            id: `${productId}:summary`,
            title: 'Summary',
            routePath: `/${productId}`,
            surface: 'dashboard',
          },
        ],
      },
    ],
    observabilityFlowManifest,
    telemetryFixtures,
  };
}

describe('active product conformance coverage', () => {
  it.each(productIds)('runs the Kernel conformance suite for %s', async (productId) => {
    const result = await runProductConformanceSuite(
      kernelProductConformanceSuite,
      createContext(productId),
    );

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });
});
