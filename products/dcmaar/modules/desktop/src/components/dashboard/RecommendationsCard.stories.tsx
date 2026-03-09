import type { Meta, StoryObj } from '@storybook/react';
import RecommendationsCard from './RecommendationsCard';
import { copilotRecommendations } from '../../mocks/mockData';

const meta: Meta<typeof RecommendationsCard> = {
  title: 'Dashboard/RecommendationsCard',
  component: RecommendationsCard,
};

export default meta;

type Story = StoryObj<typeof RecommendationsCard>;

export const Default: Story = {
  args: {
    items: copilotRecommendations,
  },
};
