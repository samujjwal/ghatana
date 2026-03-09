/**
 * Cron Job Scheduler
 *
 * Runs periodic maintenance tasks on configurable intervals.
 * Uses plain setInterval (lightweight, single-process).
 * Replace with node-cron / BullMQ for production multi-instance deployments.
 *
 * Jobs:
 * - Expired refresh token cleanup        (daily   @ 02:00)
 * - Expired session cleanup              (daily   @ 02:15)
 * - Expired password reset token cleanup (daily   @ 02:30)
 * - Rate-limit record cleanup            (hourly  )
 * - Audit log retention (free tier 30d)  (daily   @ 03:00)
 * - Scheduled data deletion execution    (daily   @ 04:00)
 *
 * @doc.type service
 * @doc.purpose Periodic maintenance task scheduler
 * @doc.layer product
 * @doc.pattern Scheduler
 */

import { prisma } from '../lib/prisma';
import { RefreshTokenService } from '../services/security/refresh-token-service';
import { SessionManagementService } from '../services/security/session-management-service';
import { PasswordResetService } from '../services/security/password-reset-service';
import { RateLimitService } from '../services/security/rate-limit-service';

// ============================================================================
// Configuration
// ============================================================================

const ONE_HOUR_MS = 60 * 60 * 1000;
const ONE_DAY_MS = 24 * ONE_HOUR_MS;

interface ScheduledJob {
  name: string;
  intervalMs: number;
  handler: () => Promise<void>;
  timer?: ReturnType<typeof setInterval>;
}

// ============================================================================
// Job definitions
// ============================================================================

const refreshTokenService = new RefreshTokenService();
const sessionService = new SessionManagementService();
const passwordResetService = new PasswordResetService();
const rateLimitService = new RateLimitService();

async function safeRun(name: string, fn: () => Promise<unknown>) {
  try {
    const result = await fn();
    console.log(`[scheduler] ${name}: completed`, result);
  } catch (err) {
    console.error(`[scheduler] ${name}: FAILED`, err);
  }
}

const jobs: ScheduledJob[] = [
  // ----- Hourly -----
  {
    name: 'rate-limit-cleanup',
    intervalMs: ONE_HOUR_MS,
    handler: () => safeRun('rate-limit-cleanup', () => rateLimitService.cleanupOldRecords()),
  },

  // ----- Daily -----
  {
    name: 'refresh-token-cleanup',
    intervalMs: ONE_DAY_MS,
    handler: () => safeRun('refresh-token-cleanup', () => refreshTokenService.cleanupExpiredTokens()),
  },
  {
    name: 'session-cleanup',
    intervalMs: ONE_DAY_MS,
    handler: () => safeRun('session-cleanup', () => sessionService.cleanupExpiredSessions()),
  },
  {
    name: 'password-reset-cleanup',
    intervalMs: ONE_DAY_MS,
    handler: () => safeRun('password-reset-cleanup', () => passwordResetService.cleanupExpiredTokens()),
  },
  {
    name: 'audit-log-retention',
    intervalMs: ONE_DAY_MS,
    handler: () => safeRun('audit-log-retention', async () => {
      // Delete audit events older than 30 days for free-tier users
      const cutoff = new Date(Date.now() - 30 * ONE_DAY_MS);
      const deleted = await prisma.auditEvent.deleteMany({
        where: {
          createdAt: { lt: cutoff },
          user: { subscriptionTier: 'free' },
        },
      });
      return deleted.count;
    }),
  },
  {
    name: 'scheduled-data-deletion',
    intervalMs: ONE_DAY_MS,
    handler: () => safeRun('scheduled-data-deletion', async () => {
      const now = new Date();
      // Find confirmed deletion requests whose scheduled date has passed
      const requests = await prisma.deletionRequest.findMany({
        where: {
          status: 'confirmed',
          scheduledFor: { lte: now },
          executedAt: null,
        },
        include: { user: { select: { id: true, email: true } } },
      });

      for (const req of requests) {
        try {
          if (req.scope === 'full') {
            // Soft-delete user and all their data
            await prisma.user.update({
              where: { id: req.userId },
              data: { deletedAt: now },
            });
          }
          // Mark request as executed
          await prisma.deletionRequest.update({
            where: { id: req.id },
            data: { status: 'executed', executedAt: now },
          });
          console.log(`[scheduler] Executed deletion request ${req.id} for user ${req.userId}`);
        } catch (err) {
          console.error(`[scheduler] Failed deletion request ${req.id}`, err);
        }
      }

      return requests.length;
    }),
  },
];

// ============================================================================
// Start / Stop
// ============================================================================

/**
 * Start all scheduled jobs. Call this once after the server is listening.
 */
export function startScheduler(): void {
  console.log(`[scheduler] Starting ${jobs.length} scheduled jobs`);

  for (const job of jobs) {
    // Run immediately on startup, then on interval
    job.handler();
    job.timer = setInterval(job.handler, job.intervalMs);
    console.log(`[scheduler] Registered: ${job.name} (every ${job.intervalMs / 1000}s)`);
  }
}

/**
 * Stop all scheduled jobs. Call this during graceful shutdown.
 */
export function stopScheduler(): void {
  console.log('[scheduler] Stopping all scheduled jobs');
  for (const job of jobs) {
    if (job.timer) {
      clearInterval(job.timer);
      job.timer = undefined;
    }
  }
}
