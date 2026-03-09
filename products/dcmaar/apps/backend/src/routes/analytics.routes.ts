/**
 * Analytics aggregation routes for usage statistics and insights.
 *
 * <p><b>Purpose</b><br>
 * Provides endpoints for querying aggregated usage data, screen time statistics,
 * category breakdowns, and time-series data for trending analysis.
 *
 * <p><b>Endpoints</b><br>
 * - GET /analytics - Get aggregated analytics for date range
 * - GET /analytics/trends - Get time-series trends
 * - GET /analytics/breakdown - Get category/app breakdowns
 *
 * <p><b>Aggregation Types</b><br>
 * - Total screen time (seconds)
 * - Session counts
 * - Category distribution
 * - Top apps/websites
 * - Daily/hourly breakdowns
 * - Block event statistics
 *
 * <p><b>Use Cases</b><br>
 * - Dashboard overview statistics
 * - Trend analysis and charts
 * - Parent insights and recommendations
 * - Compliance reporting
 *
 * @doc.type route
 * @doc.purpose Analytics aggregation endpoints for usage statistics
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import { query } from '../db';
import { logger } from '../utils/logger';
import { logAuditEvent, AuditEvents } from '../services/audit.service';

// Validation schemas
const getAnalyticsSchema = z.object({
  child_id: z.string().uuid().optional(),
  start_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  end_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  include_breakdown: z.coerce.boolean().optional().default(true),
});

/**
 * Analytics routes plugin.
 */
const analyticsRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /analytics - Get aggregated analytics for date range
   *
   * Query Parameters:
   * - child_id (optional): Filter by specific child
   * - start_date (required): Start date (YYYY-MM-DD)
   * - end_date (required): End date (YYYY-MM-DD)
   * - include_breakdown (optional): Include category breakdown (default: true)
   *
   * Returns:
   * {
   *   success: true,
   *   data: {
   *     total_screen_time: 7200,
   *     total_sessions: 15,
   *     by_category: {
   *       education: 3600,
   *       games: 2400,
   *       social: 1200
   *     },
   *     by_type: {
   *       app: 5400,
   *       website: 1800
   *     },
   *     top_apps: [
   *       { name: "Math App", duration: 1800, category: "education" }
   *     ],
   *     block_events: 12,
   *     period: {
   *       start_date: "2025-11-01",
   *       end_date: "2025-11-08"
   *     }
   *   }
   * }
   */
  fastify.get(
    '/',
    { preHandler: authenticate },
    async (request: AuthRequest, reply: FastifyReply) => {
      try {
        const validated = getAnalyticsSchema.parse(request.query);
        const userId = request.userId!;

        // Parse dates
        const startDate = new Date(validated.start_date + 'T00:00:00Z');
        const endDate = new Date(validated.end_date + 'T23:59:59Z');

        // Get children for this user (filter by child_id if provided)
        let childIds: string[] = [];
        if (validated.child_id) {
          // Verify user owns this child
          const childCheck = await query<{ id: string }>(
            'SELECT id FROM children WHERE id = $1 AND user_id = $2',
            [validated.child_id, userId]
          );
          if (childCheck.length === 0) {
            return reply.status(404).send({
              success: false,
              error: 'Child not found',
            });
          }
          childIds = [validated.child_id];
        } else {
          // Get all user's children
          const childrenResult = await query<{ id: string }>(
            'SELECT id FROM children WHERE user_id = $1 AND is_active = true',
            [userId]
          );
          childIds = childrenResult.map((r) => r.id);
        }

        if (childIds.length === 0) {
          return reply.send({
            success: true,
            data: {
              total_screen_time: 0,
              total_sessions: 0,
              by_category: {},
              by_type: {},
              top_apps: [],
              block_events: 0,
              period: {
                start_date: validated.start_date,
                end_date: validated.end_date,
              },
            },
          });
        }

        // Get total screen time and session count
        const totalStats = await query<{
          total_screen_time: number;
          total_sessions: number;
        }>(
          `SELECT 
            COALESCE(SUM(u.duration_seconds), 0)::int as total_screen_time,
            COUNT(*)::int as total_sessions
          FROM usage_sessions u
          JOIN devices d ON d.id = u.device_id
          WHERE d.child_id = ANY($1)
            AND u.start_time >= $2
            AND u.start_time <= $3`,
          [childIds, startDate, endDate]
        );

        const totalScreenTime = totalStats[0]?.total_screen_time || 0;
        const totalSessions = totalStats[0]?.total_sessions || 0;

        // Get category breakdown (if requested)
        let byCategory: Record<string, number> = {};
        if (validated.include_breakdown) {
          const categoryStats = await query<{
            category: string;
            duration: number;
          }>(
            `SELECT 
              COALESCE(u.category, 'unknown') as category,
              SUM(u.duration_seconds)::int as duration
            FROM usage_sessions u
            JOIN devices d ON d.id = u.device_id
            WHERE d.child_id = ANY($1)
              AND u.start_time >= $2
              AND u.start_time <= $3
            GROUP BY u.category`,
            [childIds, startDate, endDate]
          );

          byCategory = categoryStats.reduce(
            (acc, row) => {
              acc[row.category] = row.duration;
              return acc;
            },
            {} as Record<string, number>
          );
        }

        // Get type breakdown (app vs website)
        const typeStats = await query<{ session_type: string; duration: number }>(
          `SELECT 
            u.session_type,
            SUM(u.duration_seconds)::int as duration
          FROM usage_sessions u
          JOIN devices d ON d.id = u.device_id
          WHERE d.child_id = ANY($1)
            AND u.start_time >= $2
            AND u.start_time <= $3
          GROUP BY u.session_type`,
          [childIds, startDate, endDate]
        );

        const byType = typeStats.reduce(
          (acc, row) => {
            acc[row.session_type] = row.duration;
            return acc;
          },
          {} as Record<string, number>
        );

        // Get top apps
        const topApps = await query<{
          item_name: string;
          category: string;
          duration: number;
        }>(
          `SELECT 
            u.item_name,
            COALESCE(u.category, 'unknown') as category,
            SUM(u.duration_seconds)::int as duration
          FROM usage_sessions u
          JOIN devices d ON d.id = u.device_id
          WHERE d.child_id = ANY($1)
            AND u.start_time >= $2
            AND u.start_time <= $3
          GROUP BY u.item_name, u.category
          ORDER BY duration DESC
          LIMIT 10`,
          [childIds, startDate, endDate]
        );

        // Get block events count
        const blockStats = await query<{ count: number }>(
          `SELECT COUNT(*)::int as count
          FROM block_events b
          JOIN devices d ON d.id = b.device_id
          WHERE d.child_id = ANY($1)
            AND b.timestamp >= $2
            AND b.timestamp <= $3`,
          [childIds, startDate, endDate]
        );

        const blockEvents = blockStats[0]?.count || 0;

        // Log audit event
        await logAuditEvent(
          userId,
          AuditEvents.DATA_EXPORTED,
          {
            type: 'analytics',
            child_ids: childIds,
            start_date: validated.start_date,
            end_date: validated.end_date,
          },
          request as any,
          'info'
        );

        logger.info('Analytics generated', {
          userId,
          childCount: childIds.length,
          totalScreenTime,
          totalSessions,
        });

        return reply.send({
          success: true,
          data: {
            total_screen_time: totalScreenTime,
            total_sessions: totalSessions,
            by_category: byCategory,
            by_type: byType,
            top_apps: topApps.map((app) => ({
              name: app.item_name,
              duration: app.duration,
              category: app.category,
            })),
            block_events: blockEvents,
            period: {
              start_date: validated.start_date,
              end_date: validated.end_date,
            },
          },
        });
      } catch (error: unknown) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        const errorStack = error instanceof Error ? error.stack : undefined;
        
        logger.error('Failed to generate analytics', {
          error: errorMessage,
          stack: errorStack,
          userId: request.userId,
        });

        if (error instanceof z.ZodError) {
          return reply.status(400).send({
            success: false,
            error: 'Invalid request data',
            details: error.issues,
          });
        }

        return reply.status(500).send({
          success: false,
          error: 'Failed to generate analytics',
        });
      }
    }
  );

  /**
   * POST /web-vitals - Record web vitals metrics from frontend
   *
   * Body:
   * {
   *   name: "LCP|FCP|CLS|INP|TTFB",
   *   value: number,
   *   rating: "good|needs-improvement|poor",
   *   delta: number,
   *   id: string,
   *   navigationType: "navigate|reload|back-forward|back-forward-cache",
   *   url: string,
   *   timestamp: number
   * }
   */
  fastify.options('/web-vitals', async (_request, reply) => {
    return reply.status(204).send();
  });

  fastify.post('/web-vitals', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const body = request.body as any;
      
      // Log web vitals for monitoring
      logger.info('Web vitals received', {
        metric: body.name,
        value: body.value,
        rating: body.rating,
        url: body.url,
      });

      // Could store in database for later analysis
      // For now, just acknowledge receipt
      return reply.status(202).send({ success: true, message: 'Web vitals recorded' });
    } catch (error) {
      logger.warn('Failed to record web vitals', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.status(400).send({ success: false, error: 'Failed to record web vitals' });
    }
  });
};

export default analyticsRoutes;
