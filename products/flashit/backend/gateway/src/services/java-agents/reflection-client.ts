/**
 * Reflection Client
 * 
 * Provides high-level interface for AI-powered reflection, insights,
 * and pattern detection through the Java Agent Service.
 * 
 * @doc.type service
 * @doc.purpose AI reflection and insight generation client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type { ReflectionRequest, ReflectionResponse, MomentData } from './agent-client.js';
import { prisma } from '../../lib/prisma.js';

/**
 * Generate insights from a collection of moments
 * 
 * @param userId - User requesting insights
 * @param sphereId - Sphere to analyze (optional)
 * @param timeRange - Time range for moments (e.g., 'week', 'month', 'year')
 * @param limit - Maximum number of moments to analyze
 * @returns Insights, themes, and action items
 */
export async function generateInsights(
  userId: string,
  sphereId?: string,
  timeRange: 'week' | 'month' | 'year' | 'all' = 'month',
  limit: number = 50
): Promise<ReflectionResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot generate insights.');
  }

  // Fetch moments for analysis
  const moments = await fetchMomentsForAnalysis(userId, sphereId, timeRange, limit);

  if (moments.length === 0) {
    return {
      summary: 'No moments found for the specified criteria.',
      insights: [],
      patterns: [],
      connections: [],
      themes: [],
      actionItems: [],
      processingTimeMs: 0,
      model: 'none',
    };
  }

  const client = getJavaAgentClient();
  
  const request: ReflectionRequest = {
    moments,
    userId,
    sphereId: sphereId || 'all',
    reflectionType: 'insights',
    timeRange,
  };

  try {
    return await client.generateInsights(request);
  } catch (error) {
    throw new Error(
      `Failed to generate insights: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Detect patterns across moments
 * 
 * @param userId - User requesting pattern detection
 * @param sphereId - Sphere to analyze (optional)
 * @param timeRange - Time range for moments
 * @param limit - Maximum number of moments to analyze
 * @returns Detected patterns with frequency and confidence
 */
export async function detectPatterns(
  userId: string,
  sphereId?: string,
  timeRange: 'week' | 'month' | 'year' | 'all' = 'month',
  limit: number = 100
): Promise<ReflectionResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot detect patterns.');
  }

  // Fetch moments for analysis
  const moments = await fetchMomentsForAnalysis(userId, sphereId, timeRange, limit);

  if (moments.length < 5) {
    return {
      summary: 'Not enough moments to detect meaningful patterns (minimum 5 required).',
      insights: [],
      patterns: [],
      connections: [],
      themes: [],
      actionItems: [],
      processingTimeMs: 0,
      model: 'none',
    };
  }

  const client = getJavaAgentClient();
  
  const request: ReflectionRequest = {
    moments,
    userId,
    sphereId: sphereId || 'all',
    reflectionType: 'patterns',
    timeRange,
  };

  try {
    return await client.detectPatterns(request);
  } catch (error) {
    throw new Error(
      `Failed to detect patterns: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Find connections between moments
 * 
 * @param userId - User requesting connection discovery
 * @param sphereId - Sphere to analyze (optional)
 * @param timeRange - Time range for moments
 * @param limit - Maximum number of moments to analyze
 * @returns Discovered connections between moments
 */
export async function findConnections(
  userId: string,
  sphereId?: string,
  timeRange: 'week' | 'month' | 'year' | 'all' = 'month',
  limit: number = 50
): Promise<ReflectionResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot find connections.');
  }

  // Fetch moments for analysis
  const moments = await fetchMomentsForAnalysis(userId, sphereId, timeRange, limit);

  if (moments.length < 2) {
    return {
      summary: 'Not enough moments to find connections (minimum 2 required).',
      insights: [],
      patterns: [],
      connections: [],
      themes: [],
      actionItems: [],
      processingTimeMs: 0,
      model: 'none',
    };
  }

  const client = getJavaAgentClient();
  
  const request: ReflectionRequest = {
    moments,
    userId,
    sphereId: sphereId || 'all',
    reflectionType: 'connections',
    timeRange,
  };

  try {
    return await client.findConnections(request);
  } catch (error) {
    throw new Error(
      `Failed to find connections: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Generate comprehensive reflection (insights + patterns + connections)
 * 
 * @param userId - User requesting reflection
 * @param sphereId - Sphere to analyze (optional)
 * @param timeRange - Time range for moments
 * @returns Comprehensive reflection with all analysis types
 */
export async function generateComprehensiveReflection(
  userId: string,
  sphereId?: string,
  timeRange: 'week' | 'month' | 'year' | 'all' = 'month'
): Promise<{
  insights: ReflectionResponse;
  patterns: ReflectionResponse;
  connections: ReflectionResponse;
  summary: string;
  totalMomentsAnalyzed: number;
}> {
  // Run all three analyses in parallel
  const [insights, patterns, connections] = await Promise.all([
    generateInsights(userId, sphereId, timeRange),
    detectPatterns(userId, sphereId, timeRange),
    findConnections(userId, sphereId, timeRange),
  ]);

  // Combine summaries
  const summary = `
**Insights**: ${insights.summary}

**Patterns**: ${patterns.summary}

**Connections**: ${connections.summary}
  `.trim();

  return {
    insights,
    patterns,
    connections,
    summary,
    totalMomentsAnalyzed: Math.max(
      insights.insights.length,
      patterns.patterns.length,
      connections.connections.length
    ),
  };
}

/**
 * Get weekly reflection summary
 * 
 * @param userId - User ID
 * @returns Weekly reflection with insights and patterns
 */
export async function getWeeklyReflection(
  userId: string
): Promise<ReflectionResponse> {
  return generateInsights(userId, undefined, 'week', 30);
}

/**
 * Get monthly reflection summary
 * 
 * @param userId - User ID
 * @returns Monthly reflection with insights and patterns
 */
export async function getMonthlyReflection(
  userId: string
): Promise<ReflectionResponse> {
  return generateInsights(userId, undefined, 'month', 100);
}

/**
 * Fetch moments for analysis based on criteria
 * 
 * @private
 */
async function fetchMomentsForAnalysis(
  userId: string,
  sphereId: string | undefined,
  timeRange: 'week' | 'month' | 'year' | 'all',
  limit: number
): Promise<MomentData[]> {
  // Calculate date filter
  let startDate: Date | undefined;
  const now = new Date();

  switch (timeRange) {
    case 'week':
      startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      break;
    case 'month':
      startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      break;
    case 'year':
      startDate = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
      break;
    case 'all':
      startDate = undefined;
      break;
  }

  // Fetch moments from database
  const moments = await prisma.moment.findMany({
    where: {
      userId,
      ...(sphereId ? { sphereId } : {}),
      ...(startDate ? { capturedAt: { gte: startDate } } : {}),
    },
    orderBy: { capturedAt: 'desc' },
    take: limit,
    select: {
      id: true,
      contentText: true,
      contentTranscript: true,
      capturedAt: true,
      emotions: true,
      tags: true,
    },
  });

  // Convert to MomentData format
  return moments.map((moment) => ({
    id: moment.id,
    content: moment.contentText || '',
    transcript: moment.contentTranscript || undefined,
    capturedAt: moment.capturedAt.toISOString(),
    emotions: (moment.emotions as string[]) || [],
    tags: (moment.tags as string[]) || [],
  }));
}

/**
 * Check if reflection service is available
 * 
 * @returns Service availability status
 */
export async function isReflectionServiceAvailable(): Promise<boolean> {
  return isJavaAgentServiceAvailable();
}
