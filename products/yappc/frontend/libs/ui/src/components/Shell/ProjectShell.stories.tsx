import { Settings as SettingsIcon, Hammer as BuildIcon, BarChart3 as AssessmentIcon, Rocket as DeployIcon, Monitor as MonitorIcon, Folder as FolderIcon, Plus as AddIcon, RefreshCw as RefreshIcon } from 'lucide-react';
import { Box, Typography } from '@ghatana/ui';

import { ProjectShell } from './ProjectShell';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta = {
    title: 'Components/Shell/ProjectShell',
    component: ProjectShell,
    parameters: {
        layout: 'fullscreen',
        docs: {
            description: {
                component: 'A comprehensive project shell layout component that provides navigation tabs, breadcrumbs, and responsive design for project pages.',
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        title: {
            control: 'text',
            description: 'Project title displayed in the header',
        },
        description: {
            control: 'text',
            description: 'Optional project description or subtitle',
        },
        activeTab: {
            control: 'select',
            options: ['overview', 'canvas', 'backlog', 'design', 'build', 'test', 'deploy', 'monitor'],
            description: 'Currently active tab identifier',
        },
        showBackButton: {
            control: 'boolean',
            description: 'Whether to show the back navigation button',
        },
        isLoading: {
            control: 'boolean',
            description: 'Loading state for the shell content',
        },
    },
} satisfies Meta<typeof ProjectShell>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

// Mock tab items for stories
const mockTabs = [
    {
        id: 'overview',
        label: 'Overview',
        path: '/project/overview',
        icon: <AssessmentIcon size={16} />,
    },
    {
        id: 'canvas',
        label: 'Canvas',
        path: '/project/canvas',
        icon: <FolderIcon size={16} />,
    },
    {
        id: 'backlog',
        label: 'Backlog',
        path: '/project/backlog',
        badge: <Box component="span" className="text-xs text-center bg-blue-600 text-white rounded-[10px] p-[2px 6px] min-w-[18px]">5</Box>,
    },
    {
        id: 'design',
        label: 'Design',
        path: '/project/design',
    },
    {
        id: 'build',
        label: 'Build',
        path: '/project/build',
        icon: <BuildIcon size={16} />,
    },
    {
        id: 'test',
        label: 'Test',
        path: '/project/test',
    },
    {
        id: 'deploy',
        label: 'Deploy',
        path: '/project/deploy',
        icon: <DeployIcon size={16} />,
    },
    {
        id: 'monitor',
        label: 'Monitor',
        path: '/project/monitor',
        icon: <MonitorIcon size={16} />,
    },
];

// Mock breadcrumbs
const mockBreadcrumbs = [
    { label: 'Workspace', href: '/workspace', icon: <FolderIcon size={16} /> },
    { label: 'My Project', href: '/project' },
];

// Mock actions
const mockActions = [
    {
        id: 'add',
        label: 'Add Item',
        icon: <AddIcon />,
        onClick: () => console.log('Add clicked'),
    },
    {
        id: 'refresh',
        label: 'Refresh',
        icon: <RefreshIcon />,
        onClick: () => console.log('Refresh clicked'),
    },
    {
        id: 'settings',
        label: 'Settings',
        icon: <SettingsIcon />,
        onClick: () => console.log('Settings clicked'),
    },
];

// Sample content component
const SampleContent = ({ title }: { title: string }) => (
    <Box className="p-6">
        <Typography as="h5" gutterBottom>
            {title} Content
        </Typography>
        <Typography color="text.secondary" paragraph>
            This is sample content for the {title.toLowerCase()} tab. In a real application,
            this would be replaced with the actual page content.
        </Typography>
        <Box className="rounded flex items-center justify-center h-[400px] bg-gray-100" >
            <Typography color="text.secondary">
                {title} Page Content Area
            </Typography>
        </Box>
    </Box>
);

export const Default: Story = {
    args: {
        title: 'E-commerce Platform',
        description: 'Full-stack web application with React and Node.js',
        tabs: mockTabs,
        activeTab: 'overview',
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Overview" />,
    },
};

export const WithBreadcrumbs: Story = {
    args: {
        title: 'E-commerce Platform',
        description: 'Full-stack web application with React and Node.js',
        breadcrumbs: mockBreadcrumbs,
        tabs: mockTabs,
        activeTab: 'canvas',
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Canvas" />,
    },
};

export const WithActions: Story = {
    args: {
        title: 'E-commerce Platform',
        description: 'Full-stack web application with React and Node.js',
        breadcrumbs: mockBreadcrumbs,
        tabs: mockTabs,
        activeTab: 'backlog',
        actions: mockActions,
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Backlog" />,
    },
};

export const WithBackButton: Story = {
    args: {
        title: 'Project Settings',
        description: 'Configure your project settings and preferences',
        breadcrumbs: [
            ...mockBreadcrumbs,
            { label: 'Settings' },
        ],
        tabs: [
            { id: 'general', label: 'General', path: '/settings/general' },
            { id: 'security', label: 'Security', path: '/settings/security' },
            { id: 'integrations', label: 'Integrations', path: '/settings/integrations' },
        ],
        activeTab: 'general',
        showBackButton: true,
        onBackClick: () => console.log('Back clicked'),
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="General Settings" />,
    },
};

export const LoadingState: Story = {
    args: {
        title: 'Loading Project...',
        tabs: mockTabs,
        activeTab: 'overview',
        isLoading: true,
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Overview" />,
    },
};

export const MinimalShell: Story = {
    args: {
        title: 'Simple Project',
        tabs: [
            { id: 'home', label: 'Home', path: '/' },
            { id: 'about', label: 'About', path: '/about' },
            { id: 'contact', label: 'Contact', path: '/contact' },
        ],
        activeTab: 'home',
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Home" />,
    },
};

export const ManyTabs: Story = {
    args: {
        title: 'Complex Project',
        description: 'Project with many navigation tabs to test scrolling behavior',
        tabs: [
            ...mockTabs,
            { id: 'versions', label: 'Versions', path: '/project/versions' },
            { id: 'settings', label: 'Settings', path: '/project/settings' },
            { id: 'team', label: 'Team', path: '/project/team' },
            { id: 'analytics', label: 'Analytics', path: '/project/analytics' },
            { id: 'documentation', label: 'Documentation', path: '/project/docs' },
        ],
        activeTab: 'analytics',
        breadcrumbs: mockBreadcrumbs,
        actions: mockActions,
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Analytics" />,
    },
};

export const CustomHeaderContent: Story = {
    args: {
        title: 'Project with Custom Header',
        description: 'Example showing custom header content area',
        tabs: mockTabs,
        activeTab: 'build',
        headerContent: (
            <Box className="p-4 rounded mb-4 bg-blue-100 text-white">
                <Typography as="p" className="text-sm">
                    🚀 Build Status: Success • Last deployed 5 minutes ago
                </Typography>
            </Box>
        ),
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: <SampleContent title="Build" />,
    },
};

// Accessibility story
export const AccessibilityFeatures: Story = {
    args: {
        title: 'Accessible Project Shell',
        description: 'Demonstrating accessibility features and keyboard navigation',
        breadcrumbs: mockBreadcrumbs,
        tabs: mockTabs,
        activeTab: 'test',
        actions: mockActions,
        onTabChange: (tabId: string) => console.log('Tab changed to:', tabId),
        children: (
            <Box className="p-6">
                <Typography as="h5" gutterBottom>
                    Accessibility Testing Page
                </Typography>
                <Typography paragraph>
                    This shell component includes the following accessibility features:
                </Typography>
                <ul>
                    <li>Proper ARIA labels and roles</li>
                    <li>Keyboard navigation support (Tab, Arrow keys, Enter, Space)</li>
                    <li>Screen reader compatibility</li>
                    <li>Focus management and visible focus indicators</li>
                    <li>Semantic HTML structure</li>
                    <li>Color contrast compliance</li>
                </ul>
            </Box>
        ),
    },
};