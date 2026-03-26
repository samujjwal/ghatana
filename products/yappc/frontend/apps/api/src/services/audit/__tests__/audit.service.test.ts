/**
 * Audit Service Tests
 *
 * Tests the AuditService for logging workspace activities and querying audit entries.
 */

import { AuditService } from '../audit.service';
import type { PrismaClient } from '@prisma/client';
import type { AuditEntryInput } from '../../../types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const NOW = new Date().toISOString();

function makePrismaMock() {
  return {
    auditEntry: {
      create: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
    },
  };
}

function auditEntryRow(overrides: Partial<any> = {}) {
  return {
    id: 'audit-1',
    workspaceId: 'ws-1',
    userId: 'user-1',
    action: 'create',
    resourceType: 'project',
    resourceId: 'proj-1',
    resourceName: 'My Project',
    changes: null,
    ipAddress: '127.0.0.1',
    userAgent: 'Mozilla/5.0',
    createdAt: NOW,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// AuditService.log
// ---------------------------------------------------------------------------

describe('AuditService.log', () => {
  it('creates audit entry with required fields', async () => {
    const prisma = makePrismaMock();
    const input: AuditEntryInput = {
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'My Project',
    };

    prisma.auditEntry.create.mockResolvedValue(auditEntryRow());
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.log(input);

    expect(prisma.auditEntry.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        workspaceId: 'ws-1',
        userId: 'user-1',
        action: 'create',
        resourceType: 'project',
        resourceId: 'proj-1',
        resourceName: 'My Project',
      }),
    });
    expect(result.id).toBeDefined();
  });

  it('includes optional fields when provided', async () => {
    const prisma = makePrismaMock();
    const input: AuditEntryInput = {
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'update',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Design Doc',
      changes: JSON.stringify({ name: 'Old' }, { name: 'New' }),
      ipAddress: '192.168.1.1',
      userAgent: 'Chrome',
    };

    prisma.auditEntry.create.mockResolvedValue(auditEntryRow(input));
    const svc = new AuditService(prisma as unknown as PrismaClient);

    await svc.log(input);

    expect(prisma.auditEntry.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        changes: input.changes,
        ipAddress: '192.168.1.1',
        userAgent: 'Chrome',
      }),
    });
  });

  it('returns audit entry with ISO date', async () => {
    const prisma = makePrismaMock();
    const input: AuditEntryInput = {
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'delete',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'Deleted Project',
    };

    const entry = auditEntryRow();
    prisma.auditEntry.create.mockResolvedValue(entry);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.log(input);

    expect(result.createdAt).toBe(NOW);
  });
});

// ---------------------------------------------------------------------------
// AuditService.getEntriesByWorkspace
// ---------------------------------------------------------------------------

describe('AuditService.getEntriesByWorkspace', () => {
  it('retrieves all entries for a workspace', async () => {
    const prisma = makePrismaMock();
    const entries = [
      auditEntryRow({ id: 'audit-1' }),
      auditEntryRow({ id: 'audit-2', action: 'update' }),
    ];

    prisma.auditEntry.findMany.mockResolvedValue(entries);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.getEntriesByWorkspace('ws-1');

    expect(prisma.auditEntry.findMany).toHaveBeenCalledWith({
      where: { workspaceId: 'ws-1' },
      orderBy: { createdAt: 'desc' },
    });
    expect(result).toHaveLength(2);
    expect(result[0].id).toBe('audit-1');
  });

  it('handles empty result', async () => {
    const prisma = makePrismaMock();
    prisma.auditEntry.findMany.mockResolvedValue([]);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.getEntriesByWorkspace('ws-nonexistent');

    expect(result).toEqual([]);
  });

  it('orders by createdAt descending', async () => {
    const prisma = makePrismaMock();
    const entries = [
      auditEntryRow({ id: 'audit-3', createdAt: '2024-03-03T00:00:00Z' }),
      auditEntryRow({ id: 'audit-2', createdAt: '2024-03-02T00:00:00Z' }),
      auditEntryRow({ id: 'audit-1', createdAt: '2024-03-01T00:00:00Z' }),
    ];

    prisma.auditEntry.findMany.mockResolvedValue(entries);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.getEntriesByWorkspace('ws-1');

    expect(result[0].createdAt).toBe('2024-03-03T00:00:00Z');
    expect(result[2].createdAt).toBe('2024-03-01T00:00:00Z');
  });
});

// ---------------------------------------------------------------------------
// AuditService.getEntriesByUser
// ---------------------------------------------------------------------------

