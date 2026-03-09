import { Box, Stack, Typography } from '@ghatana/ui';

import { StatusBadge } from './StatusBadge';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta = {
    title: 'UI/Status/StatusBadge',
    component: StatusBadge,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'StatusBadge displays the status of various system operations like builds, tests, deployments, etc. It supports different states, categories, variants and provides consistent visual feedback across the application.',
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        status: {
            control: 'select',
            options: ['success', 'error', 'warning', 'pending', 'running', 'unknown', 'cancelled'],
            description: 'The status type that determines color and icon',
        },
        category: {
            control: 'select',
            options: ['build', 'test', 'deploy', 'security', 'quality', 'general'],
            description: 'Optional category for additional context and iconography',
        },
        variant: {
            control: 'select',
            options: ['filled', 'outlined', 'soft'],
            description: 'Visual variant of the badge',
        },
        size: {
            control: 'select',
            options: ['small', 'medium', 'large'],
            description: 'Size of the badge',
        },
        showIcon: {
            control: 'boolean',
            description: 'Whether to show the status icon',
        },
        animated: {
            control: 'boolean',
            description: 'Whether to pulse for running/pending states',
        },
        tooltip: {
            control: 'text',
            description: 'Optional tooltip text - if not provided, a default tooltip will be generated',
        },
    },
} satisfies Meta<typeof StatusBadge>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    args: {
        status: 'success',
        category: 'build',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
};

export const AllStatuses: Story = {
    args: {
        status: 'success',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={3}>
            <Box>
                <Typography as="h6" gutterBottom>
                    All Status Types
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                    <StatusBadge status="success" />
                    <StatusBadge status="error" />
                    <StatusBadge status="warning" />
                    <StatusBadge status="pending" />
                    <StatusBadge status="running" animated />
                    <StatusBadge status="unknown" />
                    <StatusBadge status="cancelled" />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'All available status types with their default styling.',
            },
        },
    },
};

export const AllCategories: Story = {
    args: {
        status: 'success',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={2}>
            <Box>
                <Typography as="h6" gutterBottom>
                    Categories with Success Status
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                    <StatusBadge status="success" category="build" />
                    <StatusBadge status="success" category="test" />
                    <StatusBadge status="success" category="deploy" />
                    <StatusBadge status="success" category="security" />
                    <StatusBadge status="success" category="quality" />
                    <StatusBadge status="success" category="general" />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Categories with Error Status
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                    <StatusBadge status="error" category="build" />
                    <StatusBadge status="error" category="test" />
                    <StatusBadge status="error" category="deploy" />
                    <StatusBadge status="error" category="security" />
                    <StatusBadge status="error" category="quality" />
                    <StatusBadge status="error" category="general" />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'StatusBadge with different categories showing how icons change based on category.',
            },
        },
    },
};

export const AllVariants: Story = {
    args: {
        status: 'success',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={3}>
            <Box>
                <Typography as="h6" gutterBottom>
                    Filled Variant (Default)
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="success" variant="filled" />
                    <StatusBadge status="error" variant="filled" />
                    <StatusBadge status="warning" variant="filled" />
                    <StatusBadge status="pending" variant="filled" />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Outlined Variant
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="success" variant="outlined" />
                    <StatusBadge status="error" variant="outlined" />
                    <StatusBadge status="warning" variant="outlined" />
                    <StatusBadge status="pending" variant="outlined" />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Soft Variant
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="success" variant="soft" />
                    <StatusBadge status="error" variant="soft" />
                    <StatusBadge status="warning" variant="soft" />
                    <StatusBadge status="pending" variant="soft" />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'Different visual variants of the StatusBadge component.',
            },
        },
    },
};

export const AllSizes: Story = {
    args: {
        status: 'success',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={3}>
            <Box>
                <Typography as="h6" gutterBottom>
                    Small Size
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                    <StatusBadge status="success" size="sm" />
                    <StatusBadge status="error" size="sm" />
                    <StatusBadge status="warning" size="sm" />
                    <StatusBadge status="pending" size="sm" />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Medium Size (Default)
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                    <StatusBadge status="success" size="md" />
                    <StatusBadge status="error" size="md" />
                    <StatusBadge status="warning" size="md" />
                    <StatusBadge status="pending" size="md" />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Large Size
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                    <StatusBadge status="success" size="lg" />
                    <StatusBadge status="error" size="lg" />
                    <StatusBadge status="warning" size="lg" />
                    <StatusBadge status="pending" size="lg" />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'StatusBadge in different sizes for various UI contexts.',
            },
        },
    },
};

