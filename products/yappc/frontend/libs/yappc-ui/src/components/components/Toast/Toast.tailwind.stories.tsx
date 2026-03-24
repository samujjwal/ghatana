import { useState } from 'react';

import { Toast, ToastProvider, useToast } from './Toast.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Toast> = {
  title: 'Components/Toast',
  component: Toast,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    severity: {
      control: 'select',
      options: ['info', 'success', 'warning', 'error'],
    },
    position: {
      control: 'select',
      options: ['top-left', 'top-center', 'top-right', 'bottom-left', 'bottom-center', 'bottom-right'],
    },
    duration: {
      control: 'number',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Toast>;

/**
 * Default info toast
 */
export const Default: Story = {
  args: {
    message: 'This is an informational message',
    severity: 'info',
    open: true,
    onClose: () => {},
  },
};

/**
 * All severity levels
 */
export const Severities: Story = {
  render: () => {
    const [openStates, setOpenStates] = useState({
      info: true,
      success: true,
      warning: true,
      error: true,
    });

    return (
      <div className="flex flex-col gap-4">
        <button
          onClick={() => setOpenStates({ info: true, success: true, warning: true, error: true })}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Show All Toasts
        </button>

        <Toast
          open={openStates.info}
          onClose={() => setOpenStates({ ...openStates, info: false })}
          message="This is an informational message"
          severity="info"
          position="top-left"
        />
        <Toast
          open={openStates.success}
          onClose={() => setOpenStates({ ...openStates, success: false })}
          message="Operation completed successfully!"
          severity="success"
          position="top-center"
        />
        <Toast
          open={openStates.warning}
          onClose={() => setOpenStates({ ...openStates, warning: false })}
          message="Please review your changes before proceeding"
          severity="warning"
          position="top-right"
        />
        <Toast
          open={openStates.error}
          onClose={() => setOpenStates({ ...openStates, error: false })}
          message="An error occurred while processing your request"
          severity="error"
          position="bottom-left"
        />
      </div>
    );
  },
};

/**
 * All position variants
 */
export const Positions: Story = {
  render: () => {
    const [positions, setPositions] = useState({
      'top-left': false,
      'top-center': false,
      'top-right': false,
      'bottom-left': false,
      'bottom-center': false,
      'bottom-right': false,
    });

    return (
      <div className="flex flex-col gap-2">
        <div className="grid grid-cols-3 gap-2">
          <button
            onClick={() => setPositions({ ...positions, 'top-left': true })}
            className="px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600"
          >
            Top Left
          </button>
          <button
            onClick={() => setPositions({ ...positions, 'top-center': true })}
            className="px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600"
          >
            Top Center
          </button>
          <button
            onClick={() => setPositions({ ...positions, 'top-right': true })}
            className="px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600"
          >
            Top Right
          </button>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <button
            onClick={() => setPositions({ ...positions, 'bottom-left': true })}
            className="px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600"
          >
            Bottom Left
          </button>
          <button
            onClick={() => setPositions({ ...positions, 'bottom-center': true })}
            className="px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600"
          >
            Bottom Center
          </button>
          <button
            onClick={() => setPositions({ ...positions, 'bottom-right': true })}
            className="px-3 py-2 bg-blue-500 text-white rounded text-sm hover:bg-blue-600"
          >
            Bottom Right
          </button>
        </div>

        {Object.entries(positions).map(([pos, open]) => (
          <Toast
            key={pos}
            open={open}
            onClose={() => setPositions({ ...positions, [pos]: false })}
            message={`Toast at ${pos}`}
            severity="info"
            position={pos as unknown}
          />
        ))}
      </div>
    );
  },
};

/**
 * Toast with action button
 */
export const WithAction: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <div className="flex flex-col gap-4">
        <button
          onClick={() => setOpen(true)}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Show Toast with Action
        </button>

        <Toast
          open={open}
          onClose={() => setOpen(false)}
          message="File deleted successfully"
          severity="success"
          action={
            <button className="px-3 py-1 text-xs font-semibold rounded bg-white/20 hover:bg-white/30">
              Undo
            </button>
          }
        />
      </div>
    );
  },
};

