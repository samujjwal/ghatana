import { describe, expect, it } from 'vitest';
import {
  assertTelemetryFacets,
  createTelemetryCapture,
  executeTelemetryFlowFixture,
  PRODUCT_TELEMETRY_FLOW_FIXTURES,
  REQUIRED_PRODUCT_TELEMETRY_FACETS,
  type TelemetryFacet,
} from '../index';

const REQUIRED_FACETS: readonly TelemetryFacet[] = [
  'trace',
  'tenantContext',
  'metrics',
  'audit',
  'safeLogging',
  'redaction',
];

describe('telemetry conformance harness', () => {
  it('captures all required observability facets for an executable product flow', async () => {
    const result = await executeTelemetryFlowFixture(
      {
        product: 'flashit',
        flow: 'moment-write',
        execute(capture) {
          capture.recordTrace('moment.write.trace', { correlationId: 'corr-1' });
          capture.recordTenantContext('moment.write.tenant', { tenantId: 'tenant-1' });
          capture.recordMetric('moment.write.duration', { value: 12 });
          capture.recordAudit('moment.write.audit', { action: 'create' });
          capture.recordSafeLog('moment.write.log', { redacted: true });
          capture.recordRedaction('moment.write.redaction', { field: 'contentText' });
        },
      },
      REQUIRED_FACETS,
    );

    expect(result.valid).toBe(true);
    expect(result.missingFacets).toEqual([]);
    expect(result.events).toHaveLength(6);
  });

  it('reports missing facets without throwing so product conformance can explain failures', () => {
    const capture = createTelemetryCapture();
    capture.recordTrace('request.trace');
    capture.recordAudit('request.audit');

    const result = assertTelemetryFacets(capture, REQUIRED_FACETS);

    expect(result.valid).toBe(false);
    expect(result.missingFacets).toEqual(['tenantContext', 'metrics', 'safeLogging', 'redaction']);
  });

  it('rejects unnamed telemetry evidence', () => {
    const capture = createTelemetryCapture();

    expect(() => capture.recordTrace('')).toThrow('Telemetry evidence name must not be blank');
  });

  it('executes required product observability flow fixtures', async () => {
    const requiredFlows = new Set([
      'phr:appointment-create-api',
      'phr:consent-boundary-read',
      'finance:transaction-process',
      'digital-marketing:dmos-api-bootstrap',
      'digital-marketing:kernel-bridge-adapter',
      'flashit:gateway-api-request',
      'flashit:moment-read-write',
    ]);

    for (const fixture of PRODUCT_TELEMETRY_FLOW_FIXTURES) {
      const result = await executeTelemetryFlowFixture(fixture, REQUIRED_PRODUCT_TELEMETRY_FACETS);
      expect(result.valid, `${fixture.product}:${fixture.flow}`).toBe(true);
      requiredFlows.delete(`${fixture.product}:${fixture.flow}`);
    }

    expect([...requiredFlows]).toEqual([]);
  });
});
