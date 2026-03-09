/**
 * Notification System for Flashit
 * Multi-channel notifications with smart filtering and delivery optimization
 *
 * @doc.type service
 * @doc.purpose Comprehensive notification system
 * @doc.layer product
 * @doc.pattern NotificationService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import { PrismaClient } from '@prisma/client';
import nodemailer from 'nodemailer';
import webpush from 'web-push';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
  maxRetriesPerRequest: 3,
});

// Prisma client
const prisma = new PrismaClient();

// Queue configuration
const NOTIFICATION_QUEUE = 'flashit:notifications';

// Configure web push
webpush.setVapidDetails(
  'mailto:notifications@flashit.app',
  process.env.VAPID_PUBLIC_KEY || '',
  process.env.VAPID_PRIVATE_KEY || ''
);

// Notification interfaces
export type NotificationChannel = 'in_app' | 'email' | 'push' | 'sms';
export type NotificationPriority = 'low' | 'normal' | 'high' | 'urgent';

export interface NotificationTemplate {
  type: string;
  channels: NotificationChannel[];
  priority: NotificationPriority;
  title: string;
  message: string;
  actionUrl?: string;
  emailTemplate?: string;
  pushTemplate?: string;
  aggregatable?: boolean;
  quietHoursRespect?: boolean;
}

export interface NotificationJob {
  userId: string;
  template: NotificationTemplate;
  data: Record<string, any>;
  channels?: NotificationChannel[];
  priority?: NotificationPriority;
  scheduledAt?: Date;
  expiresAt?: Date;
}

export interface NotificationPreferences {
  emailNotifications: boolean;
  pushNotifications: boolean;
  inAppNotifications: boolean;
  sphereShares: boolean;
  momentComments: boolean;
  momentReactions: boolean;
  newFollowers: boolean;
  mentionNotifications: boolean;
  digestFrequency: 'never' | 'daily' | 'weekly' | 'monthly';
  quietHoursEnabled: boolean;
  quietHoursStart?: string;
  quietHoursEnd?: string;
  quietHoursTimezone?: string;
}

// Notification templates
const NOTIFICATION_TEMPLATES: Record<string, NotificationTemplate> = {
  sphere_shared: {
    type: 'sphere_shared',
    channels: ['in_app', 'email'],
    priority: 'normal',
    title: '{{sharedBy}} shared a sphere with you',
    message: '{{sharedBy}} shared "{{sphereName}}" with {{permissionLevel}} access',
    actionUrl: '/spheres/{{sphereId}}',
    emailTemplate: 'sphere_shared',
    aggregatable: false,
    quietHoursRespect: true,
  },

  moment_commented: {
    type: 'moment_commented',
    channels: ['in_app', 'push'],
    priority: 'normal',
    title: 'New comment on your moment',
    message: '{{commenterName}} commented on "{{momentTitle}}"',
    actionUrl: '/moments/{{momentId}}#comment-{{commentId}}',
    aggregatable: true,
    quietHoursRespect: true,
  },

  moment_reaction: {
    type: 'moment_reaction',
    channels: ['in_app'],
    priority: 'low',
    title: '{{reactorName}} reacted to your moment',
    message: '{{reactorName}} {{reactionType}} your moment "{{momentTitle}}"',
    actionUrl: '/moments/{{momentId}}',
    aggregatable: true,
    quietHoursRespect: true,
  },

  user_followed: {
    type: 'user_followed',
    channels: ['in_app', 'push'],
    priority: 'low',
    title: '{{followerName}} started following you',
    message: '{{followerName}} is now following your activity',
    actionUrl: '/users/{{followerId}}',
    aggregatable: true,
    quietHoursRespect: true,
  },

  mention_in_comment: {
    type: 'mention_in_comment',
    channels: ['in_app', 'email', 'push'],
    priority: 'high',
    title: '{{mentionerName}} mentioned you',
    message: '{{mentionerName}} mentioned you in a comment on "{{momentTitle}}"',
    actionUrl: '/moments/{{momentId}}#comment-{{commentId}}',
    emailTemplate: 'mention_notification',
    aggregatable: false,
    quietHoursRespect: false,
  },

  reflection_generated: {
    type: 'reflection_generated',
    channels: ['in_app'],
    priority: 'low',
    title: 'New insights available',
    message: 'AI has generated {{insightCount}} new insights for your moments',
    actionUrl: '/analytics/insights',
    aggregatable: true,
    quietHoursRespect: true,
  },

  milestone_achieved: {
    type: 'milestone_achieved',
    channels: ['in_app', 'push'],
    priority: 'normal',
    title: '🎉 Milestone achieved!',
    message: 'You\'ve reached {{milestoneName}}: {{description}}',
    actionUrl: '/analytics',
    aggregatable: false,
    quietHoursRespect: true,
  },

  digest_weekly: {
    type: 'digest_weekly',
    channels: ['email'],
    priority: 'low',
    title: 'Your weekly Flashit summary',
    message: 'Here\'s what happened in your spheres this week',
    emailTemplate: 'weekly_digest',
    aggregatable: false,
    quietHoursRespect: false,
  },
};

// Create notification queue
export const notificationQueue = new Queue<NotificationJob>(NOTIFICATION_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 1000,
    removeOnFail: 100,
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 2000,
    },
  },
});

/**
 * Notification service
 */
