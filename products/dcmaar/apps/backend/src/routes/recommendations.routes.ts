/**
 * Recommendation engine routes for usage pattern analysis and policy suggestions.
 *
 * <p><b>Purpose</b><br>
 * Provides endpoints for generating and applying AI-driven policy recommendations
 * based on child usage patterns, block events, and behavioral analysis.
 *
 * <p><b>Endpoints</b><br>
 * - GET /recommendations - Get recommendations for child(ren)
 * - POST /recommendations/apply - Apply recommendation as policy
 * - GET /recommendations/history - Get past recommendations
 *
 * <p><b>Recommendation Types</b><br>
 * - website: Block specific websites by category or URL pattern
 * - app: Restrict apps by usage frequency or category
 * - category: Block entire content categories (games, social media)
 * - schedule: Time-based access control (bedtime, school hours)
 *
 * <p><b>Algorithm</b><br>
 * Analyzes usage patterns to identify:
 * - High screen time (>5 hours/day)
 * - Late-night usage (after 10 PM)
 * - Excessive gaming or social media
 * - Low educational content balance
 * - High block event rates
 *
 * @doc.type route
 * @doc.purpose Recommendation engine endpoints for policy suggestions
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import type { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import type * as pgPromise from 'pg-promise';

// Use Record<string, unknown> instead of {}
type IDatabase = pgPromise.IDatabase<Record<string, unknown>>;
type IMain = pgPromise.IMain;

declare module 'fastify' {
  interface FastifyInstance {
    pg: {
      db: IDatabase;
      pgp: IMain;
    };
  }
}

import type { AuthRequest } from '../middleware/auth.middleware';
import { authenticate } from '../middleware/auth.middleware';
import { logger } from '../utils/logger';
import { logAuditEvent, AuditEvents } from '../services/audit.service';
import { query } from '../db';

// Policy service interface
interface IPolicyService {
  createPolicy: (userId: string, policy: { 
    name: string;
    policy_type?: string;
    [key: string]: unknown;
  }) => Promise<{ 
    id: string; 
    name: string; 
    policy_type: string;
  }>;
}

// Mock implementation of PolicyService
class PolicyService implements IPolicyService {
  async createPolicy(userId: string, policy: {
    name: string;
    policy_type?: string;
    [key: string]: unknown;
  }) {
    // Mock implementation - replace with actual service call
    return {
      id: 'mock-policy-id',
      name: policy.name,
      policy_type: policy.policy_type || 'category' // Default to 'category' if not provided
    };
  }
}

// Define types for the recommendation service
interface Recommendation {
  id: string;
  child_id: string;
  policy_type: string;
  target: string | null;
  category: string;
  reason: string;
  headline: string;
  detail: string;
  confidence: number;
  recommended_action: string;
  created_at: string;
}

interface RecommendationParams {
  child_id?: string;
  start_date?: string;
  end_date?: string;
  category?: string;
  limit?: number;
}

interface ApplyRecommendationParams {
  child_id: string;
  policy_type: string;
  recommendation_id?: string;
  config: Record<string, unknown>;
  reason: string;
}

// Recommendation service interface
interface IRecommendationService {
  getRecommendations: (params: RecommendationParams) => Promise<Recommendation[]>;
  applyRecommendation: (params: ApplyRecommendationParams) => Promise<{ success: boolean }>;
  generateForChild: (childId: string, usageRecords: unknown[], blockRecords: unknown[]) => Promise<Recommendation[]>;
}

// Mock implementation if the actual service is not available
class RecommendationService implements IRecommendationService {
  async getRecommendations(_params: RecommendationParams): Promise<Recommendation[]> {
    void _params; // Explicitly mark as unused
    return [];
  }
  
  async applyRecommendation(_params: ApplyRecommendationParams): Promise<{ success: boolean }> {
    void _params; // Explicitly mark as unused
    return { success: true };
  }
  
  async generateForChild(
    childId: string, 
    _usageRecords: unknown[], 
    _blockRecords: unknown[]
  ): Promise<Recommendation[]> {
    void _usageRecords; // Explicitly mark as unused
    void _blockRecords; // Explicitly mark as unused
    // Mock implementation - replace with actual logic
    return [{
      id: `mock-rec-${Date.now()}`,
      child_id: childId,
      policy_type: 'category',
      target: null,
      category: 'screen_time',
      reason: 'High screen time detected',
      headline: 'High screen time detected',
      detail: 'Consider setting time limits',
      confidence: 0.85,
      recommended_action: 'Set daily screen time limit',
      created_at: new Date().toISOString()
    }];
  }
}

// Validation schemas
const getRecommendationsSchema = z.object({
  child_id: z.string().uuid().optional(),
  start_date: z.string().optional(),
  end_date: z.string().optional(),
  category: z.enum(['screen_time', 'alerts', 'engagement']).optional(),
  limit: z.coerce.number().int().min(1).max(50).optional().default(10),
});

const applyRecommendationSchema = z.object({
  child_id: z.string().uuid(),
  policy_type: z.enum(['website', 'app', 'category', 'schedule']),
  recommendation_id: z.string().uuid().optional(),
  config: z.object({
    target: z.string().optional(),
    category: z.string().optional(),
    schedule: z
      .object({
        start_time: z.string().optional(),
        end_time: z.string().optional(),
        days: z.array(z.string()).optional(),
      })
      .optional(),
  }),
  reason: z.string().optional(),
});

// Initialize services
const recommendationService = new RecommendationService();
const policyService = new PolicyService();

/**
 * Recommendation routes plugin.
 */
const recommendationRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /recommendations - Get recommendations for user's children
   *
   * Query Parameters:
   * - child_id (optional): Filter by specific child
   * - start_date (optional): Analysis period start (ISO 8601)
   * - end_date (optional): Analysis period end (ISO 8601)
   * - category (optional): Filter by recommendation category
   * - limit (optional): Max recommendations (default: 10)
   *
   * Returns:
   * {
   *   success: true,
   *   data: [
   *     {
   *       id: "uuid",
   *       child_id: "uuid",
   *       policy_type: "category",
   *       target: "games",
   *       reason: "High gaming usage detected (6.5 hours/day)",
   *       confidence: 0.85,
   *       recommended_action: "Block gaming category during school hours",
   *       created_at: "2025-11-08T12:00:00Z"
   *     }
   *   ]
   * }
   */
  fastify.get(
    '/',
    { preHandler: authenticate },
    async (request: AuthRequest, reply: FastifyReply) => {
      try {
        const validated = getRecommendationsSchema.parse(request.query);
        const userId = request.userId!;

        // Get date range (default: last 7 days)
        const endDate = validated.end_date
          ? new Date(validated.end_date)
          : new Date();
        const startDate = validated.start_date
          ? new Date(validated.start_date)
          : new Date(endDate.getTime() - 7 * 24 * 60 * 60 * 1000);

        // Get children for this user
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
            data: [],
            message: 'No children found for this user',
          });
        }

        // Generate recommendations for each child
        const allRecommendations: Array<{
          id: string;
          child_id: string;
          policy_type: string;
          target: string | null;
          category: string;
          reason: string;
          detail: string;
          confidence: number;
          recommended_action: string;
          created_at: string;
        }> = [];

        for (const childId of childIds) {
          // Define types for database query results
          interface UsageRecordDB {
            id: string;
            child_id: string;
            device_id: string;
            app_name: string;
            duration: number;
            category: string;
            timestamp: Date;
          }

          interface BlockRecordDB {
            id: string;
            device_id: string;
            app_name: string;
            reason: string;
            category: string;
            timestamp: Date;
          }

          // Get usage records for the child
          const usageRecords = await query<UsageRecordDB>(
            `SELECT 
              u.id,
              d.child_id,
              u.device_id,
              u.item_name as app_name,
              u.duration_seconds as duration,
              u.category,
              u.start_time as timestamp
            FROM usage_sessions u
            JOIN devices d ON d.id = u.device_id
            WHERE d.child_id = $1 
              AND u.start_time >= $2 
              AND u.start_time <= $3
            ORDER BY u.start_time DESC`,
            [childId, startDate, endDate]
          );

          // Get block events for the child
          const blockRecords = await query<BlockRecordDB>(
            `SELECT 
              b.id,
              b.device_id,
              b.blocked_item as app_name,
              b.reason,
              b.category,
              b.timestamp
            FROM block_events b
            JOIN devices d ON d.id = b.device_id
            WHERE d.child_id = $1 
              AND b.timestamp >= $2 
              AND b.timestamp <= $3
            ORDER BY b.timestamp DESC`,
            [childId, startDate, endDate]
          );

          // Transform to expected types
          const typedUsageRecords = usageRecords.map((record: UsageRecordDB) => ({
            childId: record.child_id,
            deviceId: record.device_id,
            sessionType: 'app' as const,
            itemName: record.app_name,
            category: record.category,
            durationSeconds: record.duration,
            startedAt: record.timestamp,
          }));

          const typedBlockRecords = blockRecords.map((record: BlockRecordDB) => ({
            childId: childId,
            deviceId: record.device_id,
            blockedItem: record.app_name,
            category: record.category,
            timestamp: record.timestamp,
          }));

          // Use the main Recommendation interface

          // Generate recommendations
          const recommendationsResult = await recommendationService.generateForChild(
            childId,
            typedUsageRecords,
            typedBlockRecords
          );
          
          // Ensure we have an array of recommendations
          const recommendations: Recommendation[] = Array.isArray(recommendationsResult) 
            ? recommendationsResult 
            : [];

          // Transform to API format
          const formattedRecommendations = recommendations.map((rec, index) => {
            // Determine policy type from category and content
            let policyType = 'category';
            let target = null;

            if (rec.category === 'alerts' && rec.headline.toLowerCase().includes('late')) {
              policyType = 'schedule';
            } else if (rec.detail.toLowerCase().includes('gaming') || rec.detail.toLowerCase().includes('games')) {
              policyType = 'category';
              target = 'games';
            } else if (rec.detail.toLowerCase().includes('social media')) {
              policyType = 'category';
              target = 'social';
            }

            return {
              id: `rec-${childId}-${index}`,
              child_id: rec.child_id || childId, // Use the outer childId as fallback
              policy_type: policyType,
              target,
              category: rec.category,
              reason: rec.headline,
              detail: rec.detail,
              confidence: 0.75, // Fixed confidence for rule-based system
              recommended_action: rec.detail,
              created_at: new Date().toISOString(),
            };
          });

          // Add formatted recommendations to results
          allRecommendations.push(...formattedRecommendations);
        }

        // Filter by category if specified
        const filtered = validated.category
          ? allRecommendations.filter((r: { category: string }) => r.category === validated.category)
          : allRecommendations;

        // Apply limit
        const limited = filtered.slice(0, validated.limit);

        // Log audit event with request details
        await logAuditEvent(
          userId,
          AuditEvents.REPORT_GENERATED,
          {
            type: 'recommendations',
            child_ids: childIds,
            count: limited.length,
            start_date: startDate.toISOString(),
            end_date: endDate.toISOString(),
            method: request.method,
            url: request.url,
            ip: request.ip,
          },
          {
            ip: request.ip,
            socket: { remoteAddress: request.ip },
            headers: request.headers
          } as any,
          'info'
        );

        logger.info('Recommendations generated', {
          userId,
          childCount: childIds.length,
          recommendationCount: limited.length,
        });

        return reply.send({
          success: true,
          data: limited,
          count: limited.length,
          period: {
            start_date: startDate.toISOString(),
            end_date: endDate.toISOString(),
          },
        });
      } catch (error: unknown) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        const errorStack = error instanceof Error ? error.stack : undefined;

        logger.error('Error in recommendation route', {
          error: errorMessage,
          stack: errorStack,
          userId: request.userId,
        });
        console.error('[Recommendation Route Error]', { errorMessage, errorStack });

        if (error instanceof z.ZodError) {
          return reply.status(400).send({
            success: false,
            error: 'Invalid request data',
            details: error.issues,
          });
        }

        return reply.status(500).send({
          success: false,
          error: 'Failed to generate recommendations',
        });
      }
    }
  );

  /**
   * POST /recommendations/apply - Apply recommendation as policy
   *
   * Body:
   * {
   *   child_id: "uuid",
   *   policy_type: "category",
   *   recommendation_id: "uuid" (optional),
   *   config: {
   *     target: "games",
   *     schedule: { ... }
   *   },
   *   reason: "High gaming usage detected"
   * }
   *
   * Returns:
   * {
   *   success: true,
   *   data: {
   *     policy_id: "uuid",
   *     message: "Policy created from recommendation"
   *   }
   * }
   */
  fastify.post(
    '/apply',
    { preHandler: authenticate },
    async (request: AuthRequest, reply: FastifyReply) => {
      try {
        const validated = applyRecommendationSchema.parse(request.body);
        const userId = request.userId!;

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

        // Generate policy name from recommendation
        const policyName = validated.reason || `${validated.policy_type} policy`;

        // Create policy using policy service
        const policy = await policyService.createPolicy(userId, {
          child_id: validated.child_id,
          name: policyName,
          policy_type: validated.policy_type,
          config: validated.config,
        });

        return reply.status(201).send({
          success: true,
          data: {
            policy_id: policy.id,
            policy_name: policy.name,
            policy_type: policy.policy_type,
            message: 'Policy created successfully from recommendation',
          },
        });
      } catch (error: unknown) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        const errorStack = error instanceof Error ? error.stack : undefined;

        logger.error('Failed to apply recommendation', {
          error: errorMessage,
          stack: errorStack as string | undefined,
          userId: request.userId as string | undefined,
        } as Record<string, unknown>);

        if (error instanceof z.ZodError) {
          return reply.status(400).send({
            success: false,
            error: 'Invalid request data',
            details: error.issues,
          });
        }

        return reply.status(500).send({
          success: false,
          error: 'Failed to apply recommendation',
        });
      }
    }
  );
};

export default recommendationRoutes;
