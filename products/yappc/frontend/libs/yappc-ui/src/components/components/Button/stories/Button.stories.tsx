import React from 'react';

import { Button } from '..';
import ThemeProvider from '../../../theme/ThemeProvider';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta: Meta<typeof Button> = {
  title: 'UI/Components/Button',
  component: Button,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider mode="light">
        <Story />
      </ThemeProvider>
    ),
  ],
  argTypes: {
    variant: {
      control: 'select',
      options: ['contained', 'outlined', 'text'],
      description: 'The variant of the button',
      defaultValue: 'contained',
    },
    color: {
      control: 'select',
      options: ['primary', 'secondary', 'error', 'warning', 'info', 'success'],
      description: 'The color of the button',
      defaultValue: 'primary',
    },
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
      description: 'The size of the button',
      defaultValue: 'medium',
    },
    disabled: {
      control: 'boolean',
      description: 'Whether the button is disabled',
      defaultValue: false,
    },
    fullWidth: {
      control: 'boolean',
      description: 'Whether the button should take up the full width of its container',
      defaultValue: false,
    },
    shape: {
      control: 'select',
      options: ['rounded', 'square', 'pill'],
      description: 'The shape of the button',
      defaultValue: 'rounded',
    },
    elevation: {
      control: 'select',
      options: [0, 1, 2, 4, 8],
      description: 'The elevation (shadow) of the button',
      defaultValue: 1,
    },
    disableRipple: {
      control: 'boolean',
      description: 'Whether to disable the ripple effect',
      defaultValue: false,
    },
    tooltip: {
      control: 'text',
      description: 'Tooltip text to display on hover',
    },
    tooltipPlacement: {
      control: 'select',
      options: ['top', 'bottom', 'left', 'right'],
      description: 'Placement of the tooltip',
      defaultValue: 'top',
    },
    onClick: { action: 'clicked' },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Button>;

export const Primary: Story = {
  args: {
    children: 'Primary Button',
    variant: 'contained',
    color: 'primary',
  },
};

export const Secondary: Story = {
  args: {
    children: 'Secondary Button',
    variant: 'contained',
    color: 'secondary',
  },
};

export const Outlined: Story = {
  args: {
    children: 'Outlined Button',
    variant: 'outlined',
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

export const Small: Story = {
  args: {
    children: 'Small Button',
    size: 'small',
  },
};

export const Medium: Story = {
  args: {
    children: 'Medium Button',
    size: 'medium',
  },
};

export const Large: Story = {
  args: {
    children: 'Large Button',
    size: 'large',
  },
};

export const Disabled: Story = {
  args: {
    children: 'Disabled Button',
    disabled: true,
  },
};

export const FullWidth: Story = {
  args: {
    children: 'Full Width Button',
    fullWidth: true,
  },
  parameters: {
    layout: 'padded',
  },
};

export const WithStartIcon: Story = {
  args: {
    children: 'Button with Icon',
    startIcon: <span>🚀</span>,
  },
};

export const WithEndIcon: Story = {
  args: {
    children: 'Button with Icon',
    endIcon: <span>👉</span>,
  },
};

export const Pill: Story = {
  args: {
    children: 'Pill Button',
    shape: 'pill',
  },
};

export const Square: Story = {
  args: {
    children: 'Square Button',
    shape: 'square',
  },
};

// Theme variants
export const PrimaryDark: Story = {
  args: {
    children: 'Primary Button (Dark)',
    variant: 'contained',
    color: 'primary',
  },
  decorators: [
    (Story) => (
      <ThemeProvider mode="dark">
        <Story />
      </ThemeProvider>
    ),
  ],
};

export const WithTooltip: Story = {
  args: {
    children: 'Hover Me',
    tooltip: 'This is a tooltip',
    variant: 'contained',
  },
};

export const WithCustomElevation: Story = {
  args: {
    children: 'Elevated Button',
    elevation: 8,
    variant: 'contained',
  },
};

export const WithoutRipple: Story = {
  args: {
    children: 'No Ripple Effect',
    disableRipple: true,
    variant: 'contained',
  },
};

export const AccessibleButton: Story = {
  args: {
    children: 'Accessible Button',
    'aria-label': 'Accessible button with custom label',
    'aria-haspopup': 'dialog',
  },
};
