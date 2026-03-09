/**
 * Visual Search Service for Flashit
 * Provides image-based search using visual embeddings
 *
 * @doc.type service
 * @doc.purpose Search moments by visual similarity using image embeddings
 * @doc.layer product
 * @doc.pattern SearchService
 */

import OpenAI from 'openai';
import { prisma } from '../../lib/prisma.js';
import { VectorEmbeddingService } from '../embeddings/vector-service.js';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface VisualSearchOptions {
  userId: string;
  imageSource: ImageSource;
  sphereIds?: string[];
  limit?: number;
  similarityThreshold?: number;
  includeTextResults?: boolean;
}

export type ImageSource =
  | { type: 'url'; url: string }
  | { type: 'base64'; data: string; mimeType: string }
  | { type: 'momentId'; momentId: string };

export interface VisualSearchResult {
  momentId: string;
  content: string;
  imageUrl: string | null;
  sphereId: string;
  sphereName: string;
  capturedAt: Date;
  emotions: string[];
  tags: string[];
  similarity: number;
  matchType: 'visual' | 'text' | 'hybrid';
  visualElements?: VisualElement[];
}

export interface VisualElement {
  type: 'object' | 'scene' | 'text' | 'face' | 'color' | 'style';
  value: string;
  confidence: number;
  boundingBox?: BoundingBox;
}

export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ImageAnalysis {
  description: string;
  objects: VisualElement[];
  scenes: VisualElement[];
  colors: VisualElement[];
  textContent: VisualElement[];
  faces: VisualElement[];
  style: VisualElement[];
  embedding?: number[];
}

export interface VisualSearchResponse {
  results: VisualSearchResult[];
  totalCount: number;
  sourceAnalysis?: ImageAnalysis;
  analytics: {
    processingTimeMs: number;
    analysisTimeMs: number;
    searchTimeMs: number;
  };
}

// ============================================================================
// Visual Search Service
// ============================================================================

/**
 * VisualSearchService provides image-based search using visual embeddings
 */
export class VisualSearchService {
  private static openai: OpenAI | null = null;
  private static readonly SIMILARITY_THRESHOLD_DEFAULT = 0.65;
  private static readonly SEARCH_LIMIT_DEFAULT = 20;

  /**
   * Initialize OpenAI client
   */
  private static getOpenAI(): OpenAI {
    if (!this.openai) {
      const apiKey = process.env.OPENAI_API_KEY;
      if (!apiKey) {
        throw new Error('OPENAI_API_KEY not configured');
      }
      this.openai = new OpenAI({ apiKey });
    }
    return this.openai;
  }

  /**
   * Search for visually similar moments
   */
  static async searchByImage(options: VisualSearchOptions): Promise<VisualSearchResponse> {
    const startTime = Date.now();
    const {
      userId,
      imageSource,
      sphereIds,
      limit = this.SEARCH_LIMIT_DEFAULT,
      similarityThreshold = this.SIMILARITY_THRESHOLD_DEFAULT,
      includeTextResults = true,
    } = options;

    // Get accessible spheres
    const accessibleSphereIds = await this.getAccessibleSpheres(userId, sphereIds);
    if (accessibleSphereIds.length === 0) {
      return this.emptyResponse(startTime);
    }

    // Analyze the source image
    const analysisStart = Date.now();
    const { imageUrl, imageBase64 } = await this.resolveImageSource(imageSource, userId);
    const sourceAnalysis = await this.analyzeImage(imageUrl, imageBase64);
    const analysisTimeMs = Date.now() - analysisStart;

    // Search for similar moments
    const searchStart = Date.now();
    const results = await this.findSimilarMoments(
      sourceAnalysis,
      accessibleSphereIds,
      limit,
      similarityThreshold,
      includeTextResults
    );
    const searchTimeMs = Date.now() - searchStart;

    return {
      results,
      totalCount: results.length,
      sourceAnalysis,
      analytics: {
        processingTimeMs: Date.now() - startTime,
        analysisTimeMs,
        searchTimeMs,
      },
    };
  }

  /**
   * Resolve image source to URL and/or base64
   */
  private static async resolveImageSource(
    source: ImageSource,
    userId: string
  ): Promise<{ imageUrl?: string; imageBase64?: string }> {
    switch (source.type) {
      case 'url':
        return { imageUrl: source.url };

      case 'base64':
        return { imageBase64: `data:${source.mimeType};base64,${source.data}` };

      case 'momentId': {
        // Get the moment's image URL
        const moment = await prisma.moment.findFirst({
          where: {
            id: source.momentId,
            sphere: {
              members: {
                some: {
                  userId,
                  revokedAt: null,
                },
              },
            },
          },
          select: { imageUrl: true },
        });

        if (!moment?.imageUrl) {
          throw new Error('Moment not found or has no image');
        }

        return { imageUrl: moment.imageUrl };
      }

      default:
        throw new Error('Invalid image source type');
    }
  }

