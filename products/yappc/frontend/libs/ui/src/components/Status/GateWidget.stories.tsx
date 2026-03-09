import { Box, Stack, Typography } from '@ghatana/ui';
import React from 'react';

import { GateWidget, type GateStatus } from './GateWidget';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta = {
    title: 'UI/Status/GateWidget',
    component: GateWidget,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'GateWidget displays a collection of gate statuses for CI/CD pipelines, quality checks, and deployment gates. It provides an overview of system health with detailed information and links to remediation.',
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        title: {
            control: 'text',
            description: 'Optional title for the widget',
        },
        loading: {
            control: 'boolean',
            description: 'Loading state',
        },
        compact: {
            control: 'boolean',
            description: 'Whether to show compact view (single row)',
        },
        showRefresh: {
            control: 'boolean',
            description: 'Whether to show refresh button',
        },
        maxGates: {
            control: 'number',
            description: 'Maximum number of gates to show before truncating',
        },
    },
} satisfies Meta<typeof GateWidget>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

// Sample gate data
const sampleGates: GateStatus[] = [
    {
        id: 'build-main',
        name: 'Build Pipeline',
        category: 'build',
        status: 'success',
        lastUpdated: new Date(Date.now() - 5 * 60 * 1000).toISOString(), // 5 minutes ago
        message: 'Build completed successfully',
        detailsUrl: 'https://github.com/example/repo/actions/runs/123',
        duration: 145000, // 2m 25s
        required: true,
    },
    {
        id: 'tests-unit',
        name: 'Unit Tests',
        category: 'test',
        status: 'success',
        lastUpdated: new Date(Date.now() - 3 * 60 * 1000).toISOString(), // 3 minutes ago
        message: '47 tests passed',
        detailsUrl: 'https://github.com/example/repo/actions/runs/124',
        duration: 89000, // 1m 29s
        required: true,
    },
    {
        id: 'security-scan',
        name: 'Security Scan',
        category: 'security',
        status: 'warning',
        lastUpdated: new Date(Date.now() - 10 * 60 * 1000).toISOString(), // 10 minutes ago
        message: '2 medium severity vulnerabilities',
        detailsUrl: 'https://github.com/example/repo/security',
        remediationUrl: 'https://docs.example.com/security-guide',
        duration: 45000, // 45s
        required: false,
    },
    {
        id: 'deploy-staging',
        name: 'Deploy to Staging',
        category: 'deploy',
        status: 'running',
        lastUpdated: new Date(Date.now() - 30 * 1000).toISOString(), // 30 seconds ago
        message: 'Deployment in progress',
        detailsUrl: 'https://github.com/example/repo/deployments',
        required: true,
    },
];

const errorGates: GateStatus[] = [
    {
        id: 'build-failed',
        name: 'Build Pipeline',
        category: 'build',
        status: 'error',
        lastUpdated: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
        message: 'Compilation failed: missing dependency',
        detailsUrl: 'https://github.com/example/repo/actions/runs/125',
        duration: 67000,
        required: true,
    },
    {
        id: 'tests-failed',
        name: 'Integration Tests',
        category: 'test',
        status: 'error',
        lastUpdated: new Date(Date.now() - 12 * 60 * 1000).toISOString(),
        message: '3 tests failed',
        detailsUrl: 'https://github.com/example/repo/actions/runs/126',
        duration: 234000,
        required: true,
    },
    {
        id: 'quality-check',
        name: 'Code Quality',
        category: 'quality',
        status: 'warning',
        lastUpdated: new Date(Date.now() - 8 * 60 * 1000).toISOString(),
        message: 'Code coverage below threshold (78%)',
        detailsUrl: 'https://codecov.io/example',
        duration: 12000,
        required: false,
    },
];

const allStatusGates: GateStatus[] = [
    {
        id: 'gate-success',
        name: 'Successful Gate',
        category: 'build',
        status: 'success',
        lastUpdated: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        message: 'All checks passed',
        detailsUrl: '#',
        duration: 120000,
    },
    {
        id: 'gate-error',
        name: 'Failed Gate',
        category: 'test',
        status: 'error',
        lastUpdated: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
        message: 'Critical failures detected',
        detailsUrl: '#',
        duration: 90000,
    },
    {
        id: 'gate-warning',
        name: 'Warning Gate',
        category: 'security',
        status: 'warning',
        lastUpdated: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
        message: 'Minor issues found',
        detailsUrl: '#',
        duration: 45000,
    },
    {
        id: 'gate-pending',
        name: 'Pending Gate',
        category: 'deploy',
        status: 'pending',
        lastUpdated: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
        message: 'Waiting for approval',
        detailsUrl: '#',
    },
    {
        id: 'gate-running',
        name: 'Running Gate',
        category: 'quality',
        status: 'running',
        lastUpdated: new Date(Date.now() - 30 * 1000).toISOString(),
        message: 'Analysis in progress',
        detailsUrl: '#',
    },
    {
        id: 'gate-unknown',
        name: 'Unknown Gate',
        category: 'general',
        status: 'unknown',
        lastUpdated: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
        message: 'Status unavailable',
        detailsUrl: '#',
    },
    {
        id: 'gate-cancelled',
        name: 'Cancelled Gate',
        category: 'build',
        status: 'cancelled',
        lastUpdated: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
        message: 'Cancelled by user',
        detailsUrl: '#',
        duration: 15000,
    },
];

export const Default: Story = {
    args: {
        gates: sampleGates,
        title: 'Quality Gates',
        loading: false,
        compact: false,
        showRefresh: true,
        onRefresh: () => console.log('Refreshing gates...'),
    },
};

