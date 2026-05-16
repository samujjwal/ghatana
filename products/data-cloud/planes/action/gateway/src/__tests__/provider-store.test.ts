import { describe, expect, it } from 'vitest';
import { InMemoryProviderStore, type ProviderRecord } from '../provider-store.js';

const SCOPE_A = {
  tenantId: 'tenant-a',
  workspaceId: 'workspace-a',
  projectId: 'project-a',
} as const;

const SCOPE_B = {
  tenantId: 'tenant-a',
  workspaceId: 'workspace-b',
  projectId: 'project-a',
} as const;

function createRecord(overrides: Partial<ProviderRecord> = {}): ProviderRecord {
  return {
    id: overrides.id ?? 'record-1',
    tenantId: overrides.tenantId ?? SCOPE_A.tenantId,
    workspaceId: overrides.workspaceId ?? SCOPE_A.workspaceId,
    projectId: overrides.projectId ?? SCOPE_A.projectId,
    providerType: overrides.providerType ?? 'events',
    providerRef: overrides.providerRef ?? 'ref-1',
    data: overrides.data ?? { productUnitId: 'product-unit-1', runId: 'run-1' },
    createdAt: overrides.createdAt ?? '2026-01-01T00:00:00.000Z',
    createdBy: overrides.createdBy ?? 'user-1',
    privacyClassification: overrides.privacyClassification,
    expiresAt: overrides.expiresAt,
  };
}

describe('InMemoryProviderStore', () => {
  it('filters records by tenant, workspace, and project scope', async () => {
    const store = new InMemoryProviderStore();
    await store.save(createRecord({ providerRef: 'ref-a' }));
    await store.save(createRecord({ providerRef: 'ref-b', workspaceId: SCOPE_B.workspaceId }));

    await expect(store.findByRef(SCOPE_A, 'ref-a')).resolves.toMatchObject({ providerRef: 'ref-a' });
    await expect(store.findByRef(SCOPE_B, 'ref-a')).resolves.toBeNull();
    await expect(store.listByProviderType(SCOPE_A, 'events', { productUnitId: 'product-unit-1' }, 10)).resolves.toHaveLength(1);
    await expect(store.listByProviderType(SCOPE_B, 'events', { productUnitId: 'product-unit-1' }, 10)).resolves.toHaveLength(1);
  });

  it('returns newest matching records first', async () => {
    const store = new InMemoryProviderStore();
    await store.save(createRecord({ providerRef: 'ref-old', createdAt: '2026-01-01T00:00:00.000Z' }));
    await store.save(createRecord({ providerRef: 'ref-new', createdAt: '2026-01-02T00:00:00.000Z' }));

    const records = await store.listByProviderType(SCOPE_A, 'events', {}, 10);

    expect(records.map((record) => record.providerRef)).toEqual(['ref-new', 'ref-old']);
  });
});