  /**
   * Analyze an image using GPT-4 Vision
   */
  private static async analyzeImage(
    imageUrl?: string,
    imageBase64?: string
  ): Promise<ImageAnalysis> {
    const openai = this.getOpenAI();
    const imageContent = imageUrl || imageBase64;

    if (!imageContent) {
      throw new Error('No image content provided');
    }

    try {
      const response = await openai.chat.completions.create({
        model: 'gpt-4o-mini',
        messages: [
          {
            role: 'system',
            content: `You are an image analysis assistant. Analyze the provided image and extract:
1. A brief description
2. Objects visible in the image
3. Scene type (indoor, outdoor, nature, urban, etc.)
4. Dominant colors
5. Any visible text
6. Faces (if any, describe without identifying)
7. Visual style (photography style, artistic elements)

Respond in JSON format with these fields:
{
  "description": "brief description",
  "objects": [{"type": "object", "value": "name", "confidence": 0.0-1.0}],
  "scenes": [{"type": "scene", "value": "scene type", "confidence": 0.0-1.0}],
  "colors": [{"type": "color", "value": "color name", "confidence": 0.0-1.0}],
  "textContent": [{"type": "text", "value": "visible text", "confidence": 0.0-1.0}],
  "faces": [{"type": "face", "value": "description", "confidence": 0.0-1.0}],
  "style": [{"type": "style", "value": "style description", "confidence": 0.0-1.0}]
}`,
          },
          {
            role: 'user',
            content: [
              {
                type: 'image_url',
                image_url: {
                  url: imageContent,
                  detail: 'low', // Use low detail for faster processing
                },
              },
              {
                type: 'text',
                text: 'Analyze this image and provide structured output.',
              },
            ],
          },
        ],
        max_tokens: 1000,
        temperature: 0.3,
      });

      const content = response.choices[0]?.message?.content;
      if (!content) {
        throw new Error('No response from image analysis');
      }

      // Parse JSON response
      const jsonMatch = content.match(/\{[\s\S]*\}/);
      if (!jsonMatch) {
        throw new Error('Invalid JSON response from image analysis');
      }

      const parsed = JSON.parse(jsonMatch[0]);
      return {
        description: parsed.description || '',
        objects: parsed.objects || [],
        scenes: parsed.scenes || [],
        colors: parsed.colors || [],
        textContent: parsed.textContent || [],
        faces: parsed.faces || [],
        style: parsed.style || [],
      };
    } catch (error) {
      console.error('Image analysis failed:', error);
      return {
        description: 'Analysis failed',
        objects: [],
        scenes: [],
        colors: [],
        textContent: [],
        faces: [],
        style: [],
      };
    }
  }

  /**
   * Find moments similar to the analyzed image
   */
  private static async findSimilarMoments(
    sourceAnalysis: ImageAnalysis,
    sphereIds: string[],
    limit: number,
    threshold: number,
    includeTextResults: boolean
  ): Promise<VisualSearchResult[]> {
    const results: VisualSearchResult[] = [];

    // Build search terms from analysis
    const searchTerms = this.buildSearchTermsFromAnalysis(sourceAnalysis);

    // Search for moments with images
    const momentsWithImages = await prisma.moment.findMany({
      where: {
        sphereId: { in: sphereIds },
        deletedAt: null,
        imageUrl: { not: null },
      },
      include: {
        sphere: { select: { name: true } },
      },
      take: limit * 3, // Get more than needed for filtering
    });

    // Score each moment based on visual element matching
    for (const moment of momentsWithImages) {
      const similarity = await this.computeVisualSimilarity(
        sourceAnalysis,
        moment,
        searchTerms
      );

      if (similarity >= threshold) {
        results.push({
          momentId: moment.id,
          content: moment.contentText || '',
          imageUrl: moment.imageUrl,
          sphereId: moment.sphereId,
          sphereName: moment.sphere.name,
          capturedAt: moment.capturedAt,
          emotions: moment.emotions,
          tags: moment.tags,
          similarity,
          matchType: 'visual',
          visualElements: await this.extractVisualElementsFromMoment(moment),
        });
      }
    }

    // Optionally include text-based results
    if (includeTextResults && searchTerms.length > 0) {
      const textResults = await this.searchByTextTerms(
        searchTerms,
        sphereIds,
        limit,
        results.map(r => r.momentId)
      );

      for (const textResult of textResults) {
        if (!results.find(r => r.momentId === textResult.momentId)) {
          results.push({
            ...textResult,
            matchType: 'text',
          });
        }
      }
    }

    // Sort by similarity and limit
    results.sort((a, b) => b.similarity - a.similarity);
    return results.slice(0, limit);
  }

