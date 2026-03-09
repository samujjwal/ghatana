/**
 * Usage Tracker - Tracks app usage time
 * NO PLATFORM-SPECIFIC CODE
 */

import { UsageData, AppInfo } from '../types';

export interface UsageSession {
  appId: string;
  appName: string;
  startTime: number;
  endTime?: number;
}

export class UsageTracker {
  private currentSession: UsageSession | null = null;
  private sessions: UsageSession[] = [];
  private dailyUsageCache: Map<string, Map<string, number>> = new Map(); // date -> appId -> duration

  /**
   * Start tracking a new app session
   */
  startSession(app: AppInfo): void {
    // End previous session if exists
    if (this.currentSession) {
      this.endSession();
    }

    this.currentSession = {
      appId: app.packageName,
      appName: app.appName,
      startTime: Date.now()
    };
  }

  /**
   * End current tracking session
   */
  endSession(): UsageSession | null {
    if (!this.currentSession) {
      return null;
    }

    const session: UsageSession = {
      ...this.currentSession,
      endTime: Date.now()
    };

    this.sessions.push(session);
    this.updateDailyUsageCache(session);
    
    const completedSession = this.currentSession;
    this.currentSession = null;
    
    return completedSession;
  }

  /**
   * Get current session
   */
  getCurrentSession(): UsageSession | null {
    return this.currentSession;
  }

  /**
   * Get total usage for an app today
   */
  getTodayUsage(appId: string): number {
    const today = this.getDateKey(new Date());
    const todayUsage = this.dailyUsageCache.get(today);
    
    if (!todayUsage) {
      return 0;
    }

    return todayUsage.get(appId) || 0;
  }

  /**
   * Get all usage for a specific date
   */
  getDailyUsage(date: Date): Map<string, number> {
    const dateKey = this.getDateKey(date);
    return this.dailyUsageCache.get(dateKey) || new Map();
  }

  /**
   * Get usage data for a date range
   */
  getUsageRange(startDate: Date, endDate: Date): UsageData[] {
    const usageData: UsageData[] = [];
    const appUsageMap: Map<string, { duration: number; launchCount: number; appName: string }> = new Map();

    // Filter sessions within date range
    const filteredSessions = this.sessions.filter(session => {
      const sessionDate = new Date(session.startTime);
      return sessionDate >= startDate && sessionDate <= endDate;
    });

    // Aggregate usage by app
    for (const session of filteredSessions) {
      if (!session.endTime) continue;

      const duration = session.endTime - session.startTime;
      const existing = appUsageMap.get(session.appId);

      if (existing) {
        existing.duration += duration;
        existing.launchCount += 1;
      } else {
        appUsageMap.set(session.appId, {
          duration,
          launchCount: 1,
          appName: session.appName
        });
      }
    }

    // Convert to UsageData array
    for (const [appId, data] of appUsageMap.entries()) {
      usageData.push({
        appId,
        appName: data.appName,
        duration: data.duration,
        launchCount: data.launchCount,
        date: this.getDateKey(startDate)
      });
    }

    return usageData;
  }

  /**
   * Get all sessions
   */
  getAllSessions(): UsageSession[] {
    return [...this.sessions];
  }

  /**
   * Clear all tracking data
   */
  clear(): void {
    this.currentSession = null;
    this.sessions = [];
    this.dailyUsageCache.clear();
  }

  /**
   * Update daily usage cache
   */
  private updateDailyUsageCache(session: UsageSession): void {
    if (!session.endTime) return;

    const dateKey = this.getDateKey(new Date(session.startTime));
    const duration = session.endTime - session.startTime;

    let dayUsage = this.dailyUsageCache.get(dateKey);
    if (!dayUsage) {
      dayUsage = new Map();
      this.dailyUsageCache.set(dateKey, dayUsage);
    }

    const currentUsage = dayUsage.get(session.appId) || 0;
    dayUsage.set(session.appId, currentUsage + duration);
  }

  /**
   * Get date key for caching (YYYY-MM-DD)
   */
  private getDateKey(date: Date): string {
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * Load historical sessions (e.g., from storage)
   */
  loadSessions(sessions: UsageSession[]): void {
    this.sessions = sessions;
    
    // Rebuild cache
    this.dailyUsageCache.clear();
    sessions.forEach(session => {
      if (session.endTime) {
        this.updateDailyUsageCache(session);
      }
    });
  }
}
