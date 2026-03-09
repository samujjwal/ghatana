/**
 * Classification Service with Java Agent Integration
 * Provides sphere classification with fallback to local heuristics
 *
 * @doc.type service
 * @doc.purpose Sphere classification using Java agent with fallback
 * @doc.layer application
 * @doc.pattern FacadeWithFallback
 */

import { prisma } from '../../lib/prisma.js';
import {
  JavaAgentClient,
  getJavaAgentClient,
  isJavaAgentServiceAvailable,
  ClassificationRequest,
  ClassificationResponse,
  SphereInfo,
  SphereSuggestion,
} from './agent-client.js';

// Use shared Prisma client
function getPrisma() {
  return prisma;
}

// Feature flag for Java agent usage
const USE_JAVA_AGENT = process.env.USE_JAVA_AGENT_CLASSIFICATION !== 'false';

export interface ClassifyMomentRequest {
  content: string;
  transcript?: string;
  contentType: 'TEXT' | 'VOICE' | 'IMAGE' | 'VIDEO';
  emotions: string[];
  tags: string[];
  userIntent?: string;
  userId: string;
}

export interface ClassifyMomentResponse {
  sphereId: string;
  sphereName: string;
  confidence: number;
  reasoning: string;
  alternatives: Array<{
    sphereId: string;
    sphereName: string;
    confidence: number;
    reasoning: string;
  }>;
  source: 'java-agent' | 'local-heuristic';
  processingTimeMs: number;
}

/**
 * Classification Service
 */
export class ClassificationService {
  private javaClient: JavaAgentClient;

  constructor() {
    this.javaClient = getJavaAgentClient();
  }

  /**
   * Classify a moment into the best matching sphere
   */
  async classifyMoment(request: ClassifyMomentRequest): Promise<ClassifyMomentResponse> {
    const startTime = Date.now();

    // Get user's available spheres
    const userSpheres = await this.getUserSpheres(request.userId);

    if (userSpheres.length === 0) {
      throw new Error('No spheres available for classification');
    }

    // If only one sphere, return it directly
    if (userSpheres.length === 1) {
      return {
        sphereId: userSpheres[0].id,
        sphereName: userSpheres[0].name,
        confidence: 1.0,
        reasoning: 'Only one sphere available',
        alternatives: [],
        source: 'local-heuristic',
        processingTimeMs: Date.now() - startTime,
      };
    }

    // Try Java agent first if enabled
    if (USE_JAVA_AGENT) {
      try {
        const isAvailable = await isJavaAgentServiceAvailable();
        if (isAvailable) {
          const javaRequest: ClassificationRequest = {
            content: request.content,
            transcript: request.transcript,
            contentType: request.contentType,
            emotions: request.emotions,
            tags: request.tags,
            userIntent: request.userIntent,
            availableSpheres: userSpheres.map(s => ({
              id: s.id,
              name: s.name,
              description: s.description || undefined,
              type: s.type,
            })),
            userId: request.userId,
          };

          const javaResponse = await this.javaClient.classify(javaRequest);

          return {
            sphereId: javaResponse.sphereId,
            sphereName: javaResponse.sphereName,
            confidence: javaResponse.confidence,
            reasoning: javaResponse.reasoning,
            alternatives: javaResponse.alternatives.map(alt => ({
              sphereId: alt.sphereId,
              sphereName: alt.sphereName,
              confidence: alt.confidence,
              reasoning: alt.reasoning,
            })),
            source: 'java-agent',
            processingTimeMs: Date.now() - startTime,
          };
        }
      } catch (error) {
        console.warn('Java agent classification failed, falling back to local heuristic:', error);
      }
    }

    // Fallback to local heuristic classification
    return this.localClassification(request, userSpheres, startTime);
  }

  /**
   * Get sphere suggestions for a moment
   */
  async suggestSpheres(request: ClassifyMomentRequest): Promise<SphereSuggestion[]> {
    const userSpheres = await this.getUserSpheres(request.userId);

    if (userSpheres.length === 0) {
      return [];
    }

    // Try Java agent first if enabled
    if (USE_JAVA_AGENT) {
      try {
        const isAvailable = await isJavaAgentServiceAvailable();
        if (isAvailable) {
          const javaRequest: ClassificationRequest = {
            content: request.content,
            transcript: request.transcript,
            contentType: request.contentType,
            emotions: request.emotions,
            tags: request.tags,
            userIntent: request.userIntent,
            availableSpheres: userSpheres.map(s => ({
              id: s.id,
              name: s.name,
              description: s.description || undefined,
              type: s.type,
            })),
            userId: request.userId,
          };

          return await this.javaClient.suggestSpheres(javaRequest);
        }
      } catch (error) {
        console.warn('Java agent suggest spheres failed, falling back to local heuristic:', error);
      }
    }

    // Fallback to local heuristic
    const classification = await this.localClassification(request, userSpheres, Date.now());
    
    const suggestions: SphereSuggestion[] = [
      {
        sphereId: classification.sphereId,
        sphereName: classification.sphereName,
        confidence: classification.confidence,
        reasoning: classification.reasoning,
      },
      ...classification.alternatives,
    ];

    return suggestions;
  }

