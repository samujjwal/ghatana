/**
 * BehaviorTracker Tests
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import {
  BehaviorTracker,
  type BehaviorStorageAdapter,
  type BehaviorEvent,
  type UserSession,
  type BehaviorProfile,
} from '../BehaviorTracker';

// Mock storage adapter
class MockStorageAdapter implements BehaviorStorageAdapter {
  public events: BehaviorEvent[] = [];
  public sessions: Map<string, UserSession> = new Map();
  public profiles: Map<string, BehaviorProfile> = new Map();

  async saveEvent(event: BehaviorEvent): Promise<void> {
    this.events.push(event);
  }

  async saveSession(session: UserSession): Promise<void> {
    this.sessions.set(session.id, session);
  }

  async getSession(sessionId: string): Promise<UserSession | null> {
    return this.sessions.get(sessionId) || null;
  }

  async getProfile(userId: string): Promise<BehaviorProfile | null> {
    return this.profiles.get(userId) || null;
  }

  async saveProfile(profile: BehaviorProfile): Promise<void> {
    this.profiles.set(profile.userId || 'anonymous', profile);
  }

  clear() {
    this.events = [];
    this.sessions.clear();
    this.profiles.clear();
  }
}

describe.skip('BehaviorTracker', () => {
  let tracker: BehaviorTracker;
  let storage: MockStorageAdapter;

  beforeEach(() => {
    storage = new MockStorageAdapter();
    tracker = new BehaviorTracker({
      storageAdapter: storage,
      enableAutoTracking: false, // Disable for testing
      sessionTimeout: 1000, // 1 second for testing
    });
  });

  afterEach(async () => {
    await tracker.stop();
    storage.clear();
  });

  describe('Session Management', () => {
    it('should start a new session', async () => {
      const sessionId = await tracker.startSession('user123');

      expect(sessionId).toBeDefined();
      expect(sessionId).toMatch(/^ses_/);
      expect(storage.sessions.size).toBe(1);

      const session = storage.sessions.get(sessionId);
      expect(session).toBeDefined();
      expect(session?.userId).toBe('user123');
      expect(session?.events).toEqual([]);
    });

    it('should start anonymous session', async () => {
      const sessionId = await tracker.startSession();

      expect(sessionId).toBeDefined();
      const session = storage.sessions.get(sessionId);
      expect(session?.userId).toBeUndefined();
    });

    it('should get current session', async () => {
      await tracker.startSession('user123');
      const session = tracker.getCurrentSession();

      expect(session).toBeDefined();
      expect(session?.userId).toBe('user123');
    });
  });

  describe('Event Tracking', () => {
    beforeEach(async () => {
      await tracker.startSession('user123');
    });

    it('should track generic events', async () => {
      await tracker.track('page_view', { page: '/home' });
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('page_view');
      expect(storage.events[0].data).toEqual({ page: '/home' });
    });

    it('should track page views', async () => {
      await tracker.trackPageView('/dashboard');
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('page_view');
      expect(storage.events[0].data).toMatchObject({ page: '/dashboard' });
    });

    it('should track clicks', async () => {
      await tracker.trackClick('button', { id: 'submit' });
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('click');
      expect(storage.events[0].data).toMatchObject({
        element: 'button',
        id: 'submit',
      });
    });

    it('should track navigation', async () => {
      await tracker.trackNavigation('/home', '/about');
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('navigation');
      expect(storage.events[0].data).toMatchObject({
        from: '/home',
        to: '/about',
      });
    });

    it('should track search', async () => {
      await tracker.trackSearch('machine learning', 42);
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('search');
      expect(storage.events[0].data).toMatchObject({
        query: 'machine learning',
        results: 42,
      });
    });

    it('should track form submissions', async () => {
      await tracker.trackFormSubmit('contact-form');
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('form_submit');
      expect(storage.events[0].data).toMatchObject({ formId: 'contact-form' });
    });

    it('should track custom events', async () => {
      await tracker.trackCustom('feature-used', { feature: 'export' });
      await tracker.flush();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].type).toBe('custom');
      expect(storage.events[0].data).toMatchObject({
        eventName: 'feature-used',
        feature: 'export',
      });
    });

    it('should add events to session', async () => {
      await tracker.trackPageView('/home');
      const session = tracker.getCurrentSession();

      expect(session?.events).toHaveLength(1);
      expect(session?.events[0].type).toBe('page_view');
    });

    it('should generate unique event IDs', async () => {
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/about');
      await tracker.flush();

      const ids = storage.events.map((e) => e.id);
      expect(new Set(ids).size).toBe(2); // All unique
    });

    it('should include metadata', async () => {
      await tracker.track(
        'click',
        { element: 'button' },
        { page: '/home', component: 'header' }
      );
      await tracker.flush();

      expect(storage.events[0].metadata).toMatchObject({
        page: '/home',
        component: 'header',
      });
    });
  });

  describe('Privacy Controls', () => {
    it('should filter PII in strict mode', async () => {
      const strictTracker = new BehaviorTracker({
        storageAdapter: storage,
        privacyMode: 'strict',
        enableAutoTracking: false,
      });

      await strictTracker.startSession('user123');
      await strictTracker.track('form_submit', {
        email: 'user@example.com',
        password: 'secret123',
        name: 'John Doe',
      });
      await strictTracker.flush();
      await strictTracker.stop();

      const event = storage.events[0];
      expect(event.data.email).toBeUndefined();
      expect(event.data.password).toBeUndefined();
      expect(event.data.name).toBe('John Doe'); // Not PII keyword
    });

    it('should hash PII in moderate mode', async () => {
      const moderateTracker = new BehaviorTracker({
        storageAdapter: storage,
        privacyMode: 'moderate',
        enableAutoTracking: false,
      });

      await moderateTracker.startSession('user123');
      await moderateTracker.track('form_submit', {
        email: 'user@example.com',
        name: 'John Doe',
      });
      await moderateTracker.flush();
      await moderateTracker.stop();

      const event = storage.events[0];
      expect(event.data.email).toMatch(/^hashed_/);
      expect(event.data.name).toBe('John Doe');
    });

    it('should not filter in permissive mode', async () => {
      const permissiveTracker = new BehaviorTracker({
        storageAdapter: storage,
        privacyMode: 'permissive',
        enableAutoTracking: false,
      });

      await permissiveTracker.startSession('user123');
      await permissiveTracker.track('form_submit', {
        email: 'user@example.com',
        password: 'secret123',
      });
      await permissiveTracker.flush();
      await permissiveTracker.stop();

      const event = storage.events[0];
      expect(event.data.email).toBe('user@example.com');
      expect(event.data.password).toBe('secret123');
    });

    it('should exclude patterns', async () => {
      const excludeTracker = new BehaviorTracker({
        storageAdapter: storage,
        excludePatterns: ['/admin/*', '/internal/*'],
        enableAutoTracking: false,
      });

      await excludeTracker.startSession('user123');
      await excludeTracker.trackPageView('/admin/users');
      await excludeTracker.trackPageView('/home');
      await excludeTracker.flush();
      await excludeTracker.stop();

      expect(storage.events).toHaveLength(1);
      expect(storage.events[0].data).toMatchObject({ page: '/home' });
    });
  });

  describe('Pattern Analysis', () => {
    beforeEach(async () => {
      await tracker.startSession('user123');
    });

    it('should analyze behavior patterns', async () => {
      // Generate repeated patterns
      await tracker.trackPageView('/dashboard');
      await tracker.trackPageView('/dashboard');
      await tracker.trackPageView('/dashboard');
      await tracker.trackClick('button', { id: 'export' });
      await tracker.trackClick('button', { id: 'export' });

      const patterns = await tracker.analyzePatterns('user123');

      expect(patterns.length).toBeGreaterThan(0);

      const pageViewPattern = patterns.find((p) => p.type === 'page_view');
      expect(pageViewPattern).toBeDefined();
      expect(pageViewPattern?.frequency).toBe(3);
      expect(pageViewPattern?.confidence).toBeGreaterThan(0.5);
    });

    it('should calculate confidence based on frequency', async () => {
      // More frequency = higher confidence
      for (let i = 0; i < 10; i++) {
        await tracker.trackClick('button', { id: 'save' });
      }

      const patterns = await tracker.analyzePatterns('user123');
      const clickPattern = patterns.find((p) => p.type === 'click');

      expect(clickPattern?.confidence).toBeGreaterThan(0.8);
    });

    it('should track contexts', async () => {
      await tracker.track('click', { element: 'button' }, { page: '/home' });
      await tracker.track('click', { element: 'button' }, { page: '/about' });

      const patterns = await tracker.analyzePatterns('user123');
      const clickPattern = patterns.find((p) => p.type === 'click');

      expect(clickPattern?.contexts).toContain('/home');
      expect(clickPattern?.contexts).toContain('/about');
    });
  });

  describe('Insights', () => {
    beforeEach(async () => {
      await tracker.startSession('user123');
    });

    it('should provide behavioral insights', async () => {
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/about');
      await tracker.trackClick('button', { id: 'submit' });

      const insights = await tracker.getInsights('user123');

      expect(insights.mostVisitedPages).toBeDefined();
      expect(insights.mostUsedFeatures).toBeDefined();
      expect(insights.commonPatterns).toBeDefined();
      expect(insights.sessionStats).toBeDefined();
    });

    it('should rank pages by visit count', async () => {
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/about');

      const insights = await tracker.getInsights('user123');

      expect(insights.mostVisitedPages[0].page).toBe('/home');
      expect(insights.mostVisitedPages[0].count).toBe(3);
      expect(insights.mostVisitedPages[1].page).toBe('/about');
      expect(insights.mostVisitedPages[1].count).toBe(1);
    });

    it('should track feature usage', async () => {
      await tracker.trackClick('button', { id: 'export' });
      await tracker.trackClick('button', { id: 'export' });
      await tracker.trackFormSubmit('contact-form');

      const insights = await tracker.getInsights('user123');

      expect(insights.mostUsedFeatures.length).toBeGreaterThan(0);
      const exportFeature = insights.mostUsedFeatures.find(
        (f) => f.feature === 'export'
      );
      expect(exportFeature?.count).toBe(2);
    });

    it('should provide session statistics', async () => {
      await tracker.trackPageView('/home');
      await tracker.trackPageView('/about');

      const insights = await tracker.getInsights('user123');

      expect(insights.sessionStats.averageEvents).toBe(2);
      expect(insights.sessionStats.lastActive).toBeInstanceOf(Date);
    });
  });

  describe('Sampling', () => {
    it('should respect sample rate', async () => {
      const sampledTracker = new BehaviorTracker({
        storageAdapter: storage,
        sampleRate: 0, // No sampling
        enableAutoTracking: false,
      });

      await sampledTracker.startSession('user123');
      await sampledTracker.trackPageView('/home');
      await sampledTracker.flush();
      await sampledTracker.stop();

      expect(storage.events).toHaveLength(0);
    });

    it('should always track with rate 1.0', async () => {
      const fullTracker = new BehaviorTracker({
        storageAdapter: storage,
        sampleRate: 1.0,
        enableAutoTracking: false,
      });

      await fullTracker.startSession('user123');
      await fullTracker.trackPageView('/home');
      await fullTracker.flush();
      await fullTracker.stop();

      expect(storage.events).toHaveLength(1);
    });
  });

  describe('Buffering and Flushing', () => {
    it('should buffer events', async () => {
      await tracker.startSession('user123');
      await tracker.trackPageView('/home');

      // Event buffered, not yet saved
      expect(storage.events).toHaveLength(0);
    });

    it('should flush events', async () => {
      await tracker.startSession('user123');
      await tracker.trackPageView('/home');
      await tracker.trackClick('button');
      await tracker.flush();

      expect(storage.events).toHaveLength(2);
    });

    it('should clear buffer after flush', async () => {
      await tracker.startSession('user123');
      await tracker.trackPageView('/home');
      await tracker.flush();

      expect(storage.events).toHaveLength(1);

      await tracker.trackPageView('/about');
      await tracker.flush();

      expect(storage.events).toHaveLength(2);
    });

    it('should flush on stop', async () => {
      await tracker.startSession('user123');
      await tracker.trackPageView('/home');
      await tracker.stop();

      expect(storage.events).toHaveLength(1);
    });
  });

  describe('Profile Management', () => {
    it('should create new profile', async () => {
      const profile = await tracker.getProfile('user123');

      expect(profile).toBeDefined();
      expect(profile?.userId).toBe('user123');
      expect(profile?.sessionCount).toBe(0);
      expect(profile?.totalEvents).toBe(0);
    });

    it('should return null for non-existent profile', async () => {
      // Set up storage with no profile
      const profile = await storage.getProfile('nonexistent');
      expect(profile).toBeNull();
    });
  });
});
