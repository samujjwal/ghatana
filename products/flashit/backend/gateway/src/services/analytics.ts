/**
 * Analytics Service
 * Generate analytics and insights
 *
 * @doc.type service
 * @doc.purpose Analytics aggregation and computation
 * @doc.layer product
 * @doc.pattern AnalyticsService
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface AnalyticsData {
  timeRange: {
    start: Date;
    end: Date;
  };
  totalMoments: number;
  momentsCreated: number;
  momentsUpdated: number;
  momentsDeleted: number;
  activeUsers: number;
  avgMomentsPerDay: number;
  mostActiveDay: string;
  mostActiveHour: number;
  topTags: Array<{ tag: string; count: number }>;
  topCategories: Array<{ category: string; count: number }>;
  moodDistribution: Array<{ mood: string; count: number; percentage: number }>;
  wordCloud: Array<{ word: string; count: number }>;
  timelineData: Array<{ date: string; count: number }>;
  hourlyDistribution: Array<{ hour: number; count: number }>;
  weekdayDistribution: Array<{ day: string; count: number }>;
  engagementScore: number;
  streakDays: number;
  longestStreak: number;
}

export interface MomentData {
  id: string;
  userId: string;
  createdAt: Date;
  updatedAt: Date;
  tags: string[];
  category?: string;
  mood?: string;
  content: string;
  wordCount: number;
}

export interface UserEngagement {
  userId: string;
  totalMoments: number;
  activeDays: number;
  avgMomentsPerDay: number;
  currentStreak: number;
  longestStreak: number;
  lastActivity: Date;
}

// ============================================================================
// Analytics Service
// ============================================================================

/**
 * AnalyticsService generates insights and analytics
 */
class AnalyticsService {
  private moments: Map<string, MomentData> = new Map();

  /**
   * Add moment data
   */
  addMoment(moment: MomentData): void {
    this.moments.set(moment.id, moment);
  }

  /**
   * Bulk add moments
   */
  addMoments(moments: MomentData[]): void {
    for (const moment of moments) {
      this.moments.set(moment.id, moment);
    }
  }

  /**
   * Generate analytics for a user
   */
  generateAnalytics(
    userId: string,
    startDate: Date,
    endDate: Date
  ): AnalyticsData {
    const userMoments = this.filterMoments(userId, startDate, endDate);

    return {
      timeRange: { start: startDate, end: endDate },
      totalMoments: userMoments.length,
      momentsCreated: this.countCreatedMoments(userMoments, startDate, endDate),
      momentsUpdated: this.countUpdatedMoments(userMoments, startDate, endDate),
      momentsDeleted: 0, // Would need deletion tracking
      activeUsers: 1, // Single user analytics
      avgMomentsPerDay: this.calculateAvgMomentsPerDay(userMoments, startDate, endDate),
      mostActiveDay: this.findMostActiveDay(userMoments),
      mostActiveHour: this.findMostActiveHour(userMoments),
      topTags: this.getTopTags(userMoments, 10),
      topCategories: this.getTopCategories(userMoments, 5),
      moodDistribution: this.getMoodDistribution(userMoments),
      wordCloud: this.generateWordCloud(userMoments, 50),
      timelineData: this.generateTimelineData(userMoments, startDate, endDate),
      hourlyDistribution: this.getHourlyDistribution(userMoments),
      weekdayDistribution: this.getWeekdayDistribution(userMoments),
      engagementScore: this.calculateEngagementScore(userMoments, startDate, endDate),
      streakDays: this.calculateCurrentStreak(userMoments),
      longestStreak: this.calculateLongestStreak(userMoments),
    };
  }

  /**
   * Filter moments by user and date range
   */
  private filterMoments(
    userId: string,
    startDate: Date,
    endDate: Date
  ): MomentData[] {
    return Array.from(this.moments.values()).filter(
      (moment) =>
        moment.userId === userId &&
        moment.createdAt >= startDate &&
        moment.createdAt <= endDate
    );
  }

  /**
   * Count created moments
   */
  private countCreatedMoments(
    moments: MomentData[],
    startDate: Date,
    endDate: Date
  ): number {
    return moments.filter(
      (m) => m.createdAt >= startDate && m.createdAt <= endDate
    ).length;
  }

