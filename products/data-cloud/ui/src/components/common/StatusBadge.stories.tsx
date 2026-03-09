import type { Meta, StoryObj } from '@storybook/react';
import { StatusBadge } from './StatusBadge';

/**
 * Storybook stories for the StatusBadge component.
 *
 * Demonstrates all semantic variants used for status indicators
 * across the Data Cloud UI.
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and visual testing
 * @doc.layer frontend
 */

const meta = {
  title: 'Common/StatusBadge',
  component: StatusBadge,
  parameters: { layout: 'centered' },
  argTypes: {
    variant: {
      control: 'select',
      options: ['success', 'danger', 'warning', 'info', 'neutral'],
    },
  },
} satisfies Meta<typeof StatusBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Success: Story = {
  args: { status: 'Active', variant: 'success' },
};

export const Danger: Story = {
  args: { status: 'Failed', variant: 'danger' },
};

export const Warning: Story = {
  args: { status: 'Degraded', variant: 'warning' },
};

export const Info: Story = {
  args: { status: 'Syncing', variant: 'info' },
};

export const Neutral: Story = {
  args: { status: 'Unknown', variant: 'neutral' },
};

export const AllVariants: Story = {
  args: { status: '', variant: 'neutral' },
  render: () => (
    <div className="flex flex-wrap gap-3 p-4">
      <StatusBadge status="Active" variant="success" />
      <StatusBadge status="Failed" variant="danger" />
      <StatusBadge status="Degraded" variant="warning" />
      <StatusBadge status="Syncing" variant="info" />
      <StatusBadge status="Unknown" variant="neutral" />
    </div>
  ),
};

export const DataCloudStatuses: Story = {
  args: { status: '', variant: 'neutral' },
  render: () => (
    <div className="flex flex-col gap-2 p-4">
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600 w-32">Collection:</span>
        <StatusBadge status="HEALTHY" variant="success" />
      </div>
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600 w-32">Connector:</span>
        <StatusBadge status="DISCONNECTED" variant="danger" />
      </div>
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600 w-32">Pipeline:</span>
        <StatusBadge status="RUNNING" variant="info" />
      </div>
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600 w-32">Alert:</span>
        <StatusBadge status="TRIGGERED" variant="warning" />
      </div>
    </div>
  ),
};
