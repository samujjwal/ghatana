import { describe, expect, it } from 'vitest';
import {
  defineProductConformanceSuite,
  kernelProductConformanceSuite,
  runProductConformanceSuite,
  type ProductConformanceCheck,
} from '../index';
import type { ProductTelemetryFlowFixture } from '../../telemetry/index';

const validManifest = {
  schemaVersion: '1.0.0',
  product: 'flashit',
  kind: 'domain-pack',
  capabilities: [],
  policies: {},
  surfaces: [],
  runtimeServices: [],
} satisfies Readonly<Record<string, unknown>>;

const completeTelemetryFixture: ProductTelemetryFlowFixture = {
  product: 'flashit',
  flow: 'moment-write',
  execute(capture) {
    capture.recordTrace('moment.write.trace');
    capture.recordTenantContext('moment.write.tenant');
    capture.recordMetric('moment.write.metric');
    capture.recordAudit('moment.write.audit');
    capture.recordSafeLog('moment.write.safe-log');
    capture.recordRedaction('moment.write.redaction');
  },
};

const validObservabilityFlowManifest = {
  schemaVersion: '1.0.0',
  requiredFacets: ['trace', 'tenantContext', 'metrics', 'audit', 'safeLogging', 'redaction'],
  flows: [
    {
      product: 'flashit',
      flow: 'moment-write',
      kind: 'api',
      facets: ['trace', 'tenantContext', 'metrics', 'audit', 'safeLogging', 'redaction'],
      evidence: [
        {
          type: 'behavior',
          file: 'platform/typescript/product-conformance/src/telemetry/__tests__/telemetry.test.ts',
          requiredFacets: ['trace', 'metrics', 'audit'],
        },
      ],
    },
  ],
} satisfies Readonly<Record<string, unknown>>;

describe('product conformance suite', () => {
  it('passes when manifest and executable telemetry fixtures satisfy Kernel checks', async () => {
    const result = await runProductConformanceSuite(kernelProductConformanceSuite, {
      productId: 'flashit',
      manifest: validManifest,
      dataAccessContextSnapshots: [
        {
          tenantId: 'tenant-1',
          principalId: 'principal-1',
          correlationId: 'corr-1',
          auditClassification: 'PERSONAL_MEMORY_WRITE',
          dataOwnerScope: 'flashit:moment:m1',
          idempotencyKey: 'idem-1',
          metadata: { surface: 'web' },
        },
      ],
      idempotencyObservations: [
        {
          operation: 'flashit_moment_create',
          key: 'idem-1',
          fingerprint: 'fingerprint-a',
          status: 'miss',
          replayed: false,
          expired: false,
          principalId: 'principal-1',
          tenantId: 'tenant-1',
          correlationId: 'corr-1',
        },
      ],
      routeEntitlementPayloads: [
        {
          product: 'flashit',
          principalId: 'principal-1',
          tenantId: 'tenant-1',
          role: 'member',
          routes: [{ path: '/', label: 'Home' }],
          actions: [{ id: 'view-dashboard', label: 'View dashboard', routePath: '/' }],
          cards: [{ id: 'summary', title: 'Summary', routePath: '/', surface: 'dashboard' }],
        },
      ],
      observabilityFlowManifest: validObservabilityFlowManifest,
      telemetryFixtures: [completeTelemetryFixture],
    });

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.results.map((check) => check.id)).toEqual([
      'manifest-envelope',
      'data-access-context-snapshots',
      'idempotency-observations',
      'route-entitlement-payloads',
      'observability-flow-manifest',
      'telemetry-fixtures',
    ]);
  });

  it('reports actionable manifest and telemetry fixture failures', async () => {
    const result = await runProductConformanceSuite(kernelProductConformanceSuite, {
      productId: 'phr',
      manifest: { product: 'flashit' },
      dataAccessContextSnapshots: [{ tenantId: '', principalId: 'principal-1' }],
      dataAccessValidationOptions: { requireCorrelationId: true },
      idempotencyObservations: [{ operation: '', key: '', status: 'completed', replayed: false, expired: false }],
      routeEntitlementPayloads: [{ product: 'flashit', role: '', routes: [] }],
      telemetryFixtures: [],
    });

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('manifest.schemaVersion must be a non-empty string');
    expect(result.errors).toContain('manifest.product must be phr');
    expect(result.errors).toContain('snapshot[0]: dataAccess.tenantId must be a non-empty string');
    expect(result.errors).toContain('snapshot[0]: dataAccess.correlationId must be a non-empty string');
    expect(result.errors).toContain('phr: idempotency[0].operation must be a non-empty string');
    expect(result.errors).toContain('phr: idempotency[0].completed observation must set replayed=true');
    expect(result.errors).toContain('payload[0]: entitlement.product must be phr');
    expect(result.errors).toContain('observability flow manifest is required');
    expect(result.errors).toContain('no telemetry fixtures registered for product phr');
  });

  it('turns thrown checks into failed conformance results instead of crashing the suite', async () => {
    const throwingCheck: ProductConformanceCheck = {
      id: 'throws',
      area: 'authorization',
      description: 'Throwing check',
      run() {
        throw new Error('adapter failed');
      },
    };

    const result = await runProductConformanceSuite(
      defineProductConformanceSuite({ id: 'throwing-suite', checks: [throwingCheck] }),
      { productId: 'flashit' },
    );

    expect(result.valid).toBe(false);
    expect(result.errors).toEqual(['Throwing check threw: adapter failed']);
  });

  it('rejects duplicate check ids at suite definition time', () => {
    const check: ProductConformanceCheck = {
      id: 'same',
      area: 'manifest',
      description: 'first',
      run() {
        return { id: 'same', area: 'manifest', valid: true, errors: [], warnings: [] };
      },
    };

    expect(() => defineProductConformanceSuite({ id: 'invalid', checks: [check, check] })).toThrow(
      'duplicate check ids: same',
    );
  });
});