/**
 * Auto-dismiss toast
 */
export const AutoDismiss: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <div className="flex flex-col gap-4">
        <button
          onClick={() => setOpen(true)}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Show Auto-Dismiss Toast (3s)
        </button>

        <Toast
          open={open}
          onClose={() => setOpen(false)}
          message="This toast will auto-dismiss in 3 seconds"
          severity="info"
          duration={3000}
        />
      </div>
    );
  },
};

/**
 * Persistent toast (no auto-dismiss)
 */
export const Persistent: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <div className="flex flex-col gap-4">
        <button
          onClick={() => setOpen(true)}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Show Persistent Toast
        </button>

        <Toast
          open={open}
          onClose={() => setOpen(false)}
          message="This toast will stay until manually closed"
          severity="warning"
          duration={0}
        />
      </div>
    );
  },
};

/**
 * Long message toast
 */
export const LongMessage: Story = {
  args: {
    message: 'This is a very long message that demonstrates how the toast component handles longer content. The message will wrap to multiple lines while maintaining good readability and layout.',
    severity: 'info',
    open: true,
    onClose: () => {},
  },
};

/**
 * Rich content toast
 */
export const RichContent: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <div className="flex flex-col gap-4">
        <button
          onClick={() => setOpen(true)}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Show Rich Content Toast
        </button>

        <Toast
          open={open}
          onClose={() => setOpen(false)}
          message={
            <div>
              <strong>Update Available</strong>
              <p className="mt-1 text-xs opacity-90">
                Version 2.0 is now available. <a href="#" className="underline">Learn more</a>
              </p>
            </div>
          }
          severity="info"
          action={
            <button className="px-3 py-1 text-xs font-semibold rounded bg-white/20 hover:bg-white/30">
              Update
            </button>
          }
        />
      </div>
    );
  },
};

/**
 * Using ToastProvider and useToast hook
 */
export const WithProvider: Story = {
  render: () => {
    /**
     *
     */
    function ToastDemo() {
      const { addToast } = useToast();

      return (
        <div className="flex flex-col gap-2">
          <button
            onClick={() => addToast({ message: 'Info message', severity: 'info' })}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            Show Info
          </button>
          <button
            onClick={() => addToast({ message: 'Success message', severity: 'success' })}
            className="px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600"
          >
            Show Success
          </button>
          <button
            onClick={() => addToast({ message: 'Warning message', severity: 'warning' })}
            className="px-4 py-2 bg-orange-500 text-white rounded hover:bg-orange-600"
          >
            Show Warning
          </button>
          <button
            onClick={() => addToast({ message: 'Error message', severity: 'error' })}
            className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
          >
            Show Error
          </button>
          <button
            onClick={() => {
              addToast({
                message: 'File deleted successfully',
                severity: 'success',
                action: <button className="px-2 py-1 text-xs rounded bg-white/20">Undo</button>,
              });
            }}
            className="px-4 py-2 bg-purple-500 text-white rounded hover:bg-purple-600"
          >
            Show with Action
          </button>
        </div>
      );
    }

    return (
      <ToastProvider>
        <ToastDemo />
      </ToastProvider>
    );
  },
};

/**
 * Multiple stacked toasts
 */
export const Stacked: Story = {
  render: () => {
    /**
     *
     */
    function StackedDemo() {
      const { addToast } = useToast();

      return (
        <button
          onClick={() => {
            addToast({ message: 'First toast message', severity: 'info' });
            setTimeout(() => addToast({ message: 'Second toast message', severity: 'success' }), 500);
            setTimeout(() => addToast({ message: 'Third toast message', severity: 'warning' }), 1000);
          }}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Show Stacked Toasts
        </button>
      );
    }

    return (
      <ToastProvider>
        <StackedDemo />
      </ToastProvider>
    );
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => {
    const [open, setOpen] = useState(true);

    return (
      <div className="dark">
        <Toast
          open={open}
          onClose={() => setOpen(false)}
          message="Dark mode toast notification"
          severity="info"
        />
      </div>
    );
  },
};
