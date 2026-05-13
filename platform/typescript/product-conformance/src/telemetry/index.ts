export type TelemetryFacet = 'trace' | 'tenantContext' | 'metrics' | 'audit' | 'safeLogging' | 'redaction';

export interface TelemetryEvidence {
  readonly facet: TelemetryFacet;
  readonly name: string;
  readonly attributes?: Readonly<Record<string, string | number | boolean>>;
}

export interface TelemetryCapture {
  readonly events: readonly TelemetryEvidence[];
  record(event: TelemetryEvidence): void;
  recordTrace(name: string, attributes?: Readonly<Record<string, string | number | boolean>>): void;
  recordMetric(name: string, attributes?: Readonly<Record<string, string | number | boolean>>): void;
  recordAudit(name: string, attributes?: Readonly<Record<string, string | number | boolean>>): void;
  recordSafeLog(name: string, attributes?: Readonly<Record<string, string | number | boolean>>): void;
  recordRedaction(name: string, attributes?: Readonly<Record<string, string | number | boolean>>): void;
  recordTenantContext(name: string, attributes?: Readonly<Record<string, string | number | boolean>>): void;
}

export interface TelemetryAssertionResult {
  readonly valid: boolean;
  readonly missingFacets: readonly TelemetryFacet[];
  readonly events: readonly TelemetryEvidence[];
}

export interface ProductTelemetryFlowFixture {
  readonly product: string;
  readonly flow: string;
  execute(capture: TelemetryCapture): Promise<void> | void;
}

export const REQUIRED_PRODUCT_TELEMETRY_FACETS: readonly TelemetryFacet[] = [
  'trace',
  'tenantContext',
  'metrics',
  'audit',
  'safeLogging',
  'redaction',
] as const;

export const PRODUCT_TELEMETRY_FLOW_FIXTURES: readonly ProductTelemetryFlowFixture[] = [
  {
    product: 'phr',
    flow: 'appointment-create-api',
    execute(capture) {
      capture.recordTrace('phr.appointment.create.trace', { correlationId: 'phr-corr-1' });
      capture.recordTenantContext('phr.appointment.create.tenant', { tenantId: 'phr-tenant-1' });
      capture.recordMetric('phr.appointment.create.duration', { value: 18 });
      capture.recordAudit('phr.appointment.create.audit', { action: 'APPOINTMENT_CREATE' });
      capture.recordSafeLog('phr.appointment.create.log', { redacted: true });
      capture.recordRedaction('phr.appointment.create.redaction', { field: 'patientId' });
    },
  },
  {
    product: 'phr',
    flow: 'consent-boundary-read',
    execute(capture) {
      capture.recordTrace('phr.consent.boundary.read.trace', { correlationId: 'phr-consent-corr-1' });
      capture.recordTenantContext('phr.consent.boundary.read.tenant', { tenantId: 'phr-tenant-1' });
      capture.recordMetric('phr.consent.boundary.read.duration', { value: 9 });
      capture.recordAudit('phr.consent.boundary.read.audit', { action: 'PHR_CONSENT_BOUNDARY_READ' });
      capture.recordSafeLog('phr.consent.boundary.read.log', { redacted: true });
      capture.recordRedaction('phr.consent.boundary.read.redaction', { field: 'recordId' });
    },
  },
  {
    product: 'finance',
    flow: 'transaction-process',
    execute(capture) {
      capture.recordTrace('finance.transaction.process.trace', { correlationId: 'fin-corr-1' });
      capture.recordTenantContext('finance.transaction.process.tenant', { tenantId: 'finance-tenant-1' });
      capture.recordMetric('finance.transaction.process.duration', { value: 24 });
      capture.recordAudit('finance.transaction.process.audit', { action: 'finance:process' });
      capture.recordSafeLog('finance.transaction.process.log', { redacted: true });
      capture.recordRedaction('finance.transaction.process.redaction', { field: 'accountId' });
    },
  },
  {
    product: 'digital-marketing',
    flow: 'dmos-api-bootstrap',
    execute(capture) {
      capture.recordTrace('dmos.api.bootstrap.trace', { correlationId: 'dmos-corr-1' });
      capture.recordTenantContext('dmos.api.bootstrap.tenant', { tenantId: 'dmos-tenant-1' });
      capture.recordMetric('dmos.api.bootstrap.duration', { value: 31 });
      capture.recordAudit('dmos.api.bootstrap.audit', { action: 'digital-marketing:bootstrap' });
      capture.recordSafeLog('dmos.api.bootstrap.log', { redacted: true });
      capture.recordRedaction('dmos.api.bootstrap.redaction', { field: 'workspaceId' });
    },
  },
  {
    product: 'digital-marketing',
    flow: 'kernel-bridge-adapter',
    execute(capture) {
      capture.recordTrace('dmos.kernel.bridge.trace', { correlationId: 'dmos-bridge-corr-1' });
      capture.recordTenantContext('dmos.kernel.bridge.tenant', { tenantId: 'dmos-tenant-1' });
      capture.recordMetric('dmos.kernel.bridge.duration', { value: 17 });
      capture.recordAudit('dmos.kernel.bridge.audit', { action: 'digital-marketing:bridge' });
      capture.recordSafeLog('dmos.kernel.bridge.log', { redacted: true });
      capture.recordRedaction('dmos.kernel.bridge.redaction', { field: 'workspaceId' });
    },
  },
  {
    product: 'flashit',
    flow: 'gateway-api-request',
    execute(capture) {
      capture.recordTrace('flashit.gateway.request.trace', { correlationId: 'flashit-gateway-corr-1' });
      capture.recordTenantContext('flashit.gateway.request.tenant', { tenantId: 'flashit-tenant-1' });
      capture.recordMetric('flashit.gateway.request.duration', { value: 14 });
      capture.recordAudit('flashit.gateway.request.audit', { action: 'flashit:request' });
      capture.recordSafeLog('flashit.gateway.request.log', { redacted: true });
      capture.recordRedaction('flashit.gateway.request.redaction', { field: 'errorMessage' });
    },
  },
  {
    product: 'flashit',
    flow: 'moment-read-write',
    execute(capture) {
      capture.recordTrace('flashit.moment.write.trace', { correlationId: 'flashit-corr-1' });
      capture.recordTenantContext('flashit.moment.write.tenant', { tenantId: 'flashit-tenant-1' });
      capture.recordMetric('flashit.moment.write.duration', { value: 12 });
      capture.recordAudit('flashit.moment.write.audit', { action: 'write' });
      capture.recordSafeLog('flashit.moment.write.log', { redacted: true });
      capture.recordRedaction('flashit.moment.write.redaction', { field: 'contentText' });
    },
  },
] as const;

