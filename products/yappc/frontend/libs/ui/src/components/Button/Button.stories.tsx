// Import the folder entry so the component re-export (index.ts) resolves to the
// correct implementation (Button.tailwind). Using `.` preserves the public
// package-style import and avoids hard-coding filenames.
import { Button } from '.';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta = {
  title: 'Components/Button',
  component: Button,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['solid', 'outline', 'ghost', 'link'],
      description: 'The variant to use (solid/outline/ghost/link)',
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'The size of the button (sm/md/lg)',
    },
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Design token color scheme for the button',
    },
    disabled: {
      control: 'boolean',
      description: 'If true, the button is disabled',
    },
    fullWidth: {
      control: 'boolean',
      description: 'If true, the button takes up the full width of its container',
    },
    isLoading: {
      control: 'boolean',
      description: 'Show loading spinner and disable interaction',
    },
  },
} satisfies Meta<typeof Button>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Primary: Story = {
  args: {
    children: 'Primary Button',
    variant: 'solid',
    colorScheme: 'primary',
  },
};

export const Secondary: Story = {
  args: {
    children: 'Secondary Button',
    variant: 'solid',
    colorScheme: 'secondary',
  },
};

export const Outlined: Story = {
  args: {
    children: 'Outlined Button',
    variant: 'outline',
  },
};

export const Text: Story = {
  args: {
    children: 'Text Button',
    variant: 'ghost',
  },
};

export const Small: Story = {
  args: {
    children: 'Small Button',
    size: 'sm',
    variant: 'solid',
  },
};

export const Medium: Story = {
  args: {
    children: 'Medium Button',
    size: 'md',
    variant: 'solid',
  },
};

export const Large: Story = {
  args: {
    children: 'Large Button',
    size: 'lg',
    variant: 'solid',
  },
};

export const Rounded: Story = {
  args: {
    children: 'Rounded',
    variant: 'solid',
  },
};

export const Square: Story = {
  args: {
    children: 'Square',
    variant: 'solid',
  },
};

export const Pill: Story = {
  args: {
    children: 'Pill Shape',
    variant: 'solid',
  },
};

export const Disabled: Story = {
  args: {
    children: 'Disabled Button',
    disabled: true,
    variant: 'solid',
  },
};

export const FullWidth: Story = {
  args: {
    children: 'Full Width Button',
    fullWidth: true,
    variant: 'solid',
  },
  parameters: {
    layout: 'padded',
  },
};

export const Success: Story = {
  args: {
    children: 'Success',
    colorScheme: 'success',
    variant: 'solid',
  },
};

export const Error: Story = {
  args: {
    children: 'Error',
    colorScheme: 'error',
    variant: 'solid',
  },
};

export const Warning: Story = {
  args: {
    children: 'Warning',
    colorScheme: 'warning',
    variant: 'solid',
  },
};

export const Info: Story = {
  args: {
    children: 'Info',
    colorScheme: 'primary',
    variant: 'solid',
  },
};

export const WithIcon: Story = {
  args: {
    children: (
      <>
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          style={{ marginRight: '8px' }}
        >
          <path d="M5 12h14M12 5l7 7-7 7" />
        </svg>
        Button with Icon
      </>
    ),
    variant: 'solid',
  },
};

export const Loading: Story = {
  args: {
    children: 'Loading...',
    isLoading: true,
    variant: 'solid',
  },
};