  /**
   * Get user's available spheres
   */
  private async getUserSpheres(userId: string): Promise<Array<{
    id: string;
    name: string;
    description: string | null;
    type: string;
  }>> {
    return getPrisma().sphere.findMany({
      where: {
        deletedAt: null,
        sphereAccess: {
          some: {
            userId,
            revokedAt: null,
          },
        },
      },
      select: {
        id: true,
        name: true,
        description: true,
        type: true,
      },
    });
  }

  /**
   * Local heuristic-based classification (fallback)
   */
  private localClassification(
    request: ClassifyMomentRequest,
    spheres: Array<{ id: string; name: string; description: string | null; type: string }>,
    startTime: number
  ): ClassifyMomentResponse {
    const contentText = (request.content || '').toLowerCase();
    const keywords = [
      ...request.tags.map(t => t.toLowerCase()),
      ...request.emotions.map(e => e.toLowerCase()),
    ];

    const scores: Array<{ sphere: typeof spheres[0]; score: number; reasoning: string }> = [];

    for (const sphere of spheres) {
      const sphereName = sphere.name.toLowerCase();
      const sphereDesc = (sphere.description || '').toLowerCase();
      const sphereContent = `${sphereName} ${sphereDesc}`;

      let score = 0;
      const reasons: string[] = [];

      // Name match
      if (contentText.includes(sphereName)) {
        score += 10;
        reasons.push(`Content mentions "${sphere.name}"`);
      }

      // Description match
      if (sphereDesc && contentText.split(' ').some(word => sphereDesc.includes(word) && word.length > 3)) {
        score += 5;
        reasons.push('Content matches sphere description');
      }

      // Keyword matches
      for (const keyword of keywords) {
        if (keyword.length > 2 && sphereContent.includes(keyword)) {
          score += 2;
          reasons.push(`Keyword "${keyword}" matches`);
        }
      }

      // Type-based matching
      if (sphere.type === 'PERSONAL' && keywords.some(k => ['personal', 'self', 'me', 'my'].includes(k))) {
        score += 5;
        reasons.push('Personal sphere type matches keywords');
      }
      if (sphere.type === 'WORK' && keywords.some(k => ['work', 'professional', 'job', 'office', 'meeting'].includes(k))) {
        score += 5;
        reasons.push('Work sphere type matches keywords');
      }
      if (sphere.type === 'FAMILY' && keywords.some(k => ['family', 'home', 'kids', 'parent'].includes(k))) {
        score += 5;
        reasons.push('Family sphere type matches keywords');
      }
      if (sphere.type === 'HEALTH' && keywords.some(k => ['health', 'fitness', 'exercise', 'wellness', 'medical'].includes(k))) {
        score += 5;
        reasons.push('Health sphere type matches keywords');
      }

      scores.push({
        sphere,
        score,
        reasoning: reasons.length > 0 ? reasons.join('; ') : 'Default match',
      });
    }

    // Sort by score descending
    scores.sort((a, b) => b.score - a.score);

    const best = scores[0];
    const alternatives = scores.slice(1, 4).filter(s => s.score > 0);

    // Calculate confidence (normalize score)
    const maxPossibleScore = 30; // Rough estimate
    const confidence = Math.min(0.7, best.score / maxPossibleScore); // Cap at 70% for heuristic

    return {
      sphereId: best.sphere.id,
      sphereName: best.sphere.name,
      confidence: confidence || 0.3, // Minimum 30% confidence
      reasoning: best.reasoning + ' (heuristic classification)',
      alternatives: alternatives.map(alt => ({
        sphereId: alt.sphere.id,
        sphereName: alt.sphere.name,
        confidence: Math.min(0.5, alt.score / maxPossibleScore),
        reasoning: alt.reasoning,
      })),
      source: 'local-heuristic',
      processingTimeMs: Date.now() - startTime,
    };
  }
}

// Singleton instance
let serviceInstance: ClassificationService | null = null;

/**
 * Get the Classification Service singleton instance
 */
export function getClassificationService(): ClassificationService {
  if (!serviceInstance) {
    serviceInstance = new ClassificationService();
  }
  return serviceInstance;
}
