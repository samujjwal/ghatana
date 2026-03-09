/**
 * ViewModeSwitcher Component Stories
 *
 * @module DevSecOps/ViewModeSwitcher/stories
 */

import { Box, Surface as Paper, Typography } from '@ghatana/ui';
import { useState } from 'react';

import { ViewModeSwitcher } from './ViewModeSwitcher';

import type { Meta, StoryObj } from '@storybook/react';
import type { ViewMode } from '@ghatana/yappc-types/devsecops';


const meta: Meta<typeof ViewModeSwitcher> = {
  title: 'DevSecOps/ViewModeSwitcher',
  component: ViewModeSwitcher,
  tags: ['autodocs'],
  argTypes: {
    value: {
      control: 'select',
      options: ['canvas', 'kanban', 'timeline', 'table'],
    },
    variant: {
      control: 'select',
      options: ['full', 'compact'],
    },
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
    },
    orientation: {
      control: 'select',
      options: ['horizontal', 'vertical'],
    },
    disabled: {
      control: 'boolean',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof ViewModeSwitcher>;

/**
 * Interactive wrapper for stories
 */
function InteractiveWrapper(props: Omit<React.ComponentProps<typeof ViewModeSwitcher>, 'onChange'>) {
  const [value, setValue] = useState<ViewMode>(props.value);

  return (
    <Box>
      <ViewModeSwitcher {...props} value={value} onChange={setValue} />
      <Paper className="mt-4 p-4 bg-gray-50 dark:bg-gray-800">
        <Typography as="p" className="text-sm" color="text.secondary">
          Selected mode: <strong>{value}</strong>
        </Typography>
      </Paper>
    </Box>
  );
}

/**
 * Default view mode switcher with all modes
 */
export const Default: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'canvas',
    variant: 'full',
    size: 'medium',
  },
};

/**
 * Compact variant with icons only
 */
export const Compact: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'kanban',
    variant: 'compact',
    size: 'medium',
  },
};

/**
 * Small size variant
 */
export const Small: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'canvas',
    variant: 'full',
    size: 'small',
  },
};

/**
 * Large size variant
 */
export const Large: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'timeline',
    variant: 'full',
    size: 'large',
  },
};

/**
 * Vertical orientation
 */
export const Vertical: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'canvas',
    variant: 'full',
    orientation: 'vertical',
  },
};

/**
 * Custom labels
 */
export const CustomLabels: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'canvas',
    variant: 'full',
    labels: {
      canvas: 'Board',
      kanban: 'Status',
      timeline: 'Gantt',
      table: 'List',
    },
  },
};

/**
 * Limited modes (only canvas and kanban)
 */
export const LimitedModes: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'canvas',
    variant: 'full',
    modes: ['canvas', 'kanban'],
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    value: 'canvas',
    variant: 'full',
    disabled: true,
  },
};

/**
 * All variants comparison
 */
export const AllVariants: Story = {
  render: () => {
    const [mode1, setMode1] = useState<ViewMode>('canvas');
    const [mode2, setMode2] = useState<ViewMode>('kanban');
    const [mode3, setMode3] = useState<ViewMode>('timeline');

    return (
      <Box display="flex" flexDirection="column" gap={4}>
        <Box>
          <Typography as="h6" gutterBottom>
            Full Variant - Medium Size
          </Typography>
          <ViewModeSwitcher value={mode1} onChange={setMode1} variant="full" size="md" />
        </Box>

        <Box>
          <Typography as="h6" gutterBottom>
            Compact Variant - Small Size
          </Typography>
          <ViewModeSwitcher value={mode2} onChange={setMode2} variant="compact" size="sm" />
        </Box>

        <Box>
          <Typography as="h6" gutterBottom>
            Vertical Orientation
          </Typography>
          <ViewModeSwitcher
            value={mode3}
            onChange={setMode3}
            variant="full"
            orientation="vertical"
          />
        </Box>
      </Box>
    );
  },
};
