/**
 * RBACService Tests
 *
 * Unit tests for RBAC permission evaluation with Prisma mocked.
 */

import { RBACService } from '../rbac.service';
import type { AccessCheckInput } from '../rbac.service';

// ---------------------------------------------------------------------------
// Prisma Mock Factory
// ---------------------------------------------------------------------------

function makePrismaMock() {
  return {
    workspaceMember: {
      findUnique: jest.fn(),
    },
    user: {
      findUnique: jest.fn(),
    },
  };
}

// ---------------------------------------------------------------------------
// resolveRole
// ---------------------------------------------------------------------------

describe('RBACService.resolveRole', () => {
  it('returns workspace member role when workspace context provided', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue({
      role: 'EDITOR',
    });
    const svc = new RBACService(prisma as never);

    const role = await svc.resolveRole('user-1', 'ws-1');
    expect(role).toBe('EDITOR');
  });

  it('returns null when user is not a workspace member', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue(null);
    const svc = new RBACService(prisma as never);

    const role = await svc.resolveRole('user-1', 'ws-1');
    expect(role).toBeNull();
  });

  it('returns user system role when no workspace context', async () => {
    const prisma = makePrismaMock();
    prisma.user.findUnique.mockResolvedValue({ role: 'ADMIN' });
    const svc = new RBACService(prisma as never);

    const role = await svc.resolveRole('user-1');
    expect(role).toBe('ADMIN');
  });

  it('returns null when user not found and no workspace context', async () => {
    const prisma = makePrismaMock();
    prisma.user.findUnique.mockResolvedValue(null);
    const svc = new RBACService(prisma as never);

    const role = await svc.resolveRole('missing');
    expect(role).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// check
// ---------------------------------------------------------------------------

describe('RBACService.check', () => {
  it('allows EDITOR to read project', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue({ role: 'EDITOR' });
    const svc = new RBACService(prisma as never);

    const result = await svc.check({
      userId: 'user-1',
      workspaceId: 'ws-1',
      resource: 'project',
      action: 'read',
    });
    expect(result.allowed).toBe(true);
    expect(result.role).toBe('EDITOR');
  });

  it('allows EDITOR to create project', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue({ role: 'EDITOR' });
    const svc = new RBACService(prisma as never);

    const result = await svc.check({
      userId: 'user-1',
      workspaceId: 'ws-1',
      resource: 'project',
      action: 'create',
    });
    expect(result.allowed).toBe(true);
  });

  it('denies VIEWER to delete workspace', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue({ role: 'VIEWER' });
    const svc = new RBACService(prisma as never);

    const result = await svc.check({
      userId: 'user-1',
      workspaceId: 'ws-1',
      resource: 'workspace',
      action: 'delete',
    });
    expect(result.allowed).toBe(false);
  });

  it('allows ADMIN to manage members', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue({ role: 'ADMIN' });
    const svc = new RBACService(prisma as never);

    const result = await svc.check({
      userId: 'user-1',
      workspaceId: 'ws-1',
      resource: 'member',
      action: 'manage_members',
    });
    expect(result.allowed).toBe(true);
  });

  it('returns explicit reason when role is null', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue(null);
    const svc = new RBACService(prisma as never);

    const result = await svc.check({
      userId: 'user-1',
      workspaceId: 'ws-1',
      resource: 'project',
      action: 'read',
    });
    expect(result.allowed).toBe(false);
    expect(result.reason).toBeDefined();
  });
});