export const AnimatedStates: Story = {
    args: {
        status: 'running',
        showIcon: true,
        animated: true,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={2}>
            <Box>
                <Typography as="h6" gutterBottom>
                    Animated Running/Pending States
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="running" animated category="build" />
                    <StatusBadge status="pending" animated category="test" />
                    <StatusBadge status="running" animated category="deploy" />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Static States (No Animation)
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="running" category="build" />
                    <StatusBadge status="pending" category="test" />
                    <StatusBadge status="running" category="deploy" />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'StatusBadge with animation enabled for running and pending states.',
            },
        },
    },
};

export const WithoutIcons: Story = {
    args: {
        status: 'success',
        showIcon: false,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={2}>
            <Box>
                <Typography as="h6" gutterBottom>
                    With Icons (Default)
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="success" showIcon={true} />
                    <StatusBadge status="error" showIcon={true} />
                    <StatusBadge status="warning" showIcon={true} />
                    <StatusBadge status="pending" showIcon={true} />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Without Icons
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="success" showIcon={false} />
                    <StatusBadge status="error" showIcon={false} />
                    <StatusBadge status="warning" showIcon={false} />
                    <StatusBadge status="pending" showIcon={false} />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'StatusBadge with and without icons for different use cases.',
            },
        },
    },
};

export const CustomLabelsAndTooltips: Story = {
    args: {
        status: 'success',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={2}>
            <Box>
                <Typography as="h6" gutterBottom>
                    Custom Labels
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge status="success" label="Passed" category="build" />
                    <StatusBadge status="error" label="Failed" category="test" />
                    <StatusBadge status="warning" label="Issues" category="security" />
                    <StatusBadge status="running" label="Deploying..." category="deploy" animated />
                </Stack>
            </Box>
            <Box>
                <Typography as="h6" gutterBottom>
                    Custom Tooltips (Hover to see)
                </Typography>
                <Stack direction="row" spacing={1}>
                    <StatusBadge
                        status="success"
                        category="build"
                        tooltip="Build completed successfully in 2m 34s"
                    />
                    <StatusBadge
                        status="error"
                        category="test"
                        tooltip="3 tests failed out of 47 total tests"
                    />
                    <StatusBadge
                        status="warning"
                        category="security"
                        tooltip="2 medium severity vulnerabilities found"
                    />
                </Stack>
            </Box>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'StatusBadge with custom labels and detailed tooltips for better user experience.',
            },
        },
    },
};

export const AccessibilityFeatures: Story = {
    args: {
        status: 'success',
        showIcon: true,
        animated: false,
        size: 'medium',
        variant: 'filled',
    },
    render: () => (
        <Stack spacing={2}>
            <Typography as="h6" gutterBottom>
                Accessibility Features
            </Typography>
            <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                All badges have proper ARIA labels and role attributes for screen readers.
                Focus outlines are visible when navigating with keyboard.
            </Typography>
            <Stack direction="row" spacing={1}>
                <StatusBadge
                    status="success"
                    category="build"
                    aria-label="Build status: Success - All checks passed"
                />
                <StatusBadge
                    status="error"
                    category="test"
                    aria-label="Test status: Error - 3 failures detected"
                />
                <StatusBadge
                    status="warning"
                    category="security"
                    aria-label="Security status: Warning - Vulnerabilities found"
                />
                <StatusBadge
                    status="running"
                    category="deploy"
                    animated
                    aria-label="Deploy status: Running - Deployment in progress"
                />
            </Stack>
            <Typography as="p" className="text-sm" color="text.secondary">
                Try using Tab key to focus on badges and see the focus indicators.
            </Typography>
        </Stack>
    ),
    parameters: {
        docs: {
            description: {
                story: 'StatusBadge includes built-in accessibility features including ARIA labels, role attributes, and keyboard focus indicators.',
            },
        },
    },
};