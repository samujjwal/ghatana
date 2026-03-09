/**
 * Activity Feed Service
 * Track and aggregate user activity
 *
 * @doc.type service
 * @doc.purpose Activity tracking and feed generation
 * @doc.layer product
 * @doc.pattern ActivityFeed
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export type ActivityType =
  | 'moment_created'
  | 'moment_updated'
  | 'moment_deleted'
  | 'moment_shared'
  | 'tag_added'
  | 'template_used'
  | 'search_performed'
  | 'summary_generated'
  | 'collaboration_started'
  | 'comment_added';

export interface Activity {
  id: string;
  userId: string;
  type: ActivityType;
  timestamp: Date;
  metadata: Record<string, unknown>;
  description: string;
  icon?: string;
  color?: string;
}

export interface ActivityStats {
  totalActivities: number;
  momentsCreated: number;
  momentsUpdated: number;
  templatesUsed: number;
  searchesPerformed: number;
  summariesGenerated: number;
  collaborations: number;
}

export interface ActivityFilter {
  userId?: string;
  types?: ActivityType[];
  startDate?: Date;
  endDate?: Date;
  limit?: number;
  offset?: number;
}

export interface ActivityGroup {
  date: string;
  activities: Activity[];
  count: number;
}

// ============================================================================
// Activity Feed Service
// ============================================================================

/**
 * ActivityFeedService manages user activity tracking and feed generation
 */
class ActivityFeedService {
  private activities: Map<string, Activity> = new Map();
  private userActivities: Map<string, string[]> = new Map();

