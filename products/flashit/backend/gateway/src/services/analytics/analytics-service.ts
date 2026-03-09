/**
 * Analytics Service for Flashit
 * Handles data aggregation, metrics collection, and insights generation
 *
 * @doc.type service
 * @doc.purpose Comprehensive analytics and insights engine
 * @doc.layer product
 * @doc.pattern AnalyticsService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import { prisma } from '../../lib/prisma.js';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  ...(process.env.REDIS_PASSWORD ? { password: process.env.REDIS_PASSWORD } : {}),
  maxRetriesPerRequest: null,
});

// Queue configuration
const ANALYTICS_QUEUE = 'flashit-analytics';

// Job types
export type AnalyticsJobType =
  | 'daily_aggregation'
  | 'weekly_summary'
  | 'insight_generation'
  | 'trend_analysis'
  | 'real_time_metric'
  | 'export_data';

// Analytics job data interface
interface AnalyticsJobData {
  jobType: AnalyticsJobType;
  userId?: string;
  sphereId?: string;
  dateRange?: {
    from: Date;
    to: Date;
  };
  parameters?: Record<string, any>;
}

// Metric interfaces
export interface UserMetrics {
  userId: string;
  date: Date;
  momentsCreated: number;
  searchesPerformed: number;
  reflectionsGenerated: number;
  sessionDuration: number;
  emotionDiversity: number;
  productivityScore: number;
  importanceAverage: number;
  spheresAccessed: number;
}

export interface InsightData {
  type: 'trend' | 'pattern' | 'anomaly' | 'recommendation' | 'achievement';
  category: 'productivity' | 'emotional' | 'content' | 'growth' | 'usage' | 'social';
  title: string;
  description: string;
  data: Record<string, any>;
  confidence: number;
  priority: number;
  actionable: boolean;
}

// Create analytics queue
export const analyticsQueue = new Queue<AnalyticsJobData>(ANALYTICS_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 500,
    removeOnFail: 100,
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 2000,
    },
  },
});

/**
 * Real-time metrics collector
 */
export class MetricsCollector {

  /**
   * Record a real-time metric
   */
  static async recordMetric(
    metricName: string,
    value: number,
    type: 'counter' | 'gauge' | 'histogram' | 'timer',
    tags?: Record<string, any>,
    userId?: string,
    sphereId?: string
  ): Promise<void> {
    try {
      // Store in database
      await prisma.$executeRaw`
        INSERT INTO analytics.real_time_metrics
        (metric_name, metric_value, metric_type, tags, user_id, sphere_id)
        VALUES (${metricName}, ${value}, ${type}, ${JSON.stringify(tags || {})}, ${userId}, ${sphereId})
      `;

      // Also store in Redis for real-time access
      const redisKey = `metrics:${metricName}:${type}`;
      await redis.zadd(redisKey, Date.now(), JSON.stringify({
        value,
        tags,
        userId,
        sphereId,
        timestamp: Date.now(),
      }));

      // Keep only last 1000 entries per metric
      await redis.zremrangebyrank(redisKey, 0, -1001);

    } catch (error) {
      console.error('Failed to record metric:', error);
    }
  }

  /**
   * Get real-time metrics
   */
  static async getRealtimeMetrics(
    metricName: string,
    since: Date,
    userId?: string,
    sphereId?: string
  ): Promise<Array<{
    value: number;
    timestamp: Date;
    tags?: Record<string, any>;
  }>> {
    try {
      const whereClause = [
        'metric_name = $1',
        'timestamp >= $2'
      ];
      const params: any[] = [metricName, since];

      if (userId) {
        whereClause.push(`user_id = $${params.length + 1}`);
        params.push(userId);
      }

      if (sphereId) {
        whereClause.push(`sphere_id = $${params.length + 1}`);
        params.push(sphereId);
      }

      const results = await prisma.$queryRawUnsafe(`
        SELECT metric_value, timestamp, tags
        FROM analytics.real_time_metrics
        WHERE ${whereClause.join(' AND ')}
        ORDER BY timestamp DESC
        LIMIT 1000
      `, ...params) as any[];

      return results.map(row => ({
        value: parseFloat(row.metric_value),
        timestamp: new Date(row.timestamp),
        tags: row.tags,
      }));

    } catch (error) {
      console.error('Failed to get realtime metrics:', error);
      return [];
    }
  }

  /**
   * Track user activity
   */
  static async trackActivity(
    userId: string,
    activity: string,
    metadata?: Record<string, any>
  ): Promise<void> {
    await this.recordMetric(
      'user_activity',
      1,
      'counter',
      { activity, ...metadata },
      userId
    );
  }

