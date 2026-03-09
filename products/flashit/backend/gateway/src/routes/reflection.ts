/**
 * Reflection Agent Routes
 * AI-powered insights, pattern detection, and connections
 * 
 * @doc.type routes
 * @doc.purpose Expose Java Agent reflection capabilities
 * @doc.layer product
 * @doc.pattern Routes
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { getJavaAgentClient, MomentData } from '../services/java-agents/agent-client';
import { checkAIInsightLimit } from '../services/billing/usage-limits';
import { StripeBillingService } from '../services/billing/stripe-service';
import { requireAuth, type JwtPayload } from '../lib/auth';

const generateInsightsSchema = z.object({
  sphereId: z.string().uuid(),
  timeWindowDays: z.number().min(1).max(90).default(7),
});

const reflectionTypeSchema = z.object({
  sphereId: z.string().uuid(),
  timeWindowDays: z.number().min(1).max(365).default(30),
});

export const registerReflectionRoutes = async (app: FastifyInstance) => {
  /**
   * POST /api/reflection/insights
   * Generate AI insights for a specific sphere
   */
  app.post('/api/reflection/insights', 
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as JwtPayload).userId;
      const { sphereId, timeWindowDays } = generateInsightsSchema.parse(request.body);

      // Check billing limits
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkAIInsightLimit(userId, subscriptionInfo.tier);
      
      if (!limitCheck.allowed) {
        return reply.code(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'AI insight limit reached',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl
        });
      }

      // Verify access to sphere
      const sphereAccess = await prisma.sphereAccess.findFirst({
        where: {
          userId,
          sphereId,
          revokedAt: null,
        }
      });

      if (!sphereAccess) {
        return reply.code(403).send({ error: 'Access Denied', message: 'You do not have access to this sphere' });
      }

      // Fetch moments
      const startDate = new Date();
      startDate.setDate(startDate.getDate() - timeWindowDays);

      const moments = await prisma.moment.findMany({
        where: {
          sphereId,
          capturedAt: {
            gte: startDate
          },
          deletedAt: null
        },
        orderBy: {
          capturedAt: 'desc'
        },
        take: 50 // Limit context size
      });

      if (moments.length === 0) {
        return reply.send({
          success: true,
          data: {
            summary: 'No moments found in this time range.',
            insights: [],
            patterns: [],
            connections: []
          }
        });
      }

      // Map to Java Agent DTO
      const momentData: MomentData[] = moments.map(m => ({
        id: m.id,
        content: m.contentText || '',
        transcript: m.contentTranscript || undefined,
        capturedAt: m.capturedAt.toISOString(),
        emotions: (m.emotions as string[]) || [], // emotions is Json in Prisma
        tags: [] // Tags handling omitted for brevity/simplicity unless relation exists
      }));

      // Call Java Agent
      try {
        const client = getJavaAgentClient();
        const response = await client.generateInsights({
          userId,
          sphereId,
          moments: momentData,
          reflectionType: 'insights'
        });

        // Track usage — create AIInsight record so future limit checks reflect this generation
        await prisma.aiInsight.create({
          data: {
            userId,
            insightType: 'reflection_insights',
            title: response.summary?.substring(0, 500) || 'AI Insight',
            content: response.summary || '',
            confidence: 0.80,
            relatedMoments: momentData.map(m => m.id),
            metadata: {
              sphereId,
              timeWindowDays,
              momentCount: momentData.length,
              patterns: response.patterns?.length || 0,
              connections: response.connections?.length || 0,
            },
          },
        });

        return reply.send({
          success: true,
          data: response
        });

      } catch (error) {
        request.log.error({ error }, 'Reflection Agent Error');
        return reply.code(502).send({
          error: 'AI Service Error',
          message: 'Failed to generate insights. Please try again later.'
        });
      }
    }
  );

  // -----------------------------------------------------------------------
  // Shared helper: fetch moments and convert to DTO
  // -----------------------------------------------------------------------

  async function fetchMomentData(
    userId: string,
    sphereId: string,
    timeWindowDays: number,
    maxItems = 50,
  ): Promise<MomentData[] | null> {
    // Verify access
    const access = await prisma.sphereAccess.findFirst({
      where: { userId, sphereId, revokedAt: null },
    });
    if (!access) return null;

    const since = new Date();
    since.setDate(since.getDate() - timeWindowDays);

    const moments = await prisma.moment.findMany({
      where: { sphereId, capturedAt: { gte: since }, deletedAt: null },
      orderBy: { capturedAt: 'desc' },
      take: maxItems,
    });

    return moments.map((m) => ({
      id: m.id,
      content: m.contentText || '',
      transcript: m.contentTranscript || undefined,
      capturedAt: m.capturedAt.toISOString(),
      emotions: (m.emotions as string[]) || [],
      tags: [],
    }));
  }

  // -----------------------------------------------------------------------
  // POST /api/reflection/patterns
  // Detect recurring patterns across moments in a sphere
  // -----------------------------------------------------------------------

  app.post(
    '/api/reflection/patterns',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as JwtPayload).userId;
      const { sphereId, timeWindowDays } = reflectionTypeSchema.parse(request.body);

      // Check billing limits
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkAIInsightLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.code(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'AI insight limit reached',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      const momentData = await fetchMomentData(userId, sphereId, timeWindowDays);
      if (momentData === null) {
        return reply.code(403).send({ error: 'Access Denied', message: 'You do not have access to this sphere' });
      }
      if (momentData.length === 0) {
        return reply.send({ success: true, data: { patterns: [], summary: 'No moments found.' } });
      }

      try {
        const client = getJavaAgentClient();
        const response = await client.detectPatterns({
          userId,
          sphereId,
          moments: momentData,
          reflectionType: 'patterns',
        });

        // Track usage
        await prisma.aiInsight.create({
          data: {
            userId,
            insightType: 'reflection_patterns',
            title: 'Pattern Detection',
            content: response.summary || '',
            confidence: 0.80,
            relatedMoments: momentData.map(m => m.id),
            metadata: { sphereId, timeWindowDays, momentCount: momentData.length },
          },
        });

        return reply.send({ success: true, data: response });
      } catch (error) {
        request.log.error({ error }, 'Pattern Detection Error');
        return reply.code(502).send({ error: 'AI Service Error', message: 'Failed to detect patterns.' });
      }
    },
  );

  // -----------------------------------------------------------------------
  // POST /api/reflection/connections
  // Find thematic connections between moments
  // -----------------------------------------------------------------------

  app.post(
    '/api/reflection/connections',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as JwtPayload).userId;
      const { sphereId, timeWindowDays } = reflectionTypeSchema.parse(request.body);

      // Check billing limits
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkAIInsightLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.code(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'AI insight limit reached',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      const momentData = await fetchMomentData(userId, sphereId, timeWindowDays);
      if (momentData === null) {
        return reply.code(403).send({ error: 'Access Denied', message: 'You do not have access to this sphere' });
      }
      if (momentData.length === 0) {
        return reply.send({ success: true, data: { connections: [], summary: 'No moments found.' } });
      }

      try {
        const client = getJavaAgentClient();
        const response = await client.findConnections({
          userId,
          sphereId,
          moments: momentData,
          reflectionType: 'connections',
        });

        // Track usage
        await prisma.aiInsight.create({
          data: {
            userId,
            insightType: 'reflection_connections',
            title: 'Connection Discovery',
            content: response.summary || '',
            confidence: 0.80,
            relatedMoments: momentData.map(m => m.id),
            metadata: { sphereId, timeWindowDays, momentCount: momentData.length },
          },
        });

        return reply.send({ success: true, data: response });
      } catch (error) {
        request.log.error({ error }, 'Connection Finding Error');
        return reply.code(502).send({ error: 'AI Service Error', message: 'Failed to find connections.' });
      }
    },
  );

  // -----------------------------------------------------------------------
  // GET /api/reflection/weekly?sphereId=...
  // Weekly reflection summary (last 7 days)
  // -----------------------------------------------------------------------

  app.get(
    '/api/reflection/weekly',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as JwtPayload).userId;
      const { sphereId } = z.object({ sphereId: z.string().uuid() }).parse(request.query);

      // Check billing limits
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkAIInsightLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.code(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'AI insight limit reached',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      const momentData = await fetchMomentData(userId, sphereId, 7, 100);
      if (momentData === null) {
        return reply.code(403).send({ error: 'Access Denied', message: 'You do not have access to this sphere' });
      }
      if (momentData.length === 0) {
        return reply.send({ success: true, data: { summary: 'No moments captured this week.', insights: [], patterns: [], connections: [] } });
      }

      try {
        const client = getJavaAgentClient();
        const response = await client.generateInsights({
          userId,
          sphereId,
          moments: momentData,
          reflectionType: 'insights',
          timeRange: 'weekly',
        });

        // Track usage
        await prisma.aiInsight.create({
          data: {
            userId,
            insightType: 'reflection_weekly',
            title: 'Weekly Reflection',
            content: response.summary || '',
            confidence: 0.80,
            relatedMoments: momentData.map(m => m.id),
            metadata: { sphereId, period: 'weekly', momentCount: momentData.length },
          },
        });

        return reply.send({ success: true, data: { ...response, period: 'weekly' } });
      } catch (error) {
        request.log.error({ error }, 'Weekly Reflection Error');
        return reply.code(502).send({ error: 'AI Service Error', message: 'Failed to generate weekly reflection.' });
      }
    },
  );

  // -----------------------------------------------------------------------
  // GET /api/reflection/monthly?sphereId=...
  // Monthly reflection summary (last 30 days)
  // -----------------------------------------------------------------------

  app.get(
    '/api/reflection/monthly',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as JwtPayload).userId;
      const { sphereId } = z.object({ sphereId: z.string().uuid() }).parse(request.query);

      // Check billing limits
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkAIInsightLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.code(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'AI insight limit reached',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      const momentData = await fetchMomentData(userId, sphereId, 30, 200);
      if (momentData === null) {
        return reply.code(403).send({ error: 'Access Denied', message: 'You do not have access to this sphere' });
      }
      if (momentData.length === 0) {
        return reply.send({ success: true, data: { summary: 'No moments captured this month.', insights: [], patterns: [], connections: [] } });
      }

      try {
        const client = getJavaAgentClient();
        const response = await client.generateInsights({
          userId,
          sphereId,
          moments: momentData,
          reflectionType: 'insights',
          timeRange: 'monthly',
        });

        // Track usage
        await prisma.aiInsight.create({
          data: {
            userId,
            insightType: 'reflection_monthly',
            title: 'Monthly Reflection',
            content: response.summary || '',
            confidence: 0.80,
            relatedMoments: momentData.map(m => m.id),
            metadata: { sphereId, period: 'monthly', momentCount: momentData.length },
          },
        });

        return reply.send({ success: true, data: { ...response, period: 'monthly' } });
      } catch (error) {
        request.log.error({ error }, 'Monthly Reflection Error');
        return reply.code(502).send({ error: 'AI Service Error', message: 'Failed to generate monthly reflection.' });
      }
    },
  );
};
