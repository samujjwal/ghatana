import type { Meta, StoryObj } from '@storybook/react';
import TrendSparkline from './TrendSparkline';

const sample = Array.from({ length: 24 }, (_, index) => ({
  timestamp: new Date(Date.now() - (24 - index) * 3600 * 1000).toISOString(),
  value: 300 + Math.sin(index / 4) * 40 + Math.random() * 25,
}));

const meta: Meta<typeof TrendSparkline> = {
  title: 'Common/TrendSparkline',
  component: TrendSparkline,
};

export default meta;

type Story = StoryObj<typeof TrendSparkline>;

export const Default: Story = {
  args: {
    title: 'Ingest Throughput',
    value: '342',
    delta: '+12 vs prev hour',
    data: sample,
    unit: 'events/min',
  },
};
