import { useState } from 'react';

import { Dialog } from './Dialog.baseui';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Dialog> = {
  title: 'Components/Dialog',
  component: Dialog,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Dialog>;

/**
 * Default dialog with header, content, and actions
 */
export const Default: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Dialog</Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="Dialog Title"
          actions={
            <>
              <Button variant="ghost" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button variant="solid" onClick={() => setOpen(false)}>
                Confirm
              </Button>
            </>
          }
        >
          <p>This is the dialog content. You can put any content here.</p>
        </Dialog>
      </>
    );
  },
};

/**
 * Dialog sizes from xs (320px) to full (100% viewport)
 */
export const Sizes: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [size, setSize] = useState<'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'full' | null>(null);

    return (
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setSize('xs')}>XS (320px)</Button>
        <Button onClick={() => setSize('sm')}>SM (384px)</Button>
        <Button onClick={() => setSize('md')}>MD (448px)</Button>
        <Button onClick={() => setSize('lg')}>LG (512px)</Button>
        <Button onClick={() => setSize('xl')}>XL (576px)</Button>
        <Button onClick={() => setSize('full')}>Full Width</Button>

        <Dialog
          {...args}
          open={!!size}
          onOpenChange={() => setSize(null)}
          header={`${size?.toUpperCase()} Dialog`}
          size={size || 'md'}
          actions={
            <Button variant="solid" onClick={() => setSize(null)}>
              Close
            </Button>
          }
        >
          <p>This dialog has size: {size}</p>
          <p className="mt-2 text-sm text-grey-600">
            Size controls the max-width of the dialog container.
          </p>
        </Dialog>
      </div>
    );
  },
};

/**
 * Dialog shapes: rounded (8px), soft (16px), square (4px)
 */
export const Shapes: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [shape, setShape] = useState<'rounded' | 'square' | 'soft' | null>(null);

    return (
      <div className="flex gap-2">
        <Button onClick={() => setShape('rounded')}>Rounded (8px)</Button>
        <Button onClick={() => setShape('soft')}>Soft (16px)</Button>
        <Button onClick={() => setShape('square')}>Square (4px)</Button>

        <Dialog
          {...args}
          open={!!shape}
          onOpenChange={() => setShape(null)}
          header={`${shape} Shape`}
          shape={shape || 'rounded'}
          actions={
            <Button variant="solid" onClick={() => setShape(null)}>
              Close
            </Button>
          }
        >
          <p>This dialog has {shape} corners.</p>
        </Dialog>
      </div>
    );
  },
};

/**
 * Content padding variants: none, normal, dense
 */
export const ContentPadding: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [padding, setPadding] = useState<'none' | 'normal' | 'dense' | null>(null);

    return (
      <div className="flex gap-2">
        <Button onClick={() => setPadding('none')}>No Padding</Button>
        <Button onClick={() => setPadding('dense')}>Dense (12px)</Button>
        <Button onClick={() => setPadding('normal')}>Normal (24px)</Button>

        <Dialog
          {...args}
          open={!!padding}
          onOpenChange={() => setPadding(null)}
          header={`${padding} Padding`}
          contentPadding={padding || 'normal'}
          actions={
            <Button variant="solid" onClick={() => setPadding(null)}>
              Close
            </Button>
          }
        >
          <div className="bg-grey-100 p-4">
            <p>This content has a grey background to show the padding.</p>
            <p className="mt-2">Padding: {padding}</p>
          </div>
        </Dialog>
      </div>
    );
  },
};

/**
 * Dialog without close button in header
 */
export const NoCloseButton: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Dialog</Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="No Close Button"
          showCloseButton={false}
          actions={
            <Button variant="solid" onClick={() => setOpen(false)}>
              Dismiss
            </Button>
          }
        >
          <p>This dialog doesn't have a close button in the header.</p>
          <p className="mt-2 text-sm text-grey-600">
            You must use the action button or ESC key to close it.
          </p>
        </Dialog>
      </>
    );
  },
};

/**
 * Dialog without header
 */
export const NoHeader: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Dialog</Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          actions={
            <Button variant="solid" onClick={() => setOpen(false)}>
              Close
            </Button>
          }
        >
          <h2 className="text-lg font-bold mb-4">Custom Header Inside Content</h2>
          <p>When you don't use the header prop, you can create your own header inside the content area.</p>
        </Dialog>
      </>
    );
  },
};

/**
 * Dialog without actions footer
 */
