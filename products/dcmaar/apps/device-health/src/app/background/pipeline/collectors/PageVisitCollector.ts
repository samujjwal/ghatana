/**
 * @fileoverview Page Visit Collector
 *
 * Tracks page visits, time spent, and generates visit statistics.
 * Based on patterns from web-activity-time-tracker.
 *
 * @module pipeline/collectors
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import type { ProcessCollector } from '../ProcessManager';
import type { ProcessExecutionContext, DataSource } from '../../contracts/process';
import type { PageVisit, PageVisitStats } from '../../contracts/oob-analytics';
import browser from 'webextension-polyfill';

/**
 * Page Visit Collector
 *
 * Tracks active page visits and calculates time spent
 */
export class PageVisitCollector implements ProcessCollector {
  private activeVisits = new Map<number, PageVisit>();
  private visitStats = new Map<string, PageVisitStats>();
  private activityCheckInterval: NodeJS.Timeout | null = null;

  constructor() {
    this.startActivityTracking();
  }

  async collect(
    source: DataSource,
    context: ProcessExecutionContext
  ): Promise<Event[]> {
    const { config, batchSize } = source;
    const events: Event[] = [];

    try {
      // Get all tabs
      const tabs = await browser.tabs.query({});

      // Update active visits
      for (const tab of tabs.slice(0, batchSize)) {
        if (!tab.id || !tab.url) continue;

        // Skip non-http(s) URLs
        if (!tab.url.startsWith('http')) {
          continue;
        }

        // Check if visit is already tracked
        const existingVisit = this.activeVisits.get(tab.id);

        if (existingVisit) {
          // Update duration
          existingVisit.duration = Date.now() - existingVisit.startTime;
          existingVisit.lifecycleState = tab.active ? 'active' : 'passive';
        } else {
          // Create new visit
          const visit = await this.createVisit(tab);
          this.activeVisits.set(tab.id, visit);
        }
      }

      // Collect visit events
      for (const [tabId, visit] of this.activeVisits.entries()) {
        events.push({
          id: `page-visit-${visit.visitId}`,
          type: 'analytics.page-visit',
          timestamp: visit.startTime,
          payload: visit,
          metadata: {
            source: 'page-visit-collector',
            tabId,
            domain: visit.domain,
          },
        });

        // Update stats
        this.updateStats(visit);
      }

      context.logger.debug(`Collected ${events.length} page visit events`);
      return events;
    } catch (error) {
      context.logger.error('Page visit collection failed', { error });
      throw error;
    }
  }

  /**
   * Creates a new visit record
   */
  private async createVisit(tab: browser.Tabs.Tab): Promise<PageVisit> {
    const url = new URL(tab.url!);
    const domain = url.hostname;

    const visit: PageVisit = {
      visitId: `visit-${tab.id}-${Date.now()}`,
      url: tab.url!,
      domain,
      title: tab.title,
      startTime: Date.now(),
      endTime: null,
      duration: 0,
      tabId: tab.id!,
      windowId: tab.windowId!,
      lifecycleState: tab.active ? 'active' : 'passive',
    };

    return visit;
  }

  /**
   * Updates visit statistics
   */
  private updateStats(visit: PageVisit): void {
    const stats = this.visitStats.get(visit.domain) || this.createEmptyStats(visit.domain);

    stats.totalVisits++;
    stats.totalTime += visit.duration;
    stats.averageTime = stats.totalTime / stats.totalVisits;

    if (!stats.firstVisit || visit.startTime < stats.firstVisit) {
      stats.firstVisit = visit.startTime;
    }

    if (!stats.lastVisit || visit.startTime > stats.lastVisit) {
      stats.lastVisit = visit.startTime;
    }

    // Update daily stats
    const date = new Date(visit.startTime).toISOString().split('T')[0];
    const dailyStat = stats.dailyStats.find((s) => s.date === date);

    if (dailyStat) {
      dailyStat.visits++;
      dailyStat.time += visit.duration;
    } else {
      stats.dailyStats.push({
        date,
        visits: 1,
        time: visit.duration,
      });
    }

    // Classify domain
    stats.category = this.classifyDomain(visit.domain);

    this.visitStats.set(visit.domain, stats);
  }

  /**
   * Creates empty stats object
   */
  private createEmptyStats(identifier: string): PageVisitStats {
    return {
      identifier,
      totalVisits: 0,
      totalTime: 0,
      averageTime: 0,
      dailyStats: [],
    };
  }

  /**
   * Classifies domain into category
   */
  private classifyDomain(domain: string): PageVisitStats['category'] {
    const workDomains = ['github.com', 'gitlab.com', 'stackoverflow.com', 'docs.google.com', 'notion.so', 'jira.atlassian.com'];
    const socialDomains = ['twitter.com', 'facebook.com', 'linkedin.com', 'instagram.com', 'reddit.com'];
    const entertainmentDomains = ['youtube.com', 'netflix.com', 'twitch.tv', 'spotify.com'];
    const shoppingDomains = ['amazon.com', 'ebay.com', 'aliexpress.com', 'walmart.com'];
    const newsDomains = ['news.ycombinator.com', 'nytimes.com', 'bbc.com', 'cnn.com'];
    const productivityDomains = ['trello.com', 'asana.com', 'todoist.com', 'evernote.com'];

    if (workDomains.some((d) => domain.includes(d))) return 'work';
    if (socialDomains.some((d) => domain.includes(d))) return 'social';
    if (entertainmentDomains.some((d) => domain.includes(d))) return 'entertainment';
    if (shoppingDomains.some((d) => domain.includes(d))) return 'shopping';
    if (newsDomains.some((d) => domain.includes(d))) return 'news';
    if (productivityDomains.some((d) => domain.includes(d))) return 'productivity';

    return 'other';
  }

  /**
   * Starts activity tracking interval
   */
  private startActivityTracking(): void {
    if (this.activityCheckInterval) return;

    // Check every 5 seconds to update durations
    this.activityCheckInterval = setInterval(() => {
      this.updateActiveDurations();
    }, 5000);
  }

  /**
   * Updates durations for active visits
   */
  private updateActiveDurations(): void {
    const now = Date.now();

    for (const visit of this.activeVisits.values()) {
      if (visit.endTime === null) {
        visit.duration = now - visit.startTime;
      }
    }
  }

  /**
   * Ends a visit
   */
  endVisit(tabId: number): PageVisit | undefined {
    const visit = this.activeVisits.get(tabId);

    if (visit) {
      visit.endTime = Date.now();
      visit.duration = visit.endTime - visit.startTime;
      visit.lifecycleState = 'terminated';

      this.updateStats(visit);
      this.activeVisits.delete(tabId);

      return visit;
    }

    return undefined;
  }

  /**
   * Gets visit statistics
   */
  getStats(domain?: string): PageVisitStats[] {
    if (domain) {
      const stats = this.visitStats.get(domain);
      return stats ? [stats] : [];
    }

    return Array.from(this.visitStats.values());
  }

  /**
   * Clears old stats (retention policy)
   */
  clearOldStats(daysToKeep: number = 30): void {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);
    const cutoffStr = cutoffDate.toISOString().split('T')[0];

    for (const stats of this.visitStats.values()) {
      stats.dailyStats = stats.dailyStats.filter((s) => s.date >= cutoffStr);
    }
  }

  /**
   * Cleanup
   */
  destroy(): void {
    if (this.activityCheckInterval) {
      clearInterval(this.activityCheckInterval);
      this.activityCheckInterval = null;
    }
  }
}