  /**
   * Count updated moments
   */
  private countUpdatedMoments(
    moments: MomentData[],
    startDate: Date,
    endDate: Date
  ): number {
    return moments.filter(
      (m) =>
        m.updatedAt >= startDate &&
        m.updatedAt <= endDate &&
        m.updatedAt.getTime() !== m.createdAt.getTime()
    ).length;
  }

  /**
   * Calculate average moments per day
   */
  private calculateAvgMomentsPerDay(
    moments: MomentData[],
    startDate: Date,
    endDate: Date
  ): number {
    const days = Math.ceil(
      (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24)
    );
    return days > 0 ? moments.length / days : 0;
  }

  /**
   * Find most active day
   */
  private findMostActiveDay(moments: MomentData[]): string {
    const dayCounts = new Map<string, number>();

    for (const moment of moments) {
      const day = moment.createdAt.toISOString().split('T')[0];
      dayCounts.set(day, (dayCounts.get(day) || 0) + 1);
    }

    let maxDay = '';
    let maxCount = 0;

    for (const [day, count] of dayCounts.entries()) {
      if (count > maxCount) {
        maxCount = count;
        maxDay = day;
      }
    }

    return maxDay;
  }

  /**
   * Find most active hour
   */
  private findMostActiveHour(moments: MomentData[]): number {
    const hourCounts = new Array(24).fill(0);

    for (const moment of moments) {
      const hour = moment.createdAt.getHours();
      hourCounts[hour]++;
    }

    let maxHour = 0;
    let maxCount = 0;

    for (let hour = 0; hour < 24; hour++) {
      if (hourCounts[hour] > maxCount) {
        maxCount = hourCounts[hour];
        maxHour = hour;
      }
    }

    return maxHour;
  }

