/**
 * Reporting routes for generating analytics and data exports.
 *
 * <p><b>Purpose</b><br>
 * Provides endpoints for generating comprehensive reports on device usage,
 * policy enforcement, and child activity patterns. Supports multiple export
 * formats (JSON, CSV, PDF) with date range filtering and aggregation.
 *
 * <p><b>Endpoints</b><br>
 * - POST /reports/usage - Generate usage summary report
 * - POST /reports/blocks - Generate block events report
 * - POST /reports/activity - Generate comprehensive activity report
 * - POST /reports/export - Export data in requested format (CSV/PDF)
 *
 * <p><b>Report Types</b><br>
 * - Usage: Screen time, app usage, website visits by time period
 * - Blocks: Policy violations, blocked content, enforcement statistics
 * - Activity: Combined view of usage and blocks with insights
 *
 * <p><b>Rate Limiting</b><br>
 * Export endpoints are rate-limited to prevent resource abuse and ensure
 * fair usage across tenants. Heavy computation queries are queued.
 *
 * @doc.type route
 * @doc.purpose Report generation and data export
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import * as reportsService from '../services/reports.service';
import { logger } from '../utils/logger';
import { logAuditEvent, AuditEvents } from '../services/audit.service';

// Validation schemas
const dateRangeSchema = z.object({
  start_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  end_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  child_id: z.string().uuid().optional(),
});

// Rate limit config for data export
// NOTE: Global rate limiting is configured in server.ts
// Per-route rate limiting configuration is handled by the global plugin


const reportsRoutes: FastifyPluginAsync = async (fastify) => {
  // All routes require authentication
  fastify.addHook('preHandler', authenticate);

  /** GET /usage - Get usage report for a date range */
  fastify.get('/usage', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = dateRangeSchema.safeParse({
        start_date: (request.query as any).start_date,
        end_date: (request.query as any).end_date,
        child_id: (request.query as any).child_id,
      });

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request parameters',
          details: validation.error.issues,
        });
      }

      const startDate = new Date(validation.data.start_date);
      const endDate = new Date(validation.data.end_date);
      endDate.setUTCHours(23, 59, 59, 999);

      const report = await reportsService.getUsageReport(
        request.userId!,
        startDate,
        endDate,
        validation.data.child_id
      );

      return reply.send({
        success: true,
        data: report,
        date_range: {
          start: startDate.toISOString(),
          end: endDate.toISOString(),
        },
      });
    } catch (error) {
      console.error('Get usage report error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch usage report',
      });
    }
  });

  /** GET /blocks - Get block events report for a date range */
  fastify.get('/blocks', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = dateRangeSchema.safeParse({
        start_date: (request.query as any).start_date,
        end_date: (request.query as any).end_date,
        child_id: (request.query as any).child_id,
      });

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request parameters',
          details: validation.error.issues,
        });
      }

      const startDate = new Date(validation.data.start_date);
      const endDate = new Date(validation.data.end_date);
      endDate.setUTCHours(23, 59, 59, 999);

      const report = await reportsService.getBlockReport(
        request.userId!,
        startDate,
        endDate,
        validation.data.child_id
      );

      return reply.send({
        success: true,
        data: report,
        date_range: {
          start: startDate.toISOString(),
          end: endDate.toISOString(),
        },
      });
    } catch (error) {
      console.error('Get block report error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch block report',
      });
    }
  });

  /** GET /summary - Get daily summary for the last N days */
  fastify.get('/summary', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const days = parseInt((request.query as any).days as string) || 7;

      if (days < 1 || days > 90) {
        return reply.status(400).send({
          success: false,
          error: 'Days must be between 1 and 90',
        });
      }

      const summary = await reportsService.getDailySummary(request.userId!, days);

      return reply.send({
        success: true,
        data: summary,
        days,
      });
    } catch (error) {
      console.error('Get daily summary error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch daily summary',
      });
    }
  });

  /** GET /export - Export report data as CSV */
  fastify.get('/export', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const reportType = (request.query as any).type as string;
      const startDate = (request.query as any).start_date as string;
      const endDate = (request.query as any).end_date as string;
      const childId = (request.query as any).child_id as string | undefined;

      if (!reportType || !['usage', 'blocks'].includes(reportType)) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid report type. Must be "usage" or "blocks"',
        });
      }

      if (!startDate || !endDate) {
        return reply.status(400).send({
          success: false,
          error: 'start_date and end_date are required',
        });
      }

      const start = new Date(startDate);
      const end = new Date(endDate);
      end.setHours(23, 59, 59, 999);

      let data: unknown[];
      if (reportType === 'usage') {
        data = await reportsService.getUsageReport(request.userId!, start, end, childId);
      } else {
        data = await reportsService.getBlockReport(request.userId!, start, end, childId);
      }

      const csv = reportsService.exportToCSV(data, reportType as 'usage' | 'blocks');

      await logAuditEvent(
        request.userId!,
        AuditEvents.DATA_EXPORTED,
        {
          export_type: reportType,
          date_range: { start: startDate, end: endDate },
          child_id: childId,
          record_count: data.length,
        },
        request as any,
        'info'
      );

      logger.warn('Data export requested', {
        userId: request.userId,
        exportType: reportType,
        dateRange: { start: startDate, end: endDate },
        recordCount: data.length,
      });

      reply.header('Content-Type', 'text/csv');
      reply.header(
        'Content-Disposition',
        `attachment; filename="guardian-${reportType}-report-${startDate}-${endDate}.csv"`
      );
      return reply.send(csv);
    } catch (error) {
      logger.error('Export report error', { userId: request.userId, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to export report',
      });
    }
  });
};

export default reportsRoutes;
