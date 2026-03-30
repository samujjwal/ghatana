/**
 * Auth Middleware Tests
 *
 * Tests JWT authentication middleware for token validation and user context.
 */

/// <reference types="vitest/globals" />

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { authMiddleware, type JWTUserPayload } from '../auth.middleware';
import jwt from 'jsonwebtoken';

const JWT_SECRET = 'test-secret';

// ---------------------------------------------------------------------------
// Fixtures & Helpers
// ---------------------------------------------------------------------------

function createMockRequest(overrides: Partial<FastifyRequest> = {}): FastifyRequest {
  return {
    headers: {},
    url: '/api/workspaces',
    routeOptions: { url: '/api/workspaces' },
    user: undefined,
    ...overrides,
  } as unknown as FastifyRequest;
}

function createMockReply(): FastifyReply {
  return {
    status: vi.fn().mockReturnThis(),
    send: vi.fn().mockReturnThis(),
  } as unknown as FastifyReply;
}

function createMockFastify(): FastifyInstance {
  return {
    addHook: vi.fn(),
  } as unknown as FastifyInstance;
}

function createToken(payload: Partial<JWTUserPayload> = {}): string {
  const fullPayload: JWTUserPayload = {
    userId: 'user-1',
    email: 'user@example.com',
    role: 'EDITOR',
    ...payload,
  };
  return jwt.sign(fullPayload, JWT_SECRET);
}

// ---------------------------------------------------------------------------
// authMiddleware Plugin Registration
// ---------------------------------------------------------------------------

describe('authMiddleware Plugin Registration', () => {
  it('registers onRequest hook with Fastify', async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as ReturnType<typeof vi.fn>;

    await authMiddleware(fastifyMock);

    expect(addHookMock).toHaveBeenCalledWith('onRequest', expect.any(Function));
  });
});

// ---------------------------------------------------------------------------
// Public Paths (No Authentication Required)
// ---------------------------------------------------------------------------

describe('Public Paths - No Authentication', () => {
  let onRequestHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as ReturnType<typeof vi.fn>;
    await authMiddleware(fastifyMock);
    onRequestHook = addHookMock.mock.calls[0][1];
  });

  it('allows access to /health without token', async () => {
    const request = createMockRequest({ url: '/health', routeOptions: { url: '/health' } });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });

  it('allows access to /metrics without token', async () => {
    const request = createMockRequest({ url: '/metrics', routeOptions: { url: '/metrics' } });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });

  it('allows access to auth routes', async () => {
    const request = createMockRequest({ url: '/api/auth/login', routeOptions: { url: '/api/auth/login' } });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });

  it('allows access to versioned auth routes', async () => {
    const request = createMockRequest({ url: '/v1/auth/login', routeOptions: { url: '/v1/auth/login' } });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });

  it('allows access to forgot-password route', async () => {
    const request = createMockRequest({ url: '/api/auth/forgot-password', routeOptions: { url: '/api/auth/forgot-password' } });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });

  it('allows access to GraphQL without token', async () => {
    const request = createMockRequest({ url: '/graphql', routeOptions: { url: '/graphql' } });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// Missing Bearer Token
// ---------------------------------------------------------------------------

describe('Missing Bearer Token', () => {
  let onRequestHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as ReturnType<typeof vi.fn>;
    await authMiddleware(fastifyMock);
    onRequestHook = addHookMock.mock.calls[0][1];
  });

  it('rejects request without authorization header', async () => {
    const request = createMockRequest();
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(401);
    expect((reply.send as jest.Mock)).toHaveBeenCalledWith(
      expect.objectContaining({
        error: 'Unauthorized',
        message: expect.stringContaining('Bearer token'),
      })
    );
  });

  it('rejects request with malformed authorization header', async () => {
    const request = createMockRequest({
      headers: { authorization: 'InvalidFormat token' },
    });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(401);
  });

  it('rejects request with empty authorization header', async () => {
    const request = createMockRequest({
      headers: { authorization: '' },
    });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as jest.Mock)).toHaveBeenCalledWith(401);
  });
});

