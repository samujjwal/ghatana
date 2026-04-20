/**
 * Stuck Job Monitor
 *
 * Monitors for stuck jobs and escalates them including:
 * - Heartbeat monitoring
 * - Configurable stuck thresholds
 * - Automatic escalation with notifications
 * - Manual intervention queue
 *
 * @doc.type service
 * @doc.purpose Detect and escalate stuck jobs
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";
import type { JobNotificationService } from "./notification-service";

export interface StuckJobThresholds {
  warningMinutes: number;
  criticalMinutes: number;
  escalationMinutes: number;
}

export interface JobHeartbeat {
  jobId: string;
  lastHeartbeatAt: Date;
  progress: number;
  status: string;
  metadata?: Record<string, unknown>;
}

export interface StuckJob {
  jobId: string;
  userId: string;
  tenantId: string;
  jobType: string;
  startedAt: Date;
  lastHeartbeatAt?: Date;
  stuckDurationMinutes: number;
  severity: "warning" | "critical" | "escalated";
  previousEscalations: number;
}

export const DEFAULT_STUCK_THRESHOLDS: StuckJobThresholds = {
  warningMinutes: 10,
  criticalMinutes: 30,
  escalationMinutes: 60,
};

export class StuckJobMonitor {
  private heartbeats: Map<string, JobHeartbeat> = new Map();
  private thresholds: StuckJobThresholds;
  private checkInterval: NodeJS.Timeout | undefined;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly notificationService: JobNotificationService,
    thresholds?: Partial<StuckJobThresholds>,
  ) {
    this.thresholds = { ...DEFAULT_STUCK_THRESHOLDS, ...thresholds };
  }

  /**
   * Start monitoring loop
   */
  startMonitoring(checkIntervalMs: number = 60000): void {
    this.checkInterval = setInterval(() => {
      this.checkForStuckJobs().catch((err) => {
        console.error("Error checking for stuck jobs:", err);
      });
    }, checkIntervalMs);

    console.log(`Stuck job monitor started (checking every ${checkIntervalMs}ms)`);
  }

  /**
   * Stop monitoring loop
   */
  stopMonitoring(): void {
    if (this.checkInterval) {
      clearInterval(this.checkInterval);
      this.checkInterval = undefined;
    }
  }

  /**
   * Record job heartbeat
   */
  recordHeartbeat(heartbeat: JobHeartbeat): void {
    this.heartbeats.set(heartbeat.jobId, {
      ...heartbeat,
      lastHeartbeatAt: new Date(),
    });

    // Update in database for persistence
    this.persistHeartbeat(heartbeat).catch(() => {
      // Ignore persistence errors
    });
  }

  /**
   * Mark job as completed (remove from monitoring)
   */
  markCompleted(jobId: string): void {
    this.heartbeats.delete(jobId);
  }

  /**
   * Check for stuck jobs and escalate
   */
  async checkForStuckJobs(): Promise<StuckJob[]> {
    const stuckJobs: StuckJob[] = [];
    const now = Date.now();

    // Get active jobs from database
    const activeJobs = await this.prisma.$queryRaw<Array<{
      id: string;
      userId: string;
      tenantId: string;
      jobType: string;
      startedAt: Date;
      lastHeartbeatAt: Date | null;
      previousEscalations: number;
    }>>`
      SELECT 
        id,
        "userId",
        "tenantId",
        "jobType",
        "startedAt",
        "lastHeartbeatAt",
        COALESCE("previousEscalations", 0) as "previousEscalations"
      FROM "GenerationJob"
      WHERE status = 'processing'
    `.catch(() => []);

    for (const job of activeJobs) {
      const lastHeartbeat = job.lastHeartbeatAt
        ? new Date(job.lastHeartbeatAt).getTime()
        : new Date(job.startedAt).getTime();

      const stuckDurationMinutes = Math.floor((now - lastHeartbeat) / 60000);

      // Determine severity
      let severity: StuckJob["severity"] | null = null;

      if (stuckDurationMinutes >= this.thresholds.escalationMinutes) {
        severity = "escalated";
      } else if (stuckDurationMinutes >= this.thresholds.criticalMinutes) {
        severity = "critical";
      } else if (stuckDurationMinutes >= this.thresholds.warningMinutes) {
        severity = "warning";
      }

      if (severity) {
        const stuckJob: StuckJob = {
          jobId: job.id,
          userId: job.userId,
          tenantId: job.tenantId,
          jobType: job.jobType,
          startedAt: new Date(job.startedAt),
          stuckDurationMinutes,
          severity,
          previousEscalations: job.previousEscalations,
          ...(job.lastHeartbeatAt
            ? { lastHeartbeatAt: new Date(job.lastHeartbeatAt) }
            : {}),
        };

        stuckJobs.push(stuckJob);

        // Escalate based on severity
        await this.escalateStuckJob(stuckJob);
      }
    }

    return stuckJobs;
  }

  /**
   * Escalate a stuck job
   */
  private async escalateStuckJob(stuckJob: StuckJob): Promise<void> {
    // Send notification
    await this.notificationService.sendStuckJobAlert({
      jobId: stuckJob.jobId,
      userId: stuckJob.userId,
      tenantId: stuckJob.tenantId,
      jobType: stuckJob.jobType,
      stuckDurationMinutes: stuckJob.stuckDurationMinutes,
      severity: stuckJob.severity === "escalated" ? "critical" : stuckJob.severity,
      ...(stuckJob.lastHeartbeatAt
        ? { lastHeartbeat: stuckJob.lastHeartbeatAt }
        : {}),
    });

    // Increment escalation count
    await this.prisma.$executeRaw`
      UPDATE "GenerationJob"
      SET 
        "previousEscalations" = COALESCE("previousEscalations", 0) + 1,
        "lastEscalatedAt" = NOW()
      WHERE id = ${stuckJob.jobId}
    `.catch(() => {
      // Ignore errors
    });

    // For escalated jobs, add to manual intervention queue
    if (stuckJob.severity === "escalated") {
      await this.addToInterventionQueue(stuckJob);
    }

    console.log(
      `[STUCK JOB] ${stuckJob.severity.toUpperCase()}: Job ${stuckJob.jobId} stuck for ${stuckJob.stuckDurationMinutes} minutes`
    );
  }

  /**
   * Add job to manual intervention queue
   */
  private async addToInterventionQueue(stuckJob: StuckJob): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO "ManualInterventionQueue" (
        job_id, user_id, tenant_id, job_type, 
        stuck_duration_minutes, escalated_at, status
      ) VALUES (
        ${stuckJob.jobId},
        ${stuckJob.userId},
        ${stuckJob.tenantId},
        ${stuckJob.jobType},
        ${stuckJob.stuckDurationMinutes},
        NOW(),
        'pending_review'
      )
      ON CONFLICT (job_id)
      DO UPDATE SET
        stuck_duration_minutes = ${stuckJob.stuckDurationMinutes},
        escalated_at = NOW()
    `.catch(() => {
      console.error(`[MANUAL INTERVENTION] Job ${stuckJob.jobId} requires manual review`);
    });
  }

  /**
   * Get jobs requiring manual intervention
   */
  async getInterventionQueue(): Promise<Array<{
    jobId: string;
    userId: string;
    tenantId: string;
    jobType: string;
    stuckDurationMinutes: number;
    escalatedAt: Date;
    status: string;
  }>> {
    return await this.prisma.$queryRaw<Array<{
      jobId: string;
      userId: string;
      tenantId: string;
      jobType: string;
      stuckDurationMinutes: number;
      escalatedAt: Date;
      status: string;
    }>>`
      SELECT 
        job_id as "jobId",
        user_id as "userId",
        tenant_id as "tenantId",
        job_type as "jobType",
        stuck_duration_minutes as "stuckDurationMinutes",
        escalated_at as "escalatedAt",
        status
      FROM "ManualInterventionQueue"
      WHERE status = 'pending_review'
      ORDER BY escalated_at DESC
    `.catch(() => []);
  }

  /**
   * Resolve a stuck job (manual intervention)
   */
  async resolveStuckJob(
    jobId: string,
    resolution: "retry" | "cancel" | "mark_complete",
    adminId: string,
    notes?: string,
  ): Promise<void> {
    // Update intervention queue
    await this.prisma.$executeRaw`
      UPDATE "ManualInterventionQueue"
      SET 
        status = ${resolution},
        resolved_by = ${adminId},
        resolution_notes = ${notes ?? null},
        resolved_at = NOW()
      WHERE job_id = ${jobId}
    `.catch(() => {
      // Ignore errors
    });

    // Clear from heartbeats
    this.heartbeats.delete(jobId);

    // Apply resolution action
    switch (resolution) {
      case "retry":
        await this.prisma.$executeRaw`
          UPDATE "GenerationJob"
          SET 
            status = 'pending',
            "retryCount" = COALESCE("retryCount", 0) + 1,
            "updatedAt" = NOW()
          WHERE id = ${jobId}
        `;
        break;

      case "cancel":
        await this.prisma.$executeRaw`
          UPDATE "GenerationJob"
          SET 
            status = 'cancelled',
            "cancelledAt" = NOW(),
            "updatedAt" = NOW()
          WHERE id = ${jobId}
        `;
        break;

      case "mark_complete":
        await this.prisma.$executeRaw`
          UPDATE "GenerationJob"
          SET 
            status = 'completed',
            "completedAt" = NOW(),
            "updatedAt" = NOW()
          WHERE id = ${jobId}
        `;
        break;
    }

    console.log(`[STUCK JOB RESOLVED] Job ${jobId} resolved by ${adminId}: ${resolution}`);
  }

  /**
   * Get current stuck job statistics
   */
  async getStuckJobStats(): Promise<{
    warning: number;
    critical: number;
    escalated: number;
    total: number;
  }> {
    const stuckJobs = await this.checkForStuckJobs();

    return {
      warning: stuckJobs.filter((j) => j.severity === "warning").length,
      critical: stuckJobs.filter((j) => j.severity === "critical").length,
      escalated: stuckJobs.filter((j) => j.severity === "escalated").length,
      total: stuckJobs.length,
    };
  }

  /**
   * Persist heartbeat to database
   */
  private async persistHeartbeat(heartbeat: JobHeartbeat): Promise<void> {
    await this.prisma.$executeRaw`
      UPDATE "GenerationJob"
      SET 
        "lastHeartbeatAt" = NOW(),
        "progress" = ${heartbeat.progress},
        "statusMessage" = ${heartbeat.status},
        "heartbeatMetadata" = ${JSON.stringify(heartbeat.metadata ?? {})}::jsonb
      WHERE id = ${heartbeat.jobId}
    `.catch(() => {
      // Ignore errors
    });
  }

  /**
   * Update thresholds
   */
  updateThresholds(thresholds: Partial<StuckJobThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
  }

  /**
   * Get current thresholds
   */
  getThresholds(): StuckJobThresholds {
    return { ...this.thresholds };
  }
}
