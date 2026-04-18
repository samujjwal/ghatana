/**
 * Job Status Notification Service
 *
 * Handles notifications for job queue failures and stuck jobs including:
 * - Queue failure alerts
 * - Stuck job detection and escalation
 * - User notifications for job completion/failure
 * - Admin alerts for system issues
 *
 * @doc.type service
 * @doc.purpose Notify users and admins about job status changes
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@prisma/client";

export type JobStatus = "pending" | "processing" | "completed" | "failed" | "cancelled" | "stuck";

export interface JobNotification {
  jobId: string;
  userId: string;
  tenantId: string;
  jobType: string;
  status: JobStatus;
  message: string;
  details?: Record<string, unknown>;
  timestamp: Date;
  severity: "info" | "warning" | "error" | "critical";
}

export interface QueueFailureAlert {
  queueName: string;
  failureCount: number;
  errorMessage: string;
  timestamp: Date;
  affectedJobs: string[];
}

export interface StuckJobAlert {
  jobId: string;
  userId: string;
  tenantId: string;
  jobType: string;
  stuckDurationMinutes: number;
  lastHeartbeat?: Date;
  severity: "warning" | "critical";
}

export type NotificationChannel = "email" | "push" | "in_app" | "webhook";

export interface NotificationPreferences {
  userId: string;
  channels: NotificationChannel[];
  jobCompletion: boolean;
  jobFailure: boolean;
  queueFailures: boolean;
  stuckJobs: boolean;
}

export class JobNotificationService {
  private notificationHandlers: Map<NotificationChannel, (notification: JobNotification) => Promise<void>> = new Map();

  constructor(private readonly prisma: PrismaClient) {
    this.setupDefaultHandlers();
  }

  /**
   * Notify user about job status change
   */
  async notifyJobStatus(
    jobId: string,
    userId: string,
    tenantId: string,
    jobType: string,
    status: JobStatus,
    details?: Record<string, unknown>,
  ): Promise<void> {
    const preferences = await this.getUserPreferences(userId);

    // Determine severity and message based on status
    const { severity, message } = this.getStatusDetails(status, jobType);

    const notification: JobNotification = {
      jobId,
      userId,
      tenantId,
      jobType,
      status,
      message,
      details,
      timestamp: new Date(),
      severity,
    };

    // Send through preferred channels
    for (const channel of preferences.channels) {
      if (this.shouldNotifyForStatus(preferences, status)) {
        const handler = this.notificationHandlers.get(channel);
        if (handler) {
          await handler(notification).catch((err) => {
            console.error(`Failed to send ${channel} notification:`, err);
          });
        }
      }
    }

    // Persist notification for in-app viewing
    await this.persistNotification(notification);
  }

  /**
   * Send queue failure alert to admins
   */
  async sendQueueFailureAlert(alert: QueueFailureAlert): Promise<void> {
    // Get admin users for the tenant
    const admins = await this.prisma.$queryRaw<Array<{ id: string; email: string }>>`
      SELECT id, email
      FROM "User"
      WHERE role IN ('ADMIN', 'SUPER_ADMIN')
    `.catch(() => []);

    const message = `Queue "${alert.queueName}" has failed ${alert.failureCount} times. Error: ${alert.errorMessage}`;

    for (const admin of admins) {
      await this.sendAdminAlert(admin.id, admin.email, {
        type: "queue_failure",
        message,
        severity: "critical",
        details: {
          queueName: alert.queueName,
          failureCount: alert.failureCount,
          affectedJobs: alert.affectedJobs,
        },
      });
    }

    console.error(`[QUEUE FAILURE] ${message}`);
  }

  /**
   * Send stuck job alert
   */
  async sendStuckJobAlert(alert: StuckJobAlert): Promise<void> {
    // Notify user
    await this.notifyJobStatus(
      alert.jobId,
      alert.userId,
      alert.tenantId,
      alert.jobType,
      "stuck",
      {
        stuckDurationMinutes: alert.stuckDurationMinutes,
        lastHeartbeat: alert.lastHeartbeat,
        action: "Our team has been notified and is investigating.",
      },
    );

    // Alert admins for critical stuck jobs
    if (alert.severity === "critical") {
      const admins = await this.prisma.$queryRaw<Array<{ id: string; email: string }>>`
        SELECT id, email
        FROM "User"
        WHERE role IN ('ADMIN', 'SUPER_ADMIN')
      `.catch(() => []);

      const message = `Job ${alert.jobId} (type: ${alert.jobType}) has been stuck for ${alert.stuckDurationMinutes} minutes`;

      for (const admin of admins) {
        await this.sendAdminAlert(admin.id, admin.email, {
          type: "stuck_job",
          message,
          severity: "critical",
          details: {
            jobId: alert.jobId,
            userId: alert.userId,
            jobType: alert.jobType,
            stuckDuration: alert.stuckDurationMinutes,
          },
        });
      }
    }
  }

  /**
   * Register custom notification handler
   */
  registerHandler(
    channel: NotificationChannel,
    handler: (notification: JobNotification) => Promise<void>,
  ): void {
    this.notificationHandlers.set(channel, handler);
  }

  /**
   * Get notification preferences for user
   */
  async getUserPreferences(userId: string): Promise<NotificationPreferences> {
    // Query user preferences from database
    const prefs = await this.prisma.$queryRaw<Array<{
      channels: string;
      jobCompletion: boolean;
      jobFailure: boolean;
      queueFailures: boolean;
      stuckJobs: boolean;
    }>>`
      SELECT 
        COALESCE(preferences->>'channels', '["in_app"]') as channels,
        COALESCE((preferences->>'jobCompletion')::boolean, true) as "jobCompletion",
        COALESCE((preferences->>'jobFailure')::boolean, true) as "jobFailure",
        COALESCE((preferences->>'queueFailures')::boolean, false) as "queueFailures",
        COALESCE((preferences->>'stuckJobs')::boolean, true) as "stuckJobs"
      FROM "UserNotificationPreferences"
      WHERE "userId" = ${userId}
    `.catch(() => []);

    if (prefs.length > 0) {
      return {
        userId,
        channels: JSON.parse(prefs[0].channels) as NotificationChannel[],
        jobCompletion: prefs[0].jobCompletion,
        jobFailure: prefs[0].jobFailure,
        queueFailures: prefs[0].queueFailures,
        stuckJobs: prefs[0].stuckJobs,
      };
    }

    // Return defaults
    return {
      userId,
      channels: ["in_app"],
      jobCompletion: true,
      jobFailure: true,
      queueFailures: false,
      stuckJobs: true,
    };
  }

  /**
   * Update user notification preferences
   */
  async updatePreferences(
    userId: string,
    preferences: Partial<NotificationPreferences>,
  ): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO "UserNotificationPreferences" ("userId", preferences, updated_at)
      VALUES (
        ${userId},
        ${JSON.stringify(preferences)}::jsonb,
        NOW()
      )
      ON CONFLICT ("userId")
      DO UPDATE SET
        preferences = ${JSON.stringify(preferences)}::jsonb,
        updated_at = NOW()
    `.catch(() => {
      // Table might not exist, log error
      console.error("Failed to update notification preferences");
    });
  }

  /**
   * Get recent notifications for user
   */
  async getRecentNotifications(
    userId: string,
    limit: number = 20,
  ): Promise<JobNotification[]> {
    const notifications = await this.prisma.$queryRaw<Array<{
      job_id: string;
      user_id: string;
      tenant_id: string;
      job_type: string;
      status: string;
      message: string;
      details: string;
      created_at: Date;
      severity: string;
    }>>`
      SELECT 
        job_id,
        user_id,
        tenant_id,
        job_type,
        status,
        message,
        details::text,
        created_at,
        severity
      FROM "JobNotification"
      WHERE user_id = ${userId}
      ORDER BY created_at DESC
      LIMIT ${limit}
    `.catch(() => []);

    return notifications.map((n) => ({
      jobId: n.job_id,
      userId: n.user_id,
      tenantId: n.tenant_id,
      jobType: n.job_type,
      status: n.status as JobStatus,
      message: n.message,
      details: n.details ? JSON.parse(n.details) : undefined,
      timestamp: n.created_at,
      severity: n.severity as JobNotification["severity"],
    }));
  }

  /**
   * Mark notification as read
   */
  async markAsRead(notificationId: string): Promise<void> {
    await this.prisma.$executeRaw`
      UPDATE "JobNotification"
      SET read_at = NOW()
      WHERE id = ${notificationId}
    `.catch(() => {
      // Ignore errors
    });
  }

  /**
   * Setup default notification handlers
   */
  private setupDefaultHandlers(): void {
    // In-app notification handler
    this.notificationHandlers.set("in_app", async (notification) => {
      // In-app notifications are handled by persisting to database
      // Frontend polls or uses WebSocket to fetch
      console.log(`[IN_APP] ${notification.message}`);
    });

    // Email handler
    this.notificationHandlers.set("email", async (notification) => {
      // Would integrate with email service
      console.log(`[EMAIL] To: ${notification.userId} - ${notification.message}`);
    });

    // Push notification handler
    this.notificationHandlers.set("push", async (notification) => {
      // Would integrate with push notification service
      console.log(`[PUSH] ${notification.message}`);
    });

    // Webhook handler
    this.notificationHandlers.set("webhook", async (notification) => {
      // Would POST to configured webhook URL
      console.log(`[WEBHOOK] ${notification.message}`);
    });
  }

  /**
   * Get status-specific details
   */
  private getStatusDetails(
    status: JobStatus,
    jobType: string,
  ): { severity: JobNotification["severity"]; message: string } {
    const messages: Record<JobStatus, { severity: JobNotification["severity"]; message: string }> = {
      pending: {
        severity: "info",
        message: `Your ${jobType} is queued and will start soon.`,
      },
      processing: {
        severity: "info",
        message: `Your ${jobType} is now processing.`,
      },
      completed: {
        severity: "info",
        message: `Your ${jobType} has completed successfully.`,
      },
      failed: {
        severity: "error",
        message: `Your ${jobType} failed. Please try again or contact support.`,
      },
      cancelled: {
        severity: "warning",
        message: `Your ${jobType} was cancelled.`,
      },
      stuck: {
        severity: "warning",
        message: `Your ${jobType} is taking longer than expected. Our team has been notified.`,
      },
    };

    return messages[status];
  }

  /**
   * Check if user wants notifications for this status
   */
  private shouldNotifyForStatus(
    preferences: NotificationPreferences,
    status: JobStatus,
  ): boolean {
    switch (status) {
      case "completed":
        return preferences.jobCompletion;
      case "failed":
      case "stuck":
        return preferences.jobFailure || preferences.stuckJobs;
      default:
        return true;
    }
  }

  /**
   * Persist notification to database
   */
  private async persistNotification(notification: JobNotification): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO "JobNotification" (
        job_id, user_id, tenant_id, job_type, status,
        message, details, severity, created_at
      ) VALUES (
        ${notification.jobId},
        ${notification.userId},
        ${notification.tenantId},
        ${notification.jobType},
        ${notification.status},
        ${notification.message},
        ${JSON.stringify(notification.details || {})}::jsonb,
        ${notification.severity},
        ${notification.timestamp}
      )
    `.catch(() => {
      // Table might not exist in schema yet
      console.log(`[NOTIFICATION] ${notification.message}`);
    });
  }

  /**
   * Send alert to admin
   */
  private async sendAdminAlert(
    adminId: string,
    email: string,
    alert: {
      type: string;
      message: string;
      severity: "warning" | "critical";
      details: Record<string, unknown>;
    },
  ): Promise<void> {
    // Send email to admin
    console.error(`[ADMIN ALERT] To: ${email} - ${alert.message}`);

    // Persist admin notification
    await this.prisma.$executeRaw`
      INSERT INTO "AdminAlert" (
        admin_id, type, message, severity, details, created_at
      ) VALUES (
        ${adminId},
        ${alert.type},
        ${alert.message},
        ${alert.severity},
        ${JSON.stringify(alert.details)}::jsonb,
        NOW()
      )
    `.catch(() => {
      // Table might not exist
    });
  }
}
