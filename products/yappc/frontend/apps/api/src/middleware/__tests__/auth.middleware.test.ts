import { afterEach, describe, expect, it, vi } from 'vitest';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import jwt from 'jsonwebtoken';
import { authMiddleware, type JWTUserPayload } from '../auth.middleware';

const TEST_ACCESS_SECRET = 'test-access-secret';

type OnRequestHook = (
  request: FastifyRequest,
  reply: FastifyReply
) => Promise<unknown>;

function createMockRequest(
  overrides: Partial<FastifyRequest> = {}
): FastifyRequest {
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

  return jwt.sign(fullPayload, TEST_ACCESS_SECRET);
}

async function registerAuthHook(): Promise<OnRequestHook> {
  process.env.JWT_ACCESS_SECRET = TEST_ACCESS_SECRET;
  const fastify = createMockFastify();
  const addHookMock = fastify.addHook as ReturnType<typeof vi.fn>;

  await authMiddleware(fastify);

  return addHookMock.mock.calls[0][1] as OnRequestHook;
}

afterEach(() => {
  delete process.env.JWT_ACCESS_SECRET;
  vi.restoreAllMocks();
});

describe('authMiddleware', () => {
  it('fails fast when JWT access secret is missing', async () => {
    const fastify = createMockFastify();

    await expect(authMiddleware(fastify)).rejects.toThrow(
      'JWT_ACCESS_SECRET is required'
    );
  });

  it('registers the onRequest hook', async () => {
    process.env.JWT_ACCESS_SECRET = TEST_ACCESS_SECRET;
    const fastify = createMockFastify();
    const addHookMock = fastify.addHook as ReturnType<typeof vi.fn>;

    await authMiddleware(fastify);

    expect(addHookMock).toHaveBeenCalledWith('onRequest', expect.any(Function));
  });

  it('allows public paths without authentication', async () => {
    const hook = await registerAuthHook();
    const request = createMockRequest({
      url: '/health',
      routeOptions: { url: '/health' },
    });
    const reply = createMockReply();

    await hook(request, reply);

    expect(reply.status).not.toHaveBeenCalled();
  });

  it('rejects protected requests without a bearer token', async () => {
    const hook = await registerAuthHook();
    const request = createMockRequest();
    const reply = createMockReply();

    await hook(request, reply);

    expect(reply.status).toHaveBeenCalledWith(401);
    expect(reply.send).toHaveBeenCalledWith({
      error: 'Unauthorized',
      message: 'Missing Bearer token',
    });
  });

  it('populates request.user from a valid access token', async () => {
    const hook = await registerAuthHook();
    const request = createMockRequest({
      headers: {
        authorization: `Bearer ${createToken({
          userId: 'user-123',
          email: 'editor@yappc.local',
          role: 'ADMIN',
          workspaceId: 'workspace-9',
        })}`,
      },
    });
    const reply = createMockReply();

    await hook(request, reply);

    expect(reply.status).not.toHaveBeenCalled();
    expect(request.user).toEqual({
      userId: 'user-123',
      email: 'editor@yappc.local',
      role: 'ADMIN',
      workspaceId: 'workspace-9',
    });
  });

  it('rejects tokens signed with a different secret', async () => {
    const hook = await registerAuthHook();
    const request = createMockRequest({
      headers: {
        authorization: `Bearer ${jwt.sign({ userId: 'user-1' }, 'wrong-secret')}`,
      },
    });
    const reply = createMockReply();

    await hook(request, reply);

    expect(reply.status).toHaveBeenCalledWith(401);
    expect(reply.send).toHaveBeenCalledWith({
      error: 'Unauthorized',
      message: 'Invalid token',
    });
  });

  it('skips token verification when request.user is already present', async () => {
    const hook = await registerAuthHook();
    const verifySpy = vi.spyOn(jwt, 'verify');
    const request = createMockRequest({
      user: {
        userId: 'existing-user',
        email: 'existing@yappc.local',
        role: 'ADMIN',
      },
    });
    const reply = createMockReply();

    await hook(request, reply);

    expect(reply.status).not.toHaveBeenCalled();
    expect(verifySpy).not.toHaveBeenCalled();
    expect(request.user?.userId).toBe('existing-user');
  });
});