  /**
   * Track search performance
   */
  static async trackSearchPerformance(
    userId: string,
    query: string,
    resultCount: number,
    responseTime: number,
    searchType: string
  ): Promise<void> {
    await this.recordMetric(
      'search_performance',
      responseTime,
      'timer',
      { query, resultCount, searchType },
      userId
    );

    await this.recordMetric(
      'search_results',
      resultCount,
      'gauge',
      { query, searchType },
      userId
    );
  }

  /**
   * Track AI operations
   */
  static async trackAIOperation(
    userId: string,
    operation: string,
    processingTime: number,
    success: boolean,
    cost?: number
  ): Promise<void> {
    await this.recordMetric(
      'ai_operation_time',
      processingTime,
      'timer',
      { operation, success },
      userId
    );

    if (cost !== undefined) {
      await this.recordMetric(
        'ai_operation_cost',
        cost,
        'gauge',
        { operation },
        userId
      );
    }
  }
}

/**
 * Analytics aggregation service
 */
export class AnalyticsAggregator {

  /**
   * Aggregate daily analytics for a specific date
   */
  static async aggregateDailyAnalytics(date: Date): Promise<boolean> {
    try {
      console.log(`Starting daily aggregation for ${date.toISOString().split('T')[0]}`);

      // Call the database function
      await prisma.$executeRaw`
        SELECT analytics.aggregate_daily_analytics(${date.toISOString().split('T')[0]}::DATE)
      `;

      console.log('Daily aggregation completed successfully');
      return true;

    } catch (error) {
      console.error('Daily aggregation failed:', error);
      return false;
    }
  }

  /**
   * Generate weekly summary for a user
   */
  static async generateWeeklySummary(userId: string, weekStart: Date): Promise<any> {
    try {
      const weekEnd = new Date(weekStart);
      weekEnd.setDate(weekEnd.getDate() + 7);

      // Get weekly data
      const weeklyData = await prisma.$queryRaw`
        SELECT
          SUM(moments_created) as total_moments,
          AVG(productivity_score) as avg_productivity,
          AVG(emotion_diversity_score) as avg_emotion_diversity,
          SUM(searches_performed) as total_searches,
          SUM(session_duration_minutes) as total_session_time,
          COUNT(*) as active_days
        FROM analytics.user_analytics
        WHERE user_id = ${userId}::uuid
          AND date_bucket >= ${weekStart}
          AND date_bucket < ${weekEnd}
          AND time_bucket = 'day'
      ` as any[];

      if (weeklyData.length === 0) {
        return null;
      }

      const data = weeklyData[0];

      // Compare with previous week
      const prevWeekStart = new Date(weekStart);
      prevWeekStart.setDate(prevWeekStart.getDate() - 7);

      const prevWeekData = await prisma.$queryRaw`
        SELECT
          AVG(productivity_score) as avg_productivity,
          SUM(moments_created) as total_moments
        FROM analytics.user_analytics
        WHERE user_id = ${userId}::uuid
          AND date_bucket >= ${prevWeekStart}
          AND date_bucket < ${weekStart}
          AND time_bucket = 'day'
      ` as any[];

      const prevData = prevWeekData.length > 0 ? prevWeekData[0] : null;

      // Calculate changes
      const productivityChange = prevData?.avg_productivity
        ? ((data.avg_productivity - prevData.avg_productivity) / prevData.avg_productivity) * 100
        : 0;

      const momentsChange = prevData?.total_moments
        ? ((data.total_moments - prevData.total_moments) / prevData.total_moments) * 100
        : 0;

      return {
        weekStart: weekStart.toISOString(),
        weekEnd: weekEnd.toISOString(),
        totalMoments: parseInt(data.total_moments) || 0,
        avgProductivity: parseFloat(data.avg_productivity) || 0,
        avgEmotionDiversity: parseFloat(data.avg_emotion_diversity) || 0,
        totalSearches: parseInt(data.total_searches) || 0,
        totalSessionTime: parseFloat(data.total_session_time) || 0,
        activeDays: parseInt(data.active_days) || 0,
        changes: {
          productivity: productivityChange,
          moments: momentsChange,
        },
      };

    } catch (error) {
      console.error('Weekly summary generation failed:', error);
      return null;
    }
  }

