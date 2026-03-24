/**
 * Grid Component Stories (Tailwind CSS)
 */

import { Grid } from './Grid.tailwind';
import { Box } from '../Box/Box.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Layout/Grid (Tailwind)',
  component: Grid,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A layout component for CSS Grid layouts with responsive column support and auto-fit capabilities. Built with Tailwind CSS grid utilities.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    cols: {
      control: 'select',
      options: ['grid-cols-1', 'grid-cols-2', 'grid-cols-3', 'grid-cols-4', 'grid-cols-6', 'grid-cols-12'],
      description: 'Number of columns',
    },
    gap: {
      control: 'select',
      options: ['gap-2', 'gap-4', 'gap-6', 'gap-8'],
      description: 'Gap between items',
    },
    autoFit: {
      control: 'boolean',
      description: 'Enable auto-fit responsive grid',
    },
  },
} satisfies Meta<typeof Grid>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default grid (3 columns)
 */
export const Default: Story = {
  render: () => (
    <Grid cols="grid-cols-3">
      <Box p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">1</Box>
      <Box p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">2</Box>
      <Box p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">3</Box>
      <Box p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">4</Box>
      <Box p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">5</Box>
      <Box p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">6</Box>
    </Grid>
  ),
};

/**
 * Column variations
 */
export const ColumnVariations: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">1 Column</p>
        <Grid cols="grid-cols-1">
          <Box p="p-4" bg="bg-primary-100" rounded="rounded">Item 1</Box>
          <Box p="p-4" bg="bg-primary-100" rounded="rounded">Item 2</Box>
          <Box p="p-4" bg="bg-primary-100" rounded="rounded">Item 3</Box>
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">2 Columns</p>
        <Grid cols="grid-cols-2">
          <Box p="p-4" bg="bg-secondary-100" rounded="rounded">Item 1</Box>
          <Box p="p-4" bg="bg-secondary-100" rounded="rounded">Item 2</Box>
          <Box p="p-4" bg="bg-secondary-100" rounded="rounded">Item 3</Box>
          <Box p="p-4" bg="bg-secondary-100" rounded="rounded">Item 4</Box>
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">3 Columns</p>
        <Grid cols="grid-cols-3">
          <Box p="p-4" bg="bg-success-100" rounded="rounded">1</Box>
          <Box p="p-4" bg="bg-success-100" rounded="rounded">2</Box>
          <Box p="p-4" bg="bg-success-100" rounded="rounded">3</Box>
          <Box p="p-4" bg="bg-success-100" rounded="rounded">4</Box>
          <Box p="p-4" bg="bg-success-100" rounded="rounded">5</Box>
          <Box p="p-4" bg="bg-success-100" rounded="rounded">6</Box>
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">4 Columns</p>
        <Grid cols="grid-cols-4">
          <Box p="p-4" bg="bg-warning-100" rounded="rounded">1</Box>
          <Box p="p-4" bg="bg-warning-100" rounded="rounded">2</Box>
          <Box p="p-4" bg="bg-warning-100" rounded="rounded">3</Box>
          <Box p="p-4" bg="bg-warning-100" rounded="rounded">4</Box>
        </Grid>
      </div>
    </div>
  ),
};

/**
 * Gap spacing
 */
