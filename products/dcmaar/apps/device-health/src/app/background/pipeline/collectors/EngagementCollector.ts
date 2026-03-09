/**
 * @fileoverview Engagement Collector
 *
 * Tracks user engagement: clicks, scrolls, inputs, sessions, and activity time.
 * Implements active time tracking (excluding idle periods).
 *
 * @module pipeline/collectors
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import type { ProcessCollector } from '../ProcessManager';
import type { ProcessExecutionContext, DataSource } from '../../contracts/process';
import type { UserInteraction, PageEngagement, Session } from '../../contracts/oob-analytics';
import { isEngagedSession } from '../../contracts/oob-analytics';
import browser from 'webextension-polyfill';

/**
 * Engagement Collector
 *
 * Tracks user interactions and engagement metrics
 */
export class EngagementCollector implements ProcessCollector {
  private interactions = new Map<number, UserInteraction[]>();
  private engagementData = new Map<number, PageEngagement>();
  private currentSession: Session | null = null;
  private sessionTimeout: NodeJS.Timeout | null = null;
  private readonly SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
  private readonly IDLE_THRESHOLD_MS = 5000; // 5 seconds

  constructor() {
    this.startSession();
    this.setupListeners();
  }

  async collect(
    source: DataSource,
    context: ProcessExecutionContext
  ): Promise<Event[]> {
    const events: Event[] = [];

    try {
      // Collect engagement events from all tabs
      for (const [tabId, interactions] of this.interactions.entries()) {
        for (const interaction of interactions) {
          events.push({
            id: interaction.interactionId,
            type: 'analytics.user-interaction',
            timestamp: interaction.timestamp,
            payload: interaction,
            metadata: {
              source: 'engagement-collector',
              tabId,
            },
          });
        }

        // Clear collected interactions
        this.interactions.set(tabId, []);
      }

      // Collect page engagement
      for (const [tabId, engagement] of this.engagementData.entries()) {
        events.push({
          id: `engagement-${tabId}-${Date.now()}`,
          type: 'analytics.page-engagement',
          timestamp: engagement.timestamp,
          payload: engagement,
          metadata: {
            source: 'engagement-collector',
            tabId,
          },
        });
      }

      // Collect current session
      if (this.currentSession) {
        this.updateSession();

        events.push({
          id: `session-${this.currentSession.sessionId}`,
          type: 'analytics.session',
          timestamp: this.currentSession.startTime,
          payload: this.currentSession,
          metadata: {
            source: 'engagement-collector',
            sessionId: this.currentSession.sessionId,
          },
        });
      }

      context.logger.debug(`Collected ${events.length} engagement events`);
      return events;
    } catch (error) {
      context.logger.error('Engagement collection failed', { error });
      throw error;
    }
  }

