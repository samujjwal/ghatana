import {
  canvasFeatureStoryCategories,
  canvasFeatureStoryCount,
} from './canvas-feature-stories.generated';

import type { CanvasFeatureStory, CanvasFeatureStoryCategory } from './types';

export { canvasFeatureStoryCategories, canvasFeatureStoryCount } from './canvas-feature-stories.generated';

export type {
  CanvasAcceptanceCriterion,
  CanvasFeatureStory,
  CanvasFeatureStoryCategory,
  CanvasFeatureStoryProgress,
  CanvasStoryTestReference,
} from './types';

const flattenedStories: CanvasFeatureStory[] = canvasFeatureStoryCategories.flatMap(
  (category) => category.stories as CanvasFeatureStory[],
);

const storyMap: ReadonlyMap<string, CanvasFeatureStory> = new Map(
  flattenedStories.map((story) => [story.id, story]),
);
if (storyMap.size !== flattenedStories.length) {
  throw new Error('Duplicate canvas feature story ids detected during module init.');
}

const slugMap: ReadonlyMap<string, CanvasFeatureStory> = new Map(
  flattenedStories.map((story) => [story.slug, story]),
);
if (slugMap.size !== flattenedStories.length) {
  throw new Error('Duplicate canvas feature story slugs detected during module init.');
}

const categoryMap: ReadonlyMap<string, CanvasFeatureStoryCategory> = new Map(
  canvasFeatureStoryCategories.map(
    (category) => [category.id, category as CanvasFeatureStoryCategory],
  ),
);
if (categoryMap.size !== canvasFeatureStoryCategories.length) {
  throw new Error('Duplicate canvas feature story category ids detected during module init.');
}

export const allCanvasFeatureStoryCategories = canvasFeatureStoryCategories;

export const canvasFeatureStories = flattenedStories;

export const totalCanvasFeatureStoryCount = canvasFeatureStoryCount;

export const canvasFeatureStoryById = (id: string) => storyMap.get(id);

export const canvasFeatureStoryBySlug = (slug: string) => slugMap.get(slug);

export const canvasFeatureStoriesByCategoryId = (categoryId: string) => {
  const category = categoryMap.get(categoryId);
  return category ? category.stories : [];
};

export const canvasFeatureStoryCategoryById = (categoryId: string) => categoryMap.get(categoryId);

export const canvasFeatureStoryCategoriesMetadata = canvasFeatureStoryCategories.map(
  ({ id, title, blueprintReference, order }) => ({
    id,
    title,
    blueprintReference,
    order,
    storyCount: categoryMap.get(id)?.stories.length ?? 0,
  }),
);

export const canvasFeatureStoryMap = storyMap;

export const canvasFeatureStorySlugMap = slugMap;

export const canvasFeatureStoryCategoryMap = categoryMap;