describe('AuditService.getEntriesByUser', () => {
  it('retrieves all entries created by a user', async () => {
    const prisma = makePrismaMock();
    const entries = [
      auditEntryRow({ userId: 'user-1', id: 'audit-1' }),
      auditEntryRow({ userId: 'user-1', id: 'audit-2', action: 'update' }),
    ];

    prisma.auditEntry.findMany.mockResolvedValue(entries);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.getEntriesByUser('user-1');

    expect(prisma.auditEntry.findMany).toHaveBeenCalledWith({
      where: { userId: 'user-1' },
      orderBy: { createdAt: 'desc' },
    });
    expect(result).toHaveLength(2);
  });

  it('handles multiple users independently', async () => {
    const prisma = makePrismaMock();
    prisma.auditEntry.findMany.mockImplementation(({ where }) => {
      if (where.userId === 'user-1') {
        return Promise.resolve([auditEntryRow({ userId: 'user-1' })]);
      }
      if (where.userId === 'user-2') {
        return Promise.resolve([auditEntryRow({ userId: 'user-2' })]);
      }
      return Promise.resolve([]);
    });

    const svc = new AuditService(prisma as unknown as PrismaClient);

    const user1Entries = await svc.getEntriesByUser('user-1');
    const user2Entries = await svc.getEntriesByUser('user-2');

    expect(user1Entries).toHaveLength(1);
    expect(user2Entries).toHaveLength(1);
    expect(user1Entries[0].userId).toBe('user-1');
    expect(user2Entries[0].userId).toBe('user-2');
  });
});

// ---------------------------------------------------------------------------
// AuditService.getEntriesByResource
// ---------------------------------------------------------------------------

describe('AuditService.getEntriesByResource', () => {
  it('retrieves all entries for a specific resource', async () => {
    const prisma = makePrismaMock();
    const entries = [
      auditEntryRow({ resourceId: 'proj-1', id: 'audit-1', action: 'create' }),
      auditEntryRow({ resourceId: 'proj-1', id: 'audit-2', action: 'update' }),
    ];

    prisma.auditEntry.findMany.mockResolvedValue(entries);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.getEntriesByResource('proj-1');

    expect(prisma.auditEntry.findMany).toHaveBeenCalledWith({
      where: { resourceId: 'proj-1' },
      orderBy: { createdAt: 'desc' },
    });
    expect(result).toHaveLength(2);
  });

  it('shows resource lifecycle (create → update → delete)', async () => {
    const prisma = makePrismaMock();
    const entries = [
      auditEntryRow({
        resourceId: 'proj-1',
        action: 'delete',
        createdAt: '2024-03-03T00:00:00Z',
      }),
      auditEntryRow({
        resourceId: 'proj-1',
        action: 'update',
        createdAt: '2024-03-02T00:00:00Z',
      }),
      auditEntryRow({
        resourceId: 'proj-1',
        action: 'create',
        createdAt: '2024-03-01T00:00:00Z',
      }),
    ];

    prisma.auditEntry.findMany.mockResolvedValue(entries);
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.getEntriesByResource('proj-1');

    expect(result[0].action).toBe('delete');
    expect(result[1].action).toBe('update');
    expect(result[2].action).toBe('create');
  });
});

// ---------------------------------------------------------------------------
// AuditService.log action types
// ---------------------------------------------------------------------------

describe('AuditService.log various actions', () => {
  it('logs create action', async () => {
    const prisma = makePrismaMock();
    prisma.auditEntry.create.mockResolvedValue(
      auditEntryRow({ action: 'create' })
    );
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'New Project',
    });

    expect(result.action).toBe('create');
  });

  it('logs update action', async () => {
    const prisma = makePrismaMock();
    prisma.auditEntry.create.mockResolvedValue(
      auditEntryRow({ action: 'update' })
    );
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'update',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'Updated Project',
    });

    expect(result.action).toBe('update');
  });

  it('logs delete action', async () => {
    const prisma = makePrismaMock();
    prisma.auditEntry.create.mockResolvedValue(
      auditEntryRow({ action: 'delete' })
    );
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'delete',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'Deleted Project',
    });

    expect(result.action).toBe('delete');
  });

  it('logs access action for read operations', async () => {
    const prisma = makePrismaMock();
    prisma.auditEntry.create.mockResolvedValue(
      auditEntryRow({ action: 'access' })
    );
    const svc = new AuditService(prisma as unknown as PrismaClient);

    const result = await svc.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'access',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Design Doc',
    });

    expect(result.action).toBe('access');
  });
});

// ---------------------------------------------------------------------------
// AuditService resource types
// ---------------------------------------------------------------------------

describe('AuditService.log various resource types', () => {
  const actions = ['create', 'update', 'delete', 'access'];

  ['workspace', 'project', 'canvas', 'page', 'member'].forEach(
    (resourceType) => {
      actions.forEach((action) => {
        it(`logs ${action} on ${resourceType}`, async () => {
          const prisma = makePrismaMock();
          prisma.auditEntry.create.mockResolvedValue(
            auditEntryRow({ resourceType, action })
          );
          const svc = new AuditService(prisma as unknown as PrismaClient);

          const result = await svc.log({
            workspaceId: 'ws-1',
            userId: 'user-1',
            action: action as any,
            resourceType: resourceType as any,
            resourceId: `${resourceType}-1`,
            resourceName: `Test ${resourceType}`,
          });

          expect(result.resourceType).toBe(resourceType);
          expect(result.action).toBe(action);
        });
      });
    }
  );
});
