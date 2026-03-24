import { describe, expect, it } from 'vitest';

import {
  canvasFeatureStories,
  canvasFeatureStoryCategories,
  canvasFeatureStoryCategoriesMetadata,
  canvasFeatureStoryCount,
  canvasFeatureStoryMap,
  canvasFeatureStorySlugMap,
} from '../index';

describe('canvas feature stories dataset', () => {
  it('has a stable count that matches the flattened data', () => {
    expect(canvasFeatureStories.length).toBe(canvasFeatureStoryCount);
  });

  it('ensures all story ids and slugs are unique', () => {
    expect(canvasFeatureStories.length).toBe(canvasFeatureStoryMap.size);
    expect(canvasFeatureStories.length).toBe(canvasFeatureStorySlugMap.size);
  });

  it('ensures stories maintain acceptance criteria and tests', () => {
    for (const story of canvasFeatureStories) {
      // Acceptance criteria and tests are optional but should be arrays if present
      if (story.acceptanceCriteria !== undefined) {
        expect(Array.isArray(story.acceptanceCriteria)).toBe(true);
      }
      if (story.tests !== undefined) {
        expect(Array.isArray(story.tests)).toBe(true);
      }
    }
  });

  it('ensures progress entries include a status when present', () => {
    for (const story of canvasFeatureStories) {
      if (story.progress) {
        expect(story.progress.status).toBeTruthy();
      }
    }
  });

  it('keeps category metadata aligned with story lists', () => {
    for (const metadata of canvasFeatureStoryCategoriesMetadata) {
      const category = canvasFeatureStoryCategories.find((item) => item.id === metadata.id);
      expect(category).toBeTruthy();
      expect(category?.stories.length ?? 0).toBe(metadata.storyCount);
      const expectedOrder = canvasFeatureStoryCategories.findIndex((item) => item.id === metadata.id);
      expect(metadata.order).toBe(expectedOrder);
    }
  });
});
