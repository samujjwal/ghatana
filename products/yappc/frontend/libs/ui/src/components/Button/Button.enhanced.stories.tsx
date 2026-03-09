/**
 * Enhanced Button Component Stories
 *
 * Comprehensive Storybook documentation for the Button component with design tokens.
 * Demonstrates all variants, sizes, shapes, states, and accessibility features.
 *
 * @packageDocumentation
 */

import { Stack, Box, Typography } from '@ghatana/ui';

// Use the folder entry so we pick up the correct implementation re-export
// (Button.tailwind) via the component index.
import { Button } from '.';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Button> = {
  title: 'Atoms/Button (Enhanced)',
  component: Button,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: `
# Button Component

A highly composable, accessible button component with full design token integration.

## Features
- ✅ WCAG 2.1 AA compliant (minimum 44px touch targets)
- ✅ Full keyboard navigation support
- ✅ Loading and disabled states
- ✅ Multiple variants (text, outlined, contained)
- ✅ Size options (small, medium, large)
- ✅ Shape options (rounded, square, pill)
- ✅ Tooltip integration
- ✅ Icon support (start/end)
- ✅ Design token based styling

## Design Tokens Used
- Border Radius: \`borderRadiusSm\` (4px), \`borderRadiusMd\` (8px), \`borderRadiusFull\` (9999px)
- Spacing: \`spacingSm\` (8px), \`spacingMd\` (16px), \`spacingLg\` (24px)
- Typography: \`fontWeightMedium\`, \`fontWeightSemibold\`

## Accessibility
- Minimum 44px touch target for medium/large buttons
- 36px minimum for small buttons (use sparingly)
- Full keyboard navigation (Enter/Space)
- Proper ARIA attributes
- High contrast mode support
- Reduced motion support
        `,
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    children: {
      control: 'text',
      description: 'Button label text or content',
      table: {
        type: { summary: 'ReactNode' },
      },
    },
    variant: {
      control: 'select',
      options: ['text', 'outlined', 'contained'],
      description: 'Button visual style variant',
      table: {
        type: { summary: "'text' | 'outlined' | 'contained'" },
        defaultValue: { summary: 'contained' },
      },
    },
    color: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'info', 'warning'],
      description: 'Button color from theme palette',
      table: {
        type: { summary: 'string' },
        defaultValue: { summary: 'primary' },
      },
    },
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
      description: 'Button size (affects padding and minimum height)',
      table: {
        type: { summary: "'small' | 'medium' | 'large'" },
        defaultValue: { summary: 'medium' },
      },
    },
    shape: {
      control: 'select',
      options: ['rounded', 'square', 'pill'],
      description: 'Button border radius shape',
      table: {
        type: { summary: "'rounded' | 'square' | 'pill'" },
        defaultValue: { summary: 'rounded' },
      },
    },
    elevation: {
      control: { type: 'range', min: 0, max: 8, step: 1 },
      description: 'Shadow elevation (0 = no shadow)',
      table: {
        type: { summary: 'number' },
        defaultValue: { summary: 1 },
      },
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: false },
      },
    },
    fullWidth: {
      control: 'boolean',
      description: 'Full width button (100% of container)',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: false },
      },
    },
    disableRipple: {
      control: 'boolean',
      description: 'Disable ripple effect on click',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: false },
      },
    },
    tooltip: {
      control: 'text',
      description: 'Tooltip text (shown on hover)',
      table: {
        type: { summary: 'string' },
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Button>;

// ============================================================================
// Basic Stories
// ============================================================================

export const Default: Story = {
  args: {
    children: 'Button',
    variant: 'contained',
    color: 'primary',
  },
};

export const Text: Story = {
  args: {
    children: 'Text Button',
    variant: 'text',
    color: 'primary',
  },
};

export const Outlined: Story = {
  args: {
    children: 'Outlined Button',
    variant: 'outlined',
    color: 'primary',
  },
};

export const Contained: Story = {
  args: {
    children: 'Contained Button',
    variant: 'contained',
    color: 'primary',
  },
};

// ============================================================================
// Sizes Comparison
// ============================================================================

export const AllSizes: Story = {
  render: () => (
    <Stack spacing={2} alignItems="flex-start">
      <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
        Small (36px minimum) - Use sparingly for dense UIs
      </Typography>
      <Button variant="solid" size="sm">
        Small Button
      </Button>

      <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-4">
        Medium (44px minimum) - Recommended for accessibility
      </Typography>
      <Button variant="solid" size="md">
        Medium Button
      </Button>

      <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-4">
        Large (48px minimum) - For prominent actions
      </Typography>
      <Button variant="solid" size="lg">
        Large Button
      </Button>
    </Stack>
  ),
};

// ============================================================================
// Colors
// ============================================================================

export const AllColors: Story = {
  render: () => (
    <Stack spacing={3}>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Contained Variant
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button variant="solid" tone="primary">
            Primary
          </Button>
          <Button variant="solid" tone="secondary">
            Secondary
          </Button>
          <Button variant="solid" tone="success">
            Success
          </Button>
          <Button variant="solid" tone="danger">
            Error
          </Button>
          <Button variant="solid" tone="info">
            Info
          </Button>
          <Button variant="solid" tone="warning">
            Warning
          </Button>
        </Stack>
      </Box>

      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Outlined Variant
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button variant="outlined" tone="primary">
            Primary
          </Button>
          <Button variant="outlined" tone="secondary">
            Secondary
          </Button>
          <Button variant="outlined" tone="success">
            Success
          </Button>
          <Button variant="outlined" tone="danger">
            Error
          </Button>
          <Button variant="outlined" tone="info">
            Info
          </Button>
          <Button variant="outlined" tone="warning">
            Warning
          </Button>
        </Stack>
      </Box>

      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Text Variant
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button variant="ghost" tone="primary">
            Primary
          </Button>
          <Button variant="ghost" tone="secondary">
            Secondary
          </Button>
          <Button variant="ghost" tone="success">
            Success
          </Button>
          <Button variant="ghost" tone="danger">
            Error
          </Button>
          <Button variant="ghost" tone="info">
            Info
          </Button>
          <Button variant="ghost" tone="warning">
            Warning
          </Button>
        </Stack>
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Shapes (Design Token Integration)
// ============================================================================

export const AllShapes: Story = {
  render: () => (
    <Stack spacing={2} alignItems="flex-start">
      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          Square: borderRadiusSm (4px)
        </Typography>
        <Button variant="solid" shape="square">
          Square Button
        </Button>
      </Box>

      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          Rounded: borderRadiusMd (8px) - Default
        </Typography>
        <Button variant="solid" shape="rounded">
          Rounded Button
        </Button>
      </Box>

      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          Pill: borderRadiusFull (9999px)
        </Typography>
        <Button variant="solid" shape="pill">
          Pill Button
        </Button>
      </Box>
    </Stack>
  ),
};

// ============================================================================
// States
// ============================================================================

export const AllStates: Story = {
  render: () => (
    <Stack spacing={2} alignItems="flex-start">
      <Button variant="solid">Normal State</Button>
      <Button variant="solid" disabled>
        Disabled State
      </Button>
    </Stack>
  ),
};

// ============================================================================
// Special Features
// ============================================================================

export const FullWidth: Story = {
  render: () => (
    <Box width={400}>
      <Stack spacing={2}>
        <Button variant="solid" fullWidth>
          Full Width Contained
        </Button>
        <Button variant="outlined" fullWidth>
          Full Width Outlined
        </Button>
        <Button variant="ghost" fullWidth>
          Full Width Text
        </Button>
      </Stack>
    </Box>
  ),
};

export const WithTooltip: Story = {
  args: {
    children: 'Hover for Tooltip',
    variant: 'contained',
    tooltip: 'This is a helpful tooltip explaining the button action!',
    tooltipPlacement: 'top',
  },
};

export const WithElevation: Story = {
  render: () => (
    <Stack spacing={2} alignItems="flex-start">
      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          variant="flat"
        </Typography>
        <Button variant="solid" variant="flat">
          No Shadow
        </Button>
      </Box>

      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          variant="raised" - Default
        </Typography>
        <Button variant="solid" variant="raised">
          Elevation 1
        </Button>
      </Box>

      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          elevation={4}
        </Typography>
        <Button variant="solid" elevation={4}>
          Elevation 4
        </Button>
      </Box>

      <Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" gutterBottom>
          elevation={8}
        </Typography>
        <Button variant="solid" elevation={8}>
          Elevation 8
        </Button>
      </Box>
    </Stack>
  ),
};

export const NoRipple: Story = {
  args: {
    children: 'No Ripple Effect',
    variant: 'contained',
    disableRipple: true,
  },
};

// ============================================================================
// Real-world Examples
// ============================================================================

export const FormActions: Story = {
  name: 'Example: Form Actions',
  render: () => (
    <Stack direction="row" spacing={2}>
      <Button variant="solid" tone="primary">
        Save Changes
      </Button>
      <Button variant="outlined">Cancel</Button>
      <Button variant="ghost" tone="danger">
        Reset Form
      </Button>
    </Stack>
  ),
};

export const CallToAction: Story = {
  name: 'Example: Call to Action',
  render: () => (
    <Stack spacing={2} alignItems="center">
      <Button variant="solid" size="lg" tone="primary" shape="pill">
        Get Started Free
      </Button>
      <Button variant="ghost" size="sm">
        Learn more
      </Button>
    </Stack>
  ),
};

export const ButtonGroup: Story = {
  name: 'Example: Button Group',
  render: () => (
    <Stack direction="row" spacing={0}>
      <Button variant="outlined" shape="square" className="rounded-[4px 0 0 4px]">
        Left
      </Button>
      <Button variant="outlined" shape="square" className="rounded-none border-l-0">
        Center
      </Button>
      <Button
        variant="outlined"
        shape="square"
        className="border-l-0 rounded-[0 4px 4px 0px]" >
        Right
      </Button>
    </Stack>
  ),
};

// ============================================================================
// Accessibility Showcase
// ============================================================================

export const AccessibilityDemo: Story = {
  name: 'Accessibility Features',
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography as="h6" gutterBottom>
          Keyboard Navigation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Tab to focus buttons, Enter or Space to activate
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button variant="solid">Button 1</Button>
          <Button variant="solid">Button 2</Button>
          <Button variant="solid">Button 3</Button>
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Touch Targets (WCAG 2.1)
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Medium and Large buttons meet the 44px minimum touch target requirement
        </Typography>
        <Stack direction="row" spacing={2} alignItems="center">
          <Button variant="solid" size="sm">
            36px (use sparingly)
          </Button>
          <Button variant="solid" size="md">
            44px (recommended)
          </Button>
          <Button variant="solid" size="lg">
            48px (prominent)
          </Button>
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Screen Reader Support
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Proper ARIA labels for assistive technology
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button variant="solid" aria-label="Save your document changes">
            Save
          </Button>
          <Button variant="solid" tone="danger" aria-label="Permanently delete this item">
            Delete
          </Button>
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Disabled State
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Properly communicated to screen readers with aria-disabled
        </Typography>
        <Button variant="solid" disabled>
          Disabled Button
        </Button>
      </Box>
    </Stack>
  ),
};
