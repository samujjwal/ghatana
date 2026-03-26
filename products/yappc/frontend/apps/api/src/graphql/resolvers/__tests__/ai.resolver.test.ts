/**
 * AI Resolver Tests
 *
 * Tests the GraphQL resolvers for AI features including copilot, insights,
 * predictions, and anomaly handling.
 */

import { aiResolvers } from '../ai.resolver';
import type { AIService } from '../../../services/ai/ai.service';
import type { PrismaClient } from '@prisma/client';

// Mock the AI service and Prisma
jest.mock('../../../services/ai/ai.service', () => ({
  createAIService: jest.fn(),
}));

jest.mock('../../../database/client', () => ({
  getPrismaClient: jest.fn(),
}));

import { createAIService } from '../../../services/ai/ai.service';
import { getPrismaClient } from '../../../database/client';

const mockCreateAIService = createAIService as jest.MockedFunction<
  typeof createAIService
>;
const mockGetPrismaClient = getPrismaClient as jest.MockedFunction<
  typeof getPrismaClient
>;

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const NOW = new Date().toISOString();

function makeAIServiceMock(): jest.Mocked<Partial<AIService>> {
  return {
    getInsights: jest.fn(),
    getPredictions: jest.fn(),
    getAnomalies: jest.fn(),
    getCopilotSession: jest.fn(),
    getMetricsSummary: jest.fn(),
    acknowledgeAnomaly: jest.fn(),
    sendCopilotMessage: jest.fn(),
    trackMetric: jest.fn(),
  };
}

function makePrismaMock(): jest.Mocked<Partial<PrismaClient>> {
  return {
    anomalyAlert: {
      update: jest.fn(),
    },
    copilotSession: {
      findUnique: jest.fn(),
      update: jest.fn(),
    },
  } as unknown as jest.Mocked<Partial<PrismaClient>>;
}

function insightRow(overrides: Partial<any> = {}) {
  return {
    id: 'insight-1',
    projectId: 'proj-1',
    title: 'Performance Issue',
    description: 'CPU usage elevated',
    severity: 'high',
    createdAt: NOW,
    ...overrides,
  };
}

function predictionRow(overrides: Partial<any> = {}) {
  return {
    id: 'pred-1',
    projectId: 'proj-1',
    description: 'Project will miss deadline',
    confidence: 0.85,
    recommendation: 'Increase team size',
    createdAt: NOW,
    ...overrides,
  };
}

function anomalyRow(overrides: Partial<any> = {}) {
  return {
    id: 'anom-1',
    projectId: 'proj-1',
    anomallyType: 'ERROR_SPIKE',
    severity: 'critical',
    description: 'Error rate spike detected',
    acknowledged: false,
    falsePositive: false,
    createdAt: NOW,
    ...overrides,
  };
}

function copilotSessionRow(overrides: Partial<any> = {}) {
  return {
    id: 'session-1',
    userId: 'user-1',
    projectId: 'proj-1',
    messages: [],
    satisfactionRating: null,
    createdAt: NOW,
    endedAt: null,
    ...overrides,
  };
}

function contextWithAuth(userId = 'user-1', permissions: string[] = []) {
  return { userId, permissions };
}

function contextWithAdminAuth(userId = 'admin-user') {
  return { userId, permissions: ['admin'] };
}

function contextWithoutAuth() {
  return {};
}

// ---------------------------------------------------------------------------
// aiInsights Query
// ---------------------------------------------------------------------------

describe('aiResolvers.Query.aiInsights', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('retrieves insights with default filter', async () => {
    const serviceMock = makeAIServiceMock();
    const insights = [
      insightRow({ id: 'insight-1', severity: 'high' }),
      insightRow({ id: 'insight-2', severity: 'medium' }),
    ];
    serviceMock.getInsights?.mockResolvedValue(insights);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.aiInsights(
      undefined,
      {},
      contextWithAuth()
    );

    expect(serviceMock.getInsights).toHaveBeenCalledWith({});
    expect(result).toHaveLength(2);
  });

  it('retrieves insights with custom filter', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getInsights?.mockResolvedValue([]);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const filter = { severity: 'critical', projectId: 'proj-1' };
    await aiResolvers.Query.aiInsights(
      undefined,
      { filter },
      contextWithAuth()
    );

    expect(serviceMock.getInsights).toHaveBeenCalledWith(filter);
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.aiInsights(undefined, {}, contextWithoutAuth())
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.getInsights).not.toHaveBeenCalled();
  });

  it('returns empty array if no insights', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getInsights?.mockResolvedValue([]);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.aiInsights(
      undefined,
      {},
      contextWithAuth()
    );

    expect(result).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// predictions Query
// ---------------------------------------------------------------------------

describe('aiResolvers.Query.predictions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('retrieves predictions with default filter', async () => {
    const serviceMock = makeAIServiceMock();
    const predictions = [
      predictionRow({ confidence: 0.9 }),
      predictionRow({ confidence: 0.75 }),
    ];
    serviceMock.getPredictions?.mockResolvedValue(predictions);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.predictions(
      undefined,
      {},
      contextWithAuth()
    );

    expect(serviceMock.getPredictions).toHaveBeenCalledWith({});
    expect(result).toHaveLength(2);
  });

  it('retrieves predictions with custom filter', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getPredictions?.mockResolvedValue([]);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const filter = { minConfidence: 0.8 };
    await aiResolvers.Query.predictions(undefined, { filter }, contextWithAuth());

    expect(serviceMock.getPredictions).toHaveBeenCalledWith(filter);
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.predictions(undefined, {}, contextWithoutAuth())
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.getPredictions).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// anomalies Query
// ---------------------------------------------------------------------------

