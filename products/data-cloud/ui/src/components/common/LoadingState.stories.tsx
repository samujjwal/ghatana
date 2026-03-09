import type { Meta, StoryObj } from '@storybook/react';
import { LoadingState } from './LoadingState';

/**
 * Storybook stories for the LoadingState component.
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and visual testing
 * @doc.layer frontend
 */

const meta = {
  title: 'Common/LoadingState',
  component: LoadingState,
  parameters: { layout: 'centered' },
  argTypes: {
    size: {
      control: 'select',
      options: ['xs', 'sm', 'md', 'lg'],
    },
  },
} satisfies Meta<typeof LoadingState>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {},
};

export const WithMessage: Story = {
  args: { message: 'Loading collections...' },
};

export const SmallSize: Story = {
  args: { size: 'sm', message: 'Fetching data...' },
};

export const LargeSize: Story = {
  args: { size: 'lg', message: 'Processing query...' },
};

export const NoMessage: Story = {
  args: { message: '' },
};

export const DataCloudContexts: Story = {
  args: {},
  render: () => (
    <div className="flex flex-col gap-4 p-4 w-80">
      <LoadingState size="sm" message="Loading connectors..." />
      <LoadingState size="md" message="Executing analytics query..." />
      <LoadingState size="lg" message="Initializing data pipeline..." />
    </div>
  ),
};
