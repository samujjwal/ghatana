/**
 * @fileoverview Web Analytics Analyzer
 *
 * Analyzes web analytics data: page visits, engagement, sessions.
 * Generates insights and statistics.
 *
 * @module pipeline/analyzers
 * @since 2.0.0
 */

import type { ProcessAnalyzer } from '../ProcessManager';
import type { ProcessExecutionContext, AnalysisOperation } from '../../contracts/process';
import type { PageVisit, PageEngagement, Session, PageVisitStats, EngagementStats } from '../../contracts/oob-analytics';

/**
 * Web Analytics Analyzer
 */
export class WebAnalyticsAnalyzer implements ProcessAnalyzer {
  async analyze(
    data: any[],
    operation: AnalysisOperation,
    context: ProcessExecutionContext
  ): Promise<any> {
    const { config } = operation;
    const {
      analysisType = 'visit-stats',
      timeWindow,
      groupBy = 'domain',
    } = config;

    try {
      switch (analysisType) {
        case 'visit-stats':
          return this.analyzeVisitStats(data, groupBy);
        case 'engagement-stats':
          return this.analyzeEngagementStats(data, timeWindow);
        case 'session-analysis':
          return this.analyzeSessions(data, timeWindow);
        case 'top-pages':
          return this.analyzeTopPages(data, config.limit || 10);
        case 'time-distribution':
          return this.analyzeTimeDistribution(data, groupBy);
        case 'bounce-rate':
          return this.analyzeBounceRate(data);
        default:
          context.logger.warn(`Unknown analysis type: ${analysisType}`);
          return data;
      }
    } catch (error) {
      context.logger.error('Web analytics analysis failed', { error });
      throw error;
    }
  }

  /**
   * Analyzes visit statistics
   */
  private analyzeVisitStats(data: any[], groupBy: string): Record<string, PageVisitStats> {
    const stats = new Map<string, PageVisitStats>();

    for (const event of data) {
      if (event.type !== 'analytics.page-visit') continue;

      const visit = event.payload as PageVisit;
      const key = groupBy === 'domain' ? visit.domain : visit.url;

      let stat = stats.get(key);
      if (!stat) {
        stat = {
          identifier: key,
          totalVisits: 0,
          totalTime: 0,
          averageTime: 0,
          dailyStats: [],
        };
        stats.set(key, stat);
      }

      stat.totalVisits++;
      stat.totalTime += visit.duration;
      stat.averageTime = stat.totalTime / stat.totalVisits;

      if (!stat.firstVisit || visit.startTime < stat.firstVisit) {
        stat.firstVisit = visit.startTime;
      }
      if (!stat.lastVisit || visit.startTime > stat.lastVisit) {
        stat.lastVisit = visit.startTime;
      }

      // Update daily stats
      const date = new Date(visit.startTime).toISOString().split('T')[0];
      const dailyStat = stat.dailyStats.find((s) => s.date === date);
      if (dailyStat) {
        dailyStat.visits++;
        dailyStat.time += visit.duration;
      } else {
        stat.dailyStats.push({
          date,
          visits: 1,
          time: visit.duration,
        });
      }
    }

    return Object.fromEntries(stats);
  }

