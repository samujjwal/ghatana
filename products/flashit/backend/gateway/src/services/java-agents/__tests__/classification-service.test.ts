/**
 * Classification Service Tests
 * 
 * Tests the local heuristic classification logic.
 * Java agent integration is tested via integration tests.
 */

import { describe, it, expect } from 'vitest';

describe('ClassificationService - Local Heuristic Logic', () => {
  // Test the heuristic classification algorithm directly
  // without mocking complex dependencies
  
  const mockSpheres = [
    { id: 'sphere-1', name: 'Work', description: 'Work related', type: 'WORK' },
    { id: 'sphere-2', name: 'Personal', description: 'Personal thoughts', type: 'PERSONAL' },
    { id: 'sphere-3', name: 'Health', description: 'Health and fitness', type: 'HEALTH' },
  ];

  // Extract the heuristic logic for unit testing
  function classifyWithHeuristics(
    content: string,
    tags: string[],
    emotions: string[],
    spheres: typeof mockSpheres
  ): { sphereId: string; score: number; reasoning: string } {
    const contentText = content.toLowerCase();
    const keywords = [
      ...tags.map(t => t.toLowerCase()),
      ...emotions.map(e => e.toLowerCase()),
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
      if (sphere.type === 'WORK' && keywords.some(k => ['work', 'professional', 'job', 'office', 'meeting'].includes(k))) {
        score += 5;
        reasons.push('Work sphere type matches keywords');
      }
      if (sphere.type === 'HEALTH' && keywords.some(k => ['health', 'fitness', 'exercise', 'wellness', 'medical'].includes(k))) {
        score += 5;
        reasons.push('Health sphere type matches keywords');
      }
      if (sphere.type === 'PERSONAL' && keywords.some(k => ['personal', 'self', 'me', 'my'].includes(k))) {
        score += 5;
        reasons.push('Personal sphere type matches keywords');
      }

      scores.push({
        sphere,
        score,
        reasoning: reasons.length > 0 ? reasons.join('; ') : 'Default match',
      });
    }

    scores.sort((a, b) => b.score - a.score);
    const best = scores[0];

    return {
      sphereId: best.sphere.id,
      score: best.score,
      reasoning: best.reasoning,
    };
  }

  describe('heuristic classification', () => {
    it('should match work sphere based on work keywords', () => {
      const result = classifyWithHeuristics(
        'Had a great meeting with the team today',
        ['work', 'meeting'],
        ['happy'],
        mockSpheres
      );

      expect(result.sphereId).toBe('sphere-1'); // Work sphere
      expect(result.score).toBeGreaterThan(0);
    });

    it('should match health sphere based on fitness keywords', () => {
      const result = classifyWithHeuristics(
        'Went for a 5km run this morning',
        ['fitness', 'exercise'],
        ['energetic'],
        mockSpheres
      );

      expect(result.sphereId).toBe('sphere-3'); // Health sphere
      expect(result.score).toBeGreaterThan(0);
    });

    it('should match personal sphere based on personal keywords', () => {
      const result = classifyWithHeuristics(
        'Reflecting on my personal goals',
        ['personal', 'self'],
        ['thoughtful'],
        mockSpheres
      );

      expect(result.sphereId).toBe('sphere-2'); // Personal sphere
      expect(result.score).toBeGreaterThan(0);
    });

    it('should match sphere by name in content', () => {
      const result = classifyWithHeuristics(
        'This is about my health journey',
        [],
        [],
        mockSpheres
      );

      expect(result.sphereId).toBe('sphere-3'); // Health sphere (name match)
      expect(result.score).toBeGreaterThanOrEqual(10); // Name match + possible description match
    });

    it('should return first sphere when no matches', () => {
      const result = classifyWithHeuristics(
        'Random content with no keywords',
        [],
        [],
        mockSpheres
      );

      // Should return first sphere with score 0
      expect(result.sphereId).toBe('sphere-1');
      expect(result.score).toBe(0);
    });
  });
});
