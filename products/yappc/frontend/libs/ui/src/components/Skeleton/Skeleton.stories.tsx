import { Skeleton } from './Skeleton';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Skeleton> = {
  title: 'Components/Skeleton',
  component: Skeleton,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['text', 'circular', 'rectangular'],
    },
    animation: {
      control: 'boolean',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Skeleton>;

/**
 * Default text skeleton
 */
export const Default: Story = {
  args: {
    variant: 'text',
    width: 200,
  },
};

/**
 * All variants
 */
export const Variants: Story = {
  render: () => (
    <div className="flex flex-col gap-6">
      <div>
        <div className="text-sm font-medium mb-2">Text</div>
        <Skeleton variant="text" width={200} />
      </div>
      
      <div>
        <div className="text-sm font-medium mb-2">Circular (Avatar)</div>
        <Skeleton variant="circular" width={40} height={40} />
      </div>
      
      <div>
        <div className="text-sm font-medium mb-2">Rectangular (Image)</div>
        <Skeleton variant="rectangular" width={300} height={200} />
      </div>
    </div>
  ),
};

/**
 * Different sizes
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <Skeleton variant="text" width={100} />
      <Skeleton variant="text" width={200} />
      <Skeleton variant="text" width={300} />
      <Skeleton variant="text" width="100%" />
    </div>
  ),
};

/**
 * Multiple text lines
 */
export const TextLines: Story = {
  render: () => (
    <div className="flex flex-col gap-2" style={{ width: 400 }}>
      <Skeleton variant="text" width="100%" />
      <Skeleton variant="text" width="100%" />
      <Skeleton variant="text" width="80%" />
    </div>
  ),
};

/**
 * Avatar skeleton (circular)
 */
export const Avatar: Story = {
  render: () => (
    <div className="flex items-center gap-4">
      <Skeleton variant="circular" width={32} height={32} />
      <Skeleton variant="circular" width={40} height={40} />
      <Skeleton variant="circular" width={56} height={56} />
      <Skeleton variant="circular" width={80} height={80} />
    </div>
  ),
};

/**
 * Card skeleton
 */
export const Card: Story = {
  render: () => (
    <div className="border rounded-lg p-4 w-80">
      <div className="flex items-center gap-3 mb-4">
        <Skeleton variant="circular" width={40} height={40} />
        <div className="flex-1">
          <Skeleton variant="text" width="60%" height={16} className="mb-2" />
          <Skeleton variant="text" width="40%" height={12} />
        </div>
      </div>
      
      <Skeleton variant="rectangular" width="100%" height={200} className="mb-4" />
      
      <Skeleton variant="text" width="100%" className="mb-2" />
      <Skeleton variant="text" width="100%" className="mb-2" />
      <Skeleton variant="text" width="70%" />
    </div>
  ),
};

/**
 * List item skeleton
 */
export const ListItem: Story = {
  render: () => (
    <div className="w-96 space-y-4">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton variant="circular" width={40} height={40} />
          <div className="flex-1">
            <Skeleton variant="text" width="70%" height={16} className="mb-2" />
            <Skeleton variant="text" width="50%" height={12} />
          </div>
        </div>
      ))}
    </div>
  ),
};

/**
 * Table skeleton
 */
export const Table: Story = {
  render: () => (
    <div className="w-full max-w-2xl">
      {/* Header */}
      <div className="grid grid-cols-3 gap-4 mb-4 pb-2 border-b">
        <Skeleton variant="text" width="60%" height={14} />
        <Skeleton variant="text" width="50%" height={14} />
        <Skeleton variant="text" width="40%" height={14} />
      </div>
      
      {/* Rows */}
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="grid grid-cols-3 gap-4 mb-3">
          <Skeleton variant="text" width="80%" height={16} />
          <Skeleton variant="text" width="70%" height={16} />
          <Skeleton variant="text" width="60%" height={16} />
        </div>
      ))}
    </div>
  ),
};

/**
 * Form skeleton
 */
export const Form: Story = {
  render: () => (
    <div className="w-96 space-y-4">
      {/* Field 1 */}
      <div>
        <Skeleton variant="text" width={100} height={14} className="mb-2" />
        <Skeleton variant="rectangular" width="100%" height={40} />
      </div>
      
      {/* Field 2 */}
      <div>
        <Skeleton variant="text" width={120} height={14} className="mb-2" />
        <Skeleton variant="rectangular" width="100%" height={40} />
      </div>
      
      {/* Field 3 */}
      <div>
        <Skeleton variant="text" width={80} height={14} className="mb-2" />
        <Skeleton variant="rectangular" width="100%" height={80} />
      </div>
      
      {/* Button */}
      <Skeleton variant="rectangular" width={120} height={40} />
    </div>
  ),
};

/**
 * Blog post skeleton
 */
export const BlogPost: Story = {
  render: () => (
    <div className="max-w-2xl">
      {/* Title */}
      <Skeleton variant="text" width="80%" height={32} className="mb-4" />
      
      {/* Meta */}
      <div className="flex items-center gap-3 mb-6">
        <Skeleton variant="circular" width={40} height={40} />
        <div className="flex-1">
          <Skeleton variant="text" width={120} height={14} className="mb-1" />
          <Skeleton variant="text" width={80} height={12} />
        </div>
      </div>
      
      {/* Featured image */}
      <Skeleton variant="rectangular" width="100%" height={400} className="mb-6" />
      
      {/* Content */}
      <div className="space-y-2">
        <Skeleton variant="text" width="100%" />
        <Skeleton variant="text" width="100%" />
        <Skeleton variant="text" width="100%" />
        <Skeleton variant="text" width="95%" />
        <Skeleton variant="text" width="100%" />
        <Skeleton variant="text" width="90%" />
      </div>
    </div>
  ),
};

/**
 * No animation
 */
export const NoAnimation: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <Skeleton variant="text" width={200} animation={false} />
      <Skeleton variant="circular" width={40} height={40} animation={false} />
      <Skeleton variant="rectangular" width={300} height={200} animation={false} />
    </div>
  ),
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => (
    <div className="dark">
      <div className="flex flex-col gap-6">
        <Skeleton variant="text" width={200} />
        <Skeleton variant="circular" width={40} height={40} />
        <Skeleton variant="rectangular" width={300} height={200} />
        
        <div className="border border-grey-700 rounded-lg p-4 w-80">
          <div className="flex items-center gap-3 mb-4">
            <Skeleton variant="circular" width={40} height={40} />
            <div className="flex-1">
              <Skeleton variant="text" width="60%" height={16} className="mb-2" />
              <Skeleton variant="text" width="40%" height={12} />
            </div>
          </div>
          
          <Skeleton variant="rectangular" width="100%" height={200} className="mb-4" />
          
          <Skeleton variant="text" width="100%" className="mb-2" />
          <Skeleton variant="text" width="100%" className="mb-2" />
          <Skeleton variant="text" width="70%" />
        </div>
      </div>
    </div>
  ),
};