describe('aiResolvers.Query.anomalies', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('retrieves anomalies with default filter', async () => {
    const serviceMock = makeAIServiceMock();
    const anomalies = [
      anomalyRow({ id: 'anom-1', severity: 'critical' }),
      anomalyRow({ id: 'anom-2', severity: 'warning' }),
    ];
    serviceMock.getAnomalies?.mockResolvedValue(anomalies);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.anomalies(
      undefined,
      {},
      contextWithAuth()
    );

    expect(serviceMock.getAnomalies).toHaveBeenCalledWith({});
    expect(result).toHaveLength(2);
  });

  it('retrieves anomalies with custom filter', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getAnomalies?.mockResolvedValue([]);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const filter = { severity: 'critical', acknowledged: false };
    await aiResolvers.Query.anomalies(undefined, { filter }, contextWithAuth());

    expect(serviceMock.getAnomalies).toHaveBeenCalledWith(filter);
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.anomalies(undefined, {}, contextWithoutAuth())
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.getAnomalies).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// copilotSession Query
// ---------------------------------------------------------------------------

describe('aiResolvers.Query.copilotSession', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('retrieves copilot session for authenticated user', async () => {
    const serviceMock = makeAIServiceMock();
    const session = copilotSessionRow({ userId: 'user-1' });
    serviceMock.getCopilotSession?.mockResolvedValue(session);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.copilotSession(
      undefined,
      { sessionId: 'session-1' },
      contextWithAuth('user-1')
    );

    expect(serviceMock.getCopilotSession).toHaveBeenCalledWith('session-1');
    expect(result.userId).toBe('user-1');
  });

  it('returns null if session does not exist', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getCopilotSession?.mockResolvedValue(null);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.copilotSession(
      undefined,
      { sessionId: 'nonexistent' },
      contextWithAuth()
    );

    expect(result).toBeNull();
  });

  it('throws if session belongs to different user', async () => {
    const serviceMock = makeAIServiceMock();
    const session = copilotSessionRow({ userId: 'different-user' });
    serviceMock.getCopilotSession?.mockResolvedValue(session);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.copilotSession(
        undefined,
        { sessionId: 'session-1' },
        contextWithAuth('user-1')
      )
    ).rejects.toThrow('Forbidden');
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.copilotSession(
        undefined,
        { sessionId: 'session-1' },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.getCopilotSession).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// aiMetricsSummary Query
// ---------------------------------------------------------------------------

describe('aiResolvers.Query.aiMetricsSummary', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('retrieves metrics summary for admin user', async () => {
    const serviceMock = makeAIServiceMock();
    const summary = { totalInsights: 10, avgConfidence: 0.87 };
    serviceMock.getMetricsSummary?.mockResolvedValue(summary);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Query.aiMetricsSummary(
      undefined,
      {},
      contextWithAdminAuth()
    );

    expect(result).toEqual(summary);
    expect(serviceMock.getMetricsSummary).toHaveBeenCalled();
  });

  it('uses default timeRangeHours of 24', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getMetricsSummary?.mockResolvedValue({});
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await aiResolvers.Query.aiMetricsSummary(
      undefined,
      {},
      contextWithAdminAuth()
    );

    const callArgs = serviceMock.getMetricsSummary?.mock.calls[0][0] as any;
    expect(callArgs.start instanceof Date).toBe(true);
    expect(callArgs.end instanceof Date).toBe(true);
  });

  it('uses custom timeRangeHours', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.getMetricsSummary?.mockResolvedValue({});
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const beforeCall = Date.now();
    await aiResolvers.Query.aiMetricsSummary(
      undefined,
      { timeRangeHours: 48 },
      contextWithAdminAuth()
    );
    const afterCall = Date.now();

    const callArgs = serviceMock.getMetricsSummary?.mock.calls[0][0] as any;
    const diffMs = callArgs.end.getTime() - callArgs.start.getTime();
    const diffHours = diffMs / (60 * 60 * 1000);

    expect(diffHours).toBeCloseTo(48, 0);
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.aiMetricsSummary(undefined, {}, contextWithoutAuth())
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.getMetricsSummary).not.toHaveBeenCalled();
  });

  it('throws if user is not admin', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Query.aiMetricsSummary(
        undefined,
        {},
        contextWithAuth('user-1', [])
      )
    ).rejects.toThrow('Forbidden: Admin access required');

    expect(serviceMock.getMetricsSummary).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// acknowledgeAnomaly Mutation
