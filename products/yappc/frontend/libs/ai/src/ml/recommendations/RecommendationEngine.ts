/**
 * Recommendation Engine
 *
 * Provides personalized recommendations using collaborative filtering,
 * content-based filtering, and hybrid approaches.
 */

/**
 * Item to be recommended
 */
export interface RecommendationItem {
  id: string;
  type: string;
  title: string;
  description?: string;
  metadata?: Record<string, unknown>;
  features?: Record<string, number>; // Feature vector for content-based filtering
  tags?: string[];
  createdAt?: Date;
  popularity?: number;
}

/**
 * Recommendation result
 */
export interface Recommendation {
  item: RecommendationItem;
  score: number; // 0-1
  reason: string;
  confidence: number; // 0-1
  source: 'collaborative' | 'content-based' | 'hybrid' | 'popular' | 'trending';
}

/**
 * User-item interaction
 */
export interface UserInteraction {
  userId: string;
  itemId: string;
  type: 'view' | 'click' | 'like' | 'purchase' | 'share';
  rating?: number; // 0-5
  timestamp: Date;
  duration?: number;
}

/**
 * Recommendation configuration
 */
export interface RecommendationConfig {
  algorithm?: 'collaborative' | 'content-based' | 'hybrid';
  maxRecommendations?: number;
  minScore?: number;
  includePopular?: boolean;
  diversityFactor?: number; // 0-1, higher = more diverse
  recencyBoost?: number; // Boost for recent items
}

/**
 * Recommendation Engine implementation
 */
export class RecommendationEngine {
  private items: Map<string, RecommendationItem> = new Map();
  private interactions: UserInteraction[] = [];
  private userItemMatrix: Map<string, Map<string, number>> = new Map(); // userId -> itemId -> score

  /**
   *
   */
  constructor(
    private config: Required<RecommendationConfig> = {
      algorithm: 'hybrid',
      maxRecommendations: 10,
      minScore: 0.1,
      includePopular: true,
      diversityFactor: 0.3,
      recencyBoost: 0.2,
    }
  ) {}

  /**
   * Add items to the catalog
   */
  addItems(items: RecommendationItem[]): void {
    items.forEach((item) => {
      this.items.set(item.id, item);
    });
  }

  /**
   * Record a user interaction
   */
  recordInteraction(interaction: UserInteraction): void {
    this.interactions.push(interaction);

    // Update user-item matrix
    if (!this.userItemMatrix.has(interaction.userId)) {
      this.userItemMatrix.set(interaction.userId, new Map());
    }

    const userMatrix = this.userItemMatrix.get(interaction.userId)!;
    const currentScore = userMatrix.get(interaction.itemId) || 0;
    const interactionScore = this.calculateInteractionScore(interaction);

    userMatrix.set(interaction.itemId, currentScore + interactionScore);
  }

  /**
   * Get recommendations for a user
   */
  async getRecommendations(
    userId: string,
    options?: Partial<RecommendationConfig>
  ): Promise<Recommendation[]> {
    const config = { ...this.config, ...options };
    const algorithm = config.algorithm;

    let recommendations: Recommendation[] = [];

    // Get recommendations based on algorithm
    if (algorithm === 'collaborative') {
      recommendations = await this.getCollaborativeRecommendations(
        userId,
        config
      );
    } else if (algorithm === 'content-based') {
      recommendations = await this.getContentBasedRecommendations(
        userId,
        config
      );
    } else {
      // Hybrid approach
      const collaborative = await this.getCollaborativeRecommendations(
        userId,
        config
      );
      const contentBased = await this.getContentBasedRecommendations(
        userId,
        config
      );
      recommendations = this.mergeRecommendations(collaborative, contentBased);
    }

    // Add popular items if enabled
    if (
      config.includePopular &&
      recommendations.length < config.maxRecommendations
    ) {
      const popular = this.getPopularRecommendations(
        userId,
        config.maxRecommendations - recommendations.length
      );
      recommendations.push(...popular);
    }

    // Apply diversity
    recommendations = this.applyDiversity(
      recommendations,
      config.diversityFactor
    );

    // Filter by minimum score and limit results
    return recommendations
      .filter((rec) => rec.score >= config.minScore)
      .slice(0, config.maxRecommendations);
  }