export class NotificationService {

  /**
   * Send notification
   */
  static async sendNotification(
    userId: string,
    templateType: string,
    data: Record<string, any>,
    options: {
      channels?: NotificationChannel[];
      priority?: NotificationPriority;
      scheduledAt?: Date;
      expiresAt?: Date;
    } = {}
  ): Promise<string> {
    const template = NOTIFICATION_TEMPLATES[templateType];
    if (!template) {
      throw new Error(`Unknown notification template: ${templateType}`);
    }

    // Check user preferences
    const preferences = await this.getUserPreferences(userId);
    if (!this.shouldSendNotification(template, preferences, options.channels)) {
      console.log(`Notification blocked by user preferences: ${templateType} for user ${userId}`);
      return '';
    }

    // Check quiet hours
    if (template.quietHoursRespect && this.isQuietHours(preferences)) {
      // Schedule for after quiet hours
      const nextActiveTime = this.getNextActiveTime(preferences);
      options.scheduledAt = nextActiveTime;
    }

    // Enqueue notification job
    const job = await notificationQueue.add('send-notification', {
      userId,
      template: {
        ...template,
        channels: options.channels || template.channels,
        priority: options.priority || template.priority,
      },
      data,
      scheduledAt: options.scheduledAt,
      expiresAt: options.expiresAt,
    }, {
      delay: options.scheduledAt ? options.scheduledAt.getTime() - Date.now() : 0,
      priority: this.getPriority(options.priority || template.priority),
    });

    return job.id!;
  }

  /**
   * Send sphere shared notification
   */
  static async sendSphereSharedNotification(
    userId: string,
    data: {
      sharedBy: string;
      sphereName: string;
      sphereId: string;
      permissionLevel: string;
      message?: string;
    }
  ): Promise<string> {
    return this.sendNotification(userId, 'sphere_shared', data);
  }

  /**
   * Send comment notification
   */
  static async sendCommentNotification(
    userId: string,
    data: {
      commenterName: string;
      momentTitle: string;
      momentId: string;
      commentId: string;
      commentText: string;
    }
  ): Promise<string> {
    return this.sendNotification(userId, 'moment_commented', data);
  }

  /**
   * Send mention notification
   */
  static async sendMentionNotification(
    userId: string,
    data: {
      mentionerName: string;
      momentTitle: string;
      momentId: string;
      commentId: string;
      commentText: string;
    }
  ): Promise<string> {
    return this.sendNotification(userId, 'mention_in_comment', data, {
      priority: 'high',
    });
  }

  /**
   * Send weekly digest
   */
  static async sendWeeklyDigest(userId: string): Promise<string> {
    // Generate digest data
    const digestData = await this.generateWeeklyDigestData(userId);

    if (!digestData.hasActivity) {
      return ''; // No digest to send
    }

    return this.sendNotification(userId, 'digest_weekly', digestData);
  }

  /**
   * Get user notification preferences
   */
  static async getUserPreferences(userId: string): Promise<NotificationPreferences> {
    const preferences = await prisma.collaboration.notificationPreference.findUnique({
      where: { userId },
    });

    if (!preferences) {
      // Return default preferences
      return {
        emailNotifications: true,
        pushNotifications: true,
        inAppNotifications: true,
        sphereShares: true,
        momentComments: true,
        momentReactions: true,
        newFollowers: true,
        mentionNotifications: true,
        digestFrequency: 'weekly',
        quietHoursEnabled: false,
      };
    }

    return {
      emailNotifications: preferences.emailNotifications,
      pushNotifications: preferences.pushNotifications,
      inAppNotifications: preferences.inAppNotifications,
      sphereShares: preferences.sphereShares,
      momentComments: preferences.momentComments,
      momentReactions: preferences.momentReactions,
      newFollowers: preferences.newFollowers,
      mentionNotifications: preferences.mentionNotifications,
      digestFrequency: preferences.digestFrequency as any,
      quietHoursEnabled: preferences.quietHoursEnabled,
      quietHoursStart: preferences.quietHoursStart,
      quietHoursEnd: preferences.quietHoursEnd,
      quietHoursTimezone: preferences.quietHoursTimezone,
    };
  }

