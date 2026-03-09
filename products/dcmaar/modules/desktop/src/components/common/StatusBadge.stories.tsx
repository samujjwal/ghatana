import type { Meta, StoryObj } from '@storybook/react';
import StatusBadge from './StatusBadge';
import { Stack } from '../../ui/tw-compat';

const meta: Meta<typeof StatusBadge> = {
  title: 'Common/StatusBadge',
  component: StatusBadge,
};

export default meta;

type Story = StoryObj<typeof StatusBadge>;

export const Variants: Story = {
  render: () => (
    <Stack direction="row" spacing={1}>
      <StatusBadge status="healthy" />
      <StatusBadge status="warning" />
      <StatusBadge status="error" />
      <StatusBadge status="pending" label="Pending" />
    </Stack>
  ),
};
