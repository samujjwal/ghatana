/**
 * Audit Middleware Tests
 *
 * Tests automatic HTTP-level audit logging middleware.
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { auditMiddleware } from '../audit.middleware';

// Mock the audit service
vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: jest.fn(),
}));

import { getAuditService } from '../../services/audit/audit.service';

const mockGetAuditService = getAuditService as jest.MockedFunction<
  typeof getAuditService
>;

// ---------------------------------------------------------------------------
// Fixtures & Helpers
// ---------------------------------------------------------------------------

function createMockAuditService() {
  return {
    log: jest.fn().mockResolvedValue({ id: 'entry-1' }),
  };
}

function createMockFastify(): FastifyInstance {
  return {
    addHook: jest.fn(),
  } as unknown as FastifyInstance;
}

function createMockRequest(
  overrides: Partial<FastifyRequest> = {}
): FastifyRequest {
  return {
    url: '/api/workspaces',
    method: 'GET',
    ip: '127.0.0.1',
    headers: {
      'user-agent': 'Mozilla/5.0 Test Browser',
    },
    body: undefined,
    user: undefined,
    ...overrides,
  } as unknown as FastifyRequest;
}

function createMockReply(): FastifyReply {
  return {
    statusCode: 200,
  } as unknown as FastifyReply;
}

// ---------------------------------------------------------------------------
// Plugin Registration
// ---------------------------------------------------------------------------

describe('auditMiddleware Plugin Registration', () => {
  it('registers onResponse hook with Fastify', async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;

    await auditMiddleware(fastifyMock);

    expect(addHookMock).toHaveBeenCalledWith(
      'onResponse',
      expect.any(Function)
    );
  });
});

// ---------------------------------------------------------------------------
// Excluded Paths (No Audit)
// ---------------------------------------------------------------------------

describe('Excluded Paths - No Audit Logging', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('does not audit /health requests', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({ url: '/health', method: 'GET' });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).not.toHaveBeenCalled();
  });

  it('does not audit /metrics requests', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({ url: '/metrics', method: 'GET' });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).not.toHaveBeenCalled();
  });

  it('does not audit /graphiql requests', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({ url: '/graphiql', method: 'GET' });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).not.toHaveBeenCalled();
  });

  it('does not audit /status requests', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({ url: '/status', method: 'GET' });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// HTTP Method Action Inference
// ---------------------------------------------------------------------------

describe('HTTP Method to Action Mapping', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('maps GET to READ action', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces/ws-1',
      method: 'GET',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: expect.stringContaining('READ'),
      })
    );
  });

  it('maps POST to CREATE action', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'POST',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: expect.stringContaining('CREATE'),
      })
    );
  });

  it('maps PUT to UPDATE action', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces/ws-1',
      method: 'PUT',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: expect.stringContaining('UPDATE'),
      })
    );
  });

  it('maps PATCH to UPDATE action', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces/ws-1',
      method: 'PATCH',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: expect.stringContaining('UPDATE'),
      })
    );
  });

  it('maps DELETE to DELETE action', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces/ws-1',
      method: 'DELETE',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: expect.stringContaining('DELETE'),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// User Identification
// ---------------------------------------------------------------------------

describe('User Identification', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('uses userId from authenticated user', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      user: { userId: 'specific-user-id', role: 'EDITOR' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actor: 'specific-user-id',
      })
    );
  });

  it('falls back to IP address if no user', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      ip: '192.168.1.1',
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actor: '192.168.1.1',
      })
    );
  });

  it('falls back to anonymous if no user or IP', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      user: undefined,
      ip: undefined,
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actor: 'anonymous',
      })
    );
  });

  it('includes user role if available', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      user: { userId: 'user-1', role: 'ADMIN' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorRole: 'ADMIN',
      })
    );
  });

  it('defaults role to anonymous if not specified', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      user: undefined,
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorRole: 'anonymous',
      })
    );
  });
});

// ---------------------------------------------------------------------------
// Fire-and-Forget (Does Not Block Response)
// ---------------------------------------------------------------------------

describe('Fire-and-Forget Behavior', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('does not throw if service.log fails', async () => {
    const serviceMock = createMockAuditService();
    serviceMock.log.mockRejectedValue(new Error('DB connection failed'));
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    // Should not throw
    await expect(onResponseHook(request, reply)).resolves.not.toThrow();
  });

  it('completes immediately regardless of service latency', async () => {
    const serviceMock = createMockAuditService();
    serviceMock.log.mockImplementation(
      () =>
        new Promise((resolve) =>
          setTimeout(() => resolve({ id: 'entry-1' }), 1000)
        )
    );
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces',
      method: 'GET',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    const start = Date.now();
    await onResponseHook(request, reply);
    const elapsed = Date.now() - start;

    // Should complete before the 1000ms timer
    expect(elapsed).toBeLessThan(500);
  });
});

// ---------------------------------------------------------------------------
// Query Parameter Stripping
// ---------------------------------------------------------------------------

describe('Query Parameter Stripping in URL', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('strips query parameters from URL for resource identification', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const request = createMockRequest({
      url: '/api/workspaces?sort=name&limit=10',
      method: 'GET',
      user: { userId: 'user-1' },
    });
    const reply = createMockReply();

    await onResponseHook(request, reply);

    expect(serviceMock.log).toHaveBeenCalledWith(
      expect.not.objectContaining({
        resource: expect.stringContaining('?'),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// Resource Type Inference
// ---------------------------------------------------------------------------

describe('Resource Type Inference', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('infers resource from second path segment', async () => {
    const serviceMock = createMockAuditService();
    mockGetAuditService.mockReturnValue(serviceMock as any);

    const testCases = [
      { url: '/api/workspaces', expected: 'workspaces' },
      { url: '/api/projects', expected: 'projects' },
      { url: '/api/canvas', expected: 'canvas' },
      { url: '/api/audit', expected: 'audit' },
    ];

    for (const testCase of testCases) {
      jest.clearAllMocks();

      const request = createMockRequest({
        url: testCase.url,
        method: 'GET',
        user: { userId: 'user-1' },
      });
      const reply = createMockReply();

      await onResponseHook(request, reply);

      expect(serviceMock.log).toHaveBeenCalledWith(
        expect.objectContaining({
          action: expect.stringContaining(testCase.expected),
        })
      );
    }
  });
});

// ---------------------------------------------------------------------------
// All Request Methods
// ---------------------------------------------------------------------------

describe('All HTTP Methods Covered', () => {
  let onResponseHook: any;

  beforeAll(async () => {
    const fastifyMock = createMockFastify();
    const addHookMock = fastifyMock.addHook as jest.Mock;
    await auditMiddleware(fastifyMock);
    onResponseHook = addHookMock.mock.calls[0][1];
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('logs all supported HTTP methods', async () => {
    const methods = [
      'GET',
      'POST',
      'PUT',
      'PATCH',
      'DELETE',
      'HEAD',
      'OPTIONS',
    ];

    for (const method of methods) {
      jest.clearAllMocks();
      const serviceMock = createMockAuditService();
      mockGetAuditService.mockReturnValue(serviceMock as any);

      const request = createMockRequest({
        url: '/api/workspaces',
        method,
        user: { userId: 'user-1' },
      });
      const reply = createMockReply();

      await onResponseHook(request, reply);

      expect(serviceMock.log).toHaveBeenCalled();
    }
  });
});