  /**
   * Get similar items to a given item
   */
  async getSimilarItems(
    itemId: string,
    limit: number = 10
  ): Promise<Recommendation[]> {
    const item = this.items.get(itemId);
    if (!item) return [];

    const similarities: Array<{ item: RecommendationItem; score: number }> = [];

    // Calculate similarity with all other items
    for (const [id, otherItem] of this.items) {
      if (id === itemId) continue;

      const similarity = this.calculateItemSimilarity(item, otherItem);
      if (similarity > 0) {
        similarities.push({ item: otherItem, score: similarity });
      }
    }

    // Sort by similarity and return top results
    return similarities
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
      .map(({ item, score }) => ({
        item,
        score,
        reason: 'Similar to your interests',
        confidence: score,
        source: 'content-based' as const,
      }));
  }

  /**
   * Get trending items
   */
  getTrending(limit: number = 10): Recommendation[] {
    const recentInteractions = this.interactions.filter((i) => {
      const daysSinceInteraction =
        (Date.now() - i.timestamp.getTime()) / (1000 * 60 * 60 * 24);
      return daysSinceInteraction <= 7; // Last 7 days
    });

    // Count interactions per item
    const itemCounts = new Map<string, number>();
    recentInteractions.forEach((interaction) => {
      itemCounts.set(
        interaction.itemId,
        (itemCounts.get(interaction.itemId) || 0) + 1
      );
    });

    // Sort by count
    const trending: Recommendation[] = [];

    Array.from(itemCounts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, limit)
      .forEach(([itemId, count]) => {
        const item = this.items.get(itemId);
        if (item) {
          trending.push({
            item,
            score: Math.min(1, count / 10), // Normalize score
            reason: `Trending with ${count} recent interactions`,
            confidence: 0.8,
            source: 'trending',
          });
        }
      });

    return trending;
  }

  /**
   * Collaborative filtering recommendations
   */
  private async getCollaborativeRecommendations(
    userId: string,
    _config: Required<RecommendationConfig>
  ): Promise<Recommendation[]> {
    const userMatrix = this.userItemMatrix.get(userId);
    if (!userMatrix) return [];

    // Find similar users
    const similarUsers = this.findSimilarUsers(userId, 10);
    if (similarUsers.length === 0) return [];

    // Aggregate items from similar users
    const candidateScores = new Map<string, number>();
    const candidateSources = new Map<string, string[]>();

    similarUsers.forEach(({ userId: similarUserId, similarity }) => {
      const similarUserMatrix = this.userItemMatrix.get(similarUserId);
      if (!similarUserMatrix) return;

      similarUserMatrix.forEach((score, itemId) => {
        // Skip items the user has already interacted with
        if (userMatrix.has(itemId)) return;

        const weightedScore = score * similarity;
        candidateScores.set(
          itemId,
          (candidateScores.get(itemId) || 0) + weightedScore
        );

        if (!candidateSources.has(itemId)) {
          candidateSources.set(itemId, []);
        }
        candidateSources.get(itemId)!.push(similarUserId);
      });
    });

    // Convert to recommendations
    const recommendations: Recommendation[] = [];
    candidateScores.forEach((score, itemId) => {
      const item = this.items.get(itemId);
      if (!item) return;

      const sources = candidateSources.get(itemId) || [];
      const normalizedScore = Math.min(1, score / similarUsers.length);

      recommendations.push({
        item,
        score: normalizedScore,
        reason: `Recommended based on ${sources.length} similar users`,
        confidence: Math.min(0.9, sources.length / 5),
        source: 'collaborative',
      });
    });

    return recommendations.sort((a, b) => b.score - a.score);
  }

  /**
   * Content-based filtering recommendations
   */
  private async getContentBasedRecommendations(
    userId: string,
    config: Required<RecommendationConfig>
  ): Promise<Recommendation[]> {
    const userMatrix = this.userItemMatrix.get(userId);
    if (!userMatrix) return [];

    // Build user profile from their interactions
    const userProfile = this.buildUserProfile(userId);
    if (Object.keys(userProfile).length === 0) return [];

    // Score all items based on similarity to user profile
    const recommendations: Recommendation[] = [];

    this.items.forEach((item) => {
      // Skip items the user has already interacted with
      if (userMatrix.has(item.id)) return;

      const similarity = this.calculateProfileItemSimilarity(userProfile, item);
      if (similarity > 0) {
        // Apply recency boost
        let score = similarity;
        if (item.createdAt) {
          const daysSinceCreation =
            (Date.now() - item.createdAt.getTime()) / (1000 * 60 * 60 * 24);
          if (daysSinceCreation <= 30) {
            score += config.recencyBoost * (1 - daysSinceCreation / 30);
          }
        }

        recommendations.push({
          item,
          score: Math.min(1, score),
          reason: 'Matches your interests',
          confidence: similarity,
          source: 'content-based',
        });
      }
    });

    return recommendations.sort((a, b) => b.score - a.score);
  }

