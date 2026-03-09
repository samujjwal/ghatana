/**
 * Enhanced Tabs Stories
 *
 * Comprehensive demonstrations of Tabs organism component
 */

import { Heart as FavoriteIcon } from 'lucide-react';
import { Home as HomeIcon } from 'lucide-react';
import { MessageSquare as MessageIcon } from 'lucide-react';
import { Bell as NotificationsIcon } from 'lucide-react';
import { User as PersonIcon } from 'lucide-react';
import { Settings as SettingsIcon } from 'lucide-react';
import { Stack, Box, Typography, Card, CardContent } from '@ghatana/ui';
import { useState } from 'react';

import { Tabs, Tab, TabPanel } from './Tabs.enhanced';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Tabs> = {
  title: 'Organisms/Tabs',
  component: Tabs,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: `
# Tabs (Organism)

A navigation component providing tabbed interface for organizing content into separate views. Built on MUI Tabs with design token integration.

## Features
- ✅ WCAG 2.1 AA compliant (44px minimum touch targets)
- ✅ Design token integration
- ✅ Four shape variants (rounded, soft, pill, square)
- ✅ Three size variants (small, medium, large)
- ✅ Horizontal and vertical orientations
- ✅ Scrollable tabs support
- ✅ Icon and badge support
- ✅ Keyboard navigation (Arrow keys, Home, End)
- ✅ Accessible ARIA attributes

## Design Tokens Used
- **Border Radius**: \`borderRadiusSm\` (4px), \`borderRadiusMd\` (8px), \`borderRadiusFull\` (9999px)
- **Spacing**: \`spacingSm\` (8px), \`spacingMd\` (16px), \`spacingLg\` (24px)
- **Typography**: \`fontWeightMedium\` (500), \`fontWeightBold\` (700)
        `,
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    shape: {
      control: 'select',
      options: ['rounded', 'soft', 'pill', 'square'],
      description: 'Tabs shape variant using design tokens',
    },
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
      description: 'Tabs size (all maintain WCAG min height)',
    },
    orientation: {
      control: 'select',
      options: ['horizontal', 'vertical'],
      description: 'Tabs orientation',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Tabs>;

// ============================================================================
// Basic Examples
// ============================================================================

export const Default: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box>
        <Tabs value={value} onChange={(e, newValue) => setValue(newValue)}>
          <Tab label="Tab One" />
          <Tab label="Tab Two" />
          <Tab label="Tab Three" />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Typography>Content for Tab One</Typography>
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Typography>Content for Tab Two</Typography>
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Typography>Content for Tab Three</Typography>
        </TabPanel>
      </Box>
    );
  },
};

export const WithIcons: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box>
        <Tabs value={value} onChange={(e, newValue) => setValue(newValue)}>
          <Tab label="Home" startIcon={<HomeIcon />} />
          <Tab label="Profile" startIcon={<PersonIcon />} />
          <Tab label="Settings" startIcon={<SettingsIcon />} />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Typography as="h6">Home Content</Typography>
          <Typography>Welcome to your dashboard</Typography>
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Typography as="h6">Profile Content</Typography>
          <Typography>Your profile information</Typography>
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Typography as="h6">Settings Content</Typography>
          <Typography>Application settings</Typography>
        </TabPanel>
      </Box>
    );
  },
};

export const WithBadges: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box>
        <Tabs value={value} onChange={(e, newValue) => setValue(newValue)}>
          <Tab label="Messages" startIcon={<MessageIcon />} badgeCount={5} badgeColor="primary" />
          <Tab label="Notifications" startIcon={<NotificationsIcon />} badgeCount={12} badgeColor="error" />
          <Tab label="Favorites" startIcon={<FavoriteIcon />} badgeCount={3} badgeColor="secondary" />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Typography>You have 5 unread messages</Typography>
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Typography>You have 12 new notifications</Typography>
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Typography>You have 3 favorite items</Typography>
        </TabPanel>
      </Box>
    );
  },
};

// ============================================================================
// Shape Variants
// ============================================================================

export const ShapeVariants: Story = {
  render: () => {
    const [value1, setValue1] = useState(0);
    const [value2, setValue2] = useState(0);
    const [value3, setValue3] = useState(0);
    const [value4, setValue4] = useState(0);

    return (
      <Stack spacing={4}>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Rounded (4px - borderRadiusSm)
          </Typography>
          <Tabs value={value1} onChange={(e, v) => setValue1(v)} shape="rounded">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Soft (8px - borderRadiusMd)
          </Typography>
          <Tabs value={value2} onChange={(e, v) => setValue2(v)} shape="soft">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Pill (Full - borderRadiusFull)
          </Typography>
          <Tabs value={value3} onChange={(e, v) => setValue3(v)} shape="pill">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Square (0px)
          </Typography>
          <Tabs value={value4} onChange={(e, v) => setValue4(v)} shape="square">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
      </Stack>
    );
  },
};

