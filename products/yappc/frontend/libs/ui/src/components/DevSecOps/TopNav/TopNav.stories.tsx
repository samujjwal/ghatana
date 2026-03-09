/**
 * TopNav Storybook Stories
 *
 * @module DevSecOps/TopNav/stories
 */

// Small local noop used in stories when interactive callbacks are required
const fn = () => () => {};

import { TopNav } from './TopNav';
import { devsecopsTheme } from '../../../theme/devsecops-theme';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof TopNav> = {
  title: 'DevSecOps/TopNav',
  component: TopNav,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider theme={devsecopsTheme}>
        <Story />
      </ThemeProvider>
    ),
  ],
  args: {
    onNavigate: fn(),
    onProfileClick: fn(),
    onNotificationsClick: fn(),
  },
  parameters: {
    docs: {
      description: {
        component:
          'Primary navigation bar for the DevSecOps Canvas with navigation links, notifications, and user profile.',
      },
    },
    layout: 'fullscreen',
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof TopNav>;

/**
 * Default TopNav with minimal configuration
 */
export const Default: Story = {
  args: {
    currentPage: 'dashboard',
  },
};

/**
 * TopNav with user profile
 */
export const WithUser: Story = {
  args: {
    currentPage: 'dashboard',
    user: {
      name: 'Jane Smith',
      role: 'Developer',
    },
  },
};

/**
 * TopNav with user avatar
 */
export const WithAvatar: Story = {
  args: {
    currentPage: 'phases',
    user: {
      name: 'John Doe',
      avatar: 'https://i.pravatar.cc/150?img=12',
      role: 'PM',
    },
  },
};

/**
 * TopNav with notifications
 */
export const WithNotifications: Story = {
  args: {
    currentPage: 'dashboard',
    notificationCount: 5,
    user: {
      name: 'Alice Johnson',
      role: 'Security',
    },
  },
};

/**
 * TopNav on Reports page
 */
export const ReportsPage: Story = {
  args: {
    currentPage: 'reports',
    notificationCount: 12,
    user: {
      name: 'Bob Williams',
      avatar: 'https://i.pravatar.cc/150?img=33',
      role: 'Executive',
    },
  },
};

/**
 * TopNav with maximum notifications
 */
export const ManyNotifications: Story = {
  args: {
    currentPage: 'dashboard',
    notificationCount: 99,
    user: {
      name: 'Carol Martinez',
      role: 'DevOps',
    },
  },
};

/**
 * TopNav on Settings page
 */
export const SettingsPage: Story = {
  args: {
    currentPage: 'settings',
    user: {
      name: 'David Chen',
      avatar: 'https://i.pravatar.cc/150?img=68',
      role: 'QA',
    },
  },
};
