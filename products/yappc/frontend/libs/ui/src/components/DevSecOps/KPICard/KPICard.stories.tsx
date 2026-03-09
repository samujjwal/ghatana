/**
 * KPICard Storybook Stories
 *
 * Interactive examples and documentation for the KPICard component.
 *
 * @module DevSecOps/KPICard/stories
 */


import { KPICard } from './KPICard';
import { devsecopsTheme } from '../../../theme/devsecops-theme';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof KPICard> = {
  title: 'DevSecOps/KPICard',
  component: KPICard,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider theme={devsecopsTheme}>
        <Story />
      </ThemeProvider>
    ),
  ],
  argTypes: {
    trend: {
      control: 'object',
      description: 'Trend indicator configuration',
    },
    showProgress: {
      control: 'boolean',
      description: 'Show progress bar',
    },
    title: {
      control: 'text',
      description: 'Metric name',
    },
    value: {
      control: 'number',
      description: 'Current value',
    },
    target: {
      control: 'number',
      description: 'Target value for progress',
    },
    unit: {
      control: 'text',
      description: 'Unit suffix',
    },
  },
  parameters: {
    docs: {
      description: {
        component:
          'A card component for displaying Key Performance Indicators with optional trend indicators and progress tracking. Part of the DevSecOps Canvas design system.',
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof KPICard>;

/**
 * Default KPICard with basic configuration
 */
export const Default: Story = {
  args: {
    title: 'System Uptime',
    value: 99.8,
    unit: '%',
  },
  parameters: {
    docs: {
      description: {
        story: 'Basic KPI card with title, value, and unit.',
      },
    },
  },
};

/**
 * KPICard with positive trend indicator
 */
export const WithTrend: Story = {
  args: {
    title: 'Deployment Frequency',
    value: 24,
    unit: ' per week',
    trend: {
      direction: 'up',
      value: 12.5,
    },
  },
  parameters: {
    docs: {
      description: {
        story: 'KPI card showing an upward trend with percentage change.',
      },
    },
  },
};

/**
 * KPICard with progress bar
 */
export const WithProgress: Story = {
  args: {
    title: 'Story Points Completed',
    value: 156,
    target: 200,
    showProgress: true,
  },
  parameters: {
    docs: {
      description: {
        story: 'KPI card with progress bar showing completion towards target.',
      },
    },
  },
};

/**
 * KPICard with negative trend (improvement metric)
 */
export const NegativeTrend: Story = {
  args: {
    title: 'Security Vulnerabilities',
    value: 5,
    trend: {
      direction: 'down',
      value: -3.2,
    },
  },
  parameters: {
    docs: {
      description: {
        story:
          'KPI card showing a downward trend - in this case, fewer vulnerabilities is positive.',
      },
    },
  },
};

/**
 * Complete KPICard with all features
 */
export const Complete: Story = {
  args: {
    title: 'Code Coverage',
    value: 87.5,
    unit: '%',
    target: 90,
    showProgress: true,
    trend: {
      direction: 'up',
      value: 2.1,
    },
  },
  parameters: {
    docs: {
      description: {
        story: 'KPI card with all features: trend, progress, and target.',
      },
    },
  },
};

/**
 * Mean Time to Recovery (MTTR) metric
 */
export const MTTR: Story = {
  args: {
    title: 'Mean Time to Recovery',
    value: 45,
    unit: ' min',
    trend: {
      direction: 'down',
      value: -8.3,
    },
  },
  parameters: {
    docs: {
      description: {
        story: 'Time-based metric where lower values indicate improvement.',
      },
    },
  },
};

/**
 * Large value display
 */
export const LargeValue: Story = {
  args: {
    title: 'Total Deployments',
    value: 1247,
    trend: {
      direction: 'up',
      value: 15.7,
    },
  },
  parameters: {
    docs: {
      description: {
        story: 'KPI card handling larger numeric values.',
      },
    },
  },
};