// ---------------------------------------------------------------------------

describe('aiResolvers.Mutation.acknowledgeAnomaly', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('acknowledges anomaly with user context', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.acknowledgeAnomaly?.mockResolvedValue(
      anomalyRow({ acknowledged: true })
    );
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await aiResolvers.Mutation.acknowledgeAnomaly(
      undefined,
      { anomalyId: 'anom-1' },
      contextWithAuth('user-1')
    );

    expect(serviceMock.acknowledgeAnomaly).toHaveBeenCalledWith(
      'anom-1',
      'user-1'
    );
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Mutation.acknowledgeAnomaly(
        undefined,
        { anomalyId: 'anom-1' },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.acknowledgeAnomaly).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// sendCopilotMessage Mutation
// ---------------------------------------------------------------------------

describe('aiResolvers.Mutation.sendCopilotMessage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('sends copilot message and tracks metrics', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.sendCopilotMessage?.mockResolvedValue({
      sessionId: 'session-1',
      response: 'Here is the analysis...',
      tokensUsed: 150,
    });
    serviceMock.trackMetric?.mockResolvedValue(undefined);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const result = await aiResolvers.Mutation.sendCopilotMessage(
      undefined,
      {
        input: {
          sessionId: 'session-1',
          message: 'Analyze this code',
        },
      },
      contextWithAuth('user-1')
    );

    expect(serviceMock.sendCopilotMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        sessionId: 'session-1',
        message: 'Analyze this code',
        userId: 'user-1',
      })
    );

    expect(serviceMock.trackMetric).toHaveBeenCalledWith(
      expect.objectContaining({
        agentName: 'CopilotAgent',
        userId: 'user-1',
        sessionId: 'session-1',
      })
    );

    expect(result.sessionId).toBe('session-1');
    expect(result.tokensUsed).toBe(150);
  });

  it('includes context in message if provided', async () => {
    const serviceMock = makeAIServiceMock();
    serviceMock.sendCopilotMessage?.mockResolvedValue({
      sessionId: 'session-1',
      response: 'Response',
      tokensUsed: 100,
    });
    serviceMock.trackMetric?.mockResolvedValue(undefined);
    mockCreateAIService.mockReturnValue(serviceMock as any);

    const context = { projectId: 'proj-1', fileId: 'file-1' };
    await aiResolvers.Mutation.sendCopilotMessage(
      undefined,
      {
        input: {
          sessionId: 'session-1',
          message: 'Help',
          context,
        },
      },
      contextWithAuth('user-1')
    );

    expect(serviceMock.sendCopilotMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        context,
      })
    );
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeAIServiceMock();
    mockCreateAIService.mockReturnValue(serviceMock as any);

    await expect(
      aiResolvers.Mutation.sendCopilotMessage(
        undefined,
        {
          input: {
            sessionId: 'session-1',
            message: 'Test',
          },
        },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Unauthorized');

    expect(serviceMock.sendCopilotMessage).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// markAnomalyFalsePositive Mutation
// ---------------------------------------------------------------------------

describe('aiResolvers.Mutation.markAnomalyFalsePositive', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('marks anomaly as false positive', async () => {
    const prismaMock = makePrismaMock();
    prismaMock.anomalyAlert?.update.mockResolvedValue(
      anomalyRow({ falsePositive: true, acknowledged: true })
    );
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await aiResolvers.Mutation.markAnomalyFalsePositive(
      undefined,
      { anomalyId: 'anom-1' },
      contextWithAuth('user-1')
    );

    expect(prismaMock.anomalyAlert?.update).toHaveBeenCalledWith({
      where: { id: 'anom-1' },
      data: expect.objectContaining({
        falsePositive: true,
        acknowledged: true,
        acknowledgedBy: 'user-1',
      }),
    });
  });

  it('sets resolvedAt timestamp', async () => {
    const prismaMock = makePrismaMock();
    const now = new Date();
    prismaMock.anomalyAlert?.update.mockResolvedValue(
      anomalyRow({ resolvedAt: now })
    );
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await aiResolvers.Mutation.markAnomalyFalsePositive(
      undefined,
      { anomalyId: 'anom-1' },
      contextWithAuth('user-1')
    );

    const callData = (prismaMock.anomalyAlert?.update as jest.Mock).mock
      .calls[0][0].data;
    expect(callData.resolvedAt instanceof Date).toBe(true);
  });

  it('throws if not authenticated', async () => {
    const prismaMock = makePrismaMock();
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await expect(
      aiResolvers.Mutation.markAnomalyFalsePositive(
        undefined,
        { anomalyId: 'anom-1' },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Unauthorized');

    expect(prismaMock.anomalyAlert?.update).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// rateCopilotSession Mutation
// ---------------------------------------------------------------------------

describe('aiResolvers.Mutation.rateCopilotSession', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('rates copilot session with valid rating', async () => {
    const prismaMock = makePrismaMock();
    prismaMock.copilotSession?.findUnique.mockResolvedValue(
      copilotSessionRow({ userId: 'user-1' })
    );
    prismaMock.copilotSession?.update.mockResolvedValue(
      copilotSessionRow({ satisfactionRating: 5 })
    );
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await aiResolvers.Mutation.rateCopilotSession(
      undefined,
      { sessionId: 'session-1', rating: 5 },
      contextWithAuth('user-1')
    );

    expect(prismaMock.copilotSession?.update).toHaveBeenCalledWith({
      where: { id: 'session-1' },
      data: expect.objectContaining({
        satisfactionRating: 5,
        endedAt: expect.any(Date),
      }),
    });
  });

  it('accepts all valid ratings 1-5', async () => {
    const prismaMock = makePrismaMock();
    prismaMock.copilotSession?.findUnique.mockResolvedValue(
      copilotSessionRow({ userId: 'user-1' })
    );
    prismaMock.copilotSession?.update.mockResolvedValue(
      copilotSessionRow()
    );
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    for (let rating = 1; rating <= 5; rating++) {
      jest.clearAllMocks();
      prismaMock.copilotSession?.findUnique.mockResolvedValue(
        copilotSessionRow({ userId: 'user-1' })
      );
      prismaMock.copilotSession?.update.mockResolvedValue(
        copilotSessionRow()
      );

      await aiResolvers.Mutation.rateCopilotSession(
        undefined,
        { sessionId: 'session-1', rating },
        contextWithAuth('user-1')
      );

      expect(prismaMock.copilotSession?.update).toHaveBeenCalled();
    }
  });

  it('rejects rating below 1', async () => {
    const prismaMock = makePrismaMock();
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await expect(
      aiResolvers.Mutation.rateCopilotSession(
        undefined,
        { sessionId: 'session-1', rating: 0 },
        contextWithAuth('user-1')
      )
    ).rejects.toThrow('Rating must be between 1 and 5');
  });

  it('rejects rating above 5', async () => {
    const prismaMock = makePrismaMock();
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await expect(
      aiResolvers.Mutation.rateCopilotSession(
        undefined,
        { sessionId: 'session-1', rating: 6 },
        contextWithAuth('user-1')
      )
    ).rejects.toThrow('Rating must be between 1 and 5');
  });

  it('throws if session belongs to different user', async () => {
    const prismaMock = makePrismaMock();
    prismaMock.copilotSession?.findUnique.mockResolvedValue(
      copilotSessionRow({ userId: 'different-user' })
    );
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await expect(
      aiResolvers.Mutation.rateCopilotSession(
        undefined,
        { sessionId: 'session-1', rating: 5 },
        contextWithAuth('user-1')
      )
    ).rejects.toThrow('Session not found or unauthorized');
  });

  it('throws if session not found', async () => {
    const prismaMock = makePrismaMock();
    prismaMock.copilotSession?.findUnique.mockResolvedValue(null);
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await expect(
      aiResolvers.Mutation.rateCopilotSession(
        undefined,
        { sessionId: 'nonexistent', rating: 5 },
        contextWithAuth('user-1')
      )
    ).rejects.toThrow('Session not found or unauthorized');
  });

  it('throws if not authenticated', async () => {
    const prismaMock = makePrismaMock();
    mockGetPrismaClient.mockReturnValue(prismaMock as any);

    await expect(
      aiResolvers.Mutation.rateCopilotSession(
        undefined,
        { sessionId: 'session-1', rating: 5 },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Unauthorized');

    expect(prismaMock.copilotSession?.findUnique).not.toHaveBeenCalled();
  });
});
