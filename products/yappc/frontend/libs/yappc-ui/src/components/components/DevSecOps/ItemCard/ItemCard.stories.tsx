/**
 * ItemCard Storybook Stories
 *
 * @module DevSecOps/ItemCard/stories
 */

import type { Meta, StoryObj } from '@storybook/react';

// Small local noop used in stories when interactive callbacks are required
const fn = () => () => {};


import { ItemCard } from './ItemCard';
import { devsecopsTheme } from '../../../theme/devsecops-theme';

const meta: Meta<typeof ItemCard> = {
  title: 'DevSecOps/ItemCard',
  component: ItemCard,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider theme={devsecopsTheme}>
        <div style={{ maxWidth: 300 }}>
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
  args: {
    onSelect: fn(),
  },
  parameters: {
    docs: {
      description: {
        component:
          'A card component for displaying canvas items with priority, status, owners, and progress information.',
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof ItemCard>;

/**
 * Default item card
 */
export const Default: Story = {
  args: {
    item: {
      id: '123',
      title: 'User Authentication',
      priority: 'high',
      owners: ['John Doe'],
      progress: 75,
      artifacts: [
        { id: '1', type: 'diagram', title: 'Architecture' },
        { id: '2', type: 'doc', title: 'Requirements' },
      ],
    },
  },
};

/**
 * Critical priority item
 */
export const CriticalPriority: Story = {
  args: {
    item: {
      id: '456',
      title: 'Security Vulnerability Fix',
      priority: 'critical',
      owners: ['Security Team'],
      progress: 40,
      artifacts: [{ id: '1', type: 'report', title: 'Security Audit' }],
    },
  },
};

/**
 * Low priority item
 */
export const LowPriority: Story = {
  args: {
    item: {
      id: '789',
      title: 'Update Documentation',
      priority: 'low',
      owners: ['Jane Smith'],
      progress: 25,
    },
  },
};

/**
 * Item in progress
 */
export const InProgress: Story = {
  args: {
    item: {
      id: '234',
      title: 'API Integration',
      priority: 'medium',
      owners: ['Bob Wilson'],
      progress: 50,
      artifacts: [
        { id: '1', type: 'code', title: 'API Client' },
        { id: '2', type: 'test', title: 'Integration Tests' },
        { id: '3', type: 'doc', title: 'API Docs' },
      ],
    },
  },
};

/**
 * Not started yet
 */
export const NotStarted: Story = {
  args: {
    item: {
      id: '345',
      title: 'Performance Optimization',
      priority: 'medium',
      owners: ['Performance Team'],
      progress: 0,
    },
  },
};

/**
 * Nearly complete
 */
export const NearlyComplete: Story = {
  args: {
    item: {
      id: '567',
      title: 'Database Migration',
      priority: 'high',
      owners: ['Database Admin'],
      progress: 95,
      artifacts: [
        { id: '1', type: 'script', title: 'Migration Script' },
        { id: '2', type: 'test', title: 'Rollback Plan' },
      ],
    },
  },
};

/**
 * Selected state
 */
export const Selected: Story = {
  args: {
    item: {
      id: '678',
      title: 'Payment Gateway Integration',
      priority: 'critical',
      owners: ['Alice Johnson'],
      progress: 60,
      artifacts: [
        { id: '1', type: 'diagram', title: 'Flow Diagram' },
        { id: '2', type: 'code', title: 'Integration Code' },
      ],
    },
    selected: true,
  },
};

/**
 * Item with long title
 */
export const LongTitle: Story = {
  args: {
    item: {
      id: '890',
      title: 'Implement Advanced Multi-Factor Authentication System with Biometric Support',
      priority: 'high',
      owners: ['Security Engineering Team'],
      progress: 35,
    },
  },
};

/**
 * Multiple owners (shows first)
 */
export const MultipleOwners: Story = {
  args: {
    item: {
      id: '901',
      title: 'Frontend Redesign',
      priority: 'medium',
      owners: ['Alice Johnson', 'Bob Smith', 'Carol Davis'],
      progress: 80,
      artifacts: [
        { id: '1', type: 'design', title: 'Mockups' },
        { id: '2', type: 'code', title: 'Components' },
      ],
    },
  },
};

/**
 * Many artifacts
 */
export const ManyArtifacts: Story = {
  args: {
    item: {
      id: '012',
      title: 'Microservices Architecture',
      priority: 'high',
      owners: ['Architecture Team'],
      progress: 65,
      artifacts: [
        { id: '1', type: 'diagram', title: 'System Architecture' },
        { id: '2', type: 'diagram', title: 'Service Map' },
        { id: '3', type: 'doc', title: 'Design Doc' },
        { id: '4', type: 'doc', title: 'API Specs' },
        { id: '5', type: 'code', title: 'Shared Libraries' },
        { id: '6', type: 'test', title: 'Integration Tests' },
      ],
    },
  },
};
