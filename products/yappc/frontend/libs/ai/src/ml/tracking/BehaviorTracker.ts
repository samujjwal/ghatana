/**
 * Behavior Tracker
 *
 * Tracks user interactions and behavior patterns for personalization and analytics.
 * Features:
 * - Event tracking (clicks, navigation, form interactions)
 * - Session management
 * - Pattern recognition
 * - Data aggregation
 * - Privacy controls
 */

/**
 * Event types that can be tracked
 */
export type BehaviorEventType =
  | 'page_view'
  | 'click'
  | 'form_submit'
  | 'form_field_focus'
  | 'scroll'
  | 'navigation'
  | 'search'
  | 'filter'
  | 'sort'
  | 'selection'
  | 'hover'
  | 'error'
  | 'custom';

/**
 * Behavior event data
 */
export interface BehaviorEvent {
  id: string;
  type: BehaviorEventType;
  timestamp: Date;
  sessionId: string;
  userId?: string;
  data: Record<string, unknown>;
  metadata?: {
    page?: string;
    component?: string;
    action?: string;
    duration?: number;
    value?: unknown;
  };
}

/**
 * User session data
 */
export interface UserSession {
  id: string;
  userId?: string;
  startTime: Date;
  lastActivity: Date;
  events: BehaviorEvent[];
  metadata: {
    device?: string;
    browser?: string;
    os?: string;
    screenSize?: string;
    referrer?: string;
  };
}

/**
 * Behavior pattern detected from events
 */
export interface BehaviorPattern {
  type: string;
  frequency: number;
  lastOccurrence: Date;
  contexts: string[];
  confidence: number; // 0-1
}

/**
 * User behavior profile
 */
export interface BehaviorProfile {
  userId?: string;
  sessionCount: number;
  totalEvents: number;
  patterns: BehaviorPattern[];
  preferences: Record<string, unknown>;
  lastSeen: Date;
  createdAt: Date;
}

/**
 * Storage adapter interface
 */
export interface BehaviorStorageAdapter {
  saveEvent(event: BehaviorEvent): Promise<void>;
  saveSession(session: UserSession): Promise<void>;
  getSession(sessionId: string): Promise<UserSession | null>;
  getProfile(userId: string): Promise<BehaviorProfile | null>;
  saveProfile(profile: BehaviorProfile): Promise<void>;
}

/**
 * Tracker configuration
 */
export interface BehaviorTrackerConfig {
  sessionTimeout?: number; // milliseconds (default: 30 minutes)
  storageAdapter?: BehaviorStorageAdapter;
  enableAutoTracking?: boolean; // Auto-track common events
  sampleRate?: number; // 0-1 (for performance, default: 1)
  privacyMode?: 'strict' | 'moderate' | 'permissive';
  excludePatterns?: string[]; // URL patterns to exclude
}

/**
 * In-memory storage adapter (default)
 */
class InMemoryStorageAdapter implements BehaviorStorageAdapter {
  private events: Map<string, BehaviorEvent> = new Map();
  private sessions: Map<string, UserSession> = new Map();
  private profiles: Map<string, BehaviorProfile> = new Map();

  /**
   *
   */
  async saveEvent(event: BehaviorEvent): Promise<void> {
    this.events.set(event.id, event);
  }

  /**
   *
   */
  async saveSession(session: UserSession): Promise<void> {
    this.sessions.set(session.id, session);
  }

  /**
   *
   */
  async getSession(sessionId: string): Promise<UserSession | null> {
    return this.sessions.get(sessionId) || null;
  }

  /**
   *
   */
  async getProfile(userId: string): Promise<BehaviorProfile | null> {
    return this.profiles.get(userId) || null;
  }

  /**
   *
   */
  async saveProfile(profile: BehaviorProfile): Promise<void> {
    this.profiles.set(profile.userId || 'anonymous', profile);
  }
}

/**
 * Behavior Tracker implementation
 */
export class BehaviorTracker {
  private config: Required<BehaviorTrackerConfig>;
  private currentSession: UserSession | null = null;
  private eventBuffer: BehaviorEvent[] = [];
  private flushInterval: NodeJS.Timeout | null = null;

