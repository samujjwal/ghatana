import { FeatureStories } from '@ghatana/canvas';

import { CanvasFeatureStoryList } from './CanvasFeatureStoryList';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof CanvasFeatureStoryList> = {
  title: 'Canvas/Feature Stories/CanvasFeatureStoryList',
  component: CanvasFeatureStoryList,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'Interactive list of canvas feature stories generated from the blueprint-driven Markdown catalog. Supports category navigation, search, highlighting, and selection callbacks.',
      },
    },
  },
  args: {
    showCategoryLabelOnCards: true,
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof CanvasFeatureStoryList>;

export const Default: Story = {
  name: 'All Stories',
};

export const HighlightedStory: Story = {
  args: {
    highlightStoryId: '1.1',
  },
};

export const CollaborationFocus: Story = {
  name: 'Collaboration & Security',
  args: {
    defaultCategoryId: '3',
  },
};

export const CustomDataset: Story = {
  name: 'Curated Subset',
  args: {
    categories: FeatureStories.canvasFeatureStoryCategories
      .slice(0, 2)
      .map((category) => ({
        ...category,
        stories: category.stories.slice(0, 3),
      })),
    highlightStoryId: FeatureStories.canvasFeatureStoryById('1.2')?.id ?? '1.2',
    showCategoryLabelOnCards: true,
  },
};

export const ProgressFiltered: Story = {
  name: 'Progress Status — In Progress',
  args: {
    initialProgressFilter: 'in progress',
    showCategoryLabelOnCards: true,
  },
  play: async () => {
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(
          'canvas-feature-progress-filter',
          'in progress'
        );
      } catch (error) {
        // ignore storage access issues
      }
    }
  },
};
