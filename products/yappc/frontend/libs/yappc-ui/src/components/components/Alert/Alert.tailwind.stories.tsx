import { useState } from 'react';

import { Alert } from './Alert.tailwind';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Alert> = {
  title: 'Components/Feedback/Alert',
  component: Alert,
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component:
          'Alert component for displaying important messages with different severity levels. Supports titles, custom icons, actions, and dismissal.',
      },
    },
  },
  argTypes: {
    severity: {
      control: 'select',
      options: ['info', 'success', 'warning', 'error'],
      description: 'Severity level of the alert',
    },
    variant: {
      control: 'select',
      options: ['standard', 'filled', 'outlined'],
      description: 'Visual style variant',
    },
    hideIcon: {
      control: 'boolean',
      description: 'Hide the severity icon',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Alert>;

/**
 * Default info alert
 */
export const Default: Story = {
  args: {
    severity: 'info',
    children: 'This is an informational message.',
  },
};

/**
 * All severity levels
 */
export const Severities: Story = {
  render: () => (
    <div className="space-y-4">
      <Alert severity="info">
        This is an info alert — check it out!
      </Alert>
      <Alert severity="success">
        This is a success alert — nice job!
      </Alert>
      <Alert severity="warning">
        This is a warning alert — be careful!
      </Alert>
      <Alert severity="error">
        This is an error alert — something went wrong!
      </Alert>
    </div>
  ),
};

/**
 * All visual variants
 */
export const Variants: Story = {
  render: () => (
    <div className="space-y-6">
      <div>
        <h3 className="text-sm font-medium mb-3 text-grey-700 dark:text-grey-300">Standard (default)</h3>
        <div className="space-y-3">
          <Alert severity="info" variant="standard">Info standard variant</Alert>
          <Alert severity="success" variant="standard">Success standard variant</Alert>
          <Alert severity="warning" variant="standard">Warning standard variant</Alert>
          <Alert severity="error" variant="standard">Error standard variant</Alert>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-medium mb-3 text-grey-700 dark:text-grey-300">Filled</h3>
        <div className="space-y-3">
          <Alert severity="info" variant="filled">Info filled variant</Alert>
          <Alert severity="success" variant="filled">Success filled variant</Alert>
          <Alert severity="warning" variant="filled">Warning filled variant</Alert>
          <Alert severity="error" variant="filled">Error filled variant</Alert>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-medium mb-3 text-grey-700 dark:text-grey-300">Outlined</h3>
        <div className="space-y-3">
          <Alert severity="info" variant="outlined">Info outlined variant</Alert>
          <Alert severity="success" variant="outlined">Success outlined variant</Alert>
          <Alert severity="warning" variant="outlined">Warning outlined variant</Alert>
          <Alert severity="error" variant="outlined">Error outlined variant</Alert>
        </div>
      </div>
    </div>
  ),
};

/**
 * Alerts with titles
 */
export const WithTitle: Story = {
  render: () => (
    <div className="space-y-4">
      <Alert severity="info" title="Information">
        This alert has a title to emphasize the message.
      </Alert>
      <Alert severity="success" title="Success">
        Your changes have been saved successfully.
      </Alert>
      <Alert severity="warning" title="Warning">
        This action cannot be undone. Please proceed with caution.
      </Alert>
      <Alert severity="error" title="Error">
        An error occurred while processing your request.
      </Alert>
    </div>
  ),
};

/**
 * Closable alerts with dismiss button
 */
export const Closable: Story = {
  render: () => {
    const [show1, setShow1] = useState(true);
    const [show2, setShow2] = useState(true);
    const [show3, setShow3] = useState(true);

    return (
      <div className="space-y-4">
        {show1 && (
          <Alert severity="info" onClose={() => setShow1(false)}>
            You can dismiss this alert by clicking the close button.
          </Alert>
        )}
        {show2 && (
          <Alert severity="success" title="Success" onClose={() => setShow2(false)}>
            This alert has both a title and a close button.
          </Alert>
        )}
        {show3 && (
          <Alert severity="warning" variant="filled" onClose={() => setShow3(false)}>
            Filled variant with close button.
          </Alert>
        )}
        {!show1 && !show2 && !show3 && (
          <Button onClick={() => { setShow1(true); setShow2(true); setShow3(true); }}>
            Reset Alerts
          </Button>
        )}
      </div>
    );
  },
};

/**
 * Alerts with custom actions
 */
export const WithActions: Story = {
  render: () => (
    <div className="space-y-4">
      <Alert
        severity="info"
        title="New update available"
        action={<Button size="sm" variant="outline">Update Now</Button>}
      >
        A new version of the application is ready to install.
      </Alert>
      <Alert
        severity="warning"
        title="Storage almost full"
        action={
          <div className="flex gap-2">
            <Button size="sm" variant="outline">Manage</Button>
            <Button size="sm">Upgrade</Button>
          </div>
        }
      >
        You have used 90% of your storage quota.
      </Alert>
      <Alert
        severity="error"
        title="Connection failed"
        action={<Button size="sm">Retry</Button>}
      >
        Unable to connect to the server. Please try again.
      </Alert>
    </div>
  ),
};

/**
 * Alerts without icons
 */
export const WithoutIcon: Story = {
  render: () => (
    <div className="space-y-4">
      <Alert severity="info" hideIcon>
        This alert has no icon.
      </Alert>
      <Alert severity="success" title="Success" hideIcon>
        Alert with title but no icon.
      </Alert>
      <Alert severity="warning" hideIcon onClose={() => {}}>
        Alert without icon but with close button.
      </Alert>
    </div>
  ),
};

/**
 * Alerts with custom icons
 */
export const CustomIcons: Story = {
  render: () => (
    <div className="space-y-4">
      <Alert
        severity="info"
        icon={
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
        }
      >
        Alert with custom plus icon
      </Alert>
      <Alert
        severity="success"
        icon={
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        }
      >
        Alert with custom checkmark icon
      </Alert>
      <Alert
        severity="warning"
        icon="⚡"
      >
        Alert with emoji icon
      </Alert>
    </div>
  ),
};

/**
 * Long content alert
 */
export const LongContent: Story = {
  render: () => (
    <Alert severity="info" title="Terms and Conditions">
      <p>
        By using this service, you agree to our terms and conditions. This includes but is not limited to:
      </p>
      <ul className="list-disc list-inside mt-2 space-y-1">
        <li>Accepting our privacy policy</li>
        <li>Agreeing to data collection and processing</li>
        <li>Compliance with community guidelines</li>
        <li>Responsibility for account security</li>
      </ul>
      <p className="mt-2">
        Please read the full terms before continuing.
      </p>
    </Alert>
  ),
};

/**
 * Rich content with links
 */
export const RichContent: Story = {
  render: () => (
    <div className="space-y-4">
      <Alert severity="info" title="Documentation Updated">
        <p>
          We've updated our{' '}
          <a href="#" className="underline hover:no-underline">
            API documentation
          </a>{' '}
          with new examples and best practices.
        </p>
      </Alert>
      <Alert severity="warning" title="Deprecated Feature">
        <p>
          The <code className="bg-black/10 dark:bg-white/10 px-1.5 py-0.5 rounded">oldMethod()</code> is deprecated
          and will be removed in v3.0. Please migrate to{' '}
          <code className="bg-black/10 dark:bg-white/10 px-1.5 py-0.5 rounded">newMethod()</code>.
        </p>
      </Alert>
    </div>
  ),
};

/**
 * Form validation alerts
 */
export const FormValidation: Story = {
  render: () => (
    <div className="space-y-4 max-w-md">
      <Alert severity="error" title="Form Validation Failed">
        <ul className="list-disc list-inside space-y-1">
          <li>Email address is required</li>
          <li>Password must be at least 8 characters</li>
          <li>Terms and conditions must be accepted</li>
        </ul>
      </Alert>
      <Alert severity="warning">
        Some fields contain warnings. Please review before submitting.
      </Alert>
      <Alert severity="success">
        All fields validated successfully! You may proceed.
      </Alert>
    </div>
  ),
};

/**
 * Stacked alerts
 */
export const Stacked: Story = {
  render: () => (
    <div className="space-y-2 max-w-2xl">
      <Alert severity="error" title="Critical Error">
        The application encountered a critical error and needs to restart.
      </Alert>
      <Alert severity="warning" title="Unsaved Changes">
        You have unsaved changes that will be lost.
      </Alert>
      <Alert severity="info">
        Auto-save is enabled and will save your work every 5 minutes.
      </Alert>
    </div>
  ),
};

/**
 * Compact alerts
 */
export const Compact: Story = {
  render: () => (
    <div className="space-y-2">
      <Alert severity="info" className="py-2">
        Compact info alert
      </Alert>
      <Alert severity="success" className="py-2">
        Compact success alert
      </Alert>
      <Alert severity="warning" className="py-2" onClose={() => {}}>
        Compact warning with close
      </Alert>
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
    <div className="dark space-y-4">
      <Alert severity="info" title="Dark Mode">
        This alert adapts to dark mode automatically.
      </Alert>
      <Alert severity="success" variant="standard">
        Success alert in dark mode.
      </Alert>
      <Alert severity="warning" variant="outlined">
        Warning with outlined variant.
      </Alert>
      <Alert severity="error" variant="filled">
        Error with filled variant.
      </Alert>
    </div>
  ),
};