  /**
   * Check if notification should be sent based on preferences
   */
  private static shouldSendNotification(
    template: NotificationTemplate,
    preferences: NotificationPreferences,
    overrideChannels?: NotificationChannel[]
  ): boolean {
    const channels = overrideChannels || template.channels;

    // Check global preferences
    if (channels.includes('email') && !preferences.emailNotifications) {
      return false;
    }
    if (channels.includes('push') && !preferences.pushNotifications) {
      return false;
    }
    if (channels.includes('in_app') && !preferences.inAppNotifications) {
      return false;
    }

    // Check specific notification type preferences
    switch (template.type) {
      case 'sphere_shared':
        return preferences.sphereShares;
      case 'moment_commented':
        return preferences.momentComments;
      case 'moment_reaction':
        return preferences.momentReactions;
      case 'user_followed':
        return preferences.newFollowers;
      case 'mention_in_comment':
        return preferences.mentionNotifications;
      default:
        return true;
    }
  }

  /**
   * Check if current time is within quiet hours
   */
  private static isQuietHours(preferences: NotificationPreferences): boolean {
    if (!preferences.quietHoursEnabled || !preferences.quietHoursStart || !preferences.quietHoursEnd) {
      return false;
    }

    try {
      const now = new Date();
      const timezone = preferences.quietHoursTimezone || 'UTC';

      const currentTime = new Intl.DateTimeFormat('en', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        timeZone: timezone,
      }).format(now);

      const start = preferences.quietHoursStart;
      const end = preferences.quietHoursEnd;

      // Handle overnight quiet hours (e.g., 22:00 to 08:00)
      if (start > end) {
        return currentTime >= start || currentTime <= end;
      } else {
        return currentTime >= start && currentTime <= end;
      }
    } catch (error) {
      console.error('Failed to check quiet hours:', error);
      return false;
    }
  }

  /**
   * Get next time when notifications should be active
   */
  private static getNextActiveTime(preferences: NotificationPreferences): Date {
    if (!preferences.quietHoursEnabled || !preferences.quietHoursEnd) {
      return new Date();
    }

    const now = new Date();
    const [endHour, endMinute] = preferences.quietHoursEnd.split(':').map(Number);

    const nextActive = new Date(now);
    nextActive.setHours(endHour, endMinute, 0, 0);

    // If end time is in the past today, schedule for tomorrow
    if (nextActive <= now) {
      nextActive.setDate(nextActive.getDate() + 1);
    }

    return nextActive;
  }

  /**
   * Convert priority to BullMQ priority number
   */
  private static getPriority(priority: NotificationPriority): number {
    switch (priority) {
      case 'urgent': return 10;
      case 'high': return 7;
      case 'normal': return 5;
      case 'low': return 3;
      default: return 5;
    }
  }

  /**
   * Generate weekly digest data
   */
  private static async generateWeeklyDigestData(userId: string): Promise<any> {
    const oneWeekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);

    const [
      weeklyStats,
      recentMoments,
      recentInsights,
      collaborationActivity,
    ] = await Promise.all([
      // Weekly stats
      prisma.analytics.userAnalytics.findFirst({
        where: {
          userId,
          dateBucket: { gte: oneWeekAgo },
          timeBucket: 'week',
        },
        orderBy: { dateBucket: 'desc' },
      }),

      // Recent moments
      prisma.moment.findMany({
        where: {
          userId,
          capturedAt: { gte: oneWeekAgo },
          deletedAt: null,
        },
        select: {
          contentText: true,
          importance: true,
          capturedAt: true,
          sphere: { select: { name: true } },
        },
        take: 5,
        orderBy: { importance: 'desc' },
      }),

      // Recent insights
      prisma.analytics.userInsight.findMany({
        where: {
          userId,
          createdAt: { gte: oneWeekAgo },
        },
        select: {
          title: true,
          insightCategory: true,
          confidenceScore: true,
        },
        take: 3,
        orderBy: { priorityScore: 'desc' },
      }),

      // Collaboration activity
      prisma.collaboration.activityFeed.count({
        where: {
          userId,
          createdAt: { gte: oneWeekAgo },
        },
      }),
    ]);

    const hasActivity = (recentMoments.length > 0) ||
      (recentInsights.length > 0) ||
      (collaborationActivity > 0);

    return {
      hasActivity,
      weeklyStats: weeklyStats ? {
        momentsCreated: weeklyStats.momentsCreated,
        productivityScore: weeklyStats.productivityScore,
        searchesPerformed: weeklyStats.searchesPerformed,
      } : null,
      recentMoments: recentMoments.map(moment => ({
        text: moment.contentText.substring(0, 100) + '...',
        importance: moment.importance,
        sphereName: moment.sphere.name,
        capturedAt: moment.capturedAt.toISOString(),
      })),
      recentInsights: recentInsights.map(insight => ({
        title: insight.title,
        category: insight.insightCategory,
        confidence: insight.confidenceScore,
      })),
      collaborationActivity,
      weekPeriod: {
        start: oneWeekAgo.toISOString(),
        end: new Date().toISOString(),
      },
    };
  }

  /**
   * Mark notifications as read
   */
  static async markNotificationsAsRead(userId: string, notificationIds?: string[]): Promise<void> {
    const whereClause: any = { userId };

    if (notificationIds && notificationIds.length > 0) {
      whereClause.id = { in: notificationIds };
    }

    await prisma.collaboration.activityFeed.updateMany({
      where: whereClause,
      data: { readAt: new Date() },
    });
  }

  /**
   * Get notification statistics
   */
  static async getNotificationStats(userId: string): Promise<{
    unreadCount: number;
    todayCount: number;
    weekCount: number;
    channelBreakdown: Record<string, number>;
  }> {
    const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const oneWeekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);

    const [unreadCount, todayCount, weekCount] = await Promise.all([
      prisma.collaboration.activityFeed.count({
        where: { userId, readAt: null },
      }),
      prisma.collaboration.activityFeed.count({
        where: { userId, createdAt: { gte: oneDayAgo } },
      }),
      prisma.collaboration.activityFeed.count({
        where: { userId, createdAt: { gte: oneWeekAgo } },
      }),
    ]);

    // Get channel breakdown (simplified)
    const channelBreakdown = {
      in_app: unreadCount,
      email: 0, // Would track email notifications separately
      push: 0,  // Would track push notifications separately
    };

    return {
      unreadCount,
      todayCount,
      weekCount,
      channelBreakdown,
    };
  }
}

