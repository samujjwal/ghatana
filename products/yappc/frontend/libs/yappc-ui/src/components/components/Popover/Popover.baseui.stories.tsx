import { useState } from 'react';

import { Popover, PopoverClose } from './Popover.baseui';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Popover> = {
  title: 'Components/Advanced/Popover',
  component: Popover,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Popover component for displaying floating content. Built on Base UI Popover with positioning and animations.',
      },
    },
  },
  argTypes: {
    placement: {
      control: 'select',
      options: [
        'top',
        'top-start',
        'top-end',
        'right',
        'right-start',
        'right-end',
        'bottom',
        'bottom-start',
        'bottom-end',
        'left',
        'left-start',
        'left-end',
      ],
      description: 'Placement of the popover relative to trigger',
    },
    showArrow: {
      control: 'boolean',
      description: 'Show arrow pointing to trigger',
    },
    offset: {
      control: 'number',
      description: 'Offset from trigger in pixels',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Popover>;

/**
 * Default popover with basic content
 */
export const Default: Story = {
  render: () => (
    <Popover trigger={<Button>Open Popover</Button>}>
      <p>This is the popover content. It appears when you click the trigger button.</p>
    </Popover>
  ),
};

/**
 * All placement positions
 */
export const Placements: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-8 p-16">
      <Popover trigger={<Button size="sm">Top Start</Button>} placement="top-start">
        Top Start
      </Popover>
      <Popover trigger={<Button size="sm">Top</Button>} placement="top">
        Top
      </Popover>
      <Popover trigger={<Button size="sm">Top End</Button>} placement="top-end">
        Top End
      </Popover>

      <Popover trigger={<Button size="sm">Left Start</Button>} placement="left-start">
        Left Start
      </Popover>
      <div className="text-center text-grey-500 text-sm py-4">Trigger Button</div>
      <Popover trigger={<Button size="sm">Right Start</Button>} placement="right-start">
        Right Start
      </Popover>

      <Popover trigger={<Button size="sm">Left</Button>} placement="left">
        Left
      </Popover>
      <div />
      <Popover trigger={<Button size="sm">Right</Button>} placement="right">
        Right
      </Popover>

      <Popover trigger={<Button size="sm">Left End</Button>} placement="left-end">
        Left End
      </Popover>
      <div />
      <Popover trigger={<Button size="sm">Right End</Button>} placement="right-end">
        Right End
      </Popover>

      <Popover trigger={<Button size="sm">Bottom Start</Button>} placement="bottom-start">
        Bottom Start
      </Popover>
      <Popover trigger={<Button size="sm">Bottom</Button>} placement="bottom">
        Bottom (Default)
      </Popover>
      <Popover trigger={<Button size="sm">Bottom End</Button>} placement="bottom-end">
        Bottom End
      </Popover>
    </div>
  ),
};

/**
 * Popover with title and description
 */
export const WithTitleAndDescription: Story = {
  render: () => (
    <Popover
      trigger={<Button>User Info</Button>}
      title="John Doe"
      description="Software Engineer at YAPPC"
    >
      <div className="space-y-2 text-sm">
        <div>
          <strong>Email:</strong> john.doe@example.com
        </div>
        <div>
          <strong>Location:</strong> San Francisco, CA
        </div>
        <div>
          <strong>Joined:</strong> January 2024
        </div>
      </div>
    </Popover>
  ),
};

/**
 * Popover without arrow
 */
export const NoArrow: Story = {
  render: () => (
    <Popover trigger={<Button>No Arrow</Button>} showArrow={false}>
      <p>This popover has no arrow pointer.</p>
    </Popover>
  ),
};

/**
 * Popover with custom offset
 */
export const CustomOffset: Story = {
  render: () => (
    <div className="flex gap-4">
      <Popover trigger={<Button>Small Offset (4px)</Button>} offset={4}>
        Close to trigger
      </Popover>
      <Popover trigger={<Button>Default (8px)</Button>}>
        Default offset
      </Popover>
      <Popover trigger={<Button>Large Offset (16px)</Button>} offset={16}>
        Far from trigger
      </Popover>
    </div>
  ),
};

/**
 * Controlled popover
 */