  /**
   * Get popular recommendations
   */
  private getPopularRecommendations(
    userId: string,
    limit: number
  ): Recommendation[] {
    const userMatrix = this.userItemMatrix.get(userId) || new Map();

    // Count interactions per item
    const itemCounts = new Map<string, number>();
    this.interactions.forEach((interaction) => {
      itemCounts.set(
        interaction.itemId,
        (itemCounts.get(interaction.itemId) || 0) + 1
      );
    });

    // Sort by popularity
    const popular: Recommendation[] = [];

    Array.from(itemCounts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, limit * 2) // Get more to filter out already seen
      .forEach(([itemId, count]) => {
        const item = this.items.get(itemId);
        if (item && !userMatrix.has(itemId)) {
          popular.push({
            item,
            score: Math.min(1, count / 50), // Normalize
            reason: `Popular item with ${count} interactions`,
            confidence: 0.7,
            source: 'popular',
          });
        }
      });

    return popular.slice(0, limit);
  }

  /**
   * Merge recommendations from multiple sources
   */
  private mergeRecommendations(
    collaborative: Recommendation[],
    contentBased: Recommendation[]
  ): Recommendation[] {
    const merged = new Map<string, Recommendation>();

    // Add collaborative recommendations
    collaborative.forEach((rec) => {
      merged.set(rec.item.id, {
        ...rec,
        score: rec.score * 0.6, // Weight collaborative at 60%
      });
    });

    // Add or merge content-based recommendations
    contentBased.forEach((rec) => {
      if (merged.has(rec.item.id)) {
        // Merge scores
        const existing = merged.get(rec.item.id)!;
        existing.score += rec.score * 0.4; // Weight content-based at 40%
        existing.confidence = (existing.confidence + rec.confidence) / 2;
        existing.source = 'hybrid';
        existing.reason =
          'Recommended based on similar users and your interests';
      } else {
        merged.set(rec.item.id, {
          ...rec,
          score: rec.score * 0.4,
        });
      }
    });

    return Array.from(merged.values()).sort((a, b) => b.score - a.score);
  }

  /**
   * Apply diversity to recommendations
   */
  private applyDiversity(
    recommendations: Recommendation[],
    diversityFactor: number
  ): Recommendation[] {
    if (diversityFactor === 0 || recommendations.length <= 1) {
      return recommendations;
    }

    const diversified: Recommendation[] = [recommendations[0]];
    const remaining = recommendations.slice(1);

    while (
      diversified.length < recommendations.length &&
      remaining.length > 0
    ) {
      let maxDiversity = -1;
      let bestIndex = 0;

      remaining.forEach((candidate, index) => {
        // Calculate average diversity from already selected items
        const avgDiversity =
          diversified.reduce((sum, selected) => {
            const similarity = this.calculateItemSimilarity(
              candidate.item,
              selected.item
            );
            return sum + (1 - similarity); // Diversity is inverse of similarity
          }, 0) / diversified.length;

        // Combine score with diversity
        const combinedScore =
          candidate.score * (1 - diversityFactor) +
          avgDiversity * diversityFactor;

        if (combinedScore > maxDiversity) {
          maxDiversity = combinedScore;
          bestIndex = index;
        }
      });

      diversified.push(remaining[bestIndex]);
      remaining.splice(bestIndex, 1);
    }

    return diversified;
  }

  /**
   * Find similar users based on interaction patterns
   */
  private findSimilarUsers(
    userId: string,
    limit: number
  ): Array<{ userId: string; similarity: number }> {
    const userMatrix = this.userItemMatrix.get(userId);
    if (!userMatrix) return [];

    const similarities: Array<{ userId: string; similarity: number }> = [];

    // Calculate similarity with all other users
    this.userItemMatrix.forEach((otherMatrix, otherUserId) => {
      if (otherUserId === userId) return;

      const similarity = this.calculateUserSimilarity(userMatrix, otherMatrix);
      if (similarity > 0) {
        similarities.push({ userId: otherUserId, similarity });
      }
    });

    return similarities
      .sort((a, b) => b.similarity - a.similarity)
      .slice(0, limit);
  }

