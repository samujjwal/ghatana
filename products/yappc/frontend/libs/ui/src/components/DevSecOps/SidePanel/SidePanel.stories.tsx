/**
 * SidePanel Storybook Stories
 *
 * @module DevSecOps/SidePanel/stories
 */

import { Box, Button, Typography, Divider, Chip } from '@ghatana/ui';
import { useState } from 'react';


import { SidePanel } from './SidePanel';
import { devsecopsTheme } from '../../../theme/devsecops-theme';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof SidePanel> = {
  title: 'DevSecOps/SidePanel',
  component: SidePanel,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider theme={devsecopsTheme}>
        <Story />
      </ThemeProvider>
    ),
  ],
  parameters: {
    docs: {
      description: {
        component:
          'A slide-out panel component that appears from the right side for displaying detailed information.',
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof SidePanel>;

/**
 * Interactive example with trigger button
 */
export const Default: Story = {
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button variant="solid" onClick={() => setOpen(true)}>
          Open Side Panel
        </Button>

        <SidePanel
          open={open}
          onClose={() => setOpen(false)}
          title="Item Details"
        >
          <Typography as="p">
            This is the panel content. You can put any component here.
          </Typography>
        </SidePanel>
      </>
    );
  },
};

/**
 * Panel with simple content
 */
export const SimpleContent: Story = {
  render: () => {
    const [open, setOpen] = useState(true);

    return (
      <>
        <Button variant="solid" onClick={() => setOpen(true)}>
          Open Panel
        </Button>

        <SidePanel
          open={open}
          onClose={() => setOpen(false)}
          title="Simple Panel"
        >
          <Typography as="p" paragraph>
            This is a simple side panel with basic text content.
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary">
            Click the close button or click outside to dismiss.
          </Typography>
        </SidePanel>
      </>
    );
  },
};

/**
 * Panel with rich content (item details)
 */
export const ItemDetails: Story = {
  render: () => {
    const [open, setOpen] = useState(true);

    return (
      <>
        <Button variant="solid" onClick={() => setOpen(true)}>
          View Item Details
        </Button>

        <SidePanel
          open={open}
          onClose={() => setOpen(false)}
          title="Feature #123: User Authentication"
        >
          <Box className="mb-6">
            <Chip label="High Priority" tone="danger" size="sm" className="mr-2" />
            <Chip label="In Progress" tone="warning" size="sm" />
          </Box>

          <Typography as="h6" gutterBottom>
            Description
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary" paragraph>
            Implement OAuth 2.0 authentication with support for Google, GitHub,
            and email/password login.
          </Typography>

          <Divider className="my-4" />

          <Typography as="h6" gutterBottom>
            Owners
          </Typography>
          <Typography as="p" className="text-sm" paragraph>
            John Doe, Jane Smith
          </Typography>

          <Divider className="my-4" />

          <Typography as="h6" gutterBottom>
            Artifacts
          </Typography>
          <ul>
            <li>Architecture Diagram</li>
            <li>Requirements Document</li>
            <li>Security Review</li>
          </ul>

          <Box className="mt-6">
            <Button variant="solid" fullWidth>
              Edit Item
            </Button>
          </Box>
        </SidePanel>
      </>
    );
  },
};

/**
 * Wide panel (custom width)
 */
export const WidePanel: Story = {
  render: () => {
    const [open, setOpen] = useState(true);

    return (
      <>
        <Button variant="solid" onClick={() => setOpen(true)}>
          Open Wide Panel
        </Button>

        <SidePanel
          open={open}
          onClose={() => setOpen(false)}
          title="Wide Panel"
          width={800}
        >
          <Typography as="p">
            This panel is wider (800px) to accommodate more content.
          </Typography>
        </SidePanel>
      </>
    );
  },
};

/**
 * Narrow panel
 */
export const NarrowPanel: Story = {
  render: () => {
    const [open, setOpen] = useState(true);

    return (
      <>
        <Button variant="solid" onClick={() => setOpen(true)}>
          Open Narrow Panel
        </Button>

        <SidePanel
          open={open}
          onClose={() => setOpen(false)}
          title="Narrow Panel"
          width={320}
        >
          <Typography as="p">
            This is a narrow panel (320px) for compact information.
          </Typography>
        </SidePanel>
      </>
    );
  },
};

/**
 * Panel with scrollable content
 */
export const ScrollableContent: Story = {
  render: () => {
    const [open, setOpen] = useState(true);

    return (
      <>
        <Button variant="solid" onClick={() => setOpen(true)}>
          Open Scrollable Panel
        </Button>

        <SidePanel
          open={open}
          onClose={() => setOpen(false)}
          title="Long Content Panel"
        >
          {Array.from({ length: 50 }, (_, i) => (
            <Box key={i} className="mb-4">
              <Typography as="h6">Section {i + 1}</Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                This is section {i + 1} of the scrollable content. The panel
                content area scrolls independently while the header stays fixed.
              </Typography>
            </Box>
          ))}
        </SidePanel>
      </>
    );
  },
};