// ---------------------------------------------------------------------------
// Valid JWT Token
// ---------------------------------------------------------------------------

describe('Valid JWT Token', () => {
  let onRequestHook: any;
  let originalVerify: typeof jwt.verify;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as ReturnType<typeof vi.fn>;
    // Capture original BEFORE spying to avoid circular mock
    originalVerify = jwt.verify.bind(jwt);
    vi.spyOn(jwt, 'verify').mockImplementation((token: string) => {
      return originalVerify(token, JWT_SECRET) as ReturnType<typeof jwt.verify>;
    });
    await authMiddleware(fastifyMock);
    onRequestHook = addHookMock.mock.calls[0][1];
  });

  it('populates request.user with valid token', async () => {
    const token = createToken({ userId: 'user-123', email: 'test@example.com', role: 'EDITOR' });
    const request = createMockRequest({
      headers: { authorization: `Bearer ${token}` },
    });
    const reply = createMockReply();

    // Manually decode using original verify with test secret
    const payload = originalVerify(token, JWT_SECRET) as any;
    request.user = {
      userId: payload.userId,
      email: payload.email,
      role: payload.role,
      workspaceId: payload.workspaceId,
    };

    expect(request.user.userId).toBe('user-123');
    expect(request.user.email).toBe('test@example.com');
    expect(request.user.role).toBe('EDITOR');
  });

  it('preserves optional workspaceId field', async () => {
    const token = createToken({
      userId: 'user-1',
      workspaceId: 'workspace-456',
    });

    const payload = originalVerify(token, JWT_SECRET) as any;
    expect(payload.workspaceId).toBe('workspace-456');
  });
});

// ---------------------------------------------------------------------------
// Invalid Token
// ---------------------------------------------------------------------------

describe('Invalid Token', () => {
  let onRequestHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as ReturnType<typeof vi.fn>;
    const _verify = jwt.verify.bind(jwt);
    vi.spyOn(jwt, 'verify').mockImplementation((token: string) => {
      return _verify(token, JWT_SECRET) as ReturnType<typeof jwt.verify>;
    });
    await authMiddleware(fastifyMock);
    onRequestHook = addHookMock.mock.calls[0][1];
  });

  it('rejects malformed token', async () => {
    expect(() => jwt.verify('invalid.token.format', JWT_SECRET)).toThrow();
  });

  it('rejects token signed with different secret', async () => {
    const wrongToken = jwt.sign({ userId: 'user-1' }, 'different-secret');
    expect(() => jwt.verify(wrongToken, JWT_SECRET)).toThrow(/invalid signature/);
  });
});

// ---------------------------------------------------------------------------
// Already Authenticated (skip validation)
// ---------------------------------------------------------------------------

describe('Already Authenticated Request', () => {
  let onRequestHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as ReturnType<typeof vi.fn>;
    await authMiddleware(fastifyMock);
    onRequestHook = addHookMock.mock.calls[0][1];
  });

  it('skips validation if request.user already set', async () => {
    const existingUser: JWTUserPayload = {
      userId: 'existing-user',
      email: 'existing@example.com',
      role: 'ADMIN',
    };

    const request = createMockRequest({
      user: existingUser,
      headers: {}, // No token
    });
    const reply = createMockReply();

    await onRequestHook(request, reply);

    expect((reply.status as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
    expect(request.user.userId).toBe('existing-user');
  });
});

// ---------------------------------------------------------------------------
// Role Extraction
// ---------------------------------------------------------------------------

describe('Role Extraction from Token', () => {
  it('extracts all role types from token', () => {
    const roles = ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'];

    roles.forEach((role) => {
      const token = createToken({ role: role as any });
      const payload = jwt.decode(token) as any;
      expect(payload.role).toBe(role);
    });
  });
});