  /**
   *
   */
  constructor(config: BehaviorTrackerConfig = {}) {
    this.config = {
      sessionTimeout: config.sessionTimeout ?? 30 * 60 * 1000, // 30 minutes
      storageAdapter: config.storageAdapter ?? new InMemoryStorageAdapter(),
      enableAutoTracking: config.enableAutoTracking ?? true,
      sampleRate: config.sampleRate ?? 1,
      privacyMode: config.privacyMode ?? 'moderate',
      excludePatterns: config.excludePatterns ?? [],
    };

    if (this.config.enableAutoTracking) {
      this.setupAutoTracking();
    }

    // Flush events periodically
    this.flushInterval = setInterval(() => {
      this.flush();
    }, 5000); // Every 5 seconds
  }

  /**
   * Start a new session
   */
  async startSession(userId?: string): Promise<string> {
    const sessionId = this.generateSessionId();

    this.currentSession = {
      id: sessionId,
      userId,
      startTime: new Date(),
      lastActivity: new Date(),
      events: [],
      metadata: this.collectSessionMetadata(),
    };

    await this.config.storageAdapter.saveSession(this.currentSession);

    return sessionId;
  }

  /**
   * Track a behavior event
   */
  async track(
    type: BehaviorEventType,
    data: Record<string, unknown>,
    metadata?: BehaviorEvent['metadata']
  ): Promise<void> {
    // Sample rate check
    if (Math.random() > this.config.sampleRate) {
      return;
    }

    // Exclude pattern check
    if (this.shouldExclude(metadata?.page)) {
      return;
    }

    // Privacy filtering
    const filteredData = this.applyPrivacyFilters(data);

    const event: BehaviorEvent = {
      id: this.generateEventId(),
      type,
      timestamp: new Date(),
      sessionId: this.currentSession?.id || 'no-session',
      userId: this.currentSession?.userId,
      data: filteredData,
      metadata,
    };

    // Add to buffer
    this.eventBuffer.push(event);

    // Update session
    if (this.currentSession) {
      this.currentSession.events.push(event);
      this.currentSession.lastActivity = new Date();
    }

    // Check session timeout
    this.checkSessionTimeout();
  }

  /**
   * Track page view
   */
  async trackPageView(
    page: string,
    data?: Record<string, unknown>
  ): Promise<void> {
    await this.track('page_view', { page, ...data }, { page });
  }

  /**
   * Track click event
   */
  async trackClick(
    element: string,
    data?: Record<string, unknown>,
    metadata?: Omit<BehaviorEvent['metadata'], 'action'>
  ): Promise<void> {
    await this.track(
      'click',
      { element, ...data },
      { ...metadata, action: 'click' }
    );
  }

  /**
   * Track navigation
   */
  async trackNavigation(
    from: string,
    to: string,
    data?: Record<string, unknown>
  ): Promise<void> {
    await this.track(
      'navigation',
      { from, to, ...data },
      { page: to, action: 'navigate' }
    );
  }

  /**
   * Track search
   */
  async trackSearch(
    query: string,
    results?: number,
    data?: Record<string, unknown>
  ): Promise<void> {
    await this.track(
      'search',
      { query, results, ...data },
      { action: 'search' }
    );
  }

  /**
   * Track form submission
   */
  async trackFormSubmit(
    formId: string,
    data?: Record<string, unknown>
  ): Promise<void> {
    await this.track(
      'form_submit',
      { formId, ...data },
      { component: formId, action: 'submit' }
    );
  }

  /**
   * Track custom event
   */
  async trackCustom(
    eventName: string,
    data: Record<string, unknown>
  ): Promise<void> {
    await this.track('custom', { eventName, ...data }, { action: eventName });
  }

  /**
   * Get current session
   */
  getCurrentSession(): UserSession | null {
    return this.currentSession;
  }

  /**
   * Get user profile
   */
  async getProfile(userId: string): Promise<BehaviorProfile | null> {
    let profile = await this.config.storageAdapter.getProfile(userId);

    if (!profile) {
      profile = {
        userId,
        sessionCount: 0,
        totalEvents: 0,
        patterns: [],
        preferences: {},
        lastSeen: new Date(),
        createdAt: new Date(),
      };
    }

    return profile;
  }