  /**
   * Get user dashboard data
   */
  static async getUserDashboardData(userId: string): Promise<any> {
    try {
      const now = new Date();
      const thirtyDaysAgo = new Date(now);
      thirtyDaysAgo.setDate(now.getDate() - 30);

      // Get recent analytics
      const recentData = await prisma.$queryRaw`
        SELECT
          date_bucket,
          moments_created,
          productivity_score,
          emotion_diversity_score,
          searches_performed,
          session_duration_minutes
        FROM analytics.user_analytics
        WHERE user_id = ${userId}::uuid
          AND date_bucket >= ${thirtyDaysAgo}
          AND time_bucket = 'day'
        ORDER BY date_bucket ASC
      ` as any[];

      // Get insights
      const insights = await prisma.$queryRaw`
        SELECT
          insight_type,
          insight_category,
          title,
          description,
          insights_data,
          confidence_score,
          priority_score,
          is_actionable,
          created_at
        FROM analytics.user_insights
        WHERE user_id = ${userId}::uuid
          AND created_at >= ${thirtyDaysAgo}
        ORDER BY priority_score DESC, created_at DESC
        LIMIT 10
      ` as any[];

      // Get sphere activity
      const sphereActivity = await prisma.$queryRaw`
        SELECT
          s.id,
          s.name,
          COUNT(m.id) as moment_count,
          AVG(m.importance) as avg_importance
        FROM spheres s
        JOIN moments m ON s.id = m.sphere_id
        WHERE m.user_id = ${userId}::uuid
          AND m.captured_at >= ${thirtyDaysAgo}
          AND m.deleted_at IS NULL
        GROUP BY s.id, s.name
        ORDER BY moment_count DESC
        LIMIT 5
      ` as any[];

      // Calculate trends
      const productivityTrend = this.calculateTrend(
        recentData.map((d: any) => parseFloat(d.productivity_score) || 0)
      );

      const emotionTrend = this.calculateTrend(
        recentData.map((d: any) => parseFloat(d.emotion_diversity_score) || 0)
      );

      return {
        dailyData: recentData.map((d: any) => ({
          date: d.date_bucket,
          moments: parseInt(d.moments_created) || 0,
          productivity: parseFloat(d.productivity_score) || 0,
          emotionDiversity: parseFloat(d.emotion_diversity_score) || 0,
          searches: parseInt(d.searches_performed) || 0,
          sessionTime: parseFloat(d.session_duration_minutes) || 0,
        })),
        insights: insights.map((i: any) => ({
          type: i.insight_type,
          category: i.insight_category,
          title: i.title,
          description: i.description,
          data: i.insights_data,
          confidence: parseFloat(i.confidence_score),
          priority: parseFloat(i.priority_score),
          actionable: i.is_actionable,
          createdAt: i.created_at,
        })),
        sphereActivity: sphereActivity.map((s: any) => ({
          id: s.id,
          name: s.name,
          momentCount: parseInt(s.moment_count),
          avgImportance: parseFloat(s.avg_importance) || 0,
        })),
        trends: {
          productivity: productivityTrend,
          emotion: emotionTrend,
        },
        summary: {
          totalMoments: recentData.reduce((sum: number, d: any) => sum + (parseInt(d.moments_created) || 0), 0),
          avgProductivity: recentData.length > 0
            ? recentData.reduce((sum: number, d: any) => sum + (parseFloat(d.productivity_score) || 0), 0) / recentData.length
            : 0,
          totalSearches: recentData.reduce((sum: number, d: any) => sum + (parseInt(d.searches_performed) || 0), 0),
          activeDays: recentData.filter((d: any) => parseInt(d.moments_created) > 0).length,
        },
      };

    } catch (error) {
      console.error('Failed to get user dashboard data:', error);
      return null;
    }
  }

  /**
   * Calculate trend direction
   */
  private static calculateTrend(values: number[]): 'up' | 'down' | 'stable' {
    if (values.length < 2) return 'stable';

    const recent = values.slice(-7); // Last 7 days
    const earlier = values.slice(-14, -7); // Previous 7 days

    if (recent.length === 0 || earlier.length === 0) return 'stable';

    const recentAvg = recent.reduce((sum, val) => sum + val, 0) / recent.length;
    const earlierAvg = earlier.reduce((sum, val) => sum + val, 0) / earlier.length;

    const change = ((recentAvg - earlierAvg) / earlierAvg) * 100;

    if (change > 5) return 'up';
    if (change < -5) return 'down';
    return 'stable';
  }
}

/**
 * Insights generator
 */
export class InsightsGenerator {

