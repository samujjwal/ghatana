import type { Meta, StoryObj } from '@storybook/react';
import MetricsSummary from './MetricsSummary';
import { dashboardMetrics } from '../../mocks/mockData';

const meta: Meta<typeof MetricsSummary> = {
  title: 'Dashboard/MetricsSummary',
  component: MetricsSummary,
};

export default meta;

type Story = StoryObj<typeof MetricsSummary>;

export const Default: Story = {
  args: {
    items: [
      {
        id: 'ingest',
        title: 'Ingest Throughput',
        value: `${Math.round(dashboardMetrics.ingestThroughput.at(-1)?.value ?? 0)} events/min`,
        data: dashboardMetrics.ingestThroughput,
      },
      {
        id: 'error',
        title: 'Error Rate',
        value: `${dashboardMetrics.errorRate.at(-1)?.value ?? 0} errors/min`,
        data: dashboardMetrics.errorRate,
      },
      {
        id: 'cpu',
        title: 'CPU',
        value: `${Math.round(dashboardMetrics.cpu.at(-1)?.value ?? 0)}%`,
        data: dashboardMetrics.cpu,
      },
    ],
  },
};