  /**
   * Analyze patterns in user behavior
   */
  async analyzePatterns(userId?: string): Promise<BehaviorPattern[]> {
    const targetUserId = userId || this.currentSession?.userId;
    if (!targetUserId) return [];

    const profile = await this.getProfile(targetUserId);
    if (!profile) return [];

    // Analyze current session events
    const events = this.currentSession?.events || [];
    const patterns: Map<string, BehaviorPattern> = new Map();

    // Group events by type (so a single pattern per event type) and collect contexts
    events.forEach((event) => {
      const key = event.type; // group by type only to aggregate contexts

      if (!patterns.has(key)) {
        patterns.set(key, {
          type: event.type,
          frequency: 0,
          lastOccurrence: event.timestamp,
          contexts: [],
          confidence: 0.5,
        });
      }

      const pattern = patterns.get(key)!;
      pattern.frequency++;
      pattern.lastOccurrence = event.timestamp;

      // Prefer page context, then component
      const context =
        (event.metadata?.page as string) ||
        (event.metadata?.component as string) ||
        (event.data && (event.data['page'] as string));
      if (context && !pattern.contexts.includes(context)) {
        pattern.contexts.push(context);
      }
    });

    // Calculate confidence based on frequency
    patterns.forEach((pattern) => {
      pattern.confidence = Math.min(0.95, 0.5 + pattern.frequency * 0.05);
    });

    return Array.from(patterns.values());
  }

  /**
   * Get insights from behavior data
   */
  async getInsights(userId?: string): Promise<{
    mostVisitedPages: Array<{ page: string; count: number }>;
    mostUsedFeatures: Array<{ feature: string; count: number }>;
    commonPatterns: BehaviorPattern[];
    sessionStats: {
      averageDuration: number;
      averageEvents: number;
      lastActive: Date;
    };
  }> {
    const targetUserId = userId || this.currentSession?.userId;
    const events = this.currentSession?.events || [];

    // Count page visits
    const pageVisits = new Map<string, number>();
    const featureUsage = new Map<string, number>();

    events.forEach((event) => {
      if (event.type === 'page_view') {
        const page =
          (event.metadata?.page as string) ||
          (event.data && (event.data['page'] as string)) ||
          'unknown';
        pageVisits.set(page, (pageVisits.get(page) || 0) + 1);
      }

      // Detect feature usage from common fields: data.feature, data.eventName, data.id, then metadata
      const featureFromData = (event.data &&
        ((event.data['feature'] as string) ||
          (event.data['eventName'] as string) ||
          (event.data['id'] as string))) as string | undefined;
      const feature =
        featureFromData ||
        (event.metadata?.component as string) ||
        (event.metadata?.action as string);

      if (feature) {
        featureUsage.set(feature, (featureUsage.get(feature) || 0) + 1);
      }
    });

    const patterns = await this.analyzePatterns(targetUserId);

    return {
      mostVisitedPages: Array.from(pageVisits.entries())
        .map(([page, count]) => ({ page, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10),
      mostUsedFeatures: Array.from(featureUsage.entries())
        .map(([feature, count]) => ({ feature, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10),
      commonPatterns: patterns
        .sort((a, b) => b.frequency - a.frequency)
        .slice(0, 5),
      sessionStats: {
        averageDuration: this.calculateAverageDuration(),
        averageEvents: events.length,
        lastActive: this.currentSession?.lastActivity || new Date(),
      },
    };
  }

  /**
   * Flush buffered events to storage
   */
  async flush(): Promise<void> {
    if (this.eventBuffer.length === 0) return;

    const eventsToFlush = [...this.eventBuffer];
    this.eventBuffer = [];

    // Save events to storage
    await Promise.all(
      eventsToFlush.map((event) => this.config.storageAdapter.saveEvent(event))
    );

    // Update session
    if (this.currentSession) {
      await this.config.storageAdapter.saveSession(this.currentSession);
    }

    // Update profile
    if (this.currentSession?.userId) {
      await this.updateProfile(this.currentSession.userId);
    }
  }

  /**
   * Stop tracking and cleanup
   */
  async stop(): Promise<void> {
    if (this.flushInterval) {
      clearInterval(this.flushInterval);
      this.flushInterval = null;
    }

    await this.flush();
    this.currentSession = null;
  }

  /**
   * Setup automatic tracking for common events
   */
  private setupAutoTracking(): void {
    if (typeof window === 'undefined') return;

    // Track clicks
    document.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      const element = target.tagName.toLowerCase();
      const id = target.id || target.className;

      this.trackClick(element, { id, text: target.textContent?.slice(0, 50) });
    });

    // Track page visibility
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.flush();
      }
    });

