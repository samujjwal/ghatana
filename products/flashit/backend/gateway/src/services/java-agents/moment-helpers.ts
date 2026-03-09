/**
 * Shared moment-fetching helpers for Java Agent clients.
 *
 * Eliminates duplication of moment-fetching logic across
 * recommendation-client, knowledge-graph-client, and intelligence-client.
 *
 * @doc.type module
 * @doc.purpose Shared moment query helpers for Java Agent clients
 * @doc.layer infrastructure
 * @doc.pattern Helper
 */

import type { MomentData } from './agent-client.js';
import { prisma } from '../../lib/prisma.js';

/**
 * Fetch recent moments for a user, optionally filtered by sphere(s).
 *
 * @param userId - Owner of the moments
 * @param options - Optional filters: sphereId (single), sphereIds (multiple), limit
 * @returns Normalized MomentData array compatible with Java Agent DTOs
 */
export async function fetchMoments(
  userId: string,
  options: {
    sphereId?: string;
    sphereIds?: string[];
    limit?: number;
  } = {}
): Promise<MomentData[]> {
  const { sphereId, sphereIds, limit = 50 } = options;

  const where: Record<string, unknown> = {
    userId,
    deletedAt: null,
  };

  if (sphereIds && sphereIds.length > 0) {
    where.sphereId = { in: sphereIds };
  } else if (sphereId) {
    where.sphereId = sphereId;
  }

  const moments = await prisma.moment.findMany({
    where,
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

  return moments.map((m) => ({
    id: m.id,
    content: m.contentText,
    transcript: m.contentTranscript || undefined,
    capturedAt: m.capturedAt.toISOString(),
    emotions: m.emotions,
    tags: m.tags,
  }));
}
