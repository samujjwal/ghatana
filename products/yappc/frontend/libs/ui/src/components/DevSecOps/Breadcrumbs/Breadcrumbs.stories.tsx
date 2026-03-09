/**
 * Breadcrumbs Storybook Stories
 *
 * @module DevSecOps/Breadcrumbs/stories
 */

import type { Meta, StoryObj } from '@storybook/react';

// Small local noop used in stories when interactive callbacks are required
const fn = () => () => {};


import { Breadcrumbs } from './Breadcrumbs';
import { devsecopsTheme } from '../../../theme/devsecops-theme';

const meta: Meta<typeof Breadcrumbs> = {
  title: 'DevSecOps/Breadcrumbs',
  component: Breadcrumbs,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider theme={devsecopsTheme}>
        <Story />
      </ThemeProvider>
    ),
  ],
  parameters: {
    docs: {
      description: {
        component:
          'Navigation breadcrumb trail showing the current page location within the DevSecOps Canvas.',
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Breadcrumbs>;

/**
 * Default breadcrumb with basic navigation
 */
export const Default: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'DevSecOps', href: '/devsecops' },
      { label: 'Dashboard' },
    ],
  },
};

/**
 * Breadcrumb with click handlers
 */
export const WithClickHandlers: Story = {
  args: {
    items: [
      { label: 'Home', onClick: fn() },
      { label: 'DevSecOps', onClick: fn() },
      { label: 'Planning Phase', onClick: fn() },
      { label: 'Current Item' },
    ],
  },
};

/**
 * Deep navigation path
 */
export const DeepNavigation: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'DevSecOps', href: '/devsecops' },
      { label: 'Phases', href: '/devsecops/phases' },
      { label: 'Planning', href: '/devsecops/phases/planning' },
      { label: 'Items', href: '/devsecops/phases/planning/items' },
      { label: 'Feature #123' },
    ],
  },
};

/**
 * Breadcrumb with custom separator
 */
export const CustomSeparator: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'DevSecOps', href: '/devsecops' },
      { label: 'Reports' },
    ],
    separator: '/',
  },
};

/**
 * Very long path with maxItems
 */
export const LongPathCollapsed: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'DevSecOps', href: '/devsecops' },
      { label: 'Phases', href: '/devsecops/phases' },
      { label: 'Development', href: '/devsecops/phases/development' },
      { label: 'Items', href: '/devsecops/phases/development/items' },
      { label: 'Backend', href: '/devsecops/phases/development/items/backend' },
      { label: 'API', href: '/devsecops/phases/development/items/backend/api' },
      { label: 'Authentication Service' },
    ],
    maxItems: 4,
  },
  parameters: {
    docs: {
      description: {
        story:
          'When the path is very long, breadcrumbs will automatically collapse with maxItems prop.',
      },
    },
  },
};

/**
 * Single page (no navigation)
 */
export const SingleItem: Story = {
  args: {
    items: [{ label: 'Dashboard' }],
  },
};

/**
 * Two-level navigation
 */
export const TwoLevels: Story = {
  args: {
    items: [
      { label: 'DevSecOps', href: '/devsecops' },
      { label: 'Settings' },
    ],
  },
};
