import type { Meta, StoryObj } from '@storybook/react';
import { Database, Cpu, Activity, Zap } from 'lucide-react';
import { KPICard } from './KPICard';

/**
 * Storybook stories for the KPICard component.
 *
 * Demonstrates KPI metric display with trend indicators and loading states.
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and visual testing
 * @doc.layer frontend
 */

const meta = {
  title: 'Cards/KPICard',
  component: KPICard,
  parameters: { layout: 'centered' },
} satisfies Meta<typeof KPICard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    title: 'Total Collections',
    value: 42,
    icon: <Database className="h-6 w-6" />,
  },
};

export const WithPositiveTrend: Story = {
  args: {
    title: 'Active Pipelines',
    value: 18,
    icon: <Activity className="h-6 w-6" />,
    trend: { value: 12, isPositive: true },
  },
};

export const WithNegativeTrend: Story = {
  args: {
    title: 'Error Rate',
    value: '2.4%',
    icon: <Zap className="h-6 w-6" />,
    trend: { value: 5, isPositive: false },
  },
};

export const Loading: Story = {
  args: {
    title: 'Query Throughput',
    value: 0,
    icon: <Cpu className="h-6 w-6" />,
    isLoading: true,
  },
};

export const LargeValue: Story = {
  args: {
    title: 'Records Processed',
    value: '1.2M',
    icon: <Database className="h-6 w-6" />,
    trend: { value: 34, isPositive: true },
  },
};

export const DashboardGrid: Story = {
  args: { title: '', value: 0, icon: null },
  render: () => (
    <div className="grid grid-cols-2 gap-4 p-4 w-[600px]">
      <KPICard
        title="Total Collections"
        value={42}
        icon={<Database className="h-6 w-6" />}
        trend={{ value: 8, isPositive: true }}
      />
      <KPICard
        title="Active Pipelines"
        value={18}
        icon={<Activity className="h-6 w-6" />}
        trend={{ value: 12, isPositive: true }}
      />
      <KPICard
        title="Query Throughput"
        value="4.2k/s"
        icon={<Cpu className="h-6 w-6" />}
        trend={{ value: 3, isPositive: false }}
      />
      <KPICard
        title="Records Today"
        value="1.2M"
        icon={<Zap className="h-6 w-6" />}
        trend={{ value: 25, isPositive: true }}
      />
    </div>
  ),
};
