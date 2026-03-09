import type { Meta, StoryObj } from '@storybook/react';
import RecommendationCard from './RecommendationCard';
import { copilotRecommendations } from '../../mocks/mockData';

const meta: Meta<typeof RecommendationCard> = {
  title: 'Copilot/RecommendationCard',
  component: RecommendationCard,
};

export default meta;

type Story = StoryObj<typeof RecommendationCard>;

export const Default: Story = {
  args: {
    recommendation: copilotRecommendations[0],
  },
};