  /**
   * Generate insights for a user
   */
  static async generateInsights(userId: string): Promise<InsightData[]> {
    try {
      // Call the database function
      const result = await prisma.$queryRaw`
        SELECT analytics.generate_user_insights(${userId}::uuid) as insight_count
      ` as any[];

      const insightCount = result[0]?.insight_count || 0;

      if (insightCount > 0) {
        // Get the newly generated insights
        const insights = await prisma.$queryRaw`
          SELECT
            insight_type,
            insight_category,
            title,
            description,
            insights_data,
            confidence_score,
            priority_score,
            is_actionable
          FROM analytics.user_insights
          WHERE user_id = ${userId}::uuid
            AND created_at >= NOW() - INTERVAL '1 hour'
          ORDER BY created_at DESC
          LIMIT ${insightCount}
        ` as any[];

        return insights.map((i: any) => ({
          type: i.insight_type,
          category: i.insight_category,
          title: i.title,
          description: i.description,
          data: i.insights_data,
          confidence: parseFloat(i.confidence_score),
          priority: parseFloat(i.priority_score),
          actionable: i.is_actionable,
        }));
      }

      return [];

    } catch (error) {
      console.error('Failed to generate insights:', error);
      return [];
    }
  }

  /**
   * Generate mood pattern insights
   */
  static async generateMoodPatternInsights(userId: string): Promise<InsightData | null> {
    try {
      // Get emotion data for the last 30 days
      const emotionData = await prisma.$queryRaw`
        SELECT
          date_bucket,
          dominant_emotions,
          emotion_trend,
          emotion_diversity_score
        FROM analytics.user_analytics
        WHERE user_id = ${userId}::uuid
          AND date_bucket >= CURRENT_DATE - INTERVAL '30 days'
          AND time_bucket = 'day'
          AND dominant_emotions IS NOT NULL
        ORDER BY date_bucket ASC
      ` as any[];

      if (emotionData.length < 7) {
        return null;
      }

      // Analyze patterns
      const trends = emotionData.map((d: any) => d.emotion_trend);
      const positiveDays = trends.filter((t: string) => t === 'positive').length;
      const negativeDays = trends.filter((t: string) => t === 'negative').length;
      const totalDays = emotionData.length;

      const positiveRatio = positiveDays / totalDays;
      const negativeRatio = negativeDays / totalDays;

      let insight: InsightData | null = null;

      if (positiveRatio > 0.7) {
        insight = {
          type: 'pattern',
          category: 'emotional',
          title: 'Consistently Positive Mood',
          description: `You've maintained a positive emotional tone ${(positiveRatio * 100).toFixed(1)}% of the time over the last ${totalDays} days. This suggests good emotional well-being!`,
          data: {
            positiveDays,
            totalDays,
            positiveRatio,
            avgDiversity: emotionData.reduce((sum: number, d: any) => sum + (parseFloat(d.emotion_diversity_score) || 0), 0) / emotionData.length,
          },
          confidence: 85,
          priority: 60,
          actionable: false,
        };
      } else if (negativeRatio > 0.5) {
        insight = {
          type: 'pattern',
          category: 'emotional',
          title: 'Emotional Support Opportunity',
          description: `You've experienced more challenging emotional periods recently (${(negativeRatio * 100).toFixed(1)}% of days). Consider focusing on self-care activities or reaching out for support.`,
          data: {
            negativeDays,
            totalDays,
            negativeRatio,
            recentTrend: trends.slice(-7),
          },
          confidence: 80,
          priority: 85,
          actionable: true,
        };
      }

      return insight;

    } catch (error) {
      console.error('Failed to generate mood pattern insights:', error);
      return null;
    }
  }

  /**
   * Generate productivity insights
   */
  static async generateProductivityInsights(userId: string): Promise<InsightData[]> {
    try {
      const insights: InsightData[] = [];

      // Get productivity data
      const productivityData = await prisma.$queryRaw`
        SELECT
          date_bucket,
          productivity_score,
          moments_created,
          importance_avg,
          session_duration_minutes
        FROM analytics.user_analytics
        WHERE user_id = ${userId}::uuid
          AND date_bucket >= CURRENT_DATE - INTERVAL '14 days'
          AND time_bucket = 'day'
          AND productivity_score IS NOT NULL
        ORDER BY date_bucket ASC
      ` as any[];

      if (productivityData.length < 5) {
        return insights;
      }

      // Analyze peak productivity times
      const scores = productivityData.map((d: any) => parseFloat(d.productivity_score));
      const avgScore = scores.reduce((sum, score) => sum + score, 0) / scores.length;
      const maxScore = Math.max(...scores);
      const recentAvg = scores.slice(-3).reduce((sum, score) => sum + score, 0) / Math.min(3, scores.length);

      // Peak performance insight
      if (maxScore > 80 && avgScore > 60) {
        insights.push({
          type: 'achievement',
          category: 'productivity',
          title: 'High Performance Period',
          description: `You've achieved excellent productivity with a peak score of ${maxScore.toFixed(1)} and an average of ${avgScore.toFixed(1)} over the last two weeks.`,
          data: {
            maxScore,
            avgScore,
            recentAvg,
            totalDays: productivityData.length,
          },
          confidence: 90,
          priority: 70,
          actionable: false,
        });
      }

      // Consistency insight
      const variance = scores.reduce((sum, score) => sum + Math.pow(score - avgScore, 2), 0) / scores.length;
      const standardDeviation = Math.sqrt(variance);

      if (standardDeviation < 15 && avgScore > 50) {
        insights.push({
          type: 'pattern',
          category: 'productivity',
          title: 'Consistent Productivity',
          description: `You maintain steady productivity with low variation (${standardDeviation.toFixed(1)}). This consistency is a great foundation for sustained growth.`,
          data: {
            avgScore,
            standardDeviation,
            consistency: 'high',
          },
          confidence: 85,
          priority: 65,
          actionable: false,
        });
      }

      return insights;

    } catch (error) {
      console.error('Failed to generate productivity insights:', error);
      return [];
    }
  }
}

