/**
 * SessionService Tests
 *
 * Unit tests for durable session management with Prisma mocked.
 */

import { SessionService } from '../session.service';

// ---------------------------------------------------------------------------
// Prisma Mock Factory
// ---------------------------------------------------------------------------

function makePrismaMock() {
  return {
    userSession: {
      create: jest.fn(),
      findUnique: jest.fn(),
      updateMany: jest.fn(),
      deleteMany: jest.fn(),
    },
  };
}

const FUTURE = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000); // 7 days from now
const PAST = new Date(Date.now() - 24 * 60 * 60 * 1000); // 1 day ago

function makeSession(
  overrides: Partial<{
    id: string;
    userId: string;
    workspaceId: string | null;
    sessionToken: string;
    expiresAt: Date;
    revokedAt: Date | null;
    createdAt: Date;
    updatedAt: Date;
  }> = {}
) {
  return {
    id: 'sess-001',
    userId: 'user-001',
    workspaceId: null,
    sessionToken: 'tok-abc123',
    expiresAt: FUTURE,
    revokedAt: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// create
// ---------------------------------------------------------------------------

describe('SessionService.create', () => {
  it('inserts a session row and returns a 64-char hex session token', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.create.mockResolvedValue(undefined);

    const svc = new SessionService(prisma as never);
    const token = await svc.create({ userId: 'user-001', expiresAt: FUTURE });

    expect(typeof token).toBe('string');
    expect(token).toHaveLength(64); // 32 bytes → 64 hex chars
    expect(/^[0-9a-f]+$/.test(token)).toBe(true);
    expect(prisma.userSession.create).toHaveBeenCalledOnce();
    expect(prisma.userSession.create.mock.calls[0][0].data.sessionToken).toBe(
      token
    );
  });

  it('passes workspaceId when provided', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.create.mockResolvedValue(undefined);

    const svc = new SessionService(prisma as never);
    await svc.create({
      userId: 'user-001',
      workspaceId: 'ws-001',
      expiresAt: FUTURE,
    });

    expect(prisma.userSession.create.mock.calls[0][0].data.workspaceId).toBe(
      'ws-001'
    );
  });

  it('stores null workspaceId when not provided', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.create.mockResolvedValue(undefined);

    const svc = new SessionService(prisma as never);
    await svc.create({ userId: 'user-001', expiresAt: FUTURE });

    expect(
      prisma.userSession.create.mock.calls[0][0].data.workspaceId
    ).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// findByToken
// ---------------------------------------------------------------------------

describe('SessionService.findByToken', () => {
  it('returns the session row when found', async () => {
    const session = makeSession();
    const prisma = makePrismaMock();
    prisma.userSession.findUnique.mockResolvedValue(session);

    const svc = new SessionService(prisma as never);
    const result = await svc.findByToken('tok-abc123');

    expect(result).toEqual(session);
  });

  it('returns null when session not found', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.findUnique.mockResolvedValue(null);

    const svc = new SessionService(prisma as never);
    expect(await svc.findByToken('no-such-token')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// validateSession
// ---------------------------------------------------------------------------

describe('SessionService.validateSession', () => {
  it('returns session when valid (not revoked, not expired)', async () => {
    const session = makeSession();
    const prisma = makePrismaMock();
    prisma.userSession.findUnique.mockResolvedValue(session);

    const svc = new SessionService(prisma as never);
    const result = await svc.validateSession('tok-abc123');

    expect(result).toEqual(session);
  });

  it("throws when session doesn't exist", async () => {
    const prisma = makePrismaMock();
    prisma.userSession.findUnique.mockResolvedValue(null);

    const svc = new SessionService(prisma as never);
    await expect(svc.validateSession('phantom-token')).rejects.toThrow(
      'not found'
    );
  });

  it('throws when session has been revoked', async () => {
    const session = makeSession({ revokedAt: PAST });
    const prisma = makePrismaMock();
    prisma.userSession.findUnique.mockResolvedValue(session);

    const svc = new SessionService(prisma as never);
    await expect(svc.validateSession('tok-abc123')).rejects.toThrow('revoked');
  });

  it('throws when session has expired', async () => {
    const session = makeSession({ expiresAt: PAST });
    const prisma = makePrismaMock();
    prisma.userSession.findUnique.mockResolvedValue(session);

    const svc = new SessionService(prisma as never);
    await expect(svc.validateSession('tok-abc123')).rejects.toThrow('expired');
  });
});

// ---------------------------------------------------------------------------
// revoke
// ---------------------------------------------------------------------------

describe('SessionService.revoke', () => {
  it('calls updateMany with revokedAt set to now', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.updateMany.mockResolvedValue({ count: 1 });

    const svc = new SessionService(prisma as never);
    await svc.revoke('tok-to-revoke');

    expect(prisma.userSession.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          sessionToken: 'tok-to-revoke',
          revokedAt: null,
        }),
        data: expect.objectContaining({ revokedAt: expect.any(Date) }),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// revokeAll
// ---------------------------------------------------------------------------

describe('SessionService.revokeAll', () => {
  it('revokes all active sessions for user and returns count', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.updateMany.mockResolvedValue({ count: 3 });

    const svc = new SessionService(prisma as never);
    const count = await svc.revokeAll('user-001');

    expect(count).toBe(3);
    expect(prisma.userSession.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ userId: 'user-001', revokedAt: null }),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// cleanup
// ---------------------------------------------------------------------------

describe('SessionService.cleanup', () => {
  it('deletes expired and revoked sessions and returns count', async () => {
    const prisma = makePrismaMock();
    prisma.userSession.deleteMany.mockResolvedValue({ count: 5 });

    const svc = new SessionService(prisma as never);
    const count = await svc.cleanup();

    expect(count).toBe(5);
    expect(prisma.userSession.deleteMany).toHaveBeenCalledOnce();
  });
});
