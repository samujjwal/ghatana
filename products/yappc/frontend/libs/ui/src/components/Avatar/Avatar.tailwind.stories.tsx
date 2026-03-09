import { Avatar, AvatarGroup } from './Avatar.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Avatar (Tailwind)',
  component: Avatar,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Avatar>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    alt: 'John Doe',
    size: 'medium',
    variant: 'circular',
    color: 'info',
  },
};

export const WithImage: Story = {
  args: {
    src: 'https://i.pravatar.cc/150?img=1',
    alt: 'John Doe',
    size: 'medium',
  },
};

export const Sizes: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex gap-4 items-end">
      <Avatar alt="JD" size="small" />
      <Avatar alt="JD" size="medium" />
      <Avatar alt="JD" size="large" />
      <Avatar alt="JD" size="xlarge" />
    </div>
  ),
};

export const Variants: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex gap-4 items-center">
      <Avatar alt="JD" variant="circular" />
      <Avatar alt="JD" variant="rounded" />
      <Avatar alt="JD" variant="square" />
    </div>
  ),
};

export const Colors: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex gap-4 items-center">
      <Avatar alt="PR" color="primary" />
      <Avatar alt="SE" color="secondary" />
      <Avatar alt="ER" color="error" />
      <Avatar alt="WA" color="warning" />
      <Avatar alt="IN" color="info" />
      <Avatar alt="SU" color="success" />
      <Avatar alt="DE" color="default" />
    </div>
  ),
};

export const WithInitials: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex gap-4 items-center">
      <Avatar alt="John Doe" color="primary" />
      <Avatar alt="Jane Smith" color="secondary" />
      <Avatar alt="Bob Wilson" color="success" />
      <Avatar alt="A" color="warning" />
    </div>
  ),
};

export const WithImages: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex gap-4 items-center">
      <Avatar src="https://i.pravatar.cc/150?img=1" alt="User 1" />
      <Avatar src="https://i.pravatar.cc/150?img=2" alt="User 2" />
      <Avatar src="https://i.pravatar.cc/150?img=3" alt="User 3" />
      <Avatar src="https://i.pravatar.cc/150?img=4" alt="User 4" />
    </div>
  ),
};

export const ImageFallback: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex gap-4 items-center">
      <Avatar src="https://invalid-url.com/image.jpg" alt="John Doe" color="primary" />
      <Avatar src="https://invalid-url.com/image.jpg" alt="Jane Smith" color="secondary" />
    </div>
  ),
};

export const Group: Story = {
  args: { alt: 'User' },
  render: () => (
    <AvatarGroup max={3}>
      <Avatar src="https://i.pravatar.cc/150?img=1" alt="User 1" />
      <Avatar src="https://i.pravatar.cc/150?img=2" alt="User 2" />
      <Avatar src="https://i.pravatar.cc/150?img=3" alt="User 3" />
      <Avatar src="https://i.pravatar.cc/150?img=4" alt="User 4" />
      <Avatar src="https://i.pravatar.cc/150?img=5" alt="User 5" />
    </AvatarGroup>
  ),
};

export const GroupSpacing: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex flex-col gap-6">
      <div>
        <p className="text-sm mb-2">Small spacing:</p>
        <AvatarGroup max={5} spacing="small">
          <Avatar src="https://i.pravatar.cc/150?img=1" alt="User 1" />
          <Avatar src="https://i.pravatar.cc/150?img=2" alt="User 2" />
          <Avatar src="https://i.pravatar.cc/150?img=3" alt="User 3" />
          <Avatar src="https://i.pravatar.cc/150?img=4" alt="User 4" />
        </AvatarGroup>
      </div>
      <div>
        <p className="text-sm mb-2">Medium spacing:</p>
        <AvatarGroup max={5} spacing="medium">
          <Avatar src="https://i.pravatar.cc/150?img=1" alt="User 1" />
          <Avatar src="https://i.pravatar.cc/150?img=2" alt="User 2" />
          <Avatar src="https://i.pravatar.cc/150?img=3" alt="User 3" />
          <Avatar src="https://i.pravatar.cc/150?img=4" alt="User 4" />
        </AvatarGroup>
      </div>
      <div>
        <p className="text-sm mb-2">Large spacing:</p>
        <AvatarGroup max={5} spacing="large">
          <Avatar src="https://i.pravatar.cc/150?img=1" alt="User 1" />
          <Avatar src="https://i.pravatar.cc/150?img=2" alt="User 2" />
          <Avatar src="https://i.pravatar.cc/150?img=3" alt="User 3" />
          <Avatar src="https://i.pravatar.cc/150?img=4" alt="User 4" />
        </AvatarGroup>
      </div>
    </div>
  ),
};

export const GroupWithInitials: Story = {
  args: { alt: 'User' },
  render: () => (
    <AvatarGroup max={4}>
      <Avatar alt="John Doe" color="primary" />
      <Avatar alt="Jane Smith" color="secondary" />
      <Avatar alt="Bob Wilson" color="success" />
      <Avatar alt="Alice Brown" color="warning" />
      <Avatar alt="Charlie Davis" color="error" />
      <Avatar alt="Diana Evans" color="info" />
    </AvatarGroup>
  ),
};

export const UserProfile: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="flex items-center gap-3 p-4 border rounded-lg">
      <Avatar 
        src="https://i.pravatar.cc/150?img=5" 
        alt="Sarah Johnson" 
        size="large" 
      />
      <div>
        <p className="font-semibold">Sarah Johnson</p>
        <p className="text-sm text-grey-600">Product Designer</p>
      </div>
    </div>
  ),
};

export const TeamMembers: Story = {
  args: { alt: 'User' },
  render: () => (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <Avatar alt="John Doe" color="primary" size="small" />
        <span className="text-sm">John Doe - Available</span>
      </div>
      <div className="flex items-center gap-3">
        <Avatar alt="Jane Smith" color="success" size="small" />
        <span className="text-sm">Jane Smith - Online</span>
      </div>
      <div className="flex items-center gap-3">
        <Avatar alt="Bob Wilson" color="default" size="small" />
        <span className="text-sm">Bob Wilson - Away</span>
      </div>
    </div>
  ),
};

export const Playground: Story = {
  args: {
    alt: 'John Doe',
    size: 'medium',
    variant: 'circular',
    color: 'primary',
    src: 'https://i.pravatar.cc/150?img=1',
  },
};