export const GapSpacing: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Small gap (gap-2)</p>
        <Grid cols="grid-cols-3" gap="gap-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Box key={i} p="p-4" bg="bg-primary-100" rounded="rounded" className="text-center">
              {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Medium gap (gap-4)</p>
        <Grid cols="grid-cols-3" gap="gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Box key={i} p="p-4" bg="bg-secondary-100" rounded="rounded" className="text-center">
              {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Large gap (gap-8)</p>
        <Grid cols="grid-cols-3" gap="gap-8">
          {Array.from({ length: 6 }).map((_, i) => (
            <Box key={i} p="p-4" bg="bg-success-100" rounded="rounded" className="text-center">
              {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
    </div>
  ),
};

/**
 * Responsive grid
 */
export const ResponsiveGrid: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">
          Responsive: 1 column (mobile) → 2 columns (tablet) → 4 columns (desktop)
        </p>
        <Grid cols="grid-cols-1 md:grid-cols-2 lg:grid-cols-4" gap="gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Box key={i} p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center font-medium">
              Card {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">
          Responsive: 2 columns (mobile) → 3 columns (desktop)
        </p>
        <Grid cols="grid-cols-2 lg:grid-cols-3" gap="gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <Box key={i} p="p-6" bg="bg-secondary-100" rounded="rounded-lg" className="text-center">
              Item {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
    </div>
  ),
};

/**
 * Auto-fit grid (responsive without breakpoints)
 */
export const AutoFit: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">
          Auto-fit with min-width 200px (resize window to see effect)
        </p>
        <Grid autoFit minColWidth="200px" gap="gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Box key={i} p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">
              {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">
          Auto-fit with min-width 300px
        </p>
        <Grid autoFit minColWidth="300px" gap="gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <Box key={i} p="p-8" bg="bg-secondary-100" rounded="rounded-lg" className="text-center font-medium">
              Card {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
    </div>
  ),
};

/**
 * Different gap for X and Y
 */
export const DifferentGaps: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Horizontal gap larger than vertical</p>
        <Grid cols="grid-cols-3" gapX="gap-x-8" gapY="gap-y-2">
          {Array.from({ length: 9 }).map((_, i) => (
            <Box key={i} p="p-4" bg="bg-primary-100" rounded="rounded" className="text-center">
              {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Vertical gap larger than horizontal</p>
        <Grid cols="grid-cols-4" gapX="gap-x-2" gapY="gap-y-8">
          {Array.from({ length: 8 }).map((_, i) => (
            <Box key={i} p="p-4" bg="bg-secondary-100" rounded="rounded" className="text-center">
              {i + 1}
            </Box>
          ))}
        </Grid>
      </div>
    </div>
  ),
};

/**
 * Card grid example
 */
export const CardGrid: Story = {
  render: () => (
    <Grid cols="grid-cols-1 md:grid-cols-2 lg:grid-cols-3" gap="gap-6">
      {Array.from({ length: 6 }).map((_, i) => (
        <Box
          key={i}
          bg="bg-white"
          shadow="shadow-md"
          rounded="rounded-lg"
          overflow="overflow-hidden"
          className="hover:shadow-lg transition-shadow"
        >
          <Box h="h-48" bg={`bg-${['primary', 'secondary', 'success', 'warning', 'error', 'grey'][i]}-400`} />
          <Box p="p-6">
            <Box as="h3" className="mb-2 text-lg font-semibold text-grey-900">
              Card Title {i + 1}
            </Box>
            <Box as="p" color="text-grey-600" className="mb-4 text-sm">
              This is a card in a responsive grid layout. The grid adjusts from 1 column on mobile
              to 3 columns on desktop.
            </Box>
            <button className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700">
              View Details
            </button>
          </Box>
        </Box>
      ))}
    </Grid>
  ),
};

/**
 * Dashboard layout example
 */
export const DashboardLayout: Story = {
  render: () => (
    <div className="space-y-6">
      {/* Stats row */}
      <Grid cols="grid-cols-1 md:grid-cols-2 lg:grid-cols-4" gap="gap-4">
        {[
          { label: 'Total Users', value: '12,345', color: 'primary' },
          { label: 'Revenue', value: '$45,678', color: 'success' },
          { label: 'Active Sessions', value: '1,234', color: 'secondary' },
          { label: 'Bounce Rate', value: '23.5%', color: 'warning' },
        ].map((stat, i) => (
          <Box key={i} p="p-6" bg="bg-white" shadow="shadow" rounded="rounded-lg">
            <Box as="p" color="text-grey-600" className="mb-1 text-sm font-medium">
              {stat.label}
            </Box>
            <Box as="p" className={`text-2xl font-bold text-${stat.color}-600`}>
              {stat.value}
            </Box>
          </Box>
        ))}
      </Grid>
      
      {/* Main content row */}
      <Grid cols="grid-cols-1 lg:grid-cols-3" gap="gap-6">
        {/* Chart area */}
        <Box
          className="lg:col-span-2"
          p="p-6"
          bg="bg-white"
          shadow="shadow"
          rounded="rounded-lg"
        >
          <Box as="h3" className="mb-4 text-lg font-semibold text-grey-900">
            Analytics Overview
          </Box>
          <Box h="h-64" bg="bg-grey-100" rounded="rounded" className="flex items-center justify-center text-grey-500">
            Chart Area
          </Box>
        </Box>
        
        {/* Sidebar */}
        <Box p="p-6" bg="bg-white" shadow="shadow" rounded="rounded-lg">
          <Box as="h3" className="mb-4 text-lg font-semibold text-grey-900">
            Recent Activity
          </Box>
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <Box key={i} p="p-3" bg="bg-grey-50" rounded="rounded" className="text-sm">
                Activity item {i + 1}
              </Box>
            ))}
          </div>
        </Box>
      </Grid>
    </div>
  ),
};

/**
 * Image gallery example
 */
export const ImageGallery: Story = {
  render: () => (
    <Grid autoFit minColWidth="250px" gap="gap-4">
      {Array.from({ length: 12 }).map((_, i) => (
        <Box
          key={i}
          bg={`bg-${['primary', 'secondary', 'success'][i % 3]}-${200 + (i % 4) * 100}`}
          rounded="rounded-lg"
          overflow="overflow-hidden"
          className="aspect-square cursor-pointer transition-transform hover:scale-105"
        >
          <Box
            h="h-full"
            display="flex"
            className="items-center justify-center text-2xl font-bold text-white"
          >
            {i + 1}
          </Box>
        </Box>
      ))}
    </Grid>
  ),
};

/**
 * Masonry-style grid
 */
export const MasonryStyle: Story = {
  render: () => (
    <Grid cols="grid-cols-1 md:grid-cols-2 lg:grid-cols-3" gap="gap-4">
      {[
        { height: 'h-32', color: 'primary' },
        { height: 'h-48', color: 'secondary' },
        { height: 'h-40', color: 'success' },
        { height: 'h-56', color: 'warning' },
        { height: 'h-44', color: 'error' },
        { height: 'h-36', color: 'grey' },
        { height: 'h-52', color: 'primary' },
        { height: 'h-40', color: 'secondary' },
        { height: 'h-48', color: 'success' },
      ].map((item, i) => (
        <Box
          key={i}
          p="p-6"
          h={item.height}
          bg={`bg-${item.color}-100`}
          rounded="rounded-lg"
          className="flex items-center justify-center font-medium"
        >
          Item {i + 1}
        </Box>
      ))}
    </Grid>
  ),
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    cols: 'grid-cols-3',
    gap: 'gap-4',
    autoFit: false,
    minColWidth: '250px',
    fullWidth: false,
    fullHeight: false,
  },
  render: (args) => (
    <Grid {...args}>
      {Array.from({ length: 9 }).map((_, i) => (
        <Box key={i} p="p-6" bg="bg-primary-100" rounded="rounded-md" className="text-center">
          {i + 1}
        </Box>
      ))}
    </Grid>
  ),
};