// ============================================================================
// Size Variants
// ============================================================================

export const SizeVariants: Story = {
  render: () => {
    const [value1, setValue1] = useState(0);
    const [value2, setValue2] = useState(0);
    const [value3, setValue3] = useState(0);

    return (
      <Stack spacing={4}>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Small (44px min height - WCAG compliant)
          </Typography>
          <Tabs value={value1} onChange={(e, v) => setValue1(v)} size="sm">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Medium (48px - default)
          </Typography>
          <Tabs value={value2} onChange={(e, v) => setValue2(v)} size="md">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Large (56px)
          </Typography>
          <Tabs value={value3} onChange={(e, v) => setValue3(v)} size="lg">
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
          </Tabs>
        </Box>
      </Stack>
    );
  },
};

// ============================================================================
// Orientation
// ============================================================================

export const VerticalTabs: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box className="flex h-[300px]">
        <Tabs
          value={value}
          onChange={(e, v) => setValue(v)}
          orientation="vertical"
          className="border-gray-200 dark:border-gray-700 min-w-[150px] border-r"
        >
          <Tab label="Account" startIcon={<PersonIcon />} />
          <Tab label="Security" startIcon={<SettingsIcon />} />
          <Tab label="Notifications" startIcon={<NotificationsIcon />} />
        </Tabs>
        <Box className="flex-1">
          <TabPanel value={value} index={0}>
            <Typography as="h6">Account Settings</Typography>
            <Typography>Manage your account information</Typography>
          </TabPanel>
          <TabPanel value={value} index={1}>
            <Typography as="h6">Security Settings</Typography>
            <Typography>Update your security preferences</Typography>
          </TabPanel>
          <TabPanel value={value} index={2}>
            <Typography as="h6">Notification Settings</Typography>
            <Typography>Configure notification preferences</Typography>
          </TabPanel>
        </Box>
      </Box>
    );
  },
};

// ============================================================================
// Scrollable Tabs
// ============================================================================

export const ScrollableTabs: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box className="max-w-[500px]">
        <Tabs value={value} onChange={(e, v) => setValue(v)} variant="scrollable" scrollButtons="auto">
          {Array.from({ length: 10 }, (_, i) => (
            <Tab key={i} label={`Tab ${i + 1}`} />
          ))}
        </Tabs>
        <TabPanel value={value} index={value}>
          <Typography>Content for Tab {value + 1}</Typography>
        </TabPanel>
      </Box>
    );
  },
};

// ============================================================================
// Full Width
// ============================================================================

export const FullWidthTabs: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box>
        <Tabs value={value} onChange={(e, v) => setValue(v)} variant="fullWidth">
          <Tab label="Overview" />
          <Tab label="Details" />
          <Tab label="Activity" />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Typography>Overview content</Typography>
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Typography>Details content</Typography>
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Typography>Activity content</Typography>
        </TabPanel>
      </Box>
    );
  },
};

// ============================================================================
// Real-World Examples
// ============================================================================

export const DashboardTabs: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box>
        <Typography as="h5" gutterBottom>
          Analytics Dashboard
        </Typography>
        <Tabs value={value} onChange={(e, v) => setValue(v)} shape="soft">
          <Tab label="Overview" startIcon={<HomeIcon />} />
          <Tab label="Users" startIcon={<PersonIcon />} badgeCount={156} badgeColor="info" />
          <Tab label="Messages" startIcon={<MessageIcon />} badgeCount={8} badgeColor="error" />
          <Tab label="Settings" startIcon={<SettingsIcon />} />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Card>
            <CardContent>
              <Typography as="h6" gutterBottom>
                Overview
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Total Users: 1,234
                <br />
                Active Sessions: 89
                <br />
                Revenue: $12,345
              </Typography>
            </CardContent>
          </Card>
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Card>
            <CardContent>
              <Typography as="h6" gutterBottom>
                User Management
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Total users: 156
                <br />
                Active users: 89
                <br />
                New users today: 12
              </Typography>
            </CardContent>
          </Card>
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Card>
            <CardContent>
              <Typography as="h6" gutterBottom>
                Messages
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                You have 8 unread messages
              </Typography>
            </CardContent>
          </Card>
        </TabPanel>
        <TabPanel value={value} index={3}>
          <Card>
            <CardContent>
              <Typography as="h6" gutterBottom>
                Settings
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Configure your dashboard preferences
              </Typography>
            </CardContent>
          </Card>
        </TabPanel>
      </Box>
    );
  },
};