  /**
   * Build search terms from image analysis
   */
  private static buildSearchTermsFromAnalysis(analysis: ImageAnalysis): string[] {
    const terms: string[] = [];

    // Add object names
    for (const obj of analysis.objects) {
      if (obj.confidence > 0.5) {
        terms.push(obj.value.toLowerCase());
      }
    }

    // Add scene types
    for (const scene of analysis.scenes) {
      if (scene.confidence > 0.5) {
        terms.push(scene.value.toLowerCase());
      }
    }

    // Add visible text
    for (const text of analysis.textContent) {
      if (text.confidence > 0.7) {
        terms.push(text.value.toLowerCase());
      }
    }

    // Add colors
    for (const color of analysis.colors) {
      if (color.confidence > 0.6) {
        terms.push(color.value.toLowerCase());
      }
    }

    // Add style descriptors
    for (const style of analysis.style) {
      if (style.confidence > 0.5) {
        terms.push(style.value.toLowerCase());
      }
    }

    // Deduplicate
    return [...new Set(terms)];
  }

  /**
   * Compute visual similarity between source analysis and a moment
   */
  private static async computeVisualSimilarity(
    sourceAnalysis: ImageAnalysis,
    moment: {
      contentText: string | null;
      tags: string[];
      emotions: string[];
    },
    searchTerms: string[]
  ): Promise<number> {
    let matchScore = 0;
    let totalWeight = 0;

    const momentText = (moment.contentText || '').toLowerCase();
    const momentTags = moment.tags.map(t => t.toLowerCase());

    // Check search term matches in content and tags
    for (const term of searchTerms) {
      totalWeight += 1;
      if (momentText.includes(term)) {
        matchScore += 0.8;
      } else if (momentTags.some(tag => tag.includes(term) || term.includes(tag))) {
        matchScore += 1;
      }
    }

    // Check description similarity
    if (sourceAnalysis.description) {
      const descWords = sourceAnalysis.description.toLowerCase().split(/\s+/);
      for (const word of descWords) {
        if (word.length > 3) {
          totalWeight += 0.3;
          if (momentText.includes(word) || momentTags.includes(word)) {
            matchScore += 0.3;
          }
        }
      }
    }

    return totalWeight > 0 ? Math.min(matchScore / totalWeight, 1) : 0;
  }

  /**
   * Extract visual elements from a moment (if it has been analyzed before)
   */
  private static async extractVisualElementsFromMoment(
    moment: { id: string }
  ): Promise<VisualElement[]> {
    // Check if we have cached analysis
    const cached = await prisma.momentImageAnalysis.findUnique({
      where: { momentId: moment.id },
    });

    if (cached?.analysis) {
      const analysis = cached.analysis as ImageAnalysis;
      return [
        ...analysis.objects,
        ...analysis.scenes,
        ...analysis.colors,
      ].slice(0, 10);
    }

    return [];
  }

  /**
   * Search moments by text terms derived from image analysis
   */
  private static async searchByTextTerms(
    terms: string[],
    sphereIds: string[],
    limit: number,
    excludeMomentIds: string[]
  ): Promise<Omit<VisualSearchResult, 'matchType'>[]> {
    if (terms.length === 0) return [];

    // Search using OR conditions on tags and content
    const moments = await prisma.moment.findMany({
      where: {
        sphereId: { in: sphereIds },
        deletedAt: null,
        id: { notIn: excludeMomentIds },
        OR: [
          { tags: { hasSome: terms } },
          { contentText: { contains: terms[0], mode: 'insensitive' } },
        ],
      },
      include: {
        sphere: { select: { name: true } },
      },
      take: limit,
    });

    return moments.map(m => {
      // Calculate text similarity
      const text = (m.contentText || '').toLowerCase();
      const tags = m.tags.map(t => t.toLowerCase());
      let matchCount = 0;

      for (const term of terms) {
        if (text.includes(term) || tags.some(t => t.includes(term))) {
          matchCount++;
        }
      }

      return {
        momentId: m.id,
        content: m.contentText || '',
        imageUrl: m.imageUrl,
        sphereId: m.sphereId,
        sphereName: m.sphere.name,
        capturedAt: m.capturedAt,
        emotions: m.emotions,
        tags: m.tags,
        similarity: matchCount / terms.length,
      };
    });
  }

