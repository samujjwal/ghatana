import type { Meta, StoryObj } from '@storybook/react';
import EventTimeline from './EventTimeline';
import { recentEvents } from '../../mocks/mockData';

const meta: Meta<typeof EventTimeline> = {
  title: 'Dashboard/EventTimeline',
  component: EventTimeline,
};

export default meta;

type Story = StoryObj<typeof EventTimeline>;

export const Default: Story = {
  args: {
    events: recentEvents.slice(0, 4),
  },
};
