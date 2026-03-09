/**
 * Gateway Graceful Degradation Utilities
 *
 * Provides helpers for routes that depend on the Java Agent service,
 * returning cached / fallback responses when the agent is unavailable
 * instead of hard 502 errors.
 *
 * @doc.type utility
 * @doc.purpose Graceful degradation for backend service calls
 * @doc.layer product
 * @doc.pattern CircuitBreaker
 */

import { FastifyReply, FastifyRequest } from 'fastify';

// ============================================================================
// Types
// ============================================================================

export interface DegradedResponse<T> {
  success: boolean;
  degraded: boolean;
  data: T;
  message?: string;
}

export interface AgentCallOptions<T> {
  /** A human-readable label for logging. */
  operationName: string;
  /** The primary call to the Java Agent. */
  agentCall: () => Promise<T>;
  /** Optional fallback when the agent is unavailable. Return null to let the error propagate. */
  fallback?: () => Promise<T | null> | T | null;
  /** The Fastify request for contextual logging. */
  request: FastifyRequest;
  /** The Fastify reply. */
  reply: FastifyReply;
}

// ============================================================================
// Core helper
// ============================================================================

/**
 * Wraps a Java Agent call with graceful degradation.
 *
 * On success returns the result directly.
 * On failure:
 *   - If a fallback is provided and returns non-null, returns a "degraded" response (200 with flag).
 *   - Otherwise returns a 502 with a descriptive error.
 */
export async function withGracefulDegradation<T>(
  opts: AgentCallOptions<T>
): Promise<DegradedResponse<T> | void> {
  const { operationName, agentCall, fallback, request, reply } = opts;

  try {
    const data = await agentCall();
    return { success: true, degraded: false, data };
  } catch (error: any) {
    request.log.warn(
      { err: error, operation: operationName },
      `Agent call failed for "${operationName}", attempting fallback`
    );

    if (fallback) {
      try {
        const fallbackData = await fallback();
        if (fallbackData !== null && fallbackData !== undefined) {
          return {
            success: true,
            degraded: true,
            data: fallbackData,
            message: `${operationName} is temporarily using cached/fallback data`,
          };
        }
      } catch (fallbackErr) {
        request.log.error(
          { err: fallbackErr, operation: operationName },
          `Fallback also failed for "${operationName}"`
        );
      }
    }

    // No usable fallback — return 502
    reply.code(502).send({
      error: 'AI Service Unavailable',
      message: `${operationName} is temporarily unavailable. Please try again later.`,
      degraded: true,
    });
  }
}

// ============================================================================
// Standard fallbacks
// ============================================================================

/**
 * Empty-result fallback for reflection/insight endpoints.
 */
export function emptyInsightsFallback() {
  return {
    summary: 'Insights are temporarily unavailable.',
    insights: [],
    patterns: [],
    connections: [],
  };
}

/**
 * Empty-result fallback for classification endpoints.
 */
export function emptyClassificationFallback(sphereNames: string[]) {
  return {
    sphereId: sphereNames[0] || 'default',
    confidence: 0,
    reasoning: 'Classification service temporarily unavailable.',
    suggestedSpheres: [],
  };
}

/**
 * Empty-result fallback for NLP endpoints.
 */
export function emptyNLPFallback() {
  return {
    entities: [],
    sentiment: { label: 'neutral', score: 0, polarity: { positive: 0, negative: 0, neutral: 1 } },
    mood: { primary: 'neutral', intensity: 0, moods: [] },
  };
}
