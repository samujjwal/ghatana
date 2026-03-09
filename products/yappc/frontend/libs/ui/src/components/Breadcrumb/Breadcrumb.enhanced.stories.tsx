/**
 * Enhanced Breadcrumb Stories
 *
 * Comprehensive demonstrations of Breadcrumb molecule component
 */

import { LayoutDashboard as DashboardIcon } from 'lucide-react';
import { FileText as DescriptionIcon } from 'lucide-react';
import { Folder as FolderIcon } from 'lucide-react';
import { Home as HomeIcon } from 'lucide-react';
import { User as PersonIcon } from 'lucide-react';
import { Settings as SettingsIcon } from 'lucide-react';
import { Stack, Box, Typography, Card, CardContent } from '@ghatana/ui';

import { Breadcrumb } from './Breadcrumb.enhanced';

import type { BreadcrumbItem } from './Breadcrumb.enhanced';
import type { Meta, StoryObj } from '@storybook/react';


const meta: Meta<typeof Breadcrumb> = {
  title: 'Molecules/Breadcrumb',
  component: Breadcrumb,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: `
# Breadcrumb (Molecule)

A navigation component showing the current page's location within a navigational hierarchy. Built on MUI Breadcrumbs with design token integration.

## Features
- ✅ WCAG 2.1 AA compliant
- ✅ Design token integration
- ✅ Three size variants (small, medium, large)
- ✅ Multiple separator options (slash, chevron, dot, custom)
- ✅ Optional home icon
- ✅ Icon support for items
- ✅ Keyboard navigation
- ✅ Accessible ARIA attributes
- ✅ Collapsible for long paths (maxItems)

## Design Tokens Used
- **Border Radius**: \`borderRadiusSm\` (4px)
- **Spacing**: \`spacingXs\` (4px), \`spacingSm\` (8px)
- **Typography**: \`fontSizeSm\` (0.875rem), \`fontWeightMedium\` (500)
        `,
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    separator: {
      control: 'select',
      options: ['slash', 'chevron', 'dot'],
      description: 'Separator between breadcrumb items',
    },
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
      description: 'Breadcrumb size variant',
    },
    showHomeIcon: {
      control: 'boolean',
      description: 'Show home icon for first item',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Breadcrumb>;

// Sample data
const basicItems: BreadcrumbItem[] = [
  { label: 'Home', href: '/' },
  { label: 'Products', href: '/products' },
  { label: 'Details' },
];

const deepItems: BreadcrumbItem[] = [
  { label: 'Home', href: '/' },
  { label: 'Electronics', href: '/electronics' },
  { label: 'Computers', href: '/electronics/computers' },
  { label: 'Laptops', href: '/electronics/computers/laptops' },
  { label: 'Gaming Laptops', href: '/electronics/computers/laptops/gaming' },
  { label: 'Product Details' },
];

const itemsWithIcons: BreadcrumbItem[] = [
  { label: 'Home', href: '/', icon: <HomeIcon size={16} /> },
  { label: 'Documents', href: '/documents', icon: <FolderIcon size={16} /> },
  { label: 'Report.pdf', icon: <DescriptionIcon size={16} /> },
];

// ============================================================================
// Basic Examples
// ============================================================================

export const Default: Story = {
  args: {
    items: basicItems,
  },
};

export const WithHomeIcon: Story = {
  args: {
    items: basicItems,
    showHomeIcon: true,
  },
};

export const WithIcons: Story = {
  args: {
    items: itemsWithIcons,
  },
};

export const WithClickHandler: Story = {
  args: {
    items: [
      { label: 'Home', onClick: (e) => { e.preventDefault(); alert('Home clicked'); } },
      { label: 'Products', onClick: (e) => { e.preventDefault(); alert('Products clicked'); } },
      { label: 'Details' },
    ],
  },
};

// ============================================================================
// Separator Variants
// ============================================================================

export const SeparatorVariants: Story = {
  render: () => (
    <Stack spacing={3}>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Chevron (default) - NavigateNext icon
        </Typography>
        <Breadcrumb items={basicItems} separator="chevron" />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Slash - / separator
        </Typography>
        <Breadcrumb items={basicItems} separator="slash" />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Dot - • separator
        </Typography>
        <Breadcrumb items={basicItems} separator="dot" />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Custom - any ReactNode
        </Typography>
        <Breadcrumb items={basicItems} separator="→" />
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Size Variants
// ============================================================================

export const SizeVariants: Story = {
  render: () => (
    <Stack spacing={3}>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Small (0.75rem)
        </Typography>
        <Breadcrumb items={basicItems} size="sm" />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Medium (0.875rem - default)
        </Typography>
        <Breadcrumb items={basicItems} size="md" />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Large (1rem)
        </Typography>
        <Breadcrumb items={basicItems} size="lg" />
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Long Paths
// ============================================================================

export const LongPath: Story = {
  args: {
    items: deepItems,
    showHomeIcon: true,
  },
};

export const CollapsedPath: Story = {
  args: {
    items: deepItems,
    maxItems: 4,
    showHomeIcon: true,
  },
};

// ============================================================================
// Real-World Examples
// ============================================================================

export const FileSystemNavigation: Story = {
  render: () => {
    const items: BreadcrumbItem[] = [
      { label: 'Root', href: '/', icon: <HomeIcon size={16} /> },
      { label: 'Documents', href: '/documents', icon: <FolderIcon size={16} /> },
      { label: 'Work', href: '/documents/work', icon: <FolderIcon size={16} /> },
      { label: 'Projects', href: '/documents/work/projects', icon: <FolderIcon size={16} /> },
      { label: 'Report.docx', icon: <DescriptionIcon size={16} /> },
    ];

    return (
      <Card>
        <CardContent>
          <Typography as="h6" gutterBottom>
            File Browser
          </Typography>
          <Breadcrumb items={items} />
        </CardContent>
      </Card>
    );
  },
};

export const EcommerceCatalog: Story = {
  render: () => {
    const items: BreadcrumbItem[] = [
      { label: 'Home', href: '/' },
      { label: 'Electronics', href: '/electronics' },
      { label: 'Computers', href: '/electronics/computers' },
      { label: 'Laptops', href: '/electronics/computers/laptops' },
      { label: 'Gaming Laptops' },
    ];

    return (
      <Box>
        <Typography as="h5" gutterBottom>
          Product Category
        </Typography>
        <Breadcrumb items={items} showHomeIcon size="lg" />
        <Card className="mt-4">
          <CardContent>
            <Typography as="h6">Gaming Laptops</Typography>
            <Typography as="p" className="text-sm" color="text.secondary">
              Browse our collection of high-performance gaming laptops
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  },
};

export const DashboardNavigation: Story = {
  render: () => {
    const items: BreadcrumbItem[] = [
      { label: 'Dashboard', href: '/dashboard', icon: <DashboardIcon size={16} /> },
      { label: 'Users', href: '/dashboard/users', icon: <PersonIcon size={16} /> },
      { label: 'Profile', icon: <PersonIcon size={16} /> },
    ];

    return (
      <Box>
        <Breadcrumb items={items} separator="slash" />
        <Card className="mt-4">
          <CardContent>
            <Typography as="h6">User Profile</Typography>
            <Typography as="p" className="text-sm" color="text.secondary">
              View and manage user information
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  },
};

export const SettingsPages: Story = {
  render: () => {
    const items: BreadcrumbItem[] = [
      { label: 'Settings', href: '/settings', icon: <SettingsIcon size={16} /> },
      { label: 'Account', href: '/settings/account' },
      { label: 'Security' },
    ];

    return (
      <Box>
        <Typography as="h5" gutterBottom>
          Account Security
        </Typography>
        <Breadcrumb items={items} showHomeIcon={false} size="md" />
        <Card className="mt-4">
          <CardContent>
            <Typography as="h6">Security Settings</Typography>
            <Typography as="p" className="text-sm" color="text.secondary">
              Manage your account security preferences
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  },
};

export const DocumentationPages: Story = {
  render: () => {
    const items: BreadcrumbItem[] = [
      { label: 'Docs', href: '/docs' },
      { label: 'Components', href: '/docs/components' },
      { label: 'Navigation', href: '/docs/components/navigation' },
      { label: 'Breadcrumb' },
    ];

    return (
      <Box>
        <Breadcrumb items={items} separator="chevron" size="sm" />
        <Card className="mt-4 bg-[#f5f5f5]">
          <CardContent>
            <Typography as="h6">Breadcrumb Component</Typography>
            <Typography as="p" className="text-sm" color="text.secondary">
              A navigation component showing the current page's location
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  },
};

// ============================================================================
// Disabled Links
// ============================================================================

export const WithDisabledLinks: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'Disabled Section', href: '/disabled', disabled: true },
      { label: 'Current Page' },
    ],
  },
};

// ============================================================================
// Responsive Behavior
// ============================================================================

export const ResponsiveBreadcrumb: Story = {
  render: () => {
    const items: BreadcrumbItem[] = [
      { label: 'Home', href: '/' },
      { label: 'Level 1', href: '/level1' },
      { label: 'Level 2', href: '/level1/level2' },
      { label: 'Level 3', href: '/level1/level2/level3' },
      { label: 'Level 4', href: '/level1/level2/level3/level4' },
      { label: 'Current Page' },
    ];

    return (
      <Stack spacing={3}>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Full path (Desktop)
          </Typography>
          <Breadcrumb items={items} showHomeIcon />
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Collapsed path (Mobile - maxItems=4)
          </Typography>
          <Breadcrumb items={items} maxItems={4} showHomeIcon />
        </Box>
        <Box>
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Minimal path (Small screens - maxItems=3)
          </Typography>
          <Breadcrumb items={items} maxItems={3} showHomeIcon />
        </Box>
      </Stack>
    );
  },
};

// ============================================================================
// Accessibility Demo
// ============================================================================

export const AccessibilityDemo: Story = {
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography as="h6" gutterBottom>
          Semantic Navigation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Uses <code>&lt;nav&gt;</code> with aria-label="breadcrumb"
        </Typography>
        <Breadcrumb
          items={[
            { label: 'Home', href: '/' },
            { label: 'Products', href: '/products' },
            { label: 'Current' },
          ]}
        />
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Current Page Indication
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Last item uses aria-current="page"
        </Typography>
        <Breadcrumb
          items={[
            { label: 'Dashboard', href: '/dashboard' },
            { label: 'Reports', href: '/reports' },
            { label: 'Monthly Report' },
          ]}
          separator="slash"
        />
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Keyboard Navigation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Tab to focus links, Enter to activate
        </Typography>
        <Breadcrumb
          items={[
            { label: 'Home', href: '#' },
            { label: 'Section 1', href: '#' },
            { label: 'Section 2', href: '#' },
            { label: 'Current' },
          ]}
        />
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Icon Accessibility
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Icons marked as aria-hidden="true" (decorative)
        </Typography>
        <Breadcrumb
          showHomeIcon
          items={[
            { label: 'Home', href: '#' },
            { label: 'Documents', href: '#', icon: <FolderIcon size={16} /> },
            { label: 'File.pdf', icon: <DescriptionIcon size={16} /> },
          ]}
        />
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Focus Indicators
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Visible focus outline on keyboard navigation
        </Typography>
        <Breadcrumb
          items={[
            { label: 'Tab to me', href: '#' },
            { label: 'And to me', href: '#' },
            { label: 'Focus visible' },
          ]}
          size="lg"
        />
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Interactive Playground
// ============================================================================

export const Playground: Story = {
  args: {
    items: basicItems,
    separator: 'chevron',
    size: 'medium',
    showHomeIcon: false,
  },
};