  /**
   * Track a new activity
   */
  trackActivity(
    userId: string,
    type: ActivityType,
    metadata: Record<string, unknown> = {}
  ): Activity {
    const activity: Activity = {
      id: `activity-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      userId,
      type,
      timestamp: new Date(),
      metadata,
      description: this.generateDescription(type, metadata),
      icon: this.getActivityIcon(type),
      color: this.getActivityColor(type),
    };

    // Store activity
    this.activities.set(activity.id, activity);

    // Add to user's activity list
    const userActivitiesList = this.userActivities.get(userId) || [];
    userActivitiesList.push(activity.id);
    this.userActivities.set(userId, userActivitiesList);

    return activity;
  }

  /**
   * Get activities for a user
   */
  getActivities(filter: ActivityFilter = {}): Activity[] {
    let activities = Array.from(this.activities.values());

    // Filter by user
    if (filter.userId) {
      const userActivityIds = this.userActivities.get(filter.userId) || [];
      activities = activities.filter((a) => userActivityIds.includes(a.id));
    }

    // Filter by types
    if (filter.types && filter.types.length > 0) {
      activities = activities.filter((a) => filter.types!.includes(a.type));
    }

    // Filter by date range
    if (filter.startDate) {
      activities = activities.filter(
        (a) => a.timestamp >= filter.startDate!
      );
    }
    if (filter.endDate) {
      activities = activities.filter(
        (a) => a.timestamp <= filter.endDate!
      );
    }

    // Sort by timestamp (newest first)
    activities.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());

    // Apply pagination
    if (filter.offset !== undefined) {
      activities = activities.slice(filter.offset);
    }
    if (filter.limit !== undefined) {
      activities = activities.slice(0, filter.limit);
    }

    return activities;
  }

  /**
   * Get activities grouped by date
   */
  getActivitiesGroupedByDate(filter: ActivityFilter = {}): ActivityGroup[] {
    const activities = this.getActivities(filter);
    const groups = new Map<string, Activity[]>();

    for (const activity of activities) {
      const dateKey = activity.timestamp.toISOString().split('T')[0];
      const group = groups.get(dateKey) || [];
      group.push(activity);
      groups.set(dateKey, group);
    }

    return Array.from(groups.entries())
      .map(([date, activities]) => ({
        date,
        activities,
        count: activities.length,
      }))
      .sort((a, b) => b.date.localeCompare(a.date));
  }

  /**
   * Get activity statistics
   */
  getStats(userId: string, startDate?: Date, endDate?: Date): ActivityStats {
    const filter: ActivityFilter = { userId };
    if (startDate) filter.startDate = startDate;
    if (endDate) filter.endDate = endDate;

    const activities = this.getActivities(filter);

    const stats: ActivityStats = {
      totalActivities: activities.length,
      momentsCreated: 0,
      momentsUpdated: 0,
      templatesUsed: 0,
      searchesPerformed: 0,
      summariesGenerated: 0,
      collaborations: 0,
    };

    for (const activity of activities) {
      switch (activity.type) {
        case 'moment_created':
          stats.momentsCreated++;
          break;
        case 'moment_updated':
          stats.momentsUpdated++;
          break;
        case 'template_used':
          stats.templatesUsed++;
          break;
        case 'search_performed':
          stats.searchesPerformed++;
          break;
        case 'summary_generated':
          stats.summariesGenerated++;
          break;
        case 'collaboration_started':
          stats.collaborations++;
          break;
      }
    }

    return stats;
  }

  /**
   * Get recent activities
   */
  getRecentActivities(userId: string, limit: number = 10): Activity[] {
    return this.getActivities({ userId, limit });
  }

  /**
   * Get activity trends (daily counts)
   */
  getActivityTrends(
    userId: string,
    days: number = 30
  ): Array<{ date: string; count: number }> {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - days);

    const activities = this.getActivities({ userId, startDate, endDate });
    const dailyCounts = new Map<string, number>();

    // Initialize all dates with 0
    for (let i = 0; i < days; i++) {
      const date = new Date(startDate);
      date.setDate(date.getDate() + i);
      const dateKey = date.toISOString().split('T')[0];
      dailyCounts.set(dateKey, 0);
    }

    // Count activities per day
    for (const activity of activities) {
      const dateKey = activity.timestamp.toISOString().split('T')[0];
      const currentCount = dailyCounts.get(dateKey) || 0;
      dailyCounts.set(dateKey, currentCount + 1);
    }

    return Array.from(dailyCounts.entries())
      .map(([date, count]) => ({ date, count }))
      .sort((a, b) => a.date.localeCompare(b.date));
  }

  /**
   * Clear old activities
   */
  clearOldActivities(daysToKeep: number = 90): number {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

    let deletedCount = 0;

    for (const [id, activity] of this.activities.entries()) {
      if (activity.timestamp < cutoffDate) {
        this.activities.delete(id);
        deletedCount++;

        // Remove from user's activity list
        const userActivitiesList = this.userActivities.get(activity.userId) || [];
        const index = userActivitiesList.indexOf(id);
        if (index > -1) {
          userActivitiesList.splice(index, 1);
          this.userActivities.set(activity.userId, userActivitiesList);
        }
      }
    }

    return deletedCount;
  }

  /**
   * Generate human-readable description
   */
  private generateDescription(
    type: ActivityType,
    metadata: Record<string, unknown>
  ): string {
    switch (type) {
      case 'moment_created':
        return `Created a new moment${metadata.title ? `: ${metadata.title}` : ''}`;
      case 'moment_updated':
        return `Updated a moment${metadata.title ? `: ${metadata.title}` : ''}`;
      case 'moment_deleted':
        return `Deleted a moment${metadata.title ? `: ${metadata.title}` : ''}`;
      case 'moment_shared':
        return `Shared a moment${metadata.with ? ` with ${metadata.with}` : ''}`;
      case 'tag_added':
        return `Added tag${metadata.tag ? `: ${metadata.tag}` : ''}`;
      case 'template_used':
        return `Used template${metadata.templateName ? `: ${metadata.templateName}` : ''}`;
      case 'search_performed':
        return `Searched for${metadata.query ? `: ${metadata.query}` : ' moments'}`;
      case 'summary_generated':
        return `Generated a summary${metadata.period ? ` for ${metadata.period}` : ''}`;
      case 'collaboration_started':
        return `Started collaborating${metadata.with ? ` with ${metadata.with}` : ''}`;
      case 'comment_added':
        return `Added a comment${metadata.on ? ` on ${metadata.on}` : ''}`;
      default:
        return 'Activity';
    }
  }

  /**
   * Get icon for activity type
   */
  private getActivityIcon(type: ActivityType): string {
    const icons: Record<ActivityType, string> = {
      moment_created: '✨',
      moment_updated: '✏️',
      moment_deleted: '🗑️',
      moment_shared: '📤',
      tag_added: '🏷️',
      template_used: '📝',
      search_performed: '🔍',
      summary_generated: '📊',
      collaboration_started: '🤝',
      comment_added: '💬',
    };
    return icons[type] || '📌';
  }

  /**
   * Get color for activity type
   */
  private getActivityColor(type: ActivityType): string {
    const colors: Record<ActivityType, string> = {
      moment_created: '#10b981',
      moment_updated: '#3b82f6',
      moment_deleted: '#ef4444',
      moment_shared: '#8b5cf6',
      tag_added: '#f59e0b',
      template_used: '#ec4899',
      search_performed: '#6366f1',
      summary_generated: '#14b8a6',
      collaboration_started: '#f97316',
      comment_added: '#84cc16',
    };
    return colors[type] || '#6b7280';
  }

  /**
   * Export activities to JSON
   */
  exportActivities(userId: string): string {
    const activities = this.getActivities({ userId });
    return JSON.stringify(activities, null, 2);
  }

  /**
   * Get activity by ID
   */
  getActivity(id: string): Activity | undefined {
    return this.activities.get(id);
  }

  /**
   * Delete activity
   */
  deleteActivity(id: string): boolean {
    const activity = this.activities.get(id);
    if (!activity) return false;

    this.activities.delete(id);

    // Remove from user's activity list
    const userActivitiesList = this.userActivities.get(activity.userId) || [];
    const index = userActivitiesList.indexOf(id);
    if (index > -1) {
      userActivitiesList.splice(index, 1);
      this.userActivities.set(activity.userId, userActivitiesList);
    }

    return true;
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

let activityFeedServiceInstance: ActivityFeedService | null = null;

/**
 * Get activity feed service instance
 */
export function getActivityFeedService(): ActivityFeedService {
  if (!activityFeedServiceInstance) {
    activityFeedServiceInstance = new ActivityFeedService();
  }
  return activityFeedServiceInstance;
}

export default ActivityFeedService;