export const ProfileTabs: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box maxWidth={800}>
        <Card>
          <CardContent>
            <Typography as="h5" gutterBottom>
              User Profile
            </Typography>
            <Tabs value={value} onChange={(e, v) => setValue(v)} variant="fullWidth">
              <Tab label="About" />
              <Tab label="Posts" badgeCount={24} />
              <Tab label="Photos" badgeCount={156} />
              <Tab label="Friends" badgeCount={89} />
            </Tabs>
            <TabPanel value={value} index={0}>
              <Typography as="h6">About</Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Bio: Full-stack developer passionate about building great user experiences.
                <br />
                <br />
                Location: San Francisco, CA
                <br />
                Joined: January 2024
              </Typography>
            </TabPanel>
            <TabPanel value={value} index={1}>
              <Typography as="h6">Posts (24)</Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Recent posts and updates from this user
              </Typography>
            </TabPanel>
            <TabPanel value={value} index={2}>
              <Typography as="h6">Photos (156)</Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Photo gallery and albums
              </Typography>
            </TabPanel>
            <TabPanel value={value} index={3}>
              <Typography as="h6">Friends (89)</Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Connections and friend list
              </Typography>
            </TabPanel>
          </CardContent>
        </Card>
      </Box>
    );
  },
};

// ============================================================================
// Accessibility Demo
// ============================================================================

export const AccessibilityDemo: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Stack spacing={4}>
        <Box>
          <Typography as="h6" gutterBottom>
            WCAG 2.1 AA Compliance
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary" paragraph>
            All tabs maintain minimum 44px height for touch targets
          </Typography>
          <Tabs value={value} onChange={(e, v) => setValue(v)} size="sm">
            <Tab label="Small (≥44px)" />
            <Tab label="Still Accessible" />
          </Tabs>
        </Box>

        <Box>
          <Typography as="h6" gutterBottom>
            Keyboard Navigation
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary" paragraph>
            Use Arrow keys to navigate tabs, Home/End to jump to first/last tab
          </Typography>
          <Tabs value={value} onChange={(e, v) => setValue(v)}>
            <Tab label="Tab 1" />
            <Tab label="Tab 2" />
            <Tab label="Tab 3" />
            <Tab label="Tab 4" />
          </Tabs>
        </Box>

        <Box>
          <Typography as="h6" gutterBottom>
            Screen Reader Support
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary" paragraph>
            Tabs use proper ARIA attributes (role, aria-labelledby, aria-controls)
          </Typography>
          <Tabs value={value} onChange={(e, v) => setValue(v)}>
            <Tab label="Overview" startIcon={<HomeIcon />} />
            <Tab label="Details" startIcon={<SettingsIcon />} />
          </Tabs>
          <TabPanel value={value} index={0}>
            <Typography>Overview content with proper tabpanel role</Typography>
          </TabPanel>
          <TabPanel value={value} index={1}>
            <Typography>Details content with proper tabpanel role</Typography>
          </TabPanel>
        </Box>

        <Box>
          <Typography as="h6" gutterBottom>
            Badge Accessibility
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary" paragraph>
            Badges include accessible labels for screen readers
          </Typography>
          <Tabs value={value} onChange={(e, v) => setValue(v)}>
            <Tab label="Messages" badgeCount={5} />
            <Tab label="Notifications" badgeCount={12} badgeColor="error" />
          </Tabs>
        </Box>
      </Stack>
    );
  },
};

// ============================================================================
// Interactive Playground
// ============================================================================

export const Playground: Story = {
  args: {
    shape: 'rounded',
    size: 'medium',
    orientation: 'horizontal',
  },
  render: (args) => {
    const [value, setValue] = useState(0);

    return (
      <Box>
        <Tabs {...args} value={value} onChange={(e, v) => setValue(v)}>
          <Tab label="Tab 1" />
          <Tab label="Tab 2" />
          <Tab label="Tab 3" />
        </Tabs>
        <TabPanel value={value} index={0}>
          <Typography>Content 1</Typography>
        </TabPanel>
        <TabPanel value={value} index={1}>
          <Typography>Content 2</Typography>
        </TabPanel>
        <TabPanel value={value} index={2}>
          <Typography>Content 3</Typography>
        </TabPanel>
      </Box>
    );
  },
};