export function createTelemetryCapture(): TelemetryCapture {
  const events: TelemetryEvidence[] = [];
  const capture: TelemetryCapture = {
    get events() {
      return Object.freeze([...events]);
    },
    record(event) {
      events.push(freezeEvent(event));
    },
    recordTrace(name, attributes) {
      capture.record({ facet: 'trace', name, ...(attributes ? { attributes } : {}) });
    },
    recordMetric(name, attributes) {
      capture.record({ facet: 'metrics', name, ...(attributes ? { attributes } : {}) });
    },
    recordAudit(name, attributes) {
      capture.record({ facet: 'audit', name, ...(attributes ? { attributes } : {}) });
    },
    recordSafeLog(name, attributes) {
      capture.record({ facet: 'safeLogging', name, ...(attributes ? { attributes } : {}) });
    },
    recordRedaction(name, attributes) {
      capture.record({ facet: 'redaction', name, ...(attributes ? { attributes } : {}) });
    },
    recordTenantContext(name, attributes) {
      capture.record({ facet: 'tenantContext', name, ...(attributes ? { attributes } : {}) });
    },
  };
  return capture;
}

export function assertTelemetryFacets(
  capture: Pick<TelemetryCapture, 'events'>,
  requiredFacets: readonly TelemetryFacet[],
): TelemetryAssertionResult {
  const observed = new Set(capture.events.map((event) => event.facet));
  const missingFacets = requiredFacets.filter((facet) => !observed.has(facet));
  return {
    valid: missingFacets.length === 0,
    missingFacets,
    events: capture.events,
  };
}

export async function executeTelemetryFlowFixture(
  fixture: ProductTelemetryFlowFixture,
  requiredFacets: readonly TelemetryFacet[],
): Promise<TelemetryAssertionResult> {
  const capture = createTelemetryCapture();
  await fixture.execute(capture);
  return assertTelemetryFacets(capture, requiredFacets);
}

function freezeEvent(event: TelemetryEvidence): TelemetryEvidence {
  if (!event.name || event.name.trim().length === 0) {
    throw new Error('Telemetry evidence name must not be blank');
  }
  return Object.freeze({
    facet: event.facet,
    name: event.name,
    ...(event.attributes ? { attributes: Object.freeze({ ...event.attributes }) } : {}),
  });
}
