/**
 * Analytics API routes for Flashit Web API
 * Provides analytics dashboard data and insights
 *
 * @doc.type route
 * @doc.purpose Analytics and insights API endpoints
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { requireAuth } from '../lib/auth.js';
import { prisma } from '../lib/prisma.js';
import { AnalyticsService, AnalyticsAggregator, MetricsCollector } from '../services/analytics/analytics-service';
import { generateInsights, detectPatterns } from '../services/java-agents/reflection-client.js';
import { ReportingService } from '../services/reporting/report-service';
import { MeaningTelemetryService } from '../services/analytics/meaning-telemetry-service.js';

// Validation schemas
const dashboardQuerySchema = z.object({
  period: z.enum(['day', 'week', 'month']).default('month'),
  sphereId: z.string().uuid().optional(),
});

const insightsQuerySchema = z.object({
  category: z.enum(['productivity', 'emotional', 'content', 'growth', 'usage', 'social']).optional(),
  limit: z.number().min(1).max(50).default(10),
  includeActionable: z.boolean().default(true),
});

const metricsQuerySchema = z.object({
  metricName: z.string().min(1).max(100),
  since: z.string().datetime().optional(),
  sphereId: z.string().uuid().optional(),
});

const generateReportSchema = z.object({
  format: z.enum(['pdf', 'csv', 'json']).default('pdf'),
  period: z.enum(['week', 'month', 'quarter', 'year']).default('month'),
  sphereIds: z.array(z.string().uuid()).optional(),
  emotions: z.array(z.string()).optional(),
  tags: z.array(z.string()).optional(),
  importance: z.object({
    min: z.number().min(1).max(5).optional(),
    max: z.number().min(1).max(5).optional(),
  }).optional(),
  delivery: z.object({
    method: z.enum(['download', 'email']).default('download'),
    email: z.string().email().optional(),
  }).optional(),
});

const reportStatusSchema = z.object({
  jobId: z.string(),
});

const trackActivitySchema = z.object({
  activity: z.string().min(1).max(100),
  metadata: z.record(z.any()).optional(),
});

// Meaning metrics schemas (Day 42)
const meaningMetricsQuerySchema = z.object({
  periodDays: z.coerce.number().int().min(7).max(365).default(30),
  sphereId: z.string().uuid().optional(),
});

const meaningDashboardQuerySchema = z.object({
  sphereId: z.string().uuid().optional(),
});

export default async function analyticsRoutes(fastify: FastifyInstance) {

  /**
   * Get user dashboard analytics
   * GET /api/analytics/dashboard
   */
  fastify.get('/dashboard', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(dashboardQuerySchema),
      response: {
        200: zodToJsonSchema(z.object({
          dailyData: z.array(z.object({
            date: z.string(),
            moments: z.number(),
            productivity: z.number(),
            emotionDiversity: z.number(),
            searches: z.number(),
            sessionTime: z.number(),
          })),
          insights: z.array(z.object({
            type: z.string(),
            category: z.string(),
            title: z.string(),
            description: z.string(),
            confidence: z.number(),
            priority: z.number(),
            actionable: z.boolean(),
            createdAt: z.string(),
          })),
          sphereActivity: z.array(z.object({
            id: z.string(),
            name: z.string(),
            momentCount: z.number(),
            avgImportance: z.number(),
          })),
          trends: z.object({
            productivity: z.enum(['up', 'down', 'stable']),
            emotion: z.enum(['up', 'down', 'stable']),
          }),
          summary: z.object({
            totalMoments: z.number(),
            avgProductivity: z.number(),
            totalSearches: z.number(),
            activeDays: z.number(),
          }),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const dashboardData = await AnalyticsAggregator.getUserDashboardData(userId);

      if (!dashboardData) {
        return {
          dailyData: [],
          insights: [],
          sphereActivity: [],
          trends: { productivity: 'stable', emotion: 'stable' },
          summary: { totalMoments: 0, avgProductivity: 0, totalSearches: 0, activeDays: 0 },
        };
      }

      return dashboardData;

    } catch (error) {
      fastify.log.error('Dashboard analytics failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get analytics data',
      });
    }
  });

  /**
   * Get meaning metrics (Day 42 - Return-to-Meaning, Annotation Lag, etc.)
   * GET /api/analytics/meaning
   */
  fastify.get('/meaning', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(meaningMetricsQuerySchema),
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { periodDays, sphereId } = meaningMetricsQuerySchema.parse(request.query);

    try {
      const metrics = await MeaningTelemetryService.getMeaningMetrics(userId, {
        periodDays,
        sphereId,
      });

      return metrics;

    } catch (error) {
      fastify.log.error('Meaning metrics failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get meaning metrics',
      });
    }
  });

  /**
   * Get meaning dashboard summary
   * GET /api/analytics/meaning/summary
   */
  fastify.get('/meaning/summary', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(meaningDashboardQuerySchema),
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { sphereId } = meaningDashboardQuerySchema.parse(request.query);

    try {
      const summary = await MeaningTelemetryService.getDashboardSummary(userId, sphereId);

      return summary;

    } catch (error) {
      fastify.log.error('Meaning summary failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get meaning summary',
      });
    }
  });

  /**
   * Get return-to-meaning rate details
   * GET /api/analytics/meaning/return-rate
   */
  fastify.get('/meaning/return-rate', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(meaningMetricsQuerySchema),
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { periodDays, sphereId } = meaningMetricsQuerySchema.parse(request.query);

    try {
      const data = await MeaningTelemetryService.calculateReturnToMeaningRate(
        userId,
        periodDays,
        sphereId
      );

      return data;

    } catch (error) {
      fastify.log.error('Return rate calculation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to calculate return-to-meaning rate',
      });
    }
  });

  /**
   * Get cross-time referencing analysis
   * GET /api/analytics/meaning/temporal-arcs
   */
  fastify.get('/meaning/temporal-arcs', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(meaningMetricsQuerySchema),
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { periodDays, sphereId } = meaningMetricsQuerySchema.parse(request.query);

    try {
      const data = await MeaningTelemetryService.analyzeeCrossTimeReferencing(
        userId,
        periodDays,
        sphereId
      );

      return data;

    } catch (error) {
      fastify.log.error('Temporal arc analysis failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to analyze temporal arcs',
      });
    }
  });

  /**
   * Get language evolution analysis
   * GET /api/analytics/meaning/language-evolution
   */
  fastify.get('/meaning/language-evolution', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(meaningMetricsQuerySchema),
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { periodDays, sphereId } = meaningMetricsQuerySchema.parse(request.query);

    try {
      const data = await MeaningTelemetryService.analyzeLanguageEvolution(
        userId,
        periodDays,
        sphereId
      );

      return data;

    } catch (error) {
      fastify.log.error('Language evolution analysis failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to analyze language evolution',
      });
    }
  });

  /**
   * Get user insights
   * GET /api/analytics/insights
   */
  fastify.get('/insights', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(insightsQuerySchema),
      response: {
        200: zodToJsonSchema(z.object({
          insights: z.array(z.object({
            id: z.string(),
            type: z.string(),
            category: z.string(),
            title: z.string(),
            description: z.string(),
            data: z.any(),
            confidence: z.number(),
            priority: z.number(),
            actionable: z.boolean(),
            actionTaken: z.boolean(),
            createdAt: z.string(),
          })),
          totalCount: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const { category, limit, includeActionable } = request.query;
    const userId = request.user.userId;

    try {
      const whereClause = [`user_id = $1`];
      const params: any[] = [userId];

      if (category) {
        whereClause.push(`insight_category = $${params.length + 1}`);
        params.push(category);
      }

      if (!includeActionable) {
        whereClause.push('is_actionable = false');
      }

      const insights = await prisma.$queryRawUnsafe(`
        SELECT
          id,
          insight_type,
          insight_category,
          title,
          description,
          insights_data,
          confidence_score,
          priority_score,
          is_actionable,
          action_taken,
          created_at
        FROM analytics.user_insights
        WHERE ${whereClause.join(' AND ')}
          AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY priority_score DESC, created_at DESC
        LIMIT $${params.length + 1}
      `, ...params, limit) as any[];

      const totalCount = await prisma.$queryRawUnsafe(`
        SELECT COUNT(*) as count
        FROM analytics.user_insights
        WHERE ${whereClause.join(' AND ')}
          AND (expires_at IS NULL OR expires_at > NOW())
      `, ...params.slice(0, -1)) as any[];

      return {
        insights: insights.map((insight: any) => ({
          id: insight.id,
          type: insight.insight_type,
          category: insight.insight_category,
          title: insight.title,
          description: insight.description,
          data: insight.insights_data,
          confidence: parseFloat(insight.confidence_score),
          priority: parseFloat(insight.priority_score),
          actionable: insight.is_actionable,
          actionTaken: insight.action_taken,
          createdAt: insight.created_at.toISOString(),
        })),
        totalCount: parseInt(totalCount[0].count),
      };

    } catch (error) {
      fastify.log.error('Insights fetch failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get insights',
      });
    }
  });

  /**
   * Generate new insights for user
   * POST /api/analytics/insights/generate
   */
  fastify.post('/insights/generate', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
          insightsGenerated: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      // Generate comprehensive insights using Java Agent
      const insightsResult = await generateInsights(userId, undefined, 'month', 100);
      const patternsResult = await detectPatterns(userId, undefined, 'month', 100);

      // Convert to insights format and store
      let totalInsights = 0;

      // Store main insights
      for (const insight of insightsResult.insights) {
        await prisma.$executeRaw`
          INSERT INTO analytics.user_insights
          (user_id, insight_type, insight_category, title, description, insights_data, confidence_score, priority_score, is_actionable)
          VALUES (${userId}::uuid, 'behavior', 'general', ${insight.title || 'Insight'}, ${insight.description},
                  ${JSON.stringify(insight)}, 0.8, 0.7, true)
        `;
        totalInsights++;
      }

      // Store pattern insights
      for (const pattern of patternsResult.patterns) {
        await prisma.$executeRaw`
          INSERT INTO analytics.user_insights
          (user_id, insight_type, insight_category, title, description, insights_data, confidence_score, priority_score, is_actionable)
          VALUES (${userId}::uuid, 'pattern', 'behavior', ${pattern.title || 'Pattern'}, ${pattern.description},
                  ${JSON.stringify(pattern)}, ${pattern.confidence || 0.75}, 0.7, true)
        `;
        totalInsights++;
      }

      return {
        message: 'Insights generated successfully',
        insightsGenerated: totalInsights,
      };

    } catch (error) {
      fastify.log.error('Insight generation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to generate insights',
      });
    }
  });

  /**
   * Mark insight as action taken
   * PUT /api/analytics/insights/:insightId/action
   */
  fastify.put('/insights/:insightId/action', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        insightId: z.string().uuid(),
      })),
    },
  }, async (request, reply) => {
    const { insightId } = request.params;
    const userId = request.user.userId;

    try {
      const result = await prisma.$executeRaw`
        UPDATE analytics.user_insights
        SET action_taken = true, updated_at = NOW()
        WHERE id = ${insightId}::uuid AND user_id = ${userId}::uuid
      `;

      if (result === 0) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Insight not found or access denied',
        });
      }

      return reply.status(204).send();

    } catch (error) {
      fastify.log.error('Insight action update failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to update insight',
      });
    }
  });

  /**
   * Get weekly summary
   * GET /api/analytics/weekly-summary
   */
  fastify.get('/weekly-summary', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(z.object({
        weekStart: z.string().datetime().optional(),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          weekStart: z.string(),
          weekEnd: z.string(),
          totalMoments: z.number(),
          avgProductivity: z.number(),
          avgEmotionDiversity: z.number(),
          totalSearches: z.number(),
          totalSessionTime: z.number(),
          activeDays: z.number(),
          changes: z.object({
            productivity: z.number(),
            moments: z.number(),
          }),
        }).nullable()),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { weekStart } = request.query;

    try {
      const startDate = weekStart ? new Date(weekStart) : (() => {
        const now = new Date();
        const dayOfWeek = now.getDay();
        const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek; // Get Monday of current week
        const monday = new Date(now);
        monday.setDate(now.getDate() + mondayOffset);
        monday.setHours(0, 0, 0, 0);
        return monday;
      })();

      const summary = await AnalyticsAggregator.generateWeeklySummary(userId, startDate);

      return summary;

    } catch (error) {
      fastify.log.error('Weekly summary failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get weekly summary',
      });
    }
  });

  /**
   * Get real-time metrics
   * GET /api/analytics/metrics
   */
  fastify.get('/metrics', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(metricsQuerySchema),
      response: {
        200: zodToJsonSchema(z.object({
          metrics: z.array(z.object({
            value: z.number(),
            timestamp: z.string(),
            tags: z.record(z.any()).optional(),
          })),
        })),
      },
    },
  }, async (request, reply) => {
    const { metricName, since, sphereId } = request.query;
    const userId = request.user.userId;

    try {
      const sinceDate = since ? new Date(since) : new Date(Date.now() - 24 * 60 * 60 * 1000); // Last 24 hours

      const metrics = await MetricsCollector.getRealtimeMetrics(
        metricName,
        sinceDate,
        userId,
        sphereId
      );

      return {
        metrics: metrics.map(metric => ({
          value: metric.value,
          timestamp: metric.timestamp.toISOString(),
          tags: metric.tags,
        })),
      };

    } catch (error) {
      fastify.log.error('Metrics fetch failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get metrics',
      });
    }
  });

  /**
   * Track user activity
   * POST /api/analytics/track
   */
  fastify.post('/track', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(trackActivitySchema),
    },
  }, async (request, reply) => {
    const { activity, metadata } = request.body;
    const userId = request.user.userId;

    try {
      await MetricsCollector.trackActivity(userId, activity, metadata);

      return reply.status(204).send();

    } catch (error) {
      fastify.log.error('Activity tracking failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to track activity',
      });
    }
  });

  /**
   * Generate comprehensive report
   * POST /api/analytics/reports/generate
   */
  fastify.post('/reports/generate', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(generateReportSchema),
      response: {
        200: zodToJsonSchema(z.object({
          jobId: z.string(),
          message: z.string(),
          estimatedCompletionTime: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { format, period, sphereIds, emotions, tags, importance, delivery } = request.body;
    const userId = request.user.userId;

    try {
      // Calculate date range based on period
      const end = new Date();
      const start = new Date();

      switch (period) {
        case 'week':
          start.setDate(end.getDate() - 7);
          break;
        case 'month':
          start.setMonth(end.getMonth() - 1);
          break;
        case 'quarter':
          start.setMonth(end.getMonth() - 3);
          break;
        case 'year':
          start.setFullYear(end.getFullYear() - 1);
          break;
      }

      const filters = {
        dateRange: { start, end },
        sphereIds,
        emotions,
        tags,
        importance,
      };

      // Generate report
      const jobId = await ReportingService.generateReport(userId, format, filters);

      // Estimate completion time based on format and data size
      const estimatedMs = format === 'pdf' ? 60000 : 30000; // PDF takes longer
      const estimatedCompletion = new Date(Date.now() + estimatedMs).toISOString();

      return {
        jobId,
        message: `${format.toUpperCase()} report generation started`,
        estimatedCompletionTime: estimatedCompletion,
      };

    } catch (error) {
      fastify.log.error('Report generation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to generate report',
      });
    }
  });

  /**
   * Get report generation status
   * GET /api/analytics/reports/:jobId/status
   */
  fastify.get('/reports/:jobId/status', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(reportStatusSchema),
      response: {
        200: zodToJsonSchema(z.object({
          jobId: z.string(),
          status: z.string(),
          progress: z.number().optional(),
          downloadUrl: z.string().optional(),
          expiresAt: z.string().optional(),
          error: z.string().optional(),
        })),
      },
    },
  }, async (request, reply) => {
    const { jobId } = request.params;

    try {
      const status = await ReportingService.getReportStatus(jobId);

      const response: any = {
        jobId,
        status: status.status,
      };

      if (status.progress !== undefined) {
        response.progress = status.progress;
      }

      if (status.result) {
        response.downloadUrl = status.result.downloadUrl;
        response.expiresAt = status.result.expiresAt;
      }

      if (status.error) {
        response.error = status.error;
      }

      return response;

    } catch (error) {
      fastify.log.error('Report status check failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get report status',
      });
    }
  });

  /**
   * Download generated report
   * GET /api/analytics/reports/:jobId/download
   */
  fastify.get('/reports/:jobId/download', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(reportStatusSchema),
    },
  }, async (request, reply) => {
    const { jobId } = request.params;

    try {
      const status = await ReportingService.getReportStatus(jobId);

      if (status.status !== 'completed' || !status.result?.filePath) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Report not ready or not found',
        });
      }

      const filePath = status.result.filePath;
      const fs = await import('fs');

      if (!fs.existsSync(filePath)) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Report file not found',
        });
      }

      // Determine content type based on file extension
      const ext = filePath.split('.').pop();
      const contentTypes = {
        pdf: 'application/pdf',
        csv: 'text/csv',
        json: 'application/json',
      };

      const contentType = contentTypes[ext as keyof typeof contentTypes] || 'application/octet-stream';
      const fileName = `flashit-report-${jobId}.${ext}`;

      return reply
        .header('Content-Type', contentType)
        .header('Content-Disposition', `attachment; filename="${fileName}"`)
        .send(fs.createReadStream(filePath));

    } catch (error) {
      fastify.log.error('Report download failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to download report',
      });
    }
  });

  /**
   * Get sphere analytics (for sphere owners)
   * GET /api/analytics/spheres/:sphereId
   */
  fastify.get('/spheres/:sphereId', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        sphereId: z.string().uuid(),
      })),
      querystring: zodToJsonSchema(z.object({
        period: z.enum(['week', 'month', 'quarter']).default('month'),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          sphereId: z.string(),
          sphereName: z.string(),
          analytics: z.object({
            totalMoments: z.number(),
            activeUsers: z.number(),
            avgEngagement: z.number(),
            growthRate: z.number(),
          }),
          userActivity: z.array(z.object({
            userId: z.string(),
            momentCount: z.number(),
            engagement: z.number(),
          })),
          contentMetrics: z.object({
            textMoments: z.number(),
            mediaMoments: z.number(),
            transcribedMoments: z.number(),
          }),
        })),
      },
    },
  }, async (request, reply) => {
    const { sphereId } = request.params;
    const { period } = request.query;
    const userId = request.user.userId;

    try {
      // Verify user has access to sphere analytics (owner or editor)
      const sphereAccess = await prisma.sphereAccess.findFirst({
        where: {
          sphereId,
          userId,
          role: { in: ['OWNER', 'EDITOR'] },
          revokedAt: null,
        },
        include: {
          sphere: { select: { name: true } },
        },
      });

      if (!sphereAccess) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'Insufficient permissions to view sphere analytics',
        });
      }

      // Get sphere analytics
      const periodDays = period === 'week' ? 7 : period === 'month' ? 30 : 90;
      const since = new Date(Date.now() - periodDays * 24 * 60 * 60 * 1000);

      const [sphereMetrics, userActivity, contentMetrics] = await Promise.all([
        // Sphere metrics
        prisma.$queryRaw`
          SELECT
            COUNT(DISTINCT m.id) as total_moments,
            COUNT(DISTINCT m.user_id) as active_users,
            AVG(m.importance) as avg_engagement,
            (COUNT(DISTINCT m.id) FILTER (WHERE m.captured_at >= ${since}) * 1.0 / GREATEST(${periodDays}, 1)) as growth_rate
          FROM moments m
          WHERE m.sphere_id = ${sphereId}::uuid
            AND m.deleted_at IS NULL
        `,

        // User activity
        prisma.$queryRaw`
          SELECT
            m.user_id,
            COUNT(*) as moment_count,
            AVG(m.importance) as engagement
          FROM moments m
          WHERE m.sphere_id = ${sphereId}::uuid
            AND m.captured_at >= ${since}
            AND m.deleted_at IS NULL
          GROUP BY m.user_id
          ORDER BY moment_count DESC
          LIMIT 10
        `,

        // Content metrics
        prisma.$queryRaw`
          SELECT
            COUNT(*) FILTER (WHERE content_transcript IS NULL) as text_moments,
            COUNT(*) FILTER (WHERE content_transcript IS NOT NULL) as media_moments,
            COUNT(*) FILTER (WHERE content_transcript IS NOT NULL) as transcribed_moments
          FROM moments
          WHERE sphere_id = ${sphereId}::uuid
            AND captured_at >= ${since}
            AND deleted_at IS NULL
        `,
      ]);

      const metrics = sphereMetrics[0] as any;
      const content = contentMetrics[0] as any;

      return {
        sphereId,
        sphereName: sphereAccess.sphere.name,
        analytics: {
          totalMoments: parseInt(metrics.total_moments) || 0,
          activeUsers: parseInt(metrics.active_users) || 0,
          avgEngagement: parseFloat(metrics.avg_engagement) || 0,
          growthRate: parseFloat(metrics.growth_rate) || 0,
        },
        userActivity: (userActivity as any[]).map((user: any) => ({
          userId: user.user_id,
          momentCount: parseInt(user.moment_count),
          engagement: parseFloat(user.engagement) || 0,
        })),
        contentMetrics: {
          textMoments: parseInt(content.text_moments) || 0,
          mediaMoments: parseInt(content.media_moments) || 0,
          transcribedMoments: parseInt(content.transcribed_moments) || 0,
        },
      };

    } catch (error) {
      fastify.log.error('Sphere analytics failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get sphere analytics',
      });
    }
  });

  /**
   * Get analytics queue status (admin only)
   * GET /api/analytics/admin/queue-status
   */
  fastify.get('/admin/queue-status', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          waiting: z.number(),
          active: z.number(),
          completed: z.number(),
          failed: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    // Note: In production, add admin role check

    try {
      const queueStats = await AnalyticsService.getQueueStats();

      return queueStats;

    } catch (error) {
      fastify.log.error('Queue status failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get queue status',
      });
    }
  });
}
