import type { Meta, StoryObj } from '@storybook/react';
import IncidentTimeline from './IncidentTimeline';
import { timelineRecommendations } from '../../mocks/mockData';

const meta: Meta<typeof IncidentTimeline> = {
  title: 'Copilot/IncidentTimeline',
  component: IncidentTimeline,
};

export default meta;

type Story = StoryObj<typeof IncidentTimeline>;

export const Default: Story = {
  args: {
    items: timelineRecommendations,
  },
};
