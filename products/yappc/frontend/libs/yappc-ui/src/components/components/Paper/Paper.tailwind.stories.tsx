import { Paper } from './Paper.tailwind';
import { Typography } from '../Typography';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Paper (Tailwind)',
  component: Paper,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Paper>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Paper p="p-4">
      <Typography variant="body1">
        Default Paper component with elevation 1
      </Typography>
    </Paper>
  ),
};

export const Elevations: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-4">
      <Paper elevation={0} p="p-4">
        <Typography variant="caption" className="block mb-1">Elevation 0</Typography>
        <Typography variant="body2">No shadow</Typography>
      </Paper>
      <Paper elevation={1} p="p-4">
        <Typography variant="caption" className="block mb-1">Elevation 1</Typography>
        <Typography variant="body2">Subtle shadow</Typography>
      </Paper>
      <Paper elevation={2} p="p-4">
        <Typography variant="caption" className="block mb-1">Elevation 2</Typography>
        <Typography variant="body2">Light shadow</Typography>
      </Paper>
      <Paper elevation={3} p="p-4">
        <Typography variant="caption" className="block mb-1">Elevation 3</Typography>
        <Typography variant="body2">Medium shadow</Typography>
      </Paper>
      <Paper elevation={6} p="p-4">
        <Typography variant="caption" className="block mb-1">Elevation 6</Typography>
        <Typography variant="body2">Larger shadow</Typography>
      </Paper>
      <Paper elevation={12} p="p-4">
        <Typography variant="caption" className="block mb-1">Elevation 12</Typography>
        <Typography variant="body2">Extra large shadow</Typography>
      </Paper>
    </div>
  ),
};

export const Outlined: Story = {
  render: () => (
    <Paper variant="outlined" p="p-4">
      <Typography variant="body1">
        Outlined variant with border instead of shadow
      </Typography>
    </Paper>
  ),
};

export const Square: Story = {
  render: () => (
    <div className="flex gap-4">
      <Paper p="p-4">
        <Typography variant="body2">Rounded (default)</Typography>
      </Paper>
      <Paper square p="p-4">
        <Typography variant="body2">Square (no border radius)</Typography>
      </Paper>
    </div>
  ),
};

export const CustomBackground: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-4">
      <Paper bg="bg-primary-50" p="p-4">
        <Typography variant="body2" color="primary">
          Primary background
        </Typography>
      </Paper>
      <Paper bg="bg-secondary-50" p="p-4">
        <Typography variant="body2" color="secondary">
          Secondary background
        </Typography>
      </Paper>
      <Paper bg="bg-error-50" p="p-4">
        <Typography variant="body2" color="error">
          Error background
        </Typography>
      </Paper>
      <Paper bg="bg-success-50" p="p-4">
        <Typography variant="body2" color="success">
          Success background
        </Typography>
      </Paper>
    </div>
  ),
};

export const NestedPapers: Story = {
  render: () => (
    <Paper elevation={2} p="p-6">
      <Typography variant="h6" gutterBottom>
        Outer Paper
      </Typography>
      <Typography variant="body2" gutterBottom>
        Papers can be nested to create layered effects
      </Typography>
      
      <Paper elevation={4} p="p-4" className="mt-4">
        <Typography variant="subtitle2" gutterBottom>
          Inner Paper
        </Typography>
        <Typography variant="body2">
          This paper has a higher elevation
        </Typography>
      </Paper>
    </Paper>
  ),
};

export const ContentCard: Story = {
  render: () => (
    <Paper elevation={2} p="p-0" className="overflow-hidden">
      <div className="bg-primary-500 p-4">
        <Typography variant="h6" className="text-white">
          Card Title
        </Typography>
      </div>
      <div className="p-4">
        <Typography variant="body1" gutterBottom>
          This demonstrates how Paper can be used to create card-like components
          with different sections.
        </Typography>
        <Typography variant="body2" color="text">
          You can combine Paper with other layout components to create
          complex UI structures.
        </Typography>
      </div>
      <div className="border-t border-grey-200 p-4 bg-grey-50">
        <Typography variant="caption">
          Footer section
        </Typography>
      </div>
    </Paper>
  ),
};

export const Dashboard: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-4">
      <Paper elevation={1} p="p-4">
        <Typography variant="overline" className="block mb-2">Total Users</Typography>
        <Typography variant="h3" color="primary">1,234</Typography>
        <Typography variant="caption" color="success">+12% from last month</Typography>
      </Paper>
      
      <Paper elevation={1} p="p-4">
        <Typography variant="overline" className="block mb-2">Revenue</Typography>
        <Typography variant="h3" color="primary">$56,789</Typography>
        <Typography variant="caption" color="success">+8% from last month</Typography>
      </Paper>
      
      <Paper elevation={1} p="p-4">
        <Typography variant="overline" className="block mb-2">Active Sessions</Typography>
        <Typography variant="h3" color="primary">892</Typography>
        <Typography variant="caption" color="text">Currently online</Typography>
      </Paper>
      
      <Paper elevation={1} p="p-4">
        <Typography variant="overline" className="block mb-2">Conversion Rate</Typography>
        <Typography variant="h3" color="primary">3.2%</Typography>
        <Typography variant="caption" color="error">-2% from last month</Typography>
      </Paper>
    </div>
  ),
};

export const Playground: Story = {
  args: {
    elevation: 2,
    variant: 'elevation',
    square: false,
    p: 'p-4',
    children: 'Customize this Paper using the controls below',
  },
};