/**
 * Analytics service main class
 */
export class AnalyticsService {

  /**
   * Schedule analytics job
   */
  static async scheduleJob(jobData: AnalyticsJobData): Promise<string> {
    const job = await analyticsQueue.add(jobData.jobType, jobData, {
      priority: jobData.jobType === 'real_time_metric' ? 10 : 5,
      jobId: `${jobData.jobType}-${Date.now()}`,
    });

    return job.id!;
  }

  /**
   * Get analytics queue statistics
   */
  static async getQueueStats(): Promise<{
    waiting: number;
    active: number;
    completed: number;
    failed: number;
  }> {
    const waiting = await analyticsQueue.getWaiting();
    const active = await analyticsQueue.getActive();
    const completed = await analyticsQueue.getCompleted();
    const failed = await analyticsQueue.getFailed();

    return {
      waiting: waiting.length,
      active: active.length,
      completed: completed.length,
      failed: failed.length,
    };
  }

  /**
   * Trigger daily aggregation for yesterday
   */
  static async triggerDailyAggregation(): Promise<boolean> {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);

    await this.scheduleJob({
      jobType: 'daily_aggregation',
      parameters: { date: yesterday.toISOString() },
    });

    return true;
  }

  /**
   * Generate insights for all active users
   */
  static async generateAllUserInsights(): Promise<void> {
    // Get active users (users with activity in last 7 days)
    const activeUsers = await prisma.$queryRaw`
      SELECT DISTINCT user_id
      FROM analytics.user_analytics
      WHERE date_bucket >= CURRENT_DATE - INTERVAL '7 days'
    ` as any[];

    for (const user of activeUsers) {
      await this.scheduleJob({
        jobType: 'insight_generation',
        userId: user.user_id,
      });
    }
  }
}

/**
 * Analytics worker - processes analytics jobs
 */
const analyticsWorker = new Worker<AnalyticsJobData>(
  ANALYTICS_QUEUE,
  async (job: Job<AnalyticsJobData>) => {
    const { data } = job;

    try {
      await job.updateProgress(10);

      switch (data.jobType) {
        case 'daily_aggregation':
          const date = data.parameters?.date ? new Date(data.parameters.date) : new Date();
          await AnalyticsAggregator.aggregateDailyAnalytics(date);
          break;

        case 'weekly_summary':
          if (data.userId) {
            const weekStart = data.parameters?.weekStart ? new Date(data.parameters.weekStart) : new Date();
            await AnalyticsAggregator.generateWeeklySummary(data.userId, weekStart);
          }
          break;

        case 'insight_generation':
          if (data.userId) {
            await InsightsGenerator.generateInsights(data.userId);
          }
          break;

        default:
          throw new Error(`Unknown job type: ${data.jobType}`);
      }

      await job.updateProgress(100);

    } catch (error: any) {
      console.error(`Analytics job ${data.jobType} failed:`, error);
      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 5, // Process up to 5 analytics jobs concurrently
  }
);

// Worker event handlers
analyticsWorker.on('completed', (job) => {
  console.log(`Analytics job ${job.data.jobType} completed successfully`);
});

analyticsWorker.on('failed', (job, err) => {
  console.error(`Analytics job ${job?.data.jobType} failed:`, err);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down analytics worker...');
  await analyticsWorker.close();
  await prisma.$disconnect();
  await redis.quit();
  process.exit(0);
});

export { analyticsWorker };