  /**
   * Analyzes engagement statistics
   */
  private analyzeEngagementStats(data: any[], timeWindow?: { start: number; end: number }): EngagementStats {
    const stats: EngagementStats = {
      totalSessions: 0,
      engagedSessions: 0,
      engagementRate: 0,
      avgSessionDuration: 0,
      avgPageViewsPerSession: 0,
      totalInteractions: 0,
      bounceRate: 0,
      timeWindow: timeWindow || { start: 0, end: Date.now() },
    };

    let totalSessionDuration = 0;
    let totalPageViews = 0;
    let bouncedSessions = 0;

    for (const event of data) {
      if (event.type === 'analytics.session') {
        const session = event.payload as Session;

        // Filter by time window if specified
        if (timeWindow) {
          if (session.startTime < timeWindow.start || session.startTime > timeWindow.end) {
            continue;
          }
        }

        stats.totalSessions++;
        totalSessionDuration += session.duration;
        totalPageViews += session.pageViews;
        stats.totalInteractions += session.totalInteractions;

        if (session.isEngaged) {
          stats.engagedSessions++;
        }

        if (session.pageViews === 1) {
          bouncedSessions++;
        }
      }
    }

    // Calculate averages
    if (stats.totalSessions > 0) {
      stats.engagementRate = stats.engagedSessions / stats.totalSessions;
      stats.avgSessionDuration = totalSessionDuration / stats.totalSessions;
      stats.avgPageViewsPerSession = totalPageViews / stats.totalSessions;
      stats.bounceRate = bouncedSessions / stats.totalSessions;
    }

    return stats;
  }

  /**
   * Analyzes sessions
   */
  private analyzeSessions(data: any[], timeWindow?: { start: number; end: number }): any {
    const sessions: Session[] = [];

    for (const event of data) {
      if (event.type === 'analytics.session') {
        const session = event.payload as Session;

        if (timeWindow) {
          if (session.startTime < timeWindow.start || session.startTime > timeWindow.end) {
            continue;
          }
        }

        sessions.push(session);
      }
    }

    return {
      total: sessions.length,
      engaged: sessions.filter((s) => s.isEngaged).length,
      avgDuration: sessions.reduce((sum, s) => sum + s.duration, 0) / sessions.length || 0,
      avgPageViews: sessions.reduce((sum, s) => sum + s.pageViews, 0) / sessions.length || 0,
      sessions: sessions.sort((a, b) => b.startTime - a.startTime),
    };
  }

  /**
   * Analyzes top pages by visits or time
   */
  private analyzeTopPages(data: any[], limit: number): Array<{ url: string; visits: number; time: number }> {
    const pages = new Map<string, { visits: number; time: number }>();

    for (const event of data) {
      if (event.type === 'analytics.page-visit') {
        const visit = event.payload as PageVisit;

        let page = pages.get(visit.url);
        if (!page) {
          page = { visits: 0, time: 0 };
          pages.set(visit.url, page);
        }

        page.visits++;
        page.time += visit.duration;
      }
    }

    return Array.from(pages.entries())
      .map(([url, stats]) => ({ url, ...stats }))
      .sort((a, b) => b.visits - a.visits)
      .slice(0, limit);
  }

  /**
   * Analyzes time distribution by hour/day
   */
  private analyzeTimeDistribution(data: any[], groupBy: 'hour' | 'day' | 'domain' = 'hour'): any {
    const distribution = new Map<string, number>();

    for (const event of data) {
      if (event.type === 'analytics.page-visit') {
        const visit = event.payload as PageVisit;
        const date = new Date(visit.startTime);

        let key: string;
        if (groupBy === 'hour') {
          key = `${date.getHours()}:00`;
        } else if (groupBy === 'day') {
          key = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][date.getDay()];
        } else {
          key = visit.domain;
        }

        distribution.set(key, (distribution.get(key) || 0) + visit.duration);
      }
    }

    return Object.fromEntries(
      Array.from(distribution.entries()).sort((a, b) => b[1] - a[1])
    );
  }

  /**
   * Analyzes bounce rate
   */
  private analyzeBounceRate(data: any[]): {
    bounceRate: number;
    totalSessions: number;
    bouncedSessions: number;
  } {
    let totalSessions = 0;
    let bouncedSessions = 0;

    for (const event of data) {
      if (event.type === 'analytics.session') {
        const session = event.payload as Session;
        totalSessions++;

        if (session.pageViews === 1 && session.duration < 10000) {
          bouncedSessions++;
        }
      }
    }

    return {
      bounceRate: totalSessions > 0 ? bouncedSessions / totalSessions : 0,
      totalSessions,
      bouncedSessions,
    };
  }
}
