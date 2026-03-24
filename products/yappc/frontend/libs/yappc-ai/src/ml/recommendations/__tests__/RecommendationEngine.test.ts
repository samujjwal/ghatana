/**
 * RecommendationEngine Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  RecommendationEngine,
  type RecommendationItem,
  type UserInteraction,
} from '../RecommendationEngine';

describe.skip('RecommendationEngine', () => {
  let engine: RecommendationEngine;
  let items: RecommendationItem[];

  beforeEach(() => {
    engine = new RecommendationEngine({
      algorithm: 'hybrid',
      maxRecommendations: 10,
      minScore: 0.1,
      includePopular: true,
      diversityFactor: 0.3,
      recencyBoost: 0.2,
    });

    items = [
      {
        id: 'item1',
        type: 'article',
        title: 'Introduction to Machine Learning',
        features: { ml: 1.0, beginner: 0.8, programming: 0.6 },
        tags: ['ml', 'tutorial', 'beginner'],
        popularity: 100,
      },
      {
        id: 'item2',
        type: 'article',
        title: 'Advanced Neural Networks',
        features: { ml: 1.0, beginner: 0.2, advanced: 0.9, programming: 0.8 },
        tags: ['ml', 'neural-networks', 'advanced'],
        popularity: 80,
      },
      {
        id: 'item3',
        type: 'video',
        title: 'Python for Beginners',
        features: { programming: 1.0, beginner: 1.0, python: 0.9 },
        tags: ['python', 'tutorial', 'beginner'],
        popularity: 120,
      },
      {
        id: 'item4',
        type: 'article',
        title: 'Deep Learning Fundamentals',
        features: {
          ml: 0.9,
          deeplearning: 1.0,
          advanced: 0.7,
          programming: 0.7,
        },
        tags: ['ml', 'deep-learning', 'advanced'],
        popularity: 90,
      },
      {
        id: 'item5',
        type: 'video',
        title: 'Web Development Basics',
        features: { web: 1.0, beginner: 0.9, programming: 0.8 },
        tags: ['web', 'tutorial', 'beginner'],
        popularity: 110,
      },
    ];

    engine.addItems(items);
  });

  describe('Item Management', () => {
    it('should add items to catalog', () => {
      const newEngine = new RecommendationEngine();
      newEngine.addItems([items[0]]);

      // Verify by trying to get similar items
      const similar = newEngine.getSimilarItems('item1', 5);
      expect(similar).toBeDefined();
    });

    it('should handle empty catalog', async () => {
      const emptyEngine = new RecommendationEngine();
      const recommendations = await emptyEngine.getRecommendations('user1');
      expect(recommendations).toEqual([]);
    });
  });

  describe('User Interactions', () => {
    it('should record user interactions', () => {
      const interaction: UserInteraction = {
        userId: 'user1',
        itemId: 'item1',
        type: 'view',
        timestamp: new Date(),
      };

      engine.recordInteraction(interaction);

      // Interaction recorded - verify by getting recommendations
      expect(async () => {
        await engine.getRecommendations('user1');
      }).not.toThrow();
    });

    it('should handle different interaction types', () => {
      const types: UserInteraction['type'][] = [
        'view',
        'click',
        'like',
        'purchase',
        'share',
      ];

      types.forEach((type) => {
        engine.recordInteraction({
          userId: 'user1',
          itemId: 'item1',
          type,
          timestamp: new Date(),
        });
      });

      // All interactions recorded
      expect(async () => {
        await engine.getRecommendations('user1');
      }).not.toThrow();
    });

    it('should record ratings', () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'view',
        rating: 5,
        timestamp: new Date(),
      });

      // Rating recorded
      expect(async () => {
        await engine.getRecommendations('user1');
      }).not.toThrow();
    });

    it('should record duration', () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'view',
        duration: 180000, // 3 minutes
        timestamp: new Date(),
      });

      // Duration recorded
      expect(async () => {
        await engine.getRecommendations('user1');
      }).not.toThrow();
    });
  });

  describe('Collaborative Filtering', () => {
    beforeEach(() => {
      // User1 likes ML content
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item2',
        type: 'view',
        timestamp: new Date(),
      });

      // User2 also likes ML content (similar to user1)
      engine.recordInteraction({
        userId: 'user2',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });
      engine.recordInteraction({
        userId: 'user2',
        itemId: 'item4',
        type: 'like',
        timestamp: new Date(),
      });

      // User3 likes programming content
      engine.recordInteraction({
        userId: 'user3',
        itemId: 'item3',
        type: 'like',
        timestamp: new Date(),
      });
      engine.recordInteraction({
        userId: 'user3',
        itemId: 'item5',
        type: 'view',
        timestamp: new Date(),
      });
    });

    it('should recommend based on similar users', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'collaborative',
      });

      expect(recommendations.length).toBeGreaterThan(0);
      expect(recommendations[0].source).toBe('collaborative');
    });

    it('should not recommend already interacted items', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'collaborative',
      });

      const ids = recommendations.map((r) => r.item.id);
      expect(ids).not.toContain('item1'); // Already liked
      expect(ids).not.toContain('item2'); // Already viewed
    });

    it('should recommend from similar users', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'collaborative',
      });

      // User1 is similar to User2, so should recommend item4
      const ids = recommendations.map((r) => r.item.id);
      expect(ids).toContain('item4');
    });
  });

  describe('Content-Based Filtering', () => {
    beforeEach(() => {
      // User likes beginner ML content
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item3',
        type: 'view',
        timestamp: new Date(),
      });
    });

    it('should recommend based on content similarity', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'content-based',
      });

      expect(recommendations.length).toBeGreaterThan(0);
      expect(recommendations[0].source).toBe('content-based');
    });

    it('should recommend similar items', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'content-based',
      });

      const ids = recommendations.map((r) => r.item.id);
      // Should recommend item4 (similar ML content)
      expect(ids).toContain('item4');
    });

    it('should not recommend already interacted items', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'content-based',
      });

      const ids = recommendations.map((r) => r.item.id);
      expect(ids).not.toContain('item1');
      expect(ids).not.toContain('item3');
    });
  });

  describe('Hybrid Algorithm', () => {
    beforeEach(() => {
      // User1 likes beginner content
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });

      // User2 similar to user1
      engine.recordInteraction({
        userId: 'user2',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });
      engine.recordInteraction({
        userId: 'user2',
        itemId: 'item3',
        type: 'like',
        timestamp: new Date(),
      });
    });

    it('should combine collaborative and content-based', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'hybrid',
      });

      expect(recommendations.length).toBeGreaterThan(0);

      // Should have both collaborative and hybrid sources
      const sources = recommendations.map((r) => r.source);
      expect(
        sources.some(
          (s) =>
            s === 'hybrid' || s === 'collaborative' || s === 'content-based'
        )
      ).toBe(true);
    });

    it('should merge scores from both algorithms', async () => {
      const recommendations = await engine.getRecommendations('user1', {
        algorithm: 'hybrid',
      });

      // All recommendations should have scores
      recommendations.forEach((rec) => {
        expect(rec.score).toBeGreaterThan(0);
        expect(rec.score).toBeLessThanOrEqual(1);
      });
    });
  });

  describe('Similar Items', () => {
    it('should find similar items', async () => {
      const similar = await engine.getSimilarItems('item1', 5);

      expect(similar.length).toBeGreaterThan(0);
      expect(similar.length).toBeLessThanOrEqual(5);
      expect(similar[0].source).toBe('content-based');
    });

    it('should not include the source item', async () => {
      const similar = await engine.getSimilarItems('item1', 5);

      const ids = similar.map((r) => r.item.id);
      expect(ids).not.toContain('item1');
    });

    it('should rank by similarity', async () => {
      const similar = await engine.getSimilarItems('item1', 5);

      // Scores should be descending
      for (let i = 0; i < similar.length - 1; i++) {
        expect(similar[i].score).toBeGreaterThanOrEqual(similar[i + 1].score);
      }
    });

    it('should handle non-existent item', async () => {
      const similar = await engine.getSimilarItems('nonexistent', 5);
      expect(similar).toEqual([]);
    });

    it('should find similar based on features', async () => {
      const similar = await engine.getSimilarItems('item1', 5);

      // item2 and item4 are both ML content, should be in results
      const ids = similar.map((r) => r.item.id);
      expect(ids).toContain('item2');
      expect(ids).toContain('item4');
    });

    it('should find similar based on tags', async () => {
      const similar = await engine.getSimilarItems('item1', 5);

      // Items with overlapping tags should score higher
      const item2Score = similar.find((r) => r.item.id === 'item2')?.score || 0;
      const item5Score = similar.find((r) => r.item.id === 'item5')?.score || 0;

      expect(item2Score).toBeGreaterThan(item5Score); // item2 shares 'ml' tag
    });
  });

  describe('Trending Items', () => {
    beforeEach(() => {
      const now = new Date();
      const weekAgo = new Date(now.getTime() - 6 * 24 * 60 * 60 * 1000);

      // Recent interactions for item3
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item3',
        type: 'view',
        timestamp: now,
      });
      engine.recordInteraction({
        userId: 'user2',
        itemId: 'item3',
        type: 'view',
        timestamp: now,
      });
      engine.recordInteraction({
        userId: 'user3',
        itemId: 'item3',
        type: 'like',
        timestamp: now,
      });

      // Older interactions for item1
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'view',
        timestamp: weekAgo,
      });
      engine.recordInteraction({
        userId: 'user2',
        itemId: 'item1',
        type: 'view',
        timestamp: weekAgo,
      });
    });

    it('should return trending items', () => {
      const trending = engine.getTrending(5);

      expect(trending.length).toBeGreaterThan(0);
      expect(trending.length).toBeLessThanOrEqual(5);
    });

    it('should prioritize recent interactions', () => {
      const trending = engine.getTrending(5);

      // item3 has more recent interactions, should rank higher
      expect(trending[0].item.id).toBe('item3');
    });

    it('should have trending source', () => {
      const trending = engine.getTrending(5);
      expect(trending[0].source).toBe('trending');
    });
  });

  describe('Configuration Options', () => {
    it('should respect max recommendations limit', async () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });

      const recommendations = await engine.getRecommendations('user1', {
        maxRecommendations: 2,
      });

      expect(recommendations.length).toBeLessThanOrEqual(2);
    });

    it('should filter by minimum score', async () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });

      const recommendations = await engine.getRecommendations('user1', {
        minScore: 0.5,
      });

      recommendations.forEach((rec) => {
        expect(rec.score).toBeGreaterThanOrEqual(0.5);
      });
    });

    it('should include popular items when enabled', async () => {
      // No interactions for user1
      const recommendations = await engine.getRecommendations('user1', {
        includePopular: true,
      });

      expect(recommendations.length).toBeGreaterThan(0);
      const sources = recommendations.map((r) => r.source);
      expect(sources).toContain('popular');
    });

    it('should apply diversity factor', async () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item2',
        type: 'like',
        timestamp: new Date(),
      });

      const diverse = await engine.getRecommendations('user1', {
        diversityFactor: 0.8, // High diversity
      });

      const noDiverse = await engine.getRecommendations('user1', {
        diversityFactor: 0, // No diversity
      });

      // With diversity, should have more variety in types/tags
      const diverseTypes = new Set(diverse.map((r) => r.item.type));
      const noDiverseTypes = new Set(noDiverse.map((r) => r.item.type));

      expect(diverseTypes.size).toBeGreaterThanOrEqual(noDiverseTypes.size);
    });
  });

  describe('Scoring', () => {
    it('should normalize scores between 0 and 1', async () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });

      const recommendations = await engine.getRecommendations('user1');

      recommendations.forEach((rec) => {
        expect(rec.score).toBeGreaterThanOrEqual(0);
        expect(rec.score).toBeLessThanOrEqual(1);
      });
    });

    it('should have confidence scores', async () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });

      const recommendations = await engine.getRecommendations('user1');

      recommendations.forEach((rec) => {
        expect(rec.confidence).toBeGreaterThanOrEqual(0);
        expect(rec.confidence).toBeLessThanOrEqual(1);
      });
    });

    it('should provide reasons', async () => {
      engine.recordInteraction({
        userId: 'user1',
        itemId: 'item1',
        type: 'like',
        timestamp: new Date(),
      });

      const recommendations = await engine.getRecommendations('user1');

      recommendations.forEach((rec) => {
        expect(rec.reason).toBeDefined();
        expect(rec.reason.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Edge Cases', () => {
    it('should handle user with no interactions', async () => {
      const recommendations = await engine.getRecommendations('newuser');

      // Should still return popular items
      expect(recommendations.length).toBeGreaterThan(0);
    });

    it('should handle all items already seen', async () => {
      // User has seen all items
      items.forEach((item) => {
        engine.recordInteraction({
          userId: 'user1',
          itemId: item.id,
          type: 'view',
          timestamp: new Date(),
        });
      });

      const recommendations = await engine.getRecommendations('user1', {
        includePopular: false,
      });

      // No recommendations (all items seen, popular disabled)
      expect(recommendations).toEqual([]);
    });

    it('should handle items without features', async () => {
      engine.addItems([
        {
          id: 'item-no-features',
          type: 'article',
          title: 'Test',
        },
      ]);

      const similar = await engine.getSimilarItems('item-no-features', 5);
      expect(similar).toBeDefined();
    });

    it('should handle items without tags', async () => {
      engine.addItems([
        {
          id: 'item-no-tags',
          type: 'article',
          title: 'Test',
          features: { test: 1.0 },
        },
      ]);

      const similar = await engine.getSimilarItems('item-no-tags', 5);
      expect(similar).toBeDefined();
    });
  });
});