export const Controlled: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <div className="space-y-4">
        <div className="text-sm text-grey-600">
          Popover is: <strong>{open ? 'Open' : 'Closed'}</strong>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => setOpen(true)}>Open</Button>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Close
          </Button>
        </div>
        <Popover
          open={open}
          onOpenChange={setOpen}
          trigger={<Button>Toggle Programmatically</Button>}
        >
          <p>This popover is controlled by external state.</p>
          <p className="mt-2 text-sm">Click the buttons above or the trigger to toggle.</p>
        </Popover>
      </div>
    );
  },
};

/**
 * Popover with close button
 */
export const WithCloseButton: Story = {
  render: () => (
    <Popover
      trigger={<Button>Open with Close Button</Button>}
      title="Notification"
    >
      <p className="mb-3">This is an important notification that you can dismiss.</p>
      <PopoverClose>
        <Button size="sm" variant="outline" className="w-full">
          Dismiss
        </Button>
      </PopoverClose>
    </Popover>
  ),
};

/**
 * Rich content popover
 */
export const RichContent: Story = {
  render: () => (
    <Popover
      trigger={<Button>View Details</Button>}
      title="Product Information"
      placement="right"
      className="max-w-md"
    >
      <div className="space-y-3">
        <img
          src="https://via.placeholder.com/300x150"
          alt="Product"
          className="w-full rounded-lg"
        />
        <h4 className="font-semibold">Premium Widget</h4>
        <p className="text-sm">
          A high-quality widget with advanced features and exceptional performance.
        </p>
        <div className="flex items-center justify-between pt-2">
          <span className="text-lg font-bold text-primary-600">$299</span>
          <Button size="sm">Add to Cart</Button>
        </div>
      </div>
    </Popover>
  ),
};

/**
 * Form in popover
 */
export const FormPopover: Story = {
  render: () => (
    <Popover
      trigger={<Button>Quick Feedback</Button>}
      title="Send Feedback"
      className="w-80"
    >
      <form className="space-y-3">
        <div>
          <label className="block text-sm font-medium mb-1">Your Name</label>
          <input
            type="text"
            className="w-full px-3 py-2 border border-grey-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="Enter your name"
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Feedback</label>
          <textarea
            className="w-full px-3 py-2 border border-grey-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            rows={3}
            placeholder="Your feedback..."
          />
        </div>
        <PopoverClose>
          <Button size="sm" className="w-full">
            Submit
          </Button>
        </PopoverClose>
      </form>
    </Popover>
  ),
};

/**
 * Menu-style popover
 */
export const MenuStyle: Story = {
  render: () => (
    <Popover
      trigger={<Button>Actions</Button>}
      showArrow={false}
      className="p-2 min-w-[160px]"
    >
      <div className="flex flex-col">
        <button className="px-3 py-2 text-left text-sm hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors">
          Edit
        </button>
        <button className="px-3 py-2 text-left text-sm hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors">
          Duplicate
        </button>
        <button className="px-3 py-2 text-left text-sm hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors">
          Archive
        </button>
        <hr className="my-1 border-grey-200 dark:border-grey-700" />
        <button className="px-3 py-2 text-left text-sm text-red-600 hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors">
          Delete
        </button>
      </div>
    </Popover>
  ),
};

/**
 * Help tooltip style
 */
export const HelpTooltip: Story = {
  render: () => (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <label>Email Address</label>
        <Popover
          trigger={
            <button className="text-grey-400 hover:text-grey-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </button>
          }
          placement="top"
          className="max-w-xs"
        >
          <p className="text-xs">
            Enter your email address. We'll never share it with anyone else.
          </p>
        </Popover>
      </div>
      <input
        type="email"
        className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
        placeholder="you@example.com"
      />
    </div>
  ),
};

/**
 * Nested popovers
 */
export const Nested: Story = {
  render: () => (
    <Popover
      trigger={<Button>Open First Popover</Button>}
      title="First Level"
    >
      <p className="mb-3">This is the first popover. You can nest another popover inside:</p>
      <Popover
        trigger={<Button size="sm">Open Second Popover</Button>}
        placement="right"
        title="Second Level"
      >
        <p>This is a nested popover!</p>
      </Popover>
    </Popover>
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
      <Popover
        trigger={<Button>Dark Mode Popover</Button>}
        title="Dark Theme"
        description="This popover automatically adapts to dark mode"
      >
        <p>All colors and styles are theme-aware and adjust for optimal contrast in dark mode.</p>
      </Popover>
    </div>
  ),
};