  /**
   * Get accessible sphere IDs
   */
  private static async getAccessibleSpheres(
    userId: string,
    filterSphereIds?: string[]
  ): Promise<string[]> {
    const access = await prisma.sphereAccess.findMany({
      where: {
        userId,
        revokedAt: null,
        sphere: {
          deletedAt: null,
          ...(filterSphereIds?.length ? { id: { in: filterSphereIds } } : {}),
        },
      },
      select: { sphereId: true },
    });

    return access.map(a => a.sphereId);
  }

  /**
   * Empty response helper
   */
  private static emptyResponse(startTime: number): VisualSearchResponse {
    return {
      results: [],
      totalCount: 0,
      analytics: {
        processingTimeMs: Date.now() - startTime,
        analysisTimeMs: 0,
        searchTimeMs: 0,
      },
    };
  }

  // ============================================================================
  // Image Indexing
  // ============================================================================

  /**
   * Index a moment's image for visual search
   */
  static async indexMomentImage(momentId: string): Promise<void> {
    const moment = await prisma.moment.findUnique({
      where: { id: momentId },
      select: { id: true, imageUrl: true },
    });

    if (!moment?.imageUrl) {
      return;
    }

    try {
      const analysis = await this.analyzeImage(moment.imageUrl);

      // Store the analysis
      await prisma.momentImageAnalysis.upsert({
        where: { momentId },
        create: {
          momentId,
          analysis: analysis as any,
          indexedAt: new Date(),
        },
        update: {
          analysis: analysis as any,
          indexedAt: new Date(),
        },
      });

      // Generate and store embedding for vector search
      const embeddingText = this.buildEmbeddingText(analysis);
      const embedding = await VectorEmbeddingService.generateEmbedding(embeddingText);

      await prisma.momentImageEmbedding.upsert({
        where: { momentId },
        create: {
          momentId,
          embedding,
          createdAt: new Date(),
        },
        update: {
          embedding,
          updatedAt: new Date(),
        },
      });
    } catch (error) {
      console.error(`Failed to index image for moment ${momentId}:`, error);
    }
  }

  /**
   * Build text for embedding from image analysis
   */
  private static buildEmbeddingText(analysis: ImageAnalysis): string {
    const parts: string[] = [analysis.description];

    parts.push(...analysis.objects.map(o => o.value));
    parts.push(...analysis.scenes.map(s => s.value));
    parts.push(...analysis.colors.map(c => c.value));
    parts.push(...analysis.textContent.map(t => t.value));
    parts.push(...analysis.style.map(s => s.value));

    return parts.filter(Boolean).join(' ');
  }

  /**
   * Batch index images for multiple moments
   */
  static async batchIndexImages(momentIds: string[]): Promise<{ success: number; failed: number }> {
    let success = 0;
    let failed = 0;

    for (const momentId of momentIds) {
      try {
        await this.indexMomentImage(momentId);
        success++;
      } catch {
        failed++;
      }
    }

    return { success, failed };
  }

  /**
   * Find similar images using vector embeddings
   */
  static async findSimilarByEmbedding(
    momentId: string,
    userId: string,
    limit: number = 10,
    threshold: number = 0.7
  ): Promise<VisualSearchResult[]> {
    // Get the moment's image embedding
    const sourceEmbedding = await prisma.momentImageEmbedding.findUnique({
      where: { momentId },
    });

    if (!sourceEmbedding?.embedding) {
      throw new Error('Moment has no image embedding');
    }

    // Get accessible spheres
    const sphereIds = await this.getAccessibleSpheres(userId);
    if (sphereIds.length === 0) {
      return [];
    }

    // Use vector similarity search
    const similar = await VectorEmbeddingService.findSimilarMoments(
      sourceEmbedding.embedding as number[],
      sphereIds,
      limit + 1, // +1 to exclude self
      threshold
    );

    // Filter out the source moment
    const filtered = similar.filter(s => s.momentId !== momentId).slice(0, limit);

    // Get moment details
    const momentIds = filtered.map(f => f.momentId);
    const moments = await prisma.moment.findMany({
      where: { id: { in: momentIds } },
      include: { sphere: { select: { name: true } } },
    });

    const momentMap = new Map(moments.map(m => [m.id, m]));

    return filtered.map(f => {
      const m = momentMap.get(f.momentId)!;
      return {
        momentId: m.id,
        content: m.contentText || '',
        imageUrl: m.imageUrl,
        sphereId: m.sphereId,
        sphereName: m.sphere.name,
        capturedAt: m.capturedAt,
        emotions: m.emotions,
        tags: m.tags,
        similarity: f.similarity,
        matchType: 'visual' as const,
      };
    });
  }
}

export default VisualSearchService;
