/**
 * Alert Stories
 *
 * Comprehensive demonstrations of Alert molecule component
 */

import { Stack, Box, Typography } from '@ghatana/ui';
import { useState } from 'react';

// Import the package entry for this component folder so index.ts can re-export
// the correct implementation (tailwind variant). Using `.` preserves the
// public package-style import and avoids hard-coding file suffixes.
import { Alert, AlertProps } from '.';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Alert> = {
  title: 'Molecules/Alert',
  component: Alert,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: `
# Alert (Molecule)

A feedback component for displaying important messages to users with various severity levels.

## Features
- ✅ Four severity variants (success, error, warning, info)
- ✅ Optional title and description
- ✅ Closable with callback
- ✅ Icon support (enabled by default)
- ✅ Accessible with proper ARIA role
- ✅ Visual differentiation with border and background
- ✅ Responsive design

## Variants
- **Success**: Positive feedback for successful operations
- **Error**: Critical issues that need immediate attention
- **Warning**: Important information requiring user awareness
- **Info**: General informational messages
        `,
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    severity: {
      control: 'select',
      options: ['success', 'error', 'warning', 'info'],
      description: 'Alert severity',
    },
    hideIcon: {
      control: 'boolean',
      description: 'Hide the severity icon (inverse of showIcon)',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Alert>;

// ============================================================================
// Basic Examples
// ============================================================================

export const Default: Story = {
  args: {
    severity: 'info',
    children: 'This is an informational alert message.',
  },
};

export const WithTitle: Story = {
  args: {
    severity: 'info',
    title: 'Information',
    children: 'This is an informational alert message with a title.',
  },
};

export const Closable: Story = {
  args: {
    severity: 'info',
    title: 'Closable Alert',
    children: 'Click the × button to close this alert.',
  onClose: () => console.log('Alert closed'),
  },
};

export const WithoutIcon: Story = {
  args: {
    severity: 'info',
    title: 'No Icon',
    children: 'This alert has no icon.',
  hideIcon: true,
  },
};

// ============================================================================
// Variant Examples
// ============================================================================

export const AllVariants: Story = {
  render: () => (
    <Stack spacing={2}>
      <Alert severity="success" title="Success">
        Your changes have been saved successfully.
      </Alert>
      <Alert severity="error" title="Error">
        An error occurred while processing your request.
      </Alert>
      <Alert severity="warning" title="Warning">
        This action cannot be undone. Please proceed with caution.
      </Alert>
      <Alert severity="info" title="Information">
        Your session will expire in 5 minutes.
      </Alert>
    </Stack>
  ),
};

export const SuccessVariant: Story = {
  args: {
  severity: 'success',
    title: 'Payment Successful',
    children: 'Your payment of $99.99 has been processed successfully.',
  },
};

export const ErrorVariant: Story = {
  args: {
  severity: 'error',
    title: 'Connection Failed',
    children: 'Unable to connect to the server. Please check your internet connection and try again.',
  },
};

export const WarningVariant: Story = {
  args: {
  severity: 'warning',
    title: 'Storage Limit Reached',
    children: 'You have used 95% of your storage space. Consider upgrading your plan.',
  },
};

export const InfoVariant: Story = {
  args: {
  severity: 'info',
    title: 'New Features Available',
    children: 'We have released new features. Check out our changelog to learn more.',
  },
};

// ============================================================================
// With and Without Titles
// ============================================================================

export const WithoutTitles: Story = {
  render: () => (
    <Stack spacing={2}>
  <Alert severity="success">Operation completed successfully.</Alert>
  <Alert severity="error">Something went wrong.</Alert>
  <Alert severity="warning">Please review your input.</Alert>
  <Alert severity="info">Loading your data...</Alert>
    </Stack>
  ),
};

export const WithTitles: Story = {
  render: () => (
    <Stack spacing={2}>
      <Alert severity="success" title="Success">
        Operation completed successfully.
      </Alert>
      <Alert severity="error" title="Error">
        Something went wrong.
      </Alert>
      <Alert severity="warning" title="Warning">
        Please review your input.
      </Alert>
      <Alert severity="info" title="Info">
        Loading your data...
      </Alert>
    </Stack>
  ),
};

// ============================================================================
// Closable Alerts
// ============================================================================

export const ClosableAlerts: Story = {
  render: () => {
    const [alerts, setAlerts] = useState({
      success: true,
      error: true,
      warning: true,
      info: true,
    });

    return (
      <Stack spacing={2}>
        {alerts.success && (
          <Alert
            severity="success"
            title="Success"
            onClose={() => setAlerts({ ...alerts, success: false })}
          >
            You can close this success alert.
          </Alert>
        )}
        {alerts.error && (
          <Alert
            severity="error"
            title="Error"
            onClose={() => setAlerts({ ...alerts, error: false })}
          >
            You can close this error alert.
          </Alert>
        )}
        {alerts.warning && (
          <Alert
            severity="warning"
            title="Warning"
            onClose={() => setAlerts({ ...alerts, warning: false })}
          >
            You can close this warning alert.
          </Alert>
        )}
        {alerts.info && (
          <Alert
            severity="info"
            title="Info"
            onClose={() => setAlerts({ ...alerts, info: false })}
          >
            You can close this info alert.
          </Alert>
        )}
        {!alerts.success && !alerts.error && !alerts.warning && !alerts.info && (
          <Typography as="p" className="text-sm" color="text.secondary" className="text-center">
            All alerts closed. Refresh to see them again.
          </Typography>
        )}
      </Stack>
    );
  },
};

// ============================================================================
// Content Variations
// ============================================================================

export const ShortMessage: Story = {
  args: {
    severity: 'success',
    children: 'Done!',
  },
};

export const LongMessage: Story = {
  args: {
  severity: 'warning',
    title: 'Important Security Notice',
    children:
      'We have detected unusual activity on your account. For your security, we recommend changing your password immediately. If you did not authorize this activity, please contact our support team as soon as possible. We take the security of your account very seriously and have implemented additional measures to protect your data.',
  },
};

export const MultilineMessage: Story = {
  render: () => (
  <Alert severity="error" title="Validation Errors">
      <div>
        <div>• Email address is required</div>
        <div>• Password must be at least 8 characters</div>
        <div>• Passwords do not match</div>
        <div>• Terms and conditions must be accepted</div>
      </div>
    </Alert>
  ),
};

export const WithLinks: Story = {
  render: () => (
    <Stack spacing={2}>
  <Alert severity="info" title="System Maintenance">
        Our system will undergo maintenance on Sunday, 2:00 AM - 4:00 AM.{' '}
        <a href="#" style={{ color: 'inherit', fontWeight: 600 }}>
          Learn more
        </a>
      </Alert>
  <Alert severity="warning" title="Update Available">
        A new version is available.{' '}
        <a href="#" style={{ color: 'inherit', fontWeight: 600 }}>
          Update now
        </a>
      </Alert>
    </Stack>
  ),
};

// ============================================================================
// Real-World Use Cases
// ============================================================================

export const FormValidation: Story = {
  render: () => (
    <Stack spacing={2}>
      <Alert severity="error" title="Form Validation Failed">
        Please correct the following errors before submitting:
        <ul style={{ margin: '0.5rem 0 0 0', paddingLeft: '1.25rem' }}>
          <li>Email is required</li>
          <li>Password must be at least 8 characters</li>
        </ul>
      </Alert>
    </Stack>
  ),
};

export const NetworkStatus: Story = {
  render: () => {
    const [isOnline, setIsOnline] = useState(true);

    return (
      <Stack spacing={2}>
        <Box>
          <button
            onClick={() => setIsOnline(!isOnline)}
            style={{
              padding: '0.5rem 1rem',
              marginBottom: '1rem',
              borderRadius: '0.375rem',
              border: '1px solid #e0e0e0',
              background: 'white',
              cursor: 'pointer',
            }}
          >
            Toggle Connection Status
          </button>
        </Box>
        {isOnline ? (
          <Alert severity="success" title="Connected">
            You are connected to the internet.
          </Alert>
        ) : (
          <Alert severity="error" title="No Connection">
            You are currently offline. Some features may not be available.
          </Alert>
        )}
      </Stack>
    );
  },
};

export const ProgressNotification: Story = {
  render: () => (
    <Stack spacing={2}>
      <Alert severity="info" title="Upload in Progress">
        Uploading 3 files... Please do not close this window.
      </Alert>
      <Alert severity="success" title="Upload Complete">
        All files have been uploaded successfully.
      </Alert>
    </Stack>
  ),
};

export const SystemNotifications: Story = {
  render: () => (
    <Stack spacing={2}>
      <Alert severity="info" title="Session Timeout Warning">
        Your session will expire in 5 minutes due to inactivity.
      </Alert>
      <Alert severity="warning" title="Unsaved Changes">
        You have unsaved changes. Do you want to save them before leaving?
      </Alert>
      <Alert severity="error" title="Authentication Required">
        Your session has expired. Please log in again to continue.
      </Alert>
    </Stack>
  ),
};

// ============================================================================
// Accessibility Demo
// ============================================================================

export const AccessibilityDemo: Story = {
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography as="h6" gutterBottom>
          ARIA Role
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          All alerts use <code>role="alert"</code> for screen reader support
        </Typography>
        <Alert severity="info" title="Accessible Alert">
          This alert is announced to screen readers automatically.
        </Alert>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Keyboard Navigation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Closable alerts can be dismissed with keyboard (Tab to focus, Enter to close)
        </Typography>
        <Alert severity="warning" title="Try Keyboard Navigation">
          Tab to the close button and press Enter to dismiss.
        </Alert>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Visual Differentiation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Each variant uses distinct colors and icons for quick identification
        </Typography>
        <Stack spacing={2}>
          <Alert severity="success">Success variant with green theme</Alert>
          <Alert severity="error">Error variant with red theme</Alert>
          <Alert severity="warning">Warning variant with orange theme</Alert>
          <Alert severity="info">Info variant with blue theme</Alert>
        </Stack>
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Without Icons
// ============================================================================

export const WithoutIcons: Story = {
  render: () => (
    <Stack spacing={2}>
      <Alert severity="success" title="Success" hideIcon={true}>
        Operation completed successfully.
      </Alert>
      <Alert severity="error" title="Error" hideIcon={true}>
        Something went wrong.
      </Alert>
      <Alert severity="warning" title="Warning" hideIcon={true}>
        Please review your input.
      </Alert>
      <Alert severity="info" title="Info" hideIcon={true}>
        Loading your data...
      </Alert>
    </Stack>
  ),
};

// ============================================================================
// Interactive Playground
// ============================================================================

export const Playground: Story = {
  args: {
    severity: 'info',
    title: 'Playground Alert',
    children: 'Customize this alert using the controls below!',
    hideIcon: false,
  },
};
