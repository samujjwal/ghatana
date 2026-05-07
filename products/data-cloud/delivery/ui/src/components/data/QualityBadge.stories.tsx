import type { Meta, StoryObj } from '@storybook/react';
import { QualityBadge } from './QualityBadge';

/**
 * Storybook stories for the QualityBadge component.
 *
 * Demonstrates quality score display with all levels, sizes,
 * metric tooltips, and issue indicators.
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and visual testing
 * @doc.layer frontend
 */

const meta = {
  title: 'Data/QualityBadge',
  component: QualityBadge,
  parameters: { layout: 'centered' },
  argTypes: {
    score: { control: { type: 'range', min: 0, max: 100, step: 1 } },
    size: { control: 'select', options: ['sm', 'md', 'lg'] },
    showLabel: { control: 'boolean' },
  },
} satisfies Meta<typeof QualityBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Excellent: Story = {
  args: { score: 95 },
};

export const Good: Story = {
  args: { score: 78 },
};

export const Fair: Story = {
  args: { score: 58 },
};

export const Poor: Story = {
  args: { score: 32 },
};

export const WithLabel: Story = {
  args: { score: 85, showLabel: true },
};

export const WithIssues: Story = {
  args: { score: 62, issues: 3 },
};

export const WithMetricsTooltip: Story = {
  args: {
    score: 82,
    showLabel: true,
    issues: 1,
    metrics: {
      completeness: 95,
      accuracy: 88,
      freshness: 70,
      consistency: 75,
    },
  },
};

export const SmallSize: Story = {
  args: { score: 91, size: 'sm' },
};

export const LargeSize: Story = {
  args: { score: 74, size: 'lg', showLabel: true },
};

export const AllLevels: Story = {
  args: { score: 0 },
  render: () => (
    <div className="flex items-center gap-3 p-4">
      <QualityBadge score={95} showLabel />
      <QualityBadge score={78} showLabel />
      <QualityBadge score={58} showLabel />
      <QualityBadge score={32} showLabel />
    </div>
  ),
};

export const InlineWithFields: Story = {
  args: { score: 0 },
  render: () => (
    <div className="flex flex-col gap-3 p-4 w-72">
      {[
        { field: 'customer_id', score: 98 },
        { field: 'email', score: 72, issues: 2 },
        { field: 'phone', score: 54, issues: 5 },
        { field: 'address', score: 41, issues: 8 },
      ].map(({ field, score, issues }) => (
        <div key={field} className="flex items-center justify-between">
          <span className="text-sm font-mono text-gray-700">{field}</span>
          <QualityBadge score={score} issues={issues} size="sm" />
        </div>
      ))}
    </div>
  ),
};
