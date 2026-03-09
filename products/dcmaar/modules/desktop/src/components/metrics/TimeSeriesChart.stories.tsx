import type { Meta, StoryObj } from '@storybook/react';
import TimeSeriesChart from './TimeSeriesChart';

const data = Array.from({ length: 12 }, (_, index) => ({
  timestamp: new Date(Date.now() - (12 - index) * 3600 * 1000).toISOString(),
  throughput: 800 + Math.random() * 150,
}));

const meta: Meta<typeof TimeSeriesChart> = {
  title: 'Metrics/TimeSeriesChart',
  component: TimeSeriesChart,
};

export default meta;

type Story = StoryObj<typeof TimeSeriesChart>;

export const Default: Story = {
  args: {
    title: 'Throughput',
    description: 'Example area chart rendering',
    data,
    series: [
      { key: 'throughput', label: 'Throughput', color: '#38bdf8' },
    ],
  },
};