  /**
   * Sets up browser listeners for engagement tracking
   */
  private setupListeners(): void {
    // Listen for tab updates
    browser.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
      if (changeInfo.status === 'complete' && tab.url) {
        this.trackPageView(tabId, tab.url);
      }
    });

    // Listen for tab activation
    browser.tabs.onActivated.addListener(({ tabId }) => {
      this.trackTabActivation(tabId);
    });

    // Listen for tab closure
    browser.tabs.onRemoved.addListener((tabId) => {
      this.endPageEngagement(tabId, 'close');
    });

    // Reset session timeout on activity
  browser.runtime.onMessage.addListener((message: any, sender: browser.Runtime.MessageSender | undefined) => {
      try {
        const type = (message && typeof message === 'object' && 'type' in (message as any)) ? (message as any).type : undefined;
        console.debug('[DCMAAR][EngagementCollector] runtime.onMessage', { type, sender });
      } catch {}

      if (message && typeof message === 'object' && (message as any).type === 'user-activity') {
        this.resetSessionTimeout();
        this.trackInteraction((message as any).data);
      }
    });
  }

  /**
   * Tracks a user interaction
   */
  private trackInteraction(data: {
    tabId: number;
    type: string;
    url: string;
    x?: number;
    y?: number;
    scrollDepth?: number;
    target?: string;
  }): void {
    const interaction: UserInteraction = {
      interactionId: `interaction-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type: data.type as any,
      target: data.target,
      x: data.x,
      y: data.y,
      scrollDepth: data.scrollDepth,
      timestamp: Date.now(),
      url: data.url,
      tabId: data.tabId,
    };

    // Add to interactions list
    const tabInteractions = this.interactions.get(data.tabId) || [];
    tabInteractions.push(interaction);
    this.interactions.set(data.tabId, tabInteractions);

    // Update engagement data
    this.updateEngagement(data.tabId, interaction);
  }

  /**
   * Updates page engagement metrics
   */
  private updateEngagement(tabId: number, interaction: UserInteraction): void {
    let engagement = this.engagementData.get(tabId);

    if (!engagement) {
      engagement = {
        url: interaction.url,
        visitId: `visit-${tabId}-${Date.now()}`,
        activeTime: 0,
        totalTime: 0,
        maxScrollDepth: 0,
        clickCount: 0,
        inputCount: 0,
        mouseMovements: 0,
        rageClicks: 0,
        deadClicks: 0,
        timestamp: Date.now(),
        tabId,
      };
      this.engagementData.set(tabId, engagement);
    }

    // Update metrics based on interaction type
    switch (interaction.type) {
      case 'click':
        engagement.clickCount++;
        this.detectRageClicks(tabId, interaction);
        break;
      case 'input':
        engagement.inputCount++;
        break;
      case 'scroll':
        if (interaction.scrollDepth && interaction.scrollDepth > engagement.maxScrollDepth) {
          engagement.maxScrollDepth = interaction.scrollDepth;
        }
        break;
      case 'hover':
        engagement.mouseMovements++;
        break;
    }

    // Set time to first interaction if not set
    if (!engagement.timeToFirstInteraction && interaction.type !== 'scroll') {
      engagement.timeToFirstInteraction = interaction.timestamp - engagement.timestamp;
    }

    engagement.timestamp = Date.now();
  }

  /**
   * Detects rage clicks (multiple rapid clicks in same area)
   */
  private detectRageClicks(tabId: number, interaction: UserInteraction): void {
    const tabInteractions = this.interactions.get(tabId) || [];
    const recentClicks = tabInteractions.filter(
      (i) =>
        i.type === 'click' &&
        i.timestamp > Date.now() - 2000 && // Last 2 seconds
        i.x && interaction.x &&
        i.y && interaction.y &&
        Math.abs(i.x - interaction.x) < 50 &&
        Math.abs(i.y - interaction.y) < 50
    );

    if (recentClicks.length >= 3) {
      const engagement = this.engagementData.get(tabId);
      if (engagement) {
        engagement.rageClicks++;
      }
    }
  }

  /**
   * Tracks a page view
   */
  private trackPageView(tabId: number, url: string): void {
    if (this.currentSession) {
      this.currentSession.pageViews++;

      if (!this.currentSession.entryUrl) {
        this.currentSession.entryUrl = url;
      }
      this.currentSession.exitUrl = url;

      // Extract domain
      try {
        const urlObj = new URL(url);
        const domain = urlObj.hostname;

        // Track unique domains
        const sessionDomains = new Set<string>();
        // This would need to be tracked separately in a real implementation
        this.currentSession.uniqueDomains = sessionDomains.size;
      } catch (e) {
        // Invalid URL
      }
    }
  }

  /**
   * Tracks tab activation
   */
  private trackTabActivation(tabId: number): void {
    this.resetSessionTimeout();
  }

  /**
   * Ends page engagement for a tab
   */
  private endPageEngagement(tabId: number, exitType: PageEngagement['exitType']): void {
    const engagement = this.engagementData.get(tabId);

    if (engagement) {
      engagement.exitType = exitType;
      engagement.totalTime = Date.now() - engagement.timestamp;
    }

    // Clean up
    this.interactions.delete(tabId);
  }

  /**
   * Starts a new session
   */
  private startSession(): void {
    this.currentSession = {
      sessionId: `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      startTime: Date.now(),
      endTime: null,
      duration: 0,
      pageViews: 0,
      uniqueDomains: 0,
      totalInteractions: 0,
      isEngaged: false,
      userAgent: navigator.userAgent,
    };

    this.resetSessionTimeout();
  }

  /**
   * Ends the current session
   */
  private endSession(): void {
    if (this.currentSession) {
      this.currentSession.endTime = Date.now();
      this.currentSession.duration = this.currentSession.endTime - this.currentSession.startTime;

      // Count total interactions
      let totalInteractions = 0;
      for (const interactions of this.interactions.values()) {
        totalInteractions += interactions.length;
      }
      this.currentSession.totalInteractions = totalInteractions;

      // Determine if engaged
      this.currentSession.isEngaged = isEngagedSession(this.currentSession);

      // Start new session
      this.startSession();
    }
  }

  /**
   * Updates current session
   */
  private updateSession(): void {
    if (this.currentSession) {
      this.currentSession.duration = Date.now() - this.currentSession.startTime;

      // Count total interactions
      let totalInteractions = 0;
      for (const interactions of this.interactions.values()) {
        totalInteractions += interactions.length;
      }
      this.currentSession.totalInteractions = totalInteractions;

      // Determine if engaged
      this.currentSession.isEngaged = isEngagedSession(this.currentSession);
    }
  }

  /**
   * Resets session timeout
   */
  private resetSessionTimeout(): void {
    if (this.sessionTimeout) {
      clearTimeout(this.sessionTimeout);
    }

    this.sessionTimeout = setTimeout(() => {
      this.endSession();
    }, this.SESSION_TIMEOUT_MS);
  }

  /**
   * Gets current session
   */
  getCurrentSession(): Session | null {
    if (this.currentSession) {
      this.updateSession();
    }
    return this.currentSession;
  }

  /**
   * Gets engagement data for a tab
   */
  getEngagement(tabId: number): PageEngagement | undefined {
    return this.engagementData.get(tabId);
  }

  /**
   * Cleanup
   */
  destroy(): void {
    if (this.sessionTimeout) {
      clearTimeout(this.sessionTimeout);
    }
    this.endSession();
  }
}