  /**
   * Calculate cosine similarity between two user-item matrices
   */
  private calculateUserSimilarity(
    matrix1: Map<string, number>,
    matrix2: Map<string, number>
  ): number {
    // Find common items
    const commonItems = new Set<string>();
    matrix1.forEach((_, itemId) => {
      if (matrix2.has(itemId)) {
        commonItems.add(itemId);
      }
    });

    if (commonItems.size === 0) return 0;

    // Calculate cosine similarity
    let dotProduct = 0;
    let norm1 = 0;
    let norm2 = 0;

    commonItems.forEach((itemId) => {
      const score1 = matrix1.get(itemId) || 0;
      const score2 = matrix2.get(itemId) || 0;
      dotProduct += score1 * score2;
      norm1 += score1 * score1;
      norm2 += score2 * score2;
    });

    if (norm1 === 0 || norm2 === 0) return 0;

    return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
  }

  /**
   * Build user profile from their interactions
   */
  private buildUserProfile(userId: string): Record<string, number> {
    const profile: Record<string, number> = {};
    const userMatrix = this.userItemMatrix.get(userId);
    if (!userMatrix) return profile;

    userMatrix.forEach((score, itemId) => {
      const item = this.items.get(itemId);
      if (!item?.features) return;

      // Aggregate feature scores weighted by interaction score
      Object.entries(item.features).forEach(([feature, value]) => {
        profile[feature] = (profile[feature] || 0) + value * score;
      });
    });

    // Normalize
    const maxScore = Math.max(...Object.values(profile));
    if (maxScore > 0) {
      Object.keys(profile).forEach((key) => {
        profile[key] /= maxScore;
      });
    }

    return profile;
  }

  /**
   * Calculate similarity between user profile and item
   */
  private calculateProfileItemSimilarity(
    profile: Record<string, number>,
    item: RecommendationItem
  ): number {
    if (!item.features) return 0;

    let dotProduct = 0;
    let profileNorm = 0;
    let itemNorm = 0;

    // Calculate over all features in the profile
    Object.entries(profile).forEach(([feature, profileValue]) => {
      const itemValue = item.features![feature] || 0;
      dotProduct += profileValue * itemValue;
      profileNorm += profileValue * profileValue;
      itemNorm += itemValue * itemValue;
    });

    // Also consider features in item but not in profile
    Object.entries(item.features).forEach(([feature, itemValue]) => {
      if (!(feature in profile)) {
        itemNorm += itemValue * itemValue;
      }
    });

    if (profileNorm === 0 || itemNorm === 0) return 0;

    return dotProduct / (Math.sqrt(profileNorm) * Math.sqrt(itemNorm));
  }

  /**
   * Calculate similarity between two items
   */
  private calculateItemSimilarity(
    item1: RecommendationItem,
    item2: RecommendationItem
  ): number {
    // Feature-based similarity
    let featureSim = 0;
    if (item1.features && item2.features) {
      const allFeatures = new Set([
        ...Object.keys(item1.features),
        ...Object.keys(item2.features),
      ]);

      let dotProduct = 0;
      let norm1 = 0;
      let norm2 = 0;

      allFeatures.forEach((feature) => {
        const value1 = item1.features![feature] || 0;
        const value2 = item2.features![feature] || 0;
        dotProduct += value1 * value2;
        norm1 += value1 * value1;
        norm2 += value2 * value2;
      });

      if (norm1 > 0 && norm2 > 0) {
        featureSim = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
      }
    }

    // Tag-based similarity (Jaccard)
    let tagSim = 0;
    if (item1.tags && item2.tags) {
      const tags1 = new Set(item1.tags);
      const tags2 = new Set(item2.tags);
      const intersection = new Set([...tags1].filter((tag) => tags2.has(tag)));
      const union = new Set([...tags1, ...tags2]);

      if (union.size > 0) {
        tagSim = intersection.size / union.size;
      }
    }

    // Type similarity
    const typeSim = item1.type === item2.type ? 1 : 0;

    // Combine similarities (weighted average)
    return featureSim * 0.5 + tagSim * 0.3 + typeSim * 0.2;
  }

  /**
   * Calculate interaction score
   */
  private calculateInteractionScore(interaction: UserInteraction): number {
    const baseScores = {
      view: 0.1,
      click: 0.3,
      like: 0.5,
      share: 0.7,
      purchase: 1.0,
    };

    let score = baseScores[interaction.type] || 0.1;

    // Boost score based on rating
    if (interaction.rating !== undefined) {
      score *= interaction.rating / 5;
    }

    // Boost score based on duration
    if (interaction.duration !== undefined) {
      const durationMinutes = interaction.duration / 60000;
      score *= Math.min(2, 1 + durationMinutes / 10);
    }

    return score;
  }
}
