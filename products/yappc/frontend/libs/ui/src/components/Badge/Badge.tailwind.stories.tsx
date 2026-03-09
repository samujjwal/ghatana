import { Badge } from './Badge.tailwind';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Badge (Tailwind)',
  component: Badge,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Badge>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Badge badgeContent={4}>
      <div className="w-10 h-10 bg-grey-300 rounded" />
    </Badge>
  ),
};

export const Colors: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge badgeContent={4} color="primary">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} color="secondary">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} color="error">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} color="warning">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} color="info">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} color="success">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge badgeContent={4} size="small">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} size="medium">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} size="large">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
    </div>
  ),
};

export const DotVariant: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge variant="dot" color="primary">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge variant="dot" color="error">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge variant="dot" color="success">
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
    </div>
  ),
};

export const MaxValue: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge badgeContent={99}>
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={100} max={99}>
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={1000} max={999}>
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
    </div>
  ),
};

export const AnchorPositions: Story = {
  render: () => (
    <div className="flex gap-8 items-center">
      <Badge badgeContent={4} anchorOrigin="top-right">
        <div className="w-12 h-12 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} anchorOrigin="top-left">
        <div className="w-12 h-12 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} anchorOrigin="bottom-right">
        <div className="w-12 h-12 bg-grey-300 rounded" />
      </Badge>
      <Badge badgeContent={4} anchorOrigin="bottom-left">
        <div className="w-12 h-12 bg-grey-300 rounded" />
      </Badge>
    </div>
  ),
};

export const WithPulse: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge badgeContent={4} pulse>
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
      <Badge variant="dot" color="error" pulse>
        <div className="w-10 h-10 bg-grey-300 rounded" />
      </Badge>
    </div>
  ),
};

export const ShowZero: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge badgeContent={0}>
        <div className="w-10 h-10 bg-grey-300 rounded">
          <span className="text-xs">Hidden</span>
        </div>
      </Badge>
      <Badge badgeContent={0} showZero>
        <div className="w-10 h-10 bg-grey-300 rounded">
          <span className="text-xs">Shown</span>
        </div>
      </Badge>
    </div>
  ),
};

export const Standalone: Story = {
  render: () => (
    <div className="flex gap-4 items-center">
      <Badge badgeContent={4} color="primary" />
      <Badge badgeContent={10} color="error" />
      <Badge badgeContent="New" color="success" />
      <Badge variant="dot" color="warning" />
    </div>
  ),
};

export const NotificationExample: Story = {
  render: () => (
    <div className="flex gap-6 items-center">
      <Badge badgeContent={5} color="error">
        <svg className="w-6 h-6 text-grey-700" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
      </Badge>
      
      <Badge badgeContent={12} color="primary">
        <svg className="w-6 h-6 text-grey-700" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
      </Badge>
      
      <Badge variant="dot" color="success">
        <div className="w-10 h-10 rounded-full bg-grey-300 flex items-center justify-center text-sm font-semibold">
          JD
        </div>
      </Badge>
    </div>
  ),
};

export const WithButton: Story = {
  render: () => (
    <div className="flex gap-4">
      <Badge badgeContent={4} color="error">
        <Button>Cart</Button>
      </Badge>
      <Badge badgeContent={99} color="primary" max={99}>
        <Button variant="outline">Messages</Button>
      </Badge>
    </div>
  ),
};

export const Playground: Story = {
  args: {
    badgeContent: 4,
    color: 'primary',
    size: 'medium',
    variant: 'standard',
    showZero: false,
    max: 99,
    anchorOrigin: 'top-right',
    pulse: false,
    invisible: false,
    children: <div className="w-10 h-10 bg-grey-300 rounded" />,
  },
};
