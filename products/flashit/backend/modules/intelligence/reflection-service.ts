/**
 * AI Reflection Service for Flashit
 * Generates AI-powered reflections and insights for user moments
 *
 * @doc.type service
 * @doc.purpose Generate contextual AI reflections using GPT-4
 * @doc.layer product
 * @doc.pattern AIReflectionService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import OpenAI from 'openai';
import { PrismaClient } from '@prisma/client';
import { VectorEmbeddingService } from '../embeddings/vector-service.js';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
  maxRetriesPerRequest: 3,
});

// OpenAI client
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

// Prisma client
const prisma = new PrismaClient();

// Queue configuration
const REFLECTION_QUEUE = 'flashit:reflections';

// Reflection types
export type ReflectionType = 'insights' | 'patterns' | 'connections';

// Job data interface
interface ReflectionJobData {
  momentId: string;
  userId: string;
  sphereId: string;
  reflectionType: ReflectionType;
  priority: 'high' | 'normal' | 'low';
  context?: {
    recentMoments?: number;
    timeRange?: string;
    includeTranscripts?: boolean;
  };
}

// Reflection result interface
interface ReflectionResult {
  type: ReflectionType;
  content: string;
  insights: string[];
  connections: Array<{
    momentId: string;
    relationship: string;
    confidence: number;
  }>;
  themes: string[];
  actionItems: string[];
  confidence: number;
  citations: Array<{
    momentId: string;
    excerpt: string;
    relevance: string;
  }>;
  processingTimeMs: number;
  model: string;
}

// Reflection prompt templates
const REFLECTION_PROMPTS = {
  insights: {
    system: `You are an insightful AI assistant that helps users understand patterns and insights from their personal moments.

Your role is to:
1. Analyze the user's captured moments thoughtfully
2. Identify meaningful patterns, themes, and insights
3. Provide actionable reflections without being prescriptive
4. Maintain a supportive, non-judgmental tone
5. Cite specific moments to support your insights

Guidelines:
- Be encouraging and constructive
- Focus on growth and self-awareness
- Avoid making assumptions about mental health
- Keep insights grounded in the actual content provided
- Use "you" to address the user directly`,

    user: `Please analyze this moment and related context to provide thoughtful insights:

**Current Moment:**
{currentMoment}

**Recent Related Moments:**
{relatedMoments}

**Sphere Context:** {sphereName} - {sphereDescription}

Please provide:
1. Key insights about patterns or themes you notice
2. Potential connections between moments
3. Actionable reflections for personal growth
4. Any noteworthy changes or trends over time

Format your response with clear sections and cite specific moments when relevant.`,
  },

  patterns: {
    system: `You are a pattern recognition AI that helps users identify recurring themes, behaviors, and trends in their personal data.

Your role is to:
1. Identify recurring patterns across moments
2. Highlight behavioral trends and cycles
3. Note emotional patterns and triggers
4. Suggest areas for attention or celebration
5. Provide evidence-based pattern analysis

Guidelines:
- Be specific about what patterns you observe
- Quantify patterns when possible (frequency, timing, etc.)
- Distinguish between correlation and causation
- Focus on constructive pattern recognition
- Support findings with concrete examples`,

    user: `Analyze these moments for patterns and recurring themes:

**Current Moment:**
{currentMoment}

**Historical Context ({timeRange}):**
{relatedMoments}

**Sphere:** {sphereName}

Please identify:
1. Recurring themes or topics
2. Emotional patterns and trends
3. Behavioral cycles or habits
4. Timing patterns (daily, weekly, seasonal)
5. Triggers or catalysts for different types of moments

Provide specific examples and quantify patterns where possible.`,
  },

  connections: {
    system: `You are an AI that specializes in finding meaningful connections between a user's moments, experiences, and thoughts.

Your role is to:
1. Identify relationships between seemingly unconnected moments
2. Trace the evolution of ideas or situations over time
3. Highlight cause-and-effect relationships
4. Show how different spheres of life interconnect
5. Map the user's journey and growth

Guidelines:
- Draw connections across different time periods
- Consider multiple types of relationships (causal, thematic, emotional)
- Explain the significance of connections you identify
- Be thoughtful about correlation vs causation
- Help users see their story as a connected narrative`,

    user: `Find meaningful connections between this moment and the user's broader story:

**Current Moment:**
{currentMoment}

**Connected Moments:**
{relatedMoments}

**Cross-Sphere Context:**
{sphereContext}

Please identify:
1. Direct connections to previous moments
2. Thematic relationships across time
3. How this moment fits into larger life narratives
4. Unexpected or subtle connections
5. Evolution of thoughts, situations, or relationships

Explain the significance of each connection you identify.`,
  },
};

// Create reflection queue
export const reflectionQueue = new Queue<ReflectionJobData>(REFLECTION_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 100,
    removeOnFail: 50,
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 3000,
    },
  },
});

/**
 * Context builder for AI reflections
 */
