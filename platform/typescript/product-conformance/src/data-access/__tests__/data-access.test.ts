import { describe, expect, it } from 'vitest';
import { validateDataAccessContextSnapshot } from '../index';

describe('data access context validator', () => {
  it('accepts a complete product data-access snapshot', () => {
    const result = validateDataAccessContextSnapshot(
      {
        tenantId: 'tenant-1',
        principalId: 'principal-1',
        correlationId: 'corr-1',
        auditClassification: 'PERSONAL_MEMORY_WRITE',
        dataOwnerScope: 'flashit:moment:m1',
        idempotencyKey: 'idem-1',
        metadata: { surface: 'web', retryCount: 0, highRisk: false },
      },
      {
        requireCorrelationId: true,
        requireAuditClassification: true,
        requireDataOwnerScope: true,
        requireIdempotencyKey: true,
      },
    );

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.snapshot?.metadata).toEqual({ surface: 'web', retryCount: 0, highRisk: false });
  });

  it('reports missing required fields and unsafe metadata values', () => {
    const result = validateDataAccessContextSnapshot(
      {
        tenantId: '',
        principalId: 'principal-1',
        correlationId: 123,
        metadata: { ok: true, unsafe: { nested: true }, '': 'blank-key' },
      },
      {
        requireCorrelationId: true,
        requireAuditClassification: true,
        requireDataOwnerScope: true,
        requireIdempotencyKey: true,
      },
    );

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('dataAccess.tenantId must be a non-empty string');
    expect(result.errors).toContain('dataAccess.correlationId must be a string when present');
    expect(result.errors).toContain('dataAccess.auditClassification must be a non-empty string');
    expect(result.errors).toContain('dataAccess.dataOwnerScope must be a non-empty string');
    expect(result.errors).toContain('dataAccess.idempotencyKey must be a non-empty string');
    expect(result.errors).toContain('dataAccess.metadata.unsafe must be a string, number, or boolean');
    expect(result.errors).toContain('dataAccess.metadata keys must be non-empty strings');
  });

  it('allows read-only contexts to omit mutation-only fields when not required', () => {
    const result = validateDataAccessContextSnapshot({
      tenantId: 'tenant-1',
      principalId: 'principal-1',
    });

    expect(result.valid).toBe(true);
  });
});