export const NoActions: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Dialog</Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="Informational Dialog"
        >
          <p>This dialog doesn't have an actions footer.</p>
          <p className="mt-2 text-sm text-grey-600">
            Click the X button or press ESC to close.
          </p>
        </Dialog>
      </>
    );
  },
};

/**
 * Confirm dialog pattern with destructive action
 */
export const ConfirmDialog: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    const handleDelete = () => {
      alert('Item deleted!');
      setOpen(false);
    };

    return (
      <>
        <Button variant="solid" color="error" onClick={() => setOpen(true)}>
          Delete Item
        </Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="Confirm Deletion"
          size="sm"
          actions={
            <>
              <Button variant="ghost" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button variant="solid" color="error" onClick={handleDelete}>
                Delete
              </Button>
            </>
          }
        >
          <p>Are you sure you want to delete this item?</p>
          <p className="mt-2 text-sm text-grey-600">
            This action cannot be undone.
          </p>
        </Dialog>
      </>
    );
  },
};

/**
 * Form dialog with inputs
 */
export const FormDialog: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      alert('Form submitted!');
      setOpen(false);
    };

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Form</Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="Create New Item"
          size="md"
          actions={
            <>
              <Button variant="ghost" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button variant="solid" type="submit" form="item-form">
                Create
              </Button>
            </>
          }
        >
          <form id="item-form" onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="name" className="block text-sm font-medium mb-1">
                Name
              </label>
              <input
                id="name"
                type="text"
                className="w-full px-3 py-2 border border-grey-300 rounded"
                required
              />
            </div>
            <div>
              <label htmlFor="description" className="block text-sm font-medium mb-1">
                Description
              </label>
              <textarea
                id="description"
                className="w-full px-3 py-2 border border-grey-300 rounded"
                rows={4}
              />
            </div>
          </form>
        </Dialog>
      </>
    );
  },
};

/**
 * Scrollable content dialog
 */
export const ScrollableContent: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Long Content</Button>
        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="Terms of Service"
          size="lg"
          actions={
            <>
              <Button variant="ghost" onClick={() => setOpen(false)}>
                Decline
              </Button>
              <Button variant="solid" onClick={() => setOpen(false)}>
                Accept
              </Button>
            </>
          }
        >
          <div className="space-y-4">
            <h3 className="font-semibold">1. Introduction</h3>
            <p>
              Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor 
              incididunt ut labore et dolore magna aliqua.
            </p>

            <h3 className="font-semibold">2. User Responsibilities</h3>
            <p>
              Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip 
              ex ea commodo consequat.
            </p>

            <h3 className="font-semibold">3. Privacy Policy</h3>
            <p>
              Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu 
              fugiat nulla pariatur.
            </p>

            <h3 className="font-semibold">4. Termination</h3>
            <p>
              Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt 
              mollit anim id est laborum.
            </p>

            <h3 className="font-semibold">5. Limitation of Liability</h3>
            <p>
              Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque 
              laudantium, totam rem aperiam.
            </p>

            <h3 className="font-semibold">6. Governing Law</h3>
            <p>
              Eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta 
              sunt explicabo.
            </p>
          </div>
        </Dialog>
      </>
    );
  },
};

/**
 * Keyboard accessibility demonstration
 */
export const KeyboardAccessibility: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <>
        <div className="space-y-2">
          <Button onClick={() => setOpen(true)}>Open Dialog</Button>
          <p className="text-sm text-grey-600">
            Keyboard controls:
            <br />
            • ESC - Close dialog
            <br />
            • TAB - Navigate between focusable elements
            <br />• ENTER/SPACE - Activate buttons
          </p>
        </div>

        <Dialog
          {...args}
          open={open}
          onOpenChange={setOpen}
          header="Keyboard Navigation Demo"
          actions={
            <>
              <Button variant="ghost" onClick={() => setOpen(false)}>
                Secondary Action
              </Button>
              <Button variant="solid" onClick={() => setOpen(false)}>
                Primary Action
              </Button>
            </>
          }
        >
          <p>Try navigating this dialog using only your keyboard!</p>
          <p className="mt-2 text-sm text-grey-600">
            Focus is trapped within the dialog when open. Press ESC or click outside to close.
          </p>
          <div className="mt-4 space-y-2">
            <input type="text" placeholder="Input 1" className="w-full px-3 py-2 border rounded" />
            <input type="text" placeholder="Input 2" className="w-full px-3 py-2 border rounded" />
          </div>
        </Dialog>
      </>
    );
  },
};
