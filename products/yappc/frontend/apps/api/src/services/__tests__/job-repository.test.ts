import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../database/client', () => ({
  getPrismaClient: vi.fn(),
}));

type SourceImportJob = import('../job-repository').SourceImportJob;

function createJob(): Omit<SourceImportJob, 'id' | 'createdAt' | 'updatedAt'> {
  return {
    status: 'VALIDATING',
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
    sourceType: 'github',
    source: 'ghatana/example-repo',
    percentComplete: 0,
    currentStep: 'validate_scope',
    steps: [],
  };
}

describe('DatabaseJobRepository', () => {
  afterEach(() => {
    delete process.env.NODE_ENV;
    delete process.env.DATABASE_URL;
    delete process.env.YAPPC_SOURCE_IMPORT_JOB_BACKEND;
    delete process.env.SOURCE_IMPORT_JOB_BACKEND;
  });

  it('creates and reads jobs through the database adapter', async () => {
    const { DatabaseJobRepository } = await import('../job-repository');
    const executeRaw = vi.fn().mockResolvedValue(undefined);
    const queryRaw = vi
      .fn()
      .mockResolvedValueOnce([
        {
          id: 'source-import-existing',
          job_data: {
            ...createJob(),
            id: 'source-import-existing',
            createdAt: '2026-05-15T00:00:00.000Z',
            updatedAt: '2026-05-15T00:00:00.000Z',
          },
        },
      ]);
    const repo = new DatabaseJobRepository({
      $executeRaw: executeRaw,
      $queryRaw: queryRaw,
    } as never);

    const created = await repo.create(createJob());
    const found = await repo.findById('source-import-existing');

    expect(created.id).toMatch(/^source-import-/);
    expect(executeRaw).toHaveBeenCalled();
    expect(found).toMatchObject({
      id: 'source-import-existing',
      projectId: 'project-1',
      sourceType: 'github',
    });
  });

  it('selects database storage only when explicitly requested or in production with a database URL', async () => {
    const { shouldUseDatabaseJobRepository } = await import('../job-repository');
    process.env.NODE_ENV = 'test';
    process.env.DATABASE_URL = 'postgresql://example';
    expect(shouldUseDatabaseJobRepository()).toBe(false);

    process.env.YAPPC_SOURCE_IMPORT_JOB_BACKEND = 'database';
    expect(shouldUseDatabaseJobRepository()).toBe(true);

    delete process.env.YAPPC_SOURCE_IMPORT_JOB_BACKEND;
    process.env.NODE_ENV = 'production';
    expect(shouldUseDatabaseJobRepository()).toBe(true);

    process.env.SOURCE_IMPORT_JOB_BACKEND = 'file';
    expect(shouldUseDatabaseJobRepository()).toBe(false);
  });
});