  /**
   * Get top tags
   */
  private getTopTags(
    moments: MomentData[],
    limit: number
  ): Array<{ tag: string; count: number }> {
    const tagCounts = new Map<string, number>();

    for (const moment of moments) {
      for (const tag of moment.tags) {
        tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
      }
    }

    return Array.from(tagCounts.entries())
      .map(([tag, count]) => ({ tag, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, limit);
  }

  /**
   * Get top categories
   */
  private getTopCategories(
    moments: MomentData[],
    limit: number
  ): Array<{ category: string; count: number }> {
    const categoryCounts = new Map<string, number>();

    for (const moment of moments) {
      if (moment.category) {
        categoryCounts.set(
          moment.category,
          (categoryCounts.get(moment.category) || 0) + 1
        );
      }
    }

    return Array.from(categoryCounts.entries())
      .map(([category, count]) => ({ category, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, limit);
  }

  /**
   * Get mood distribution
   */
  private getMoodDistribution(
    moments: MomentData[]
  ): Array<{ mood: string; count: number; percentage: number }> {
    const moodCounts = new Map<string, number>();
    let totalWithMood = 0;

    for (const moment of moments) {
      if (moment.mood) {
        moodCounts.set(moment.mood, (moodCounts.get(moment.mood) || 0) + 1);
        totalWithMood++;
      }
    }

    return Array.from(moodCounts.entries())
      .map(([mood, count]) => ({
        mood,
        count,
        percentage: totalWithMood > 0 ? (count / totalWithMood) * 100 : 0,
      }))
      .sort((a, b) => b.count - a.count);
  }

  /**
   * Generate word cloud data
   */
  private generateWordCloud(
    moments: MomentData[],
    limit: number
  ): Array<{ word: string; count: number }> {
    const wordCounts = new Map<string, number>();
    const stopWords = new Set([
      'the',
      'a',
      'an',
      'and',
      'or',
      'but',
      'in',
      'on',
      'at',
      'to',
      'for',
      'of',
      'is',
      'it',
      'that',
      'this',
      'with',
      'as',
      'was',
      'be',
      'by',
    ]);

    for (const moment of moments) {
      const words = moment.content
        .toLowerCase()
        .replace(/[^\w\s]/g, '')
        .split(/\s+/);

      for (const word of words) {
        if (word.length > 3 && !stopWords.has(word)) {
          wordCounts.set(word, (wordCounts.get(word) || 0) + 1);
        }
      }
    }

    return Array.from(wordCounts.entries())
      .map(([word, count]) => ({ word, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, limit);
  }

  /**
   * Generate timeline data
   */
  private generateTimelineData(
    moments: MomentData[],
    startDate: Date,
    endDate: Date
  ): Array<{ date: string; count: number }> {
    const dailyCounts = new Map<string, number>();

    // Initialize all dates with 0
    const currentDate = new Date(startDate);
    while (currentDate <= endDate) {
      const dateKey = currentDate.toISOString().split('T')[0];
      dailyCounts.set(dateKey, 0);
      currentDate.setDate(currentDate.getDate() + 1);
    }

    // Count moments per day
    for (const moment of moments) {
      const dateKey = moment.createdAt.toISOString().split('T')[0];
      dailyCounts.set(dateKey, (dailyCounts.get(dateKey) || 0) + 1);
    }

    return Array.from(dailyCounts.entries())
      .map(([date, count]) => ({ date, count }))
      .sort((a, b) => a.date.localeCompare(b.date));
  }

  /**
   * Get hourly distribution
   */
  private getHourlyDistribution(
    moments: MomentData[]
  ): Array<{ hour: number; count: number }> {
    const hourCounts = new Array(24).fill(0);

    for (const moment of moments) {
      const hour = moment.createdAt.getHours();
      hourCounts[hour]++;
    }

    return hourCounts.map((count, hour) => ({ hour, count }));
  }

  /**
   * Get weekday distribution
   */
  private getWeekdayDistribution(
    moments: MomentData[]
  ): Array<{ day: string; count: number }> {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const dayCounts = new Array(7).fill(0);

    for (const moment of moments) {
      const dayIndex = moment.createdAt.getDay();
      dayCounts[dayIndex]++;
    }

    return dayCounts.map((count, index) => ({ day: days[index], count }));
  }

  /**
   * Calculate engagement score (0-100)
   */
  private calculateEngagementScore(
    moments: MomentData[],
    startDate: Date,
    endDate: Date
  ): number {
    const days = Math.ceil(
      (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24)
    );
    
    const avgPerDay = days > 0 ? moments.length / days : 0;
    const currentStreak = this.calculateCurrentStreak(moments);
    const longestStreak = this.calculateLongestStreak(moments);
    
    // Score components (weighted)
    const avgScore = Math.min(avgPerDay * 20, 40); // Max 40 points
    const streakScore = Math.min(currentStreak * 3, 30); // Max 30 points
    const longestStreakScore = Math.min(longestStreak * 2, 30); // Max 30 points
    
    return Math.round(avgScore + streakScore + longestStreakScore);
  }

  /**
   * Calculate current streak
   */
  private calculateCurrentStreak(moments: MomentData[]): number {
    if (moments.length === 0) return 0;

    const sortedMoments = moments
      .slice()
      .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    let streak = 0;
    let currentDate = new Date(today);

    while (true) {
      const hasActivity = sortedMoments.some((moment) => {
        const momentDate = new Date(moment.createdAt);
        momentDate.setHours(0, 0, 0, 0);
        return momentDate.getTime() === currentDate.getTime();
      });

      if (!hasActivity) break;

      streak++;
      currentDate.setDate(currentDate.getDate() - 1);
    }

    return streak;
  }

  /**
   * Calculate longest streak
   */
  private calculateLongestStreak(moments: MomentData[]): number {
    if (moments.length === 0) return 0;

    const sortedMoments = moments
      .slice()
      .sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());

    const uniqueDates = new Set<string>();
    for (const moment of sortedMoments) {
      const dateKey = moment.createdAt.toISOString().split('T')[0];
      uniqueDates.add(dateKey);
    }

    const dates = Array.from(uniqueDates).sort();
    let longestStreak = 0;
    let currentStreak = 1;

    for (let i = 1; i < dates.length; i++) {
      const prevDate = new Date(dates[i - 1]);
      const currDate = new Date(dates[i]);
      const diffDays = Math.floor(
        (currDate.getTime() - prevDate.getTime()) / (1000 * 60 * 60 * 24)
      );

      if (diffDays === 1) {
        currentStreak++;
      } else {
        longestStreak = Math.max(longestStreak, currentStreak);
        currentStreak = 1;
      }
    }

    return Math.max(longestStreak, currentStreak);
  }

  /**
   * Clear all data
   */
  clear(): void {
    this.moments.clear();
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

let analyticsServiceInstance: AnalyticsService | null = null;

/**
 * Get analytics service instance
 */
export function getAnalyticsService(): AnalyticsService {
  if (!analyticsServiceInstance) {
    analyticsServiceInstance = new AnalyticsService();
  }
  return analyticsServiceInstance;
}

export default AnalyticsService;
