import { useState } from 'react';

import { Drawer, DrawerTrigger, DrawerClose } from './Drawer.baseui';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Drawer> = {
  title: 'Components/Advanced/Drawer',
  component: Drawer,
  tags: ['autodocs'],
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'Drawer is a slide-in panel component for navigation or content. Built on Base UI Dialog with slide animations.',
      },
    },
  },
  argTypes: {
    open: {
      control: 'boolean',
      description: 'Whether the drawer is open',
    },
    position: {
      control: 'select',
      options: ['left', 'right', 'top', 'bottom'],
      description: 'Position of the drawer',
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg', 'full'],
      description: 'Size of the drawer',
    },
    showCloseButton: {
      control: 'boolean',
      description: 'Show close button in header',
    },
    showBackdrop: {
      control: 'boolean',
      description: 'Show backdrop overlay',
    },
    closeOnBackdropClick: {
      control: 'boolean',
      description: 'Close when clicking backdrop',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Drawer>;

/**
 * Default Drawer opening from the right side
 */
export const Default: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Drawer</Button>
        <Drawer open={open} onOpenChange={setOpen} header="Drawer Title">
          <p>This is the drawer content. It slides in from the right by default.</p>
          <p className="mt-4">You can put any content here.</p>
        </Drawer>
      </>
    );
  },
};

/**
 * Drawer positions - left, right, top, bottom
 */
export const Positions: Story = {
  render: () => {
    const [openLeft, setOpenLeft] = useState(false);
    const [openRight, setOpenRight] = useState(false);
    const [openTop, setOpenTop] = useState(false);
    const [openBottom, setOpenBottom] = useState(false);

    return (
      <div className="flex flex-wrap gap-4 p-8">
        <Button onClick={() => setOpenLeft(true)}>Open Left</Button>
        <Button onClick={() => setOpenRight(true)}>Open Right</Button>
        <Button onClick={() => setOpenTop(true)}>Open Top</Button>
        <Button onClick={() => setOpenBottom(true)}>Open Bottom</Button>

        <Drawer open={openLeft} onOpenChange={setOpenLeft} position="left" header="Left Drawer">
          <p>This drawer slides in from the left.</p>
        </Drawer>

        <Drawer open={openRight} onOpenChange={setOpenRight} position="right" header="Right Drawer">
          <p>This drawer slides in from the right.</p>
        </Drawer>

        <Drawer open={openTop} onOpenChange={setOpenTop} position="top" header="Top Drawer">
          <p>This drawer slides in from the top.</p>
        </Drawer>

        <Drawer open={openBottom} onOpenChange={setOpenBottom} position="bottom" header="Bottom Drawer">
          <p>This drawer slides in from the bottom.</p>
        </Drawer>
      </div>
    );
  },
};

/**
 * Different drawer sizes - sm, md, lg, full
 */
export const Sizes: Story = {
  render: () => {
    const [openSm, setOpenSm] = useState(false);
    const [openMd, setOpenMd] = useState(false);
    const [openLg, setOpenLg] = useState(false);
    const [openFull, setOpenFull] = useState(false);

    return (
      <div className="flex flex-wrap gap-4 p-8">
        <Button onClick={() => setOpenSm(true)}>Small (320px)</Button>
        <Button onClick={() => setOpenMd(true)}>Medium (384px)</Button>
        <Button onClick={() => setOpenLg(true)}>Large (512px)</Button>
        <Button onClick={() => setOpenFull(true)}>Full Width</Button>

        <Drawer open={openSm} onOpenChange={setOpenSm} size="sm" header="Small Drawer">
          <p>This is a small drawer (320px wide).</p>
        </Drawer>

        <Drawer open={openMd} onOpenChange={setOpenMd} size="md" header="Medium Drawer">
          <p>This is a medium drawer (384px wide) - default size.</p>
        </Drawer>

        <Drawer open={openLg} onOpenChange={setOpenLg} size="lg" header="Large Drawer">
          <p>This is a large drawer (512px wide).</p>
        </Drawer>

        <Drawer open={openFull} onOpenChange={setOpenFull} size="full" header="Full Width Drawer">
          <p>This drawer takes up the full screen width.</p>
        </Drawer>
      </div>
    );
  },
};

/**
 * Drawer with header, content, and footer
 */
export const WithFooter: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Drawer with Footer</Button>
        <Drawer
          open={open}
          onOpenChange={setOpen}
          header="Confirm Action"
          footer={
            <div className="flex gap-2 justify-end">
              <Button variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button variant="solid">Confirm</Button>
            </div>
          }
        >
          <p>Are you sure you want to proceed with this action?</p>
          <p className="mt-4">This action cannot be undone.</p>
        </Drawer>
      </>
    );
  },
};

