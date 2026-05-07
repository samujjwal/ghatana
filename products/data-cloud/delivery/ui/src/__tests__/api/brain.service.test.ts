import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AUTONOMY_POLICY_UPDATE_BOUNDARY_MESSAGE,
  BRAIN_MEMORY_STORE_BOUNDARY_MESSAGE,
  BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE,
} from '@/lib/runtime-boundaries';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import { brainService } from '../../api/brain.service';

describe('brainService autonomy boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('maps autonomy logs from the canonical route into sidebar actions', async () => {
    mockApiClient.get.mockResolvedValue({
      logs: [
        {
          id: 'log-1',
          actionType: 'ingestion',
          tenantId: 'tenant-a',
          level: 'NOTIFY',
          decision: 'ALLOWED',
          confidence: 0.91,
          context: { source: 'contract-test' },
          timestamp: '2026-04-14T12:00:00Z',
        },
        {
          id: 'log-2',
          actionType: 'security',
          tenantId: 'tenant-a',
          level: 'SUGGEST',
          decision: 'ADVISORY',
          confidence: 0.42,
          context: {},
          timestamp: '2026-04-14T12:05:00Z',
        },
      ],
      count: 2,
      globalOverride: 'NONE',
      timestamp: '2026-04-14T12:06:00Z',
    });

    const actions = await brainService.getAutonomyTimeline('ingestion', 10);

    expect(mockApiClient.get).toHaveBeenCalledWith('/autonomy/logs');
    expect(actions).toHaveLength(1);
    expect(actions[0]).toMatchObject({
      id: 'log-1',
      domain: 'ingestion',
      action: 'ALLOWED (NOTIFY)',
      status: 'SUCCESS',
      confidence: 0.91,
    });
  });

  it('parses global autonomy override status from the canonical route', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      globalOverride: 'CONFIRM',
      shutoffActive: false,
      domainCount: 4,
    });

    const status = await brainService.getGlobalAutonomyLevel();

    expect(mockApiClient.get).toHaveBeenCalledWith('/autonomy/level');
    expect(status).toEqual({
      globalOverride: 'CONFIRM',
      shutoffActive: false,
      domainCount: 4,
    });
  });

  it('parses global autonomy override updates through the shared request and response contracts', async () => {
    mockApiClient.put.mockResolvedValueOnce({
      globalLevel: 'NOTIFY',
      affectedDomains: 6,
      timestamp: '2026-04-15T12:20:00Z',
      reason: 'Emergency review mode',
    });

    const response = await brainService.setGlobalAutonomyLevel('NOTIFY', 'Emergency review mode');

    expect(mockApiClient.put).toHaveBeenCalledWith('/autonomy/level', {
      level: 'NOTIFY',
      reason: 'Emergency review mode',
    });
    expect(response).toEqual({
      globalLevel: 'NOTIFY',
      affectedDomains: 6,
      timestamp: '2026-04-15T12:20:00Z',
      reason: 'Emergency review mode',
    });
  });

  it('maps domain autonomy state from the canonical route into the UI control model', async () => {
    mockApiClient.get.mockResolvedValue({
      domain: 'optimization',
      state: {
        actionType: 'optimization',
        tenantId: 'tenant-a',
        currentLevel: 'NOTIFY',
        effectiveMaxLevel: 'AUTONOMOUS',
        confidence: 0.73,
        lastActionAt: '2026-04-14T12:10:00Z',
      },
      timestamp: '2026-04-14T12:11:00Z',
    });

    const state = await brainService.getAutonomyState('optimization');

    expect(mockApiClient.get).toHaveBeenCalledWith('/autonomy/domains/optimization');
    expect(state).toEqual({
      domain: 'optimization',
      mode: 'CHATTY',
      enabled: true,
      confidenceThreshold: 0.73,
      lastAction: '2026-04-14T12:10:00Z',
    });
  });

  it('fails explicitly for unsupported domain policy updates', async () => {
    await expect(
      brainService.updateAutonomyPolicy('security', { mode: 'ADVISORY' })
    ).rejects.toThrow(AUTONOMY_POLICY_UPDATE_BOUNDARY_MESSAGE);
  });

  it('maps memory recall onto the canonical root memory plane', async () => {
    mockApiClient.get.mockResolvedValue({
      items: [
        {
          id: 'mem-1',
          tenantId: 'tenant-a',
          agentId: 'brain-ui',
          type: 'SEMANTIC',
          content: 'Customer prefers morning batch windows.',
          tags: ['ops'],
          salience: 0.82,
          createdAt: '2026-04-14T12:15:00Z',
          metadata: { source: 'contract-test' },
        },
      ],
      total: 1,
      tenantId: 'tenant-a',
      agentId: 'ALL',
      type: 'ALL',
      query: 'morning batch',
      timestamp: '2026-04-14T12:16:00Z',
    });

    const recalls = await brainService.recallMemory('morning batch', 'SEMANTIC');

    expect(mockApiClient.get).toHaveBeenCalledWith('/memory', {
      params: {
        query: 'morning batch',
        type: 'semantic',
      },
    });
    expect(recalls[0]).toEqual({
      id: 'mem-1',
      timestamp: '2026-04-14T12:15:00Z',
      tier: 'SEMANTIC',
      similarity: 0.82,
      content: 'Customer prefers morning batch windows.',
      context: { source: 'contract-test' },
    });
  });

  it('fails explicitly for unsupported memory store calls without agent identity', async () => {
    await expect(
      brainService.storeMemory({ content: 'remember this' })
    ).rejects.toThrow(BRAIN_MEMORY_STORE_BOUNDARY_MESSAGE);
  });

  it('maps canonical brain stats into the UI brain stats model', async () => {
    mockApiClient.get.mockResolvedValue({
      totalRecordsProcessed: 482,
      activePatterns: 7,
      activeRules: 12,
      hotTierRecords: 43,
      warmTierRecords: 128,
      avgProcessingTimeMs: 18.4,
      uptimeSeconds: 86400,
      tenantId: 'tenant-a',
      timestamp: '2026-04-14T12:20:00Z',
    });

    const stats = await brainService.getBrainStats();

    expect(mockApiClient.get).toHaveBeenCalledWith('/brain/stats');
    expect(stats).toEqual({
      totalRecordsProcessed: 482,
      activePatterns: 7,
      activeRules: 12,
      hotTierRecords: 43,
      warmTierRecords: 128,
      avgProcessingTimeMs: 18.4,
      uptimeSeconds: 86400,
      tenantId: 'tenant-a',
      timestamp: '2026-04-14T12:20:00Z',
    });
  });

  it('treats workspace status as supported without inventing spotlight items', async () => {
    mockApiClient.get.mockResolvedValue({
      status: 'active',
      brainId: 'brain-1',
      note: 'Detailed spotlight items available via GET /api/v1/brain/stats',
      timestamp: '2026-04-14T12:21:00Z',
    });

    const spotlight = await brainService.getSpotlight();

    expect(mockApiClient.get).toHaveBeenCalledWith('/brain/workspace');
    expect(spotlight).toEqual([]);
  });

  it('maps canonical attention thresholds into the frontend threshold model', async () => {
    mockApiClient.get.mockResolvedValue({
      elevationThreshold: 0.71,
      emergencyThreshold: 0.93,
      salienceThreshold: 0.64,
      totalProcessed: 100,
      elevatedCount: 15,
      emergencyCount: 2,
      elevationRate: 0.15,
      timestamp: '2026-04-14T12:22:00Z',
    });

    const thresholds = await brainService.getAttentionThresholds();

    expect(mockApiClient.get).toHaveBeenCalledWith('/brain/attention/thresholds');
    expect(thresholds).toEqual({
      elevation: 0.71,
      emergency: 0.93,
      salience: 0.64,
    });
  });

  it('maps canonical pattern list and match envelopes into UI pattern models', async () => {
    mockApiClient.get.mockResolvedValue({
      patterns: [
        {
          id: 'pattern-1',
          name: 'Morning Spike',
          type: 'TEMPORAL',
          description: 'Weekly morning traffic spike',
          confidence: 0.86,
          observations: 14,
          discoveredAt: '2026-04-01T09:00:00Z',
          updatedAt: '2026-04-14T09:00:00Z',
        },
      ],
      count: 1,
      limit: 100,
      tenantId: 'tenant-a',
      timestamp: '2026-04-14T12:23:00Z',
    });
    mockApiClient.post.mockResolvedValue({
      recordId: 'record-1',
      matches: [
        {
          patternId: 'pattern-1',
          patternName: 'Morning Spike',
          score: 0.88,
          confidence: 0.86,
          explanation: 'Matches the known weekly batch spike.',
        },
      ],
      count: 1,
      tenantId: 'tenant-a',
      timestamp: '2026-04-14T12:24:00Z',
    });

    const patterns = await brainService.getPatterns();
    const matches = await brainService.matchPatterns({ content: 'Morning batch traffic increased' });

    expect(patterns).toEqual([
      {
        patternId: 'pattern-1',
        name: 'Morning Spike',
        confidence: 0.86,
        description: 'Weekly morning traffic spike',
        historicalOccurrences: 14,
        lastSeen: '2026-04-14T09:00:00Z',
        metadata: {
          type: 'TEMPORAL',
          discoveredAt: '2026-04-01T09:00:00Z',
        },
      },
    ]);
    expect(matches).toEqual([
      {
        patternId: 'pattern-1',
        name: 'Morning Spike',
        confidence: 0.86,
        description: 'Matches the known weekly batch spike.',
        historicalOccurrences: 0,
        metadata: {
          score: 0.88,
        },
      },
    ]);
  });

  it('maps canonical salience payloads into the frontend salience score breakdown', async () => {
    mockApiClient.get.mockResolvedValue({
      itemId: 'spot-1',
      salienceScore: 0.91,
      isHigh: true,
      isEmergency: false,
      priority: 2,
      summary: 'Quality regression detected',
      tenantId: 'tenant-a',
      spotlightedAt: '2026-04-14T12:25:00Z',
      timestamp: '2026-04-14T12:26:00Z',
    });

    const salience = await brainService.getSalienceScore('spot-1');

    expect(mockApiClient.get).toHaveBeenCalledWith('/brain/salience/spot-1');
    expect(salience).toEqual({
      score: 0.91,
      breakdown: {
        recency: 0.91,
        novelty: 0.91,
        impact: 0.5,
        urgency: 0.5,
      },
    });
  });

  it('parses canonical learning-trigger responses when feedback is submitted', async () => {
    mockApiClient.post.mockResolvedValue({
      id: 'feedback-1',
      timestamp: '2026-04-14T12:27:00Z',
      signalType: 'manual-feedback',
      impact: 0.35,
      status: 'PROCESSED',
      affectedComponents: ['brain', 'feedback-widget'],
    });

    const signal = await brainService.submitFeedback({
      eventType: 'classification-correction',
      correctValue: 'high-priority',
      incorrectValue: 'medium-priority',
      context: { source: 'contract-test' },
      tags: ['ui'],
    });

    expect(mockApiClient.post).toHaveBeenCalledWith('/learning/trigger', {
      eventType: 'classification-correction',
      correctValue: 'high-priority',
      incorrectValue: 'medium-priority',
      context: { source: 'contract-test' },
      tags: ['ui'],
    });
    expect(signal).toEqual({
      id: 'feedback-1',
      timestamp: '2026-04-14T12:27:00Z',
      signalType: 'manual-feedback',
      impact: 0.35,
      status: 'PROCESSED',
      affectedComponents: ['brain', 'feedback-widget'],
    });
  });

  it('derives learning signals from the canonical learning status payload', async () => {
    mockApiClient.get.mockResolvedValue({
      running: false,
      lastRunTime: '2026-04-14T12:00:00Z',
      nextScheduledRun: '2026-04-14T13:00:00Z',
      intervalMinutes: 60,
      pendingReviews: 2,
      lastResult: {
        status: 'COMPLETED',
        manual: false,
        patternsDiscovered: 3,
        patternsUpdated: 1,
        recordsAnalyzed: 24,
        ranAt: '2026-04-14T12:00:00Z',
      },
      timestamp: '2026-04-14T12:01:00Z',
    });

    const signals = await brainService.getLearningSignals(10);

    expect(mockApiClient.get).toHaveBeenCalledWith('/learning/status', {
      params: { limit: 10 },
    });
    expect(signals).toEqual([
      {
        id: 'learning-run-2026-04-14T12:00:00Z',
        timestamp: '2026-04-14T12:00:00Z',
        signalType: 'scheduled-learning-cycle',
        impact: 0.4,
        status: 'APPLIED',
        affectedComponents: ['brain', 'learning-bridge'],
      },
      {
        id: 'learning-pending-reviews',
        timestamp: '2026-04-14T12:01:00Z',
        signalType: 'pending-review-queue',
        impact: 0.2,
        status: 'PENDING',
        affectedComponents: ['brain', 'review-queue'],
      },
    ]);
  });

  describe('brain autonomy feature gate', () => {
    beforeEach(() => {
      vi.stubEnv('VITE_FEATURE_BRAIN_AUTONOMY', 'false');
    });

    afterEach(() => {
      vi.unstubAllEnvs();
    });

    it('getAutonomyTimeline throws boundary error when brain autonomy disabled', async () => {
      await expect(brainService.getAutonomyTimeline('ingestion', 10)).rejects.toThrow(
        BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE
      );
      expect(mockApiClient.get).not.toHaveBeenCalled();
    });

    it('setGlobalAutonomyLevel throws boundary error when brain autonomy disabled', async () => {
      await expect(
        brainService.setGlobalAutonomyLevel('SUGGEST', 'emergency halt')
      ).rejects.toThrow(BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE);
      expect(mockApiClient.put).not.toHaveBeenCalled();
    });

    it('getGlobalAutonomyLevel throws boundary error when brain autonomy disabled', async () => {
      await expect(brainService.getGlobalAutonomyLevel()).rejects.toThrow(
        BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE
      );
      expect(mockApiClient.get).not.toHaveBeenCalled();
    });
  });
});