class ReflectionContextBuilder {

  /**
   * Get sphere context for reflection
   */
  static async getSphereContext(sphereId: string): Promise<{
    name: string;
    description: string;
    recentActivity: number;
    topThemes: string[];
  }> {
    const sphere = await prisma.sphere.findUnique({
      where: { id: sphereId },
      include: {
        moments: {
          where: {
            deletedAt: null,
            capturedAt: {
              gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), // Last 30 days
            },
          },
          select: {
            contentText: true,
            tags: true,
            emotions: true,
          },
        },
      },
    });

    if (!sphere) {
      throw new Error('Sphere not found');
    }

    // Extract themes from recent moments
    const allTags = sphere.moments.flatMap(m => m.tags);
    const tagCounts = allTags.reduce((acc, tag) => {
      acc[tag] = (acc[tag] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    const topThemes = Object.entries(tagCounts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 5)
      .map(([tag]) => tag);

    return {
      name: sphere.name,
      description: sphere.description || '',
      recentActivity: sphere.moments.length,
      topThemes,
    };
  }

  /**
   * Get related moments for context
   */
  static async getRelatedMoments(
    momentId: string,
    userId: string,
    sphereId: string,
    options: {
      limit?: number;
      timeRange?: string;
      includeTranscripts?: boolean;
      useSimilarity?: boolean;
    } = {}
  ): Promise<Array<{
    id: string;
    content: string;
    capturedAt: Date;
    emotions: string[];
    tags: string[];
    similarity?: number;
  }>> {
    const {
      limit = 10,
      timeRange = '30 days',
      includeTranscripts = true,
      useSimilarity = true,
    } = options;

    try {
      const currentMoment = await prisma.moment.findUnique({
        where: { id: momentId },
        select: { contentText: true, contentTranscript: true },
      });

      if (!currentMoment) {
        return [];
      }

      let relatedMoments;

      if (useSimilarity) {
        // Get user's accessible spheres
        const userSpheres = await prisma.sphereAccess.findMany({
          where: { userId, revokedAt: null },
          select: { sphereId: true },
        });
        const sphereIds = userSpheres.map(sa => sa.sphereId);

        // Use vector similarity search
        const queryText = includeTranscripts && currentMoment.contentTranscript
          ? `${currentMoment.contentText} ${currentMoment.contentTranscript}`
          : currentMoment.contentText;

        const similarMoments = await VectorEmbeddingService.findSimilarMoments(
          queryText,
          sphereIds,
          { limit, similarityThreshold: 0.5 }
        );

        relatedMoments = await prisma.moment.findMany({
          where: {
            id: { in: similarMoments.map(m => m.momentId) },
            id: { not: momentId }, // Exclude current moment
          },
          select: {
            id: true,
            contentText: true,
            contentTranscript: true,
            capturedAt: true,
            emotions: true,
            tags: true,
          },
          orderBy: { capturedAt: 'desc' },
        });

        // Add similarity scores
        return relatedMoments.map(moment => {
          const similar = similarMoments.find(s => s.momentId === moment.id);
          return {
            id: moment.id,
            content: includeTranscripts && moment.contentTranscript
              ? `${moment.contentText}\n\nTranscript: ${moment.contentTranscript}`
              : moment.contentText,
            capturedAt: moment.capturedAt,
            emotions: moment.emotions,
            tags: moment.tags,
            similarity: similar?.similarity,
          };
        });

      } else {
        // Use temporal proximity
        const timeRangeMs = this.parseTimeRange(timeRange);
        const cutoffDate = new Date(Date.now() - timeRangeMs);

        relatedMoments = await prisma.moment.findMany({
          where: {
            sphereId,
            id: { not: momentId },
            capturedAt: { gte: cutoffDate },
            deletedAt: null,
          },
          select: {
            id: true,
            contentText: true,
            contentTranscript: true,
            capturedAt: true,
            emotions: true,
            tags: true,
          },
          orderBy: { capturedAt: 'desc' },
          take: limit,
        });

        return relatedMoments.map(moment => ({
          id: moment.id,
          content: includeTranscripts && moment.contentTranscript
            ? `${moment.contentText}\n\nTranscript: ${moment.contentTranscript}`
            : moment.contentText,
          capturedAt: moment.capturedAt,
          emotions: moment.emotions,
          tags: moment.tags,
        }));
      }

    } catch (error) {
      console.error('Failed to get related moments:', error);
      return [];
    }
  }

  /**
   * Parse time range string to milliseconds
   */
  private static parseTimeRange(timeRange: string): number {
    const match = timeRange.match(/(\d+)\s*(days?|weeks?|months?)/i);
    if (!match) return 30 * 24 * 60 * 60 * 1000; // Default 30 days

    const value = parseInt(match[1]);
    const unit = match[2].toLowerCase();

    switch (unit) {
      case 'day':
      case 'days':
        return value * 24 * 60 * 60 * 1000;
      case 'week':
      case 'weeks':
        return value * 7 * 24 * 60 * 60 * 1000;
      case 'month':
      case 'months':
        return value * 30 * 24 * 60 * 60 * 1000;
      default:
        return 30 * 24 * 60 * 60 * 1000;
    }
  }

  /**
   * Format moments for AI prompt
   */
  static formatMomentsForPrompt(moments: Array<{
    id: string;
    content: string;
    capturedAt: Date;
    emotions: string[];
    tags: string[];
    similarity?: number;
  }>): string {
    if (moments.length === 0) {
      return 'No related moments found.';
    }

    return moments.map((moment, index) => {
      const date = moment.capturedAt.toLocaleDateString();
      const time = moment.capturedAt.toLocaleTimeString();
      const emotions = moment.emotions.length > 0 ? ` (Emotions: ${moment.emotions.join(', ')})` : '';
      const tags = moment.tags.length > 0 ? ` (Tags: ${moment.tags.join(', ')})` : '';
      const similarity = moment.similarity ? ` (Similarity: ${(moment.similarity * 100).toFixed(1)}%)` : '';

      return `[${index + 1}] ${date} ${time}${emotions}${tags}${similarity}\n${moment.content}\n`;
    }).join('\n');
  }
}

/**
 * AI Reflection Service
 */
export class AIReflectionService {

  /**
   * Enqueue reflection generation job
   */
  static async enqueueReflection(jobData: ReflectionJobData): Promise<string> {
    const job = await reflectionQueue.add('generate-reflection', jobData, {
      priority: jobData.priority === 'high' ? 10 : jobData.priority === 'low' ? 1 : 5,
      jobId: `reflection-${jobData.momentId}-${jobData.reflectionType}`,
    });

    return job.id!;
  }

  /**
   * Generate reflection directly (sync)
   */
  static async generateReflection(jobData: ReflectionJobData): Promise<ReflectionResult> {
    const startTime = Date.now();

    try {
      // Get current moment
      const currentMoment = await prisma.moment.findUnique({
        where: { id: jobData.momentId },
        include: {
          sphere: true,
        },
      });

      if (!currentMoment) {
        throw new Error('Moment not found');
      }

      // Build context
      const sphereContext = await ReflectionContextBuilder.getSphereContext(jobData.sphereId);
      const relatedMoments = await ReflectionContextBuilder.getRelatedMoments(
        jobData.momentId,
        jobData.userId,
        jobData.sphereId,
        jobData.context
      );

      // Format current moment
      const currentContent = jobData.context?.includeTranscripts && currentMoment.contentTranscript
        ? `${currentMoment.contentText}\n\nTranscript: ${currentMoment.contentTranscript}`
        : currentMoment.contentText;

      // Get prompt template
      const promptTemplate = REFLECTION_PROMPTS[jobData.reflectionType];

      // Build prompt
      const userPrompt = promptTemplate.user
        .replace('{currentMoment}', currentContent)
        .replace('{relatedMoments}', ReflectionContextBuilder.formatMomentsForPrompt(relatedMoments))
        .replace('{sphereName}', sphereContext.name)
        .replace('{sphereDescription}', sphereContext.description)
        .replace('{timeRange}', jobData.context?.timeRange || '30 days')
        .replace('{sphereContext}', `${sphereContext.name}: Recent themes include ${sphereContext.topThemes.join(', ')}`);

      // Generate reflection with GPT-4
      const response = await openai.chat.completions.create({
        model: 'gpt-4-1106-preview',
        messages: [
          { role: 'system', content: promptTemplate.system },
          { role: 'user', content: userPrompt },
        ],
        max_tokens: 1500,
        temperature: 0.7,
        presence_penalty: 0.1,
      });

      const reflectionContent = response.choices[0]?.message?.content || '';

      // Parse structured response (this is simplified - in production, use function calling)
      const insights = this.extractInsights(reflectionContent);
      const connections = this.extractConnections(reflectionContent, relatedMoments);
      const themes = this.extractThemes(reflectionContent);
      const actionItems = this.extractActionItems(reflectionContent);
      const citations = this.extractCitations(reflectionContent, relatedMoments);

      return {
        type: jobData.reflectionType,
        content: reflectionContent,
        insights,
        connections,
        themes,
        actionItems,
        confidence: 0.85, // Would be calculated based on various factors
        citations,
        processingTimeMs: Date.now() - startTime,
        model: 'gpt-4-1106-preview',
      };

    } catch (error: any) {
      throw new Error(`Reflection generation failed: ${error.message}`);
    }
  }

  /**
   * Generate automatic reflection for new moment
   */
  static async generateAutomaticReflection(
    momentId: string,
    reflectionType: ReflectionType = 'insights',
    priority: 'high' | 'normal' | 'low' = 'normal'
  ): Promise<string> {
    const moment = await prisma.moment.findUnique({
      where: { id: momentId },
      select: { userId: true, sphereId: true },
    });

    if (!moment) {
      throw new Error('Moment not found');
    }

    return this.enqueueReflection({
      momentId,
      userId: moment.userId,
      sphereId: moment.sphereId,
      reflectionType,
      priority,
      context: {
        recentMoments: 10,
        timeRange: '30 days',
        includeTranscripts: true,
      },
    });
  }

  /**
   * Extract insights from reflection content (simplified parser)
   */
  private static extractInsights(content: string): string[] {
    const insightPatterns = [
      /insights?[:\-]\s*([^\n]+)/gi,
      /key observations?[:\-]\s*([^\n]+)/gi,
      /patterns?[:\-]\s*([^\n]+)/gi,
    ];

    const insights: string[] = [];
    for (const pattern of insightPatterns) {
      const matches = content.matchAll(pattern);
      for (const match of matches) {
        if (match[1]) {
          insights.push(match[1].trim());
        }
      }
    }

    return insights.slice(0, 5); // Limit to top 5
  }

  /**
   * Extract connections from reflection content
   */
  private static extractConnections(content: string, relatedMoments: any[]): Array<{
    momentId: string;
    relationship: string;
    confidence: number;
  }> {
    // Simplified connection extraction
    // In production, this would use NER and more sophisticated parsing
    const connections: Array<{
      momentId: string;
      relationship: string;
      confidence: number;
    }> = [];

    // Look for references to moment numbers [1], [2], etc.
    const momentRefs = content.matchAll(/\[(\d+)\][^.]*([^.]*\.)/g);
    for (const match of momentRefs) {
      const momentIndex = parseInt(match[1]) - 1;
      if (momentIndex >= 0 && momentIndex < relatedMoments.length) {
        connections.push({
          momentId: relatedMoments[momentIndex].id,
          relationship: match[2]?.trim() || 'Related content',
          confidence: 0.7,
        });
      }
    }

    return connections.slice(0, 3); // Limit to top 3
  }

  /**
   * Extract themes from reflection content
   */
  private static extractThemes(content: string): string[] {
    // Simple keyword extraction (in production, use proper NLP)
    const themeKeywords = [
      'growth', 'learning', 'reflection', 'productivity', 'wellness',
      'relationships', 'creativity', 'challenge', 'success', 'mindfulness',
      'balance', 'progress', 'change', 'development', 'achievement',
    ];

    const themes = themeKeywords.filter(theme =>
      content.toLowerCase().includes(theme)
    );

    return themes.slice(0, 3);
  }

  /**
   * Extract action items from reflection content
   */
  private static extractActionItems(content: string): string[] {
    const actionPatterns = [
      /consider[:\-]\s*([^\n]+)/gi,
      /try[:\-]\s*([^\n]+)/gi,
      /action[:\-]\s*([^\n]+)/gi,
      /suggestion[:\-]\s*([^\n]+)/gi,
    ];

    const actionItems: string[] = [];
    for (const pattern of actionPatterns) {
      const matches = content.matchAll(pattern);
      for (const match of matches) {
        if (match[1]) {
          actionItems.push(match[1].trim());
        }
      }
    }

    return actionItems.slice(0, 3); // Limit to top 3
  }

  /**
   * Extract citations from reflection content
   */
  private static extractCitations(content: string, relatedMoments: any[]): Array<{
    momentId: string;
    excerpt: string;
    relevance: string;
  }> {
    const citations: Array<{
      momentId: string;
      excerpt: string;
      relevance: string;
    }> = [];

    // Look for direct references to moments
    const momentRefs = content.matchAll(/\[(\d+)\][^.]*([^.]*\.)/g);
    for (const match of momentRefs) {
      const momentIndex = parseInt(match[1]) - 1;
      if (momentIndex >= 0 && momentIndex < relatedMoments.length) {
        const moment = relatedMoments[momentIndex];
        citations.push({
          momentId: moment.id,
          excerpt: moment.content.substring(0, 100) + '...',
          relevance: match[2]?.trim() || 'Referenced in reflection',
        });
      }
    }

    return citations.slice(0, 3); // Limit to top 3
  }

  /**
   * Get reflection job status
   */
  static async getJobStatus(jobId: string): Promise<{
    status: string;
    progress?: number;
    error?: string;
    result?: ReflectionResult;
  }> {
    try {
      const job = await Job.fromId(reflectionQueue, jobId);

      if (!job) {
        return { status: 'not_found' };
      }

      const state = await job.getState();

      return {
        status: state,
        progress: job.progress as number,
        error: state === 'failed' ? job.failedReason : undefined,
        result: state === 'completed' ? job.returnvalue : undefined,
      };
    } catch (error) {
      return { status: 'error', error: 'Failed to get job status' };
    }
  }
}

/**
 * Reflection worker - processes reflection generation jobs
 */
const reflectionWorker = new Worker<ReflectionJobData>(
  REFLECTION_QUEUE,
  async (job: Job<ReflectionJobData>) => {
    const { data } = job;

    try {
      await job.updateProgress(10);

      // Generate reflection
      const result = await AIReflectionService.generateReflection(data);

      await job.updateProgress(80);

      // Store reflection in database (extend moment with reflection)
      await prisma.moment.update({
        where: { id: data.momentId },
        data: {
          metadata: {
            reflection: {
              type: result.type,
              content: result.content,
              insights: result.insights,
              connections: result.connections,
              themes: result.themes,
              actionItems: result.actionItems,
              confidence: result.confidence,
              citations: result.citations,
              generatedAt: new Date().toISOString(),
              model: result.model,
              processingTimeMs: result.processingTimeMs,
            },
          },
        },
      });

      await job.updateProgress(90);

      // Create audit event
      await prisma.auditEvent.create({
        data: {
          eventType: 'AI_REFLECTION_GENERATED' as any, // Add to enum
          userId: data.userId,
          momentId: data.momentId,
          actor: 'system:ai-reflection',
          action: 'REFLECTION_GENERATED',
          resourceType: 'moment',
          resourceId: data.momentId,
          details: {
            reflectionType: data.reflectionType,
            insightCount: result.insights.length,
            connectionCount: result.connections.length,
            confidence: result.confidence,
            processingTimeMs: result.processingTimeMs,
          },
        },
      });

      await job.updateProgress(100);

      return result;

    } catch (error: any) {
      console.error('Reflection job failed:', error);

      // Create failure audit event
      try {
        await prisma.auditEvent.create({
          data: {
            eventType: 'AI_REFLECTION_FAILED' as any,
            userId: data.userId,
            momentId: data.momentId,
            actor: 'system:ai-reflection',
            action: 'REFLECTION_FAILED',
            resourceType: 'moment',
            resourceId: data.momentId,
            details: {
              error: error.message,
              reflectionType: data.reflectionType,
            },
          },
        });
      } catch (auditError) {
        console.error('Failed to create audit event:', auditError);
      }

      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 2, // Process up to 2 reflections concurrently (GPT-4 is expensive)
  }
);

// Worker event handlers
reflectionWorker.on('completed', (job) => {
  console.log(`Reflection job ${job.id} completed successfully`);
});

reflectionWorker.on('failed', (job, err) => {
  console.error(`Reflection job ${job?.id} failed:`, err);
});

reflectionWorker.on('progress', (job, progress) => {
  console.log(`Reflection job ${job.id} progress: ${progress}%`);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down reflection worker...');
  await reflectionWorker.close();
  await prisma.$disconnect();
  await redis.quit();
  process.exit(0);
});

export { reflectionWorker };