/**
 * Email notification service
 */
export class EmailNotificationService {
  private static transporter = nodemailer.createTransporter({
    host: process.env.SMTP_HOST || 'localhost',
    port: parseInt(process.env.SMTP_PORT || '587'),
    secure: false,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  });

  static async sendEmail(
    to: string,
    subject: string,
    template: string,
    data: any
  ): Promise<void> {
    const html = this.renderTemplate(template, data);

    await this.transporter.sendMail({
      from: process.env.SMTP_FROM || 'notifications@flashit.app',
      to,
      subject,
      html,
    });
  }

  private static renderTemplate(templateName: string, data: any): string {
    // Simplified template rendering - in production, use a proper template engine
    const templates = {
      sphere_shared: `
        <h2>Sphere Shared with You</h2>
        <p>${data.sharedBy} has shared the sphere "${data.sphereName}" with you.</p>
        ${data.message ? `<p>Message: "${data.message}"</p>` : ''}
        <p>Permission level: ${data.permissionLevel}</p>
        <p><a href="${process.env.FRONTEND_URL}/spheres/${data.sphereId}">View Sphere</a></p>
      `,
      weekly_digest: `
        <h2>Your Weekly Flashit Summary</h2>
        <h3>This Week's Activity</h3>
        ${data.weeklyStats ? `
          <p>You created ${data.weeklyStats.momentsCreated} moments with an average productivity score of ${data.weeklyStats.productivityScore}%.</p>
        ` : ''}
        ${data.recentMoments.length > 0 ? `
          <h3>Your Recent Moments</h3>
          <ul>
            ${data.recentMoments.map((moment: any) => `
              <li><strong>${moment.sphereName}:</strong> ${moment.text}</li>
            `).join('')}
          </ul>
        ` : ''}
        ${data.recentInsights.length > 0 ? `
          <h3>New Insights</h3>
          <ul>
            ${data.recentInsights.map((insight: any) => `
              <li><strong>${insight.title}</strong> (${insight.category})</li>
            `).join('')}
          </ul>
        ` : ''}
        <p><a href="${process.env.FRONTEND_URL}/analytics">View Full Analytics</a></p>
      `,
    };

    return templates[templateName as keyof typeof templates] || `
      <h2>${data.title}</h2>
      <p>${data.message}</p>
    `;
  }
}