/**
 * Navigation drawer example
 */
export const NavigationDrawer: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Navigation</Button>
        <Drawer open={open} onOpenChange={setOpen} position="left" size="sm" header="Navigation">
          <nav className="flex flex-col gap-2">
            <a href="#" className="px-4 py-3 hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors">
              <div className="flex items-center gap-3">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
                  />
                </svg>
                <span>Home</span>
              </div>
            </a>
            <a href="#" className="px-4 py-3 hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors">
              <div className="flex items-center gap-3">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                  />
                </svg>
                <span>Dashboard</span>
              </div>
            </a>
            <a href="#" className="px-4 py-3 hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors">
              <div className="flex items-center gap-3">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"
                  />
                </svg>
                <span>Projects</span>
              </div>
            </a>
            <a href="#" className="px-4 py-3 hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors">
              <div className="flex items-center gap-3">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                  />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                <span>Settings</span>
              </div>
            </a>
          </nav>
        </Drawer>
      </>
    );
  },
};

/**
 * Scrollable content drawer
 */
export const ScrollableContent: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Long Content</Button>
        <Drawer open={open} onOpenChange={setOpen} header="Long Content">
          <div className="space-y-4">
            {Array.from({ length: 30 }).map((_, i) => (
              <p key={i}>
                This is paragraph {i + 1}. The drawer content is scrollable when it exceeds the viewport height.
              </p>
            ))}
          </div>
        </Drawer>
      </>
    );
  },
};

/**
 * Form in drawer
 */
export const FormDrawer: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Add New Item</Button>
        <Drawer
          open={open}
          onOpenChange={setOpen}
          header="Add New Item"
          footer={
            <div className="flex gap-2 justify-end">
              <Button variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button variant="solid">Save</Button>
            </div>
          }
        >
          <form className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1">Name</label>
              <input
                type="text"
                className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
                placeholder="Enter name"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1">Description</label>
              <textarea
                className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
                rows={4}
                placeholder="Enter description"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1">Category</label>
              <select className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500">
                <option>Option 1</option>
                <option>Option 2</option>
                <option>Option 3</option>
              </select>
            </div>
          </form>
        </Drawer>
      </>
    );
  },
};

/**
 * Drawer without close button
 */
export const NoCloseButton: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open (No Close Button)</Button>
        <Drawer
          open={open}
          onOpenChange={setOpen}
          header="No Close Button"
          showCloseButton={false}
          footer={
            <Button variant="solid" onClick={() => setOpen(false)} className="w-full">
              Done
            </Button>
          }
        >
          <p>This drawer has no close button in the header. Close it using the button in the footer or backdrop.</p>
        </Drawer>
      </>
    );
  },
};

/**
 * Drawer without backdrop
 */
export const NoBackdrop: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open (No Backdrop)</Button>
        <Drawer open={open} onOpenChange={setOpen} header="No Backdrop" showBackdrop={false}>
          <p>This drawer has no backdrop overlay. You can interact with the page behind it.</p>
        </Drawer>
      </>
    );
  },
};

/**
 * Prevent closing on backdrop click
 */
export const NoBackdropClose: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open (No Backdrop Close)</Button>
        <Drawer
          open={open}
          onOpenChange={setOpen}
          header="Cannot Close by Backdrop"
          closeOnBackdropClick={false}
          footer={
            <Button variant="solid" onClick={() => setOpen(false)} className="w-full">
              Close Drawer
            </Button>
          }
        >
          <p>Clicking the backdrop will not close this drawer. You must use the close button or footer button.</p>
        </Drawer>
      </>
    );
  },
};

/**
 * Keyboard accessibility demonstration
 */
export const KeyboardAccessible: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Drawer (Try ESC key)</Button>
        <Drawer open={open} onOpenChange={setOpen} header="Keyboard Accessible">
          <p>This drawer is fully keyboard accessible:</p>
          <ul className="list-disc list-inside mt-2 space-y-1">
            <li>Press ESC key to close</li>
            <li>Tab through interactive elements</li>
            <li>Close button is focusable</li>
            <li>Focus is trapped within the drawer</li>
          </ul>
        </Drawer>
      </>
    );
  },
};

/**
 * Dark mode drawer
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <div className="dark">
        <Button onClick={() => setOpen(true)}>Open Dark Drawer</Button>
        <Drawer open={open} onOpenChange={setOpen} header="Dark Mode Drawer">
          <p>This drawer adapts to dark mode automatically.</p>
          <p className="mt-4">The background, text, and borders all adjust for dark theme.</p>
        </Drawer>
      </div>
    );
  },
};