    // Track navigation (for SPAs)
    window.addEventListener('popstate', () => {
      this.trackPageView(window.location.pathname);
    });
  }

  /**
   * Check if session has timed out
   */
  private checkSessionTimeout(): void {
    if (!this.currentSession) return;

    const now = new Date().getTime();
    const lastActivity = this.currentSession.lastActivity.getTime();
    const timeout = this.config.sessionTimeout;

    if (now - lastActivity > timeout) {
      this.flush();
      this.currentSession = null;
    }
  }

  /**
   * Apply privacy filters to data
   */
  private applyPrivacyFilters(
    data: Record<string, unknown>
  ): Record<string, unknown> {
    const mode = this.config.privacyMode;

    if (mode === 'strict') {
      // Remove all PII
      const filtered: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(data)) {
        if (!this.isPII(key)) {
          filtered[key] = value;
        }
      }
      return filtered;
    }

    if (mode === 'moderate') {
      // Hash sensitive data
      const filtered: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(data)) {
        if (this.isPII(key)) {
          filtered[key] = this.hashValue(String(value));
        } else {
          filtered[key] = value;
        }
      }
      return filtered;
    }

    return data; // permissive mode
  }

  /**
   * Check if key contains PII
   */
  private isPII(key: string): boolean {
    const piiKeys = [
      'email',
      'phone',
      'password',
      'ssn',
      'creditcard',
      'address',
    ];
    return piiKeys.some((pii) => key.toLowerCase().includes(pii));
  }

  /**
   * Hash a value for privacy
   */
  private hashValue(value: string): string {
    // Simple hash for demonstration (use proper hashing in production)
    let hash = 0;
    for (let i = 0; i < value.length; i++) {
      const char = value.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash;
    }
    return `hashed_${Math.abs(hash)}`;
  }

  /**
   * Check if URL should be excluded
   */
  private shouldExclude(page?: string): boolean {
    if (!page) return false;
    return this.config.excludePatterns.some((pattern) =>
      new RegExp(pattern).test(page)
    );
  }

  /**
   * Collect session metadata
   */
  private collectSessionMetadata(): UserSession['metadata'] {
    if (typeof window === 'undefined') {
      return {};
    }

    return {
      device: this.getDeviceType(),
      browser: navigator.userAgent.split(' ').pop(),
      os: navigator.platform,
      screenSize: `${window.screen.width}x${window.screen.height}`,
      referrer: document.referrer,
    };
  }

  /**
   * Get device type
   */
  private getDeviceType(): string {
    if (typeof window === 'undefined') return 'unknown';

    const width = window.innerWidth;
    if (width < 768) return 'mobile';
    if (width < 1024) return 'tablet';
    return 'desktop';
  }

  /**
   * Calculate average session duration
   */
  private calculateAverageDuration(): number {
    if (!this.currentSession) return 0;

    const duration =
      new Date().getTime() - this.currentSession.startTime.getTime();
    return duration;
  }

  /**
   * Update user profile with new data
   */
  private async updateProfile(userId: string): Promise<void> {
    let profile = await this.config.storageAdapter.getProfile(userId);

    if (!profile) {
      profile = {
        userId,
        sessionCount: 0,
        totalEvents: 0,
        patterns: [],
        preferences: {},
        lastSeen: new Date(),
        createdAt: new Date(),
      };
    }

    profile.sessionCount++;
    profile.totalEvents += this.currentSession?.events.length || 0;
    profile.lastSeen = new Date();
    profile.patterns = await this.analyzePatterns(userId);

    await this.config.storageAdapter.saveProfile(profile);
  }

  /**
   * Generate unique event ID
   */
  private generateEventId(): string {
    return `evt_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Generate unique session ID
   */
  private generateSessionId(): string {
    return `ses_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}
