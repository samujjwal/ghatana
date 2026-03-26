/**
 * RBAC Middleware Tests
 *
 * Tests role-based access control enforcement via Fastify pre-handlers.
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import { requirePermission, requireRole } from '../rbac.middleware';
import type { UserRole } from '../../services/auth/permissions';

// ---------------------------------------------------------------------------
// Fixtures & Helpers
// ---------------------------------------------------------------------------

function createMockRequest(overrides: Partial<FastifyRequest> = {}): FastifyRequest {
  return {
    user: undefined,
    ...overrides,
  } as unknown as FastifyRequest;
}

function createMockReply(): FastifyReply {
  return {
    status: jest.fn().mockReturnThis(),
    send: jest.fn().mockReturnThis(),
  } as unknown as FastifyReply;
}

function createUserRequest(role: UserRole, userId = 'user-1') {
  return createMockRequest({
    user: {
      userId,
      email: 'test@example.com',
      role,
    },
  });
}

// ---------------------------------------------------------------------------
// requirePermission - Workspace CRUD
// ---------------------------------------------------------------------------

describe('requirePermission - workspace', () => {
  it('allows VIEWER to read workspace', async () => {
    const handler = requirePermission('workspace', 'read');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('denies VIEWER to update workspace', async () => {
    const handler = requirePermission('workspace', 'update');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
    expect((reply.send as jest.Mock)).toHaveBeenCalledWith(
      expect.objectContaining({
        error: 'Forbidden',
      })
    );
  });

  it('denies VIEWER to delete workspace', async () => {
    const handler = requirePermission('workspace', 'delete');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('allows ADMIN to update workspace', async () => {
    const handler = requirePermission('workspace', 'update');
    const request = createUserRequest('ADMIN');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('allows ADMIN to delete workspace', async () => {
    const handler = requirePermission('workspace', 'delete');
    const request = createUserRequest('ADMIN');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// requirePermission - Project CRUD
// ---------------------------------------------------------------------------

describe('requirePermission - project', () => {
  it('allows VIEWER to read project', async () => {
    const handler = requirePermission('project', 'read');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('denies VIEWER to create project', async () => {
    const handler = requirePermission('project', 'create');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('allows EDITOR to create project', async () => {
    const handler = requirePermission('project', 'create');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('denies EDITOR to delete project', async () => {
    const handler = requirePermission('project', 'delete');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('allows ADMIN to delete project', async () => {
    const handler = requirePermission('project', 'delete');
    const request = createUserRequest('ADMIN');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// requirePermission - Canvas CRUD
// ---------------------------------------------------------------------------

describe('requirePermission - canvas', () => {
  it('allows VIEWER to read canvas', async () => {
    const handler = requirePermission('canvas', 'read');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('allows EDITOR to create canvas', async () => {
    const handler = requirePermission('canvas', 'create');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('allows EDITOR to update canvas', async () => {
    const handler = requirePermission('canvas', 'update');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('denies EDITOR to delete canvas', async () => {
    const handler = requirePermission('canvas', 'delete');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });
});

// ---------------------------------------------------------------------------
// requirePermission - Member Management
// ---------------------------------------------------------------------------

describe('requirePermission - member management', () => {
  it('denies VIEWER to manage members', async () => {
    const handler = requirePermission('member', 'manage_members');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('denies EDITOR to manage members', async () => {
    const handler = requirePermission('member', 'manage_members');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('allows ADMIN to manage members', async () => {
    const handler = requirePermission('member', 'manage_members');
    const request = createUserRequest('ADMIN');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('allows OWNER to manage members', async () => {
    const handler = requirePermission('member', 'manage_members');
    const request = createUserRequest('OWNER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// Missing Authentication
// ---------------------------------------------------------------------------

describe('requirePermission - Missing Authentication', () => {
  it('denies unauthenticated request', async () => {
    const handler = requirePermission('workspace', 'read');
    const request = createMockRequest(); // No user
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(401);
    expect((reply.send as jest.Mock)).toHaveBeenCalledWith(
      expect.objectContaining({
        error: 'Unauthorized',
        message: expect.stringContaining('Authentication'),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// requireRole - Role Hierarchy
// ---------------------------------------------------------------------------

describe('requireRole - Role Hierarchy', () => {
  it('allows VIEWER to access VIEWER-level resource', async () => {
    const handler = requireRole('VIEWER');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('allows EDITOR to access VIEWER-level resource', async () => {
    const handler = requireRole('VIEWER');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('denies VIEWER to access EDITOR-level resource', async () => {
    const handler = requireRole('EDITOR');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('allows ADMIN to access any resource', async () => {
    const levels: UserRole[] = ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'];
    const handler = requireRole('ADMIN');

    for (const level of levels) {
      const request = createUserRequest(level);
      const reply = createMockReply();

      await handler(request, reply);

      if (level === 'VIEWER' || level === 'EDITOR') {
        expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
      } else {
        expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
      }

      jest.clearAllMocks();
    }
  });

  it('allows OWNER to access any resource', async () => {
    const handler = requireRole('OWNER');
    const request = createUserRequest('OWNER');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
  });

  it('denies EDITOR to access OWNER-level resource', async () => {
    const handler = requireRole('OWNER');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });

  it('denies ADMIN to access OWNER-level resource', async () => {
    const handler = requireRole('OWNER');
    const request = createUserRequest('ADMIN');
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
  });
});

// ---------------------------------------------------------------------------
// requireRole - Missing Authentication
// ---------------------------------------------------------------------------

describe('requireRole - Missing Authentication', () => {
  it('denies unauthenticated request', async () => {
    const handler = requireRole('VIEWER');
    const request = createMockRequest(); // No user
    const reply = createMockReply();

    await handler(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(401);
  });
});

// ---------------------------------------------------------------------------
// Error Message Quality
// ---------------------------------------------------------------------------

describe('Error Messages', () => {
  it('includes resource and action in permission denial message', async () => {
    const handler = requirePermission('project', 'delete');
    const request = createUserRequest('VIEWER');
    const reply = createMockReply();

    await handler(request, reply);

    const sendArg = (reply.send as jest.Mock).mock.calls[0][0];
    expect(sendArg.message).toContain('project');
    expect(sendArg.message).toContain('delete');
    expect(sendArg.message).toContain('VIEWER');
  });

  it('includes role requirement in role denial message', async () => {
    const handler = requireRole('ADMIN');
    const request = createUserRequest('EDITOR');
    const reply = createMockReply();

    await handler(request, reply);

    const sendArg = (reply.send as jest.Mock).mock.calls[0][0];
    expect(sendArg.message).toContain('ADMIN');
    expect(sendArg.message).toContain('EDITOR');
  });
});

// ---------------------------------------------------------------------------
// All Role Types
// ---------------------------------------------------------------------------

describe('All Role Types - Comprehensive', () => {
  const roles: UserRole[] = ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'];

  it('all roles can read', () => {
    const handler = requirePermission('workspace', 'read');

    roles.forEach((role) => {
      const request = createUserRequest(role);
      const reply = createMockReply();

      handler(request, reply);

      expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
      jest.clearAllMocks();
    });
  });

  it('only ADMIN/OWNER can manage members', () => {
    const handler = requirePermission('member', 'manage_members');

    roles.forEach((role) => {
      const request = createUserRequest(role);
      const reply = createMockReply();

      handler(request, reply);

      if (['ADMIN', 'OWNER'].includes(role)) {
        expect((reply.status as jest.Mock).mock.calls).toHaveLength(0);
      } else {
        expect((reply.status as jest.Mock)).toHaveBeenCalledWith(403);
      }

      jest.clearAllMocks();
    });
  });
});