/**
 * Push notification service
 */
export class PushNotificationService {
  static async sendPushNotification(
    subscription: any,
    title: string,
    body: string,
    data?: any
  ): Promise<void> {
    const payload = JSON.stringify({
      title,
      body,
      icon: '/icons/icon-192x192.png',
      badge: '/icons/badge-72x72.png',
      data,
    });

    try {
      await webpush.sendNotification(subscription, payload);
    } catch (error: any) {
      console.error('Failed to send push notification:', error);
      // Remove invalid subscriptions
      if (error.statusCode === 410 || error.statusCode === 404) {
        await prisma.pushSubscription.delete({
          where: { endpoint: subscription.endpoint },
        }).catch(err => {
          console.error('Failed to remove invalid push subscription:', err);
        });
      }
    }
  }
}

/**
 * Notification worker - processes notification jobs
 */
const notificationWorker = new Worker<NotificationJob>(
  NOTIFICATION_QUEUE,
  async (job: Job<NotificationJob>) => {
    const { data } = job;

    try {
      await job.updateProgress(10);

      // Get user details
      const user = await prisma.user.findUnique({
        where: { id: data.userId },
        select: { email: true, displayName: true },
      });

      if (!user) {
        throw new Error('User not found');
      }

      // Render notification content
      const renderedContent = this.renderNotificationContent(data.template, data.data);

      await job.updateProgress(30);

      // Send to each channel
      const results: any[] = [];

      for (const channel of data.template.channels) {
        try {
          switch (channel) {
            case 'in_app':
              await this.sendInAppNotification(data.userId, renderedContent, data.data);
              break;
            case 'email':
              await EmailNotificationService.sendEmail(
                user.email,
                renderedContent.title,
                data.template.emailTemplate || 'default',
                { ...data.data, ...renderedContent }
              );
              break;
            case 'push':
              await this.sendPushToUser(data.userId, renderedContent);
              break;
          }
          results.push({ channel, status: 'sent' });
        } catch (error) {
          console.error(`Failed to send ${channel} notification:`, error);
          results.push({ channel, status: 'failed', error: error.message });
        }
      }

      await job.updateProgress(100);

      return { results };

    } catch (error: any) {
      console.error('Notification job failed:', error);
      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 10, // Process up to 10 notifications concurrently
  }
);

// Helper functions for the worker
async function renderNotificationContent(template: NotificationTemplate, data: Record<string, any>) {
  let title = template.title;
  let message = template.message;

  // Simple template variable replacement
  for (const [key, value] of Object.entries(data)) {
    title = title.replace(new RegExp(`{{${key}}}`, 'g'), String(value));
    message = message.replace(new RegExp(`{{${key}}}`, 'g'), String(value));
  }

  return { title, message };
}

async function sendInAppNotification(userId: string, content: any, data: any) {
  // Store in-app notification in activity feed (already handled by collaboration events)
  // This would be handled by the collaboration event system
  console.log(`In-app notification sent to ${userId}: ${content.title}`);
}

async function sendPushToUser(userId: string, content: any) {
  // Get user's push subscriptions
  const subscriptions = await prisma.pushSubscription.findMany({
    where: {
      userId,
      enabled: true
    },
  });

  for (const subscription of subscriptions) {
    const pushSubscription = {
      endpoint: subscription.endpoint,
      keys: {
        p256dh: subscription.p256dh,
        auth: subscription.auth,
      },
    };

    await PushNotificationService.sendPushNotification(
      pushSubscription,
      content.title,
      content.message
    );

    // Update last used timestamp
    await prisma.pushSubscription.update({
      where: { id: subscription.id },
      data: { lastUsedAt: new Date() },
    }).catch(err => {
      console.error('Failed to update push subscription last used:', err);
    });
  }
}

// Worker event handlers
notificationWorker.on('completed', (job) => {
  console.log(`Notification job ${job.id} completed successfully`);
});

notificationWorker.on('failed', (job, err) => {
  console.error(`Notification job ${job?.id} failed:`, err);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down notification worker...');
  await notificationWorker.close();
  await prisma.$disconnect();
  await redis.quit();
  process.exit(0);
});

export { notificationWorker };