export const AllStatuses: Story = {
    args: {
        gates: allStatusGates,
        title: 'All Status Types',
        showRefresh: true,
        onRefresh: () => console.log('Refreshing gates...'),
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget displaying all possible status types with different categories and messages.',
            },
        },
    },
};

export const ErrorState: Story = {
    args: {
        gates: errorGates,
        title: 'Failed Pipeline',
        showRefresh: true,
        onRefresh: () => console.log('Refreshing gates...'),
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget showing a pipeline with errors and warnings that need attention.',
            },
        },
    },
};

export const Loading: Story = {
    args: {
        gates: [],
        title: 'Loading Gates',
        loading: true,
        showRefresh: false,
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget in loading state with skeleton placeholders.',
            },
        },
    },
};

export const Empty: Story = {
    args: {
        gates: [],
        title: 'No Gates Configured',
        loading: false,
        showRefresh: true,
        onRefresh: () => console.log('Refreshing gates...'),
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget when no gates are configured or available.',
            },
        },
    },
};

export const CompactView: Story = {
    args: { gates: [] },
    render: () => (
        <Stack spacing={2} className="w-full max-w-[600px]">
            <Typography as="h6">Compact View Examples</Typography>

            <Box>
                <Typography as="p" className="text-sm font-medium" gutterBottom>
                    Success State
                </Typography>
                <GateWidget
                    gates={sampleGates}
                    title="Pipeline"
                    compact
                    showRefresh={false}
                />
            </Box>

            <Box>
                <Typography as="p" className="text-sm font-medium" gutterBottom>
                    Error State
                </Typography>
                <GateWidget
                    gates={errorGates}
                    title="Failed"
                    compact
                    showRefresh={false}
                />
            </Box>

            <Box>
                <Typography as="p" className="text-sm font-medium" gutterBottom>
                    Loading State
                </Typography>
                <GateWidget
                    gates={[]}
                    title="Loading"
                    compact
                    loading
                    showRefresh={false}
                />
            </Box>

            <Box>
                <Typography as="p" className="text-sm font-medium" gutterBottom>
                    Truncated View (max 3 gates)
                </Typography>
                <GateWidget
                    gates={allStatusGates}
                    title="Gates"
                    compact
                    maxGates={3}
                    showRefresh={false}
                />
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'GateWidget in compact mode for inline display within other components or tight layouts.',
            },
        },
    },
};

export const TruncatedGates: Story = {
    args: {
        gates: allStatusGates,
        title: 'Many Gates (Limited to 4)',
        maxGates: 4,
        showRefresh: true,
        onRefresh: () => console.log('Refreshing gates...'),
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget with many gates truncated to show only the first few, with indication of additional gates.',
            },
        },
    },
};

export const WithoutRefresh: Story = {
    args: {
        gates: sampleGates,
        title: 'Static Gates',
        showRefresh: false,
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget without refresh functionality for read-only contexts.',
            },
        },
    },
};

export const CustomTitle: Story = {
    args: {
        gates: sampleGates,
        title: '🚀 Deployment Pipeline',
        showRefresh: true,
        onRefresh: () => console.log('Refreshing deployment gates...'),
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget with custom title including emojis or special formatting.',
            },
        },
    },
};

export const RealTimeUpdates: Story = {
    args: { gates: [] },
    render: () => {
        const [gates, setGates] = React.useState<GateStatus[]>(sampleGates);
        const [loading, setLoading] = React.useState(false);

        const handleRefresh = React.useCallback(() => {
            setLoading(true);
            // Simulate API call
            setTimeout(() => {
                setGates(prev => prev.map(gate => ({
                    ...gate,
                    lastUpdated: new Date().toISOString(),
                    status: gate.status === 'running' ? 'success' : gate.status,
                })));
                setLoading(false);
            }, 1500);
        }, []);

        React.useEffect(() => {
            const interval = setInterval(() => {
                setGates(prev => prev.map(gate => ({
                    ...gate,
                    lastUpdated: new Date().toISOString(),
                })));
            }, 10000);

            return () => clearInterval(interval);
        }, []);

        return (
            <Stack spacing={2} className="w-full max-w-[500px]">
                <Typography as="h6">Real-time Gate Updates</Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Gates update every 10 seconds. Click refresh to simulate manual update.
                </Typography>
                <GateWidget
                    gates={gates}
                    title="Live Pipeline"
                    loading={loading}
                    showRefresh
                    onRefresh={handleRefresh}
                />
            </Stack>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'GateWidget with simulated real-time updates and manual refresh capability.',
            },
        },
    },
};

export const AccessibilityFeatures: Story = {
    args: { gates: [] },
    render: () => (
        <Stack spacing={2} className="w-full max-w-[500px]">
            <Typography as="h6">Accessibility Features</Typography>
            <Typography as="p" className="text-sm" color="text.secondary">
                Gates include proper ARIA labels, semantic HTML, and keyboard navigation.
                The overall status is calculated and communicated to screen readers.
            </Typography>
            <GateWidget
                gates={sampleGates}
                title="Accessible Gates"
                showRefresh
                onRefresh={() => console.log('Refreshing gates...')}
            />
            <Typography as="p" className="text-sm" color="text.secondary">
                Try using Tab and Enter keys to navigate and interact with the gates.
            </Typography>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'GateWidget includes comprehensive accessibility features including ARIA labels, semantic HTML, and keyboard navigation support.',
            },
        },
    },
};