import type { Meta, StoryObj } from '@storybook/react';
import KpiTile from './KpiTile';

const meta: Meta<typeof KpiTile> = {
  title: 'Common/KpiTile',
  component: KpiTile,
};

export default meta;

type Story = StoryObj<typeof KpiTile>;

export const Default: Story = {
  args: {
    label: 'Exporter Queue',
    value: '76%',
    trend: '+4% vs last hour',
    status: 'warning',
    caption: 'Monitoring exporter backlog',
  },
};
