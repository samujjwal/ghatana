import type { Meta, StoryObj } from '@storybook/react';
import ExecutionTimeline from './ExecutionTimeline';
import { commandExecutions } from '../../mocks/mockData';

const meta: Meta<typeof ExecutionTimeline> = {
  title: 'Commands/ExecutionTimeline',
  component: ExecutionTimeline,
};

export default meta;

type Story = StoryObj<typeof ExecutionTimeline>;

export const Default: Story = {
  args: {
    items: commandExecutions,
  },
};
