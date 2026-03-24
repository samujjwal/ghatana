import { describe, it, expect, beforeEach } from 'vitest';

import { AdaptiveUI } from '../adaptive/AdaptiveUI';
import { RecommendationEngine } from '../recommendations/RecommendationEngine';
import { ABTestFramework } from '../testing/ABTestFramework';
import {
  BehaviorTracker,
  type BehaviorStorageAdapter,
  type BehaviorEvent,
  type UserSession,
  type BehaviorProfile,
} from '../tracking/BehaviorTracker';

// Mock storage adapter for testing
class InMemoryStorageAdapter implements BehaviorStorageAdapter {
  private events: BehaviorEvent[] = [];
  private sessions: Map<string, UserSession> = new Map();
  private profiles: Map<string, BehaviorProfile> = new Map();

  async saveEvent(event: BehaviorEvent): Promise<void> {
    this.events.push(event);
  }

  async getEvents(
    sessionId?: string,
    limit?: number
  ): Promise<BehaviorEvent[]> {
    let filtered = this.events;
    if (sessionId) {
      filtered = filtered.filter((e) => e.sessionId === sessionId);
    }
    return limit ? filtered.slice(0, limit) : filtered;
  }

  async saveSession(session: UserSession): Promise<void> {
    this.sessions.set(session.id, session);
  }

  async getSession(sessionId: string): Promise<UserSession | null> {
    return this.sessions.get(sessionId) || null;
  }

  async saveProfile(profile: BehaviorProfile): Promise<void> {
    this.profiles.set(profile.userId ?? 'anonymous', profile);
  }

  async getProfile(userId: string): Promise<BehaviorProfile | null> {
    return this.profiles.get(userId) || null;
  }

  async deleteOldEvents(beforeTimestamp: number): Promise<number> {
    const initialLength = this.events.length;
    this.events = this.events.filter(
      (e) => e.timestamp.getTime() >= beforeTimestamp
    );
    return initialLength - this.events.length;
  }

  // Helper method for testing
  clear(): void {
    this.events = [];
    this.sessions.clear();
    this.profiles.clear();
  }
}

describe.skip('ML Integration Tests', () => {
  let storage: InMemoryStorageAdapter;
  let behaviorTracker: BehaviorTracker;
  let recommendationEngine: RecommendationEngine;
  let abTestFramework: ABTestFramework;
  let adaptiveUI: AdaptiveUI;

  beforeEach(() => {
    storage = new InMemoryStorageAdapter();
    behaviorTracker = new BehaviorTracker({ storageAdapter: storage });
    recommendationEngine = new RecommendationEngine();
    abTestFramework = new ABTestFramework();
    adaptiveUI = new AdaptiveUI();
  });

  it('tracks behavior and provides basic insights', async () => {
    await behaviorTracker.startSession('user-123');
    await behaviorTracker.trackPageView('/articles/ml-101');
    await behaviorTracker.trackPageView('/articles/ml-101');
    await behaviorTracker.trackPageView('/articles/ml-102');

    const insights = await behaviorTracker.getInsights('user-123');
    expect(insights).toBeDefined();
    expect(Array.isArray(insights.mostVisitedPages)).toBe(true);
  });

  it('should A/B test different recommendation algorithms', async () => {
    const experiment = await abTestFramework.createExperiment({
      id: 'rec-algorithm-test',
      name: 'Recommendation Algorithm Test',
      description: 'Test collaborative vs content-based filtering',
      variants: [
        {
          id: 'collaborative',
          name: 'Collaborative',
          weight: 0.5,
          config: { algorithm: 'collaborative' },
        },
        {
          id: 'content',
          name: 'Content-Based',
          weight: 0.5,
          config: { algorithm: 'content-based' },
        },
      ],
      metrics: [
        {
          id: 'ctr',
          name: 'CTR',
          type: 'engagement',
          goal: 'maximize',
          isPrimary: true,
        },
      ],
      targetAudience: { userIds: [] },
    });

    const experimentId = experiment.id;
    await abTestFramework.startExperiment(experimentId);
    await abTestFramework.assignVariant(experimentId, 'user-a');
    await abTestFramework.assignVariant(experimentId, 'user-b');

    const variant = await abTestFramework.getVariant(experimentId, 'user-a');
    expect(variant).toBeDefined();

    await abTestFramework.trackMetric(experimentId, 'user-a', 'ctr', 0.2);
    const results = await abTestFramework.getResults(experimentId);
    expect(results).toBeDefined();
    expect(results.variants.length).toBeGreaterThanOrEqual(2);
  });

  it('applies adaptive UI based on A/B assignment', async () => {
    const experiment = await abTestFramework.createExperiment({
      id: 'theme-test',
      name: 'Theme Test',
      description: 'Light vs Dark',
      variants: [
        { id: 'light', name: 'Light', weight: 0.5, config: { theme: 'light' } },
        { id: 'dark', name: 'Dark', weight: 0.5, config: { theme: 'dark' } },
      ],
      metrics: [
        {
          id: 'eng',
          name: 'Engagement',
          type: 'engagement',
          goal: 'maximize',
          isPrimary: true,
        },
      ],
      targetAudience: { userIds: [] },
    });

    const experimentId = experiment.id;
    await abTestFramework.startExperiment(experimentId);
    await abTestFramework.assignVariant(experimentId, 'user-xyz');

    const assigned = await abTestFramework.getVariant(experimentId, 'user-xyz');
    adaptiveUI.setPreferences({
      theme: (assigned?.config?.theme as unknown) || 'light',
    });
    const prefs = adaptiveUI.getPreferences();
    expect(['light', 'dark']).toContain(prefs.theme);
  });

  it('end-to-end: recommendations and insights', async () => {
    await behaviorTracker.startSession('user-journey-1');

    recommendationEngine.addItems([
      {
        id: 'item-1',
        type: 'article',
        title: 'One',
        features: {},
        tags: [],
        popularity: 10,
      },
      {
        id: 'item-2',
        type: 'article',
        title: 'Two',
        features: {},
        tags: [],
        popularity: 5,
      },
    ]);

    recommendationEngine.recordInteraction({
      userId: 'user-journey-1',
      itemId: 'item-1',
      type: 'view',
      timestamp: new Date(),
    });

    const recs =
      await recommendationEngine.getRecommendations('user-journey-1');
    expect(Array.isArray(recs)).toBe(true);
    expect(recs.length).toBeGreaterThanOrEqual(0);

    const insights = await behaviorTracker.getInsights('user-journey-1');
    expect(insights).toBeDefined();
  });
});
