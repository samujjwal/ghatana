/**
 * Content Generation Notification Service
 *
 * Provides real-time notifications for generation job status changes.
 * Supports WebSocket, SSE, and in-app notifications.
 *
 * @doc.type service
 * @doc.purpose Notify users of generation job status updates
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";

export interface GenerationJobNotification {
  id: string;
  tenantId: string;
  userId: string;
  requestId: string;
  jobId: string;
  jobType: string;
  status: "started" | "in_progress" | "completed" | "failed";
  progress: number;
  message: string;
  timestamp: Date;
  metadata?: {
    assetId?: string | undefined;
    assetType?: string | undefined;
    errorMessage?: string | undefined;
    durationMs?: number | undefined;
  };
}

export interface NotificationChannel {
  type: "websocket" | "sse" | "in_app" | "email" | "push";
  send(notification: GenerationJobNotification): Promise<void>;
}

/**
 * Service for managing generation job notifications
 */
export class GenerationNotificationService {
  private channels: Map<string, NotificationChannel> = new Map();
  private sseSubscribers: Map<string, Set<(notification: GenerationJobNotification) => void>> = new Map();

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
  ) {}

  /**
   * Register a notification channel
   */
  registerChannel(channelId: string, channel: NotificationChannel): void {
    this.channels.set(channelId, channel);
  }

  /**
   * Subscribe to SSE notifications for a user
   */
  subscribeToUserNotifications(
    userId: string,
    callback: (notification: GenerationJobNotification) => void,
  ): () => void {
    if (!this.sseSubscribers.has(userId)) {
      this.sseSubscribers.set(userId, new Set());
    }
    this.sseSubscribers.get(userId)!.add(callback);

    // Return unsubscribe function
    return () => {
      this.sseSubscribers.get(userId)?.delete(callback);
    };
  }

  /**
   * Notify user of generation job status change
   */
  async notifyJobStatusChange(
    notification: Omit<GenerationJobNotification, "id" | "timestamp">,
  ): Promise<void> {
    const fullNotification: GenerationJobNotification = {
      ...notification,
      id: crypto.randomUUID(),
      timestamp: new Date(),
    };

    // Persist in-app notification
    await this.persistNotification(fullNotification);

    // Broadcast to all channels
    await this.broadcastNotification(fullNotification);

    // Send to SSE subscribers
    this.broadcastToSSE(fullNotification);

    this.logger.debug(
      {
        requestId: notification.requestId,
        jobId: notification.jobId,
        status: notification.status,
        userId: notification.userId,
      },
      "Generation job notification sent",
    );
  }

  /**
   * Send job started notification
   */
  async notifyJobStarted(params: {
    tenantId: string;
    userId: string;
    requestId: string;
    jobId: string;
    jobType: string;
    requestTitle: string;
  }): Promise<void> {
    await this.notifyJobStatusChange({
      tenantId: params.tenantId,
      userId: params.userId,
      requestId: params.requestId,
      jobId: params.jobId,
      jobType: params.jobType,
      status: "started",
      progress: 0,
      message: `Started generating ${this.formatJobType(params.jobType)} for "${params.requestTitle}"`,
    });
  }

  /**
   * Send job progress notification
   */
  async notifyJobProgress(params: {
    tenantId: string;
    userId: string;
    requestId: string;
    jobId: string;
    jobType: string;
    progress: number;
    stage: string;
  }): Promise<void> {
    await this.notifyJobStatusChange({
      tenantId: params.tenantId,
      userId: params.userId,
      requestId: params.requestId,
      jobId: params.jobId,
      jobType: params.jobType,
      status: "in_progress",
      progress: params.progress,
      message: `Generating: ${params.stage}`,
    });
  }

  /**
   * Send job completed notification
   */
  async notifyJobCompleted(params: {
    tenantId: string;
    userId: string;
    requestId: string;
    jobId: string;
    jobType: string;
    requestTitle: string;
    assetId?: string;
    assetType?: string;
    durationMs?: number;
  }): Promise<void> {
    await this.notifyJobStatusChange({
      tenantId: params.tenantId,
      userId: params.userId,
      requestId: params.requestId,
      jobId: params.jobId,
      jobType: params.jobType,
      status: "completed",
      progress: 100,
      message: `Completed generating ${this.formatJobType(params.jobType)} for "${params.requestTitle}"`,
      metadata: {
        assetId: params.assetId,
        assetType: params.assetType,
        durationMs: params.durationMs,
      },
    });
  }

  /**
   * Send job failed notification
   */
  async notifyJobFailed(params: {
    tenantId: string;
    userId: string;
    requestId: string;
    jobId: string;
    jobType: string;
    requestTitle: string;
    errorMessage: string;
    isRetryable: boolean;
  }): Promise<void> {
    const retryMessage = params.isRetryable
      ? " We'll automatically retry the generation."
      : " Please try again with different parameters.";

    await this.notifyJobStatusChange({
      tenantId: params.tenantId,
      userId: params.userId,
      requestId: params.requestId,
      jobId: params.jobId,
      jobType: params.jobType,
      status: "failed",
      progress: 0,
      message: `Failed to generate ${this.formatJobType(params.jobType)}: ${params.errorMessage}.${retryMessage}`,
      metadata: {
        errorMessage: params.errorMessage,
      },
    });
  }

  /**
   * Get recent notifications for a user from in-memory cache
   * Note: Generation notifications are ephemeral and delivered via SSE/WebSocket
   */
  getRecentNotifications(userId: string, limit = 20): GenerationJobNotification[] {
    // Return from in-memory cache if implemented
    // For now, return empty as notifications are push-based via SSE
    return [];
  }

  /**
   * Mark notifications as read (client-side tracking)
   */
  async markAsRead(_notificationIds: string[]): Promise<void> {
    // Client handles read state; server broadcasts are fire-and-forget
    return;
  }

  /**
   * Create SSE stream for a user
   */
  createSSEStream(userId: string): ReadableStream {
    const encoder = new TextEncoder();

    return new ReadableStream({
      start: (controller) => {
        const callback = (notification: GenerationJobNotification) => {
          const data = `data: ${JSON.stringify(notification)}\n\n`;
          controller.enqueue(encoder.encode(data));
        };

        const unsubscribe = this.subscribeToUserNotifications(userId, callback);

        // Clean up on close
        controller.close = () => {
          unsubscribe();
        };
      },
      cancel: () => {
        this.sseSubscribers.get(userId)?.clear();
      },
    });
  }

  private async persistNotification(_notification: GenerationJobNotification): Promise<void> {
    // Generation notifications are ephemeral and delivered via SSE/WebSocket
    // We don't persist to SocialNotification table as it doesn't support generation metadata
    // Client receives real-time updates and manages its own state
    return;
  }

  private async broadcastNotification(notification: GenerationJobNotification): Promise<void> {
    for (const [channelId, channel] of this.channels) {
      try {
        await channel.send(notification);
      } catch (error) {
        this.logger.warn({ error, channelId }, "Failed to send notification to channel");
      }
    }
  }

  private broadcastToSSE(notification: GenerationJobNotification): void {
    const callbacks = this.sseSubscribers.get(notification.userId);
    if (callbacks) {
      callbacks.forEach((callback) => {
        try {
          callback(notification);
        } catch (error) {
          this.logger.warn({ error, userId: notification.userId }, "Error in SSE callback");
        }
      });
    }
  }

  private formatJobType(jobType: string): string {
    const typeMap: Record<string, string> = {
      claim: "learning objectives",
      explainer: "explanation",
      worked_example: "worked example",
      simulation: "simulation",
      animation: "animation",
      assessment: "assessment",
      evaluation: "evaluation",
    };
    return typeMap[jobType] || jobType.replace(/_/g, " ");
  }
}
