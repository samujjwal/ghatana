import type { Meta, StoryObj } from '@storybook/react';
import PageHeader from './PageHeader';

const meta: Meta<typeof PageHeader> = {
  title: 'Common/PageHeader',
  component: PageHeader,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

type Story = StoryObj<typeof PageHeader>;

export const Default: Story = {
  args: {
    title: 'Operations Overview',
    description: 'Real-time visibility with actionable insights.',
    actions: [
      { label: 'Secondary', variant: 'outlined' },
      { label: 'Primary', variant: 'contained' },
    ],
  },
};

export const WithBreadcrumbs: Story = {
  args: {
    title: 'Agent Status',
    description: 'Telemetry health for agent alpha-1.',
    breadcrumbs: [
      { label: 'Agents', href: '/agent/status' },
      { label: 'alpha-1' },
    ],
  },
};
