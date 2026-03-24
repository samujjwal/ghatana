/**
 * Stack Component Stories (Tailwind CSS)
 */

import { Stack } from './Stack.tailwind';
import { Box } from '../Box/Box.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Layout/Stack (Tailwind)',
  component: Stack,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A layout component that arranges children in a vertical or horizontal stack with consistent spacing. Built with Tailwind CSS flexbox utilities.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    direction: {
      control: 'select',
      options: ['vertical', 'horizontal'],
      description: 'Stack direction',
    },
    spacing: {
      control: 'select',
      options: ['gap-2', 'gap-4', 'gap-6', 'gap-8'],
      description: 'Spacing between items',
    },
    align: {
      control: 'select',
      options: ['items-start', 'items-center', 'items-end', 'items-stretch'],
      description: 'Cross-axis alignment',
    },
    justify: {
      control: 'select',
      options: ['justify-start', 'justify-center', 'justify-end', 'justify-between', 'justify-around'],
      description: 'Main-axis justification',
    },
  },
} satisfies Meta<typeof Stack>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default vertical stack
 */
export const Default: Story = {
  render: () => (
    <Stack>
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-md">Item 1</Box>
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-md">Item 2</Box>
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-md">Item 3</Box>
    </Stack>
  ),
};

/**
 * Horizontal stack
 */
export const Horizontal: Story = {
  render: () => (
    <Stack direction="horizontal">
      <Box p="p-4" bg="bg-secondary-100" rounded="rounded-md">Item 1</Box>
      <Box p="p-4" bg="bg-secondary-100" rounded="rounded-md">Item 2</Box>
      <Box p="p-4" bg="bg-secondary-100" rounded="rounded-md">Item 3</Box>
    </Stack>
  ),
};

/**
 * Spacing variants
 */
export const Spacing: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Small spacing (gap-2)</p>
        <Stack spacing="gap-2">
          <Box p="p-3" bg="bg-primary-100" rounded="rounded">Item 1</Box>
          <Box p="p-3" bg="bg-primary-100" rounded="rounded">Item 2</Box>
          <Box p="p-3" bg="bg-primary-100" rounded="rounded">Item 3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Medium spacing (gap-4)</p>
        <Stack spacing="gap-4">
          <Box p="p-3" bg="bg-secondary-100" rounded="rounded">Item 1</Box>
          <Box p="p-3" bg="bg-secondary-100" rounded="rounded">Item 2</Box>
          <Box p="p-3" bg="bg-secondary-100" rounded="rounded">Item 3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Large spacing (gap-8)</p>
        <Stack spacing="gap-8">
          <Box p="p-3" bg="bg-success-100" rounded="rounded">Item 1</Box>
          <Box p="p-3" bg="bg-success-100" rounded="rounded">Item 2</Box>
          <Box p="p-3" bg="bg-success-100" rounded="rounded">Item 3</Box>
        </Stack>
      </div>
    </div>
  ),
};

/**
 * Alignment options (vertical stack)
 */
export const VerticalAlignment: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Start aligned</p>
        <Stack align="items-start">
          <Box p="p-3" w="w-32" bg="bg-primary-100" rounded="rounded">Small</Box>
          <Box p="p-3" w="w-48" bg="bg-primary-100" rounded="rounded">Medium</Box>
          <Box p="p-3" w="w-64" bg="bg-primary-100" rounded="rounded">Large</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Center aligned</p>
        <Stack align="items-center">
          <Box p="p-3" w="w-32" bg="bg-secondary-100" rounded="rounded">Small</Box>
          <Box p="p-3" w="w-48" bg="bg-secondary-100" rounded="rounded">Medium</Box>
          <Box p="p-3" w="w-64" bg="bg-secondary-100" rounded="rounded">Large</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">End aligned</p>
        <Stack align="items-end">
          <Box p="p-3" w="w-32" bg="bg-success-100" rounded="rounded">Small</Box>
          <Box p="p-3" w="w-48" bg="bg-success-100" rounded="rounded">Medium</Box>
          <Box p="p-3" w="w-64" bg="bg-success-100" rounded="rounded">Large</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Stretch aligned</p>
        <Stack align="items-stretch">
          <Box p="p-3" bg="bg-warning-100" rounded="rounded">Stretched 1</Box>
          <Box p="p-3" bg="bg-warning-100" rounded="rounded">Stretched 2</Box>
          <Box p="p-3" bg="bg-warning-100" rounded="rounded">Stretched 3</Box>
        </Stack>
      </div>
    </div>
  ),
};

/**
 * Alignment options (horizontal stack)
 */
export const HorizontalAlignment: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Start aligned</p>
        <Stack direction="horizontal" align="items-start">
          <Box p="p-2" bg="bg-primary-100" rounded="rounded">Small</Box>
          <Box p="p-4" bg="bg-primary-100" rounded="rounded">Medium</Box>
          <Box p="p-6" bg="bg-primary-100" rounded="rounded">Large</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Center aligned</p>
        <Stack direction="horizontal" align="items-center">
          <Box p="p-2" bg="bg-secondary-100" rounded="rounded">Small</Box>
          <Box p="p-4" bg="bg-secondary-100" rounded="rounded">Medium</Box>
          <Box p="p-6" bg="bg-secondary-100" rounded="rounded">Large</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">End aligned</p>
        <Stack direction="horizontal" align="items-end">
          <Box p="p-2" bg="bg-success-100" rounded="rounded">Small</Box>
          <Box p="p-4" bg="bg-success-100" rounded="rounded">Medium</Box>
          <Box p="p-6" bg="bg-success-100" rounded="rounded">Large</Box>
        </Stack>
      </div>
    </div>
  ),
};

/**
 * Justification options
 */
export const Justification: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Start</p>
        <Stack direction="horizontal" justify="justify-start" className="w-full">
          <Box p="p-3" bg="bg-primary-100" rounded="rounded">1</Box>
          <Box p="p-3" bg="bg-primary-100" rounded="rounded">2</Box>
          <Box p="p-3" bg="bg-primary-100" rounded="rounded">3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Center</p>
        <Stack direction="horizontal" justify="justify-center" className="w-full">
          <Box p="p-3" bg="bg-secondary-100" rounded="rounded">1</Box>
          <Box p="p-3" bg="bg-secondary-100" rounded="rounded">2</Box>
          <Box p="p-3" bg="bg-secondary-100" rounded="rounded">3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">End</p>
        <Stack direction="horizontal" justify="justify-end" className="w-full">
          <Box p="p-3" bg="bg-success-100" rounded="rounded">1</Box>
          <Box p="p-3" bg="bg-success-100" rounded="rounded">2</Box>
          <Box p="p-3" bg="bg-success-100" rounded="rounded">3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Space Between</p>
        <Stack direction="horizontal" justify="justify-between" className="w-full">
          <Box p="p-3" bg="bg-warning-100" rounded="rounded">1</Box>
          <Box p="p-3" bg="bg-warning-100" rounded="rounded">2</Box>
          <Box p="p-3" bg="bg-warning-100" rounded="rounded">3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Space Around</p>
        <Stack direction="horizontal" justify="justify-around" className="w-full">
          <Box p="p-3" bg="bg-error-100" rounded="rounded">1</Box>
          <Box p="p-3" bg="bg-error-100" rounded="rounded">2</Box>
          <Box p="p-3" bg="bg-error-100" rounded="rounded">3</Box>
        </Stack>
      </div>
    </div>
  ),
};

/**
 * With dividers
 */
export const WithDividers: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Vertical with dividers</p>
        <Stack divider={<hr className="border-grey-300" />}>
          <Box p="p-3">Section 1</Box>
          <Box p="p-3">Section 2</Box>
          <Box p="p-3">Section 3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Horizontal with dividers</p>
        <Stack
          direction="horizontal"
          divider={<div className="w-px h-12 bg-grey-300" />}
        >
          <Box p="p-3">Item 1</Box>
          <Box p="p-3">Item 2</Box>
          <Box p="p-3">Item 3</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">Custom divider</p>
        <Stack divider={<div className="flex items-center justify-center py-2">•••</div>}>
          <Box p="p-3" bg="bg-grey-50" rounded="rounded">Content Block 1</Box>
          <Box p="p-3" bg="bg-grey-50" rounded="rounded">Content Block 2</Box>
          <Box p="p-3" bg="bg-grey-50" rounded="rounded">Content Block 3</Box>
        </Stack>
      </div>
    </div>
  ),
};

/**
 * Wrapping stack
 */
export const Wrapping: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">No wrap (default)</p>
        <Stack direction="horizontal" className="w-64 overflow-auto">
          <Box p="p-3" minW="min-w-24" bg="bg-primary-100" rounded="rounded">1</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-primary-100" rounded="rounded">2</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-primary-100" rounded="rounded">3</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-primary-100" rounded="rounded">4</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-primary-100" rounded="rounded">5</Box>
        </Stack>
      </div>
      
      <div>
        <p className="mb-2 text-sm font-medium text-grey-700">With wrap</p>
        <Stack direction="horizontal" wrap="flex-wrap" className="w-64">
          <Box p="p-3" minW="min-w-24" bg="bg-secondary-100" rounded="rounded">1</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-secondary-100" rounded="rounded">2</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-secondary-100" rounded="rounded">3</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-secondary-100" rounded="rounded">4</Box>
          <Box p="p-3" minW="min-w-24" bg="bg-secondary-100" rounded="rounded">5</Box>
        </Stack>
      </div>
    </div>
  ),
};

/**
 * Form layout example
 */
export const FormLayout: Story = {
  render: () => (
    <Box maxW="max-w-md" p="p-6" bg="bg-white" shadow="shadow-lg" rounded="rounded-xl">
      <Stack spacing="gap-6">
        <Box as="h2" className="text-2xl font-bold text-grey-900">
          Contact Form
        </Box>
        
        <Stack spacing="gap-4">
          <div>
            <label className="block text-sm font-medium text-grey-700 mb-1">Name</label>
            <input
              type="text"
              className="w-full rounded-md border border-grey-300 px-3 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
              placeholder="Your name"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-grey-700 mb-1">Email</label>
            <input
              type="email"
              className="w-full rounded-md border border-grey-300 px-3 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
              placeholder="you@example.com"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-grey-700 mb-1">Message</label>
            <textarea
              rows={4}
              className="w-full rounded-md border border-grey-300 px-3 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
              placeholder="Your message..."
            />
          </div>
        </Stack>
        
        <Stack direction="horizontal" justify="justify-end" spacing="gap-3">
          <button className="rounded-md border border-grey-300 px-4 py-2 text-sm font-medium text-grey-700 hover:bg-grey-50">
            Cancel
          </button>
          <button className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700">
            Submit
          </button>
        </Stack>
      </Stack>
    </Box>
  ),
};

/**
 * Card layout example
 */
export const CardLayout: Story = {
  render: () => (
    <Stack spacing="gap-6">
      {[1, 2, 3].map((i) => (
        <Box
          key={i}
          p="p-6"
          bg="bg-white"
          shadow="shadow-md"
          rounded="rounded-lg"
          className="hover:shadow-lg transition-shadow"
        >
          <Stack spacing="gap-3">
            <Stack direction="horizontal" justify="justify-between" align="items-start">
              <Box as="h3" className="text-lg font-semibold text-grey-900">
                Card Title {i}
              </Box>
              <Box
                p="px-3 py-1"
                bg="bg-primary-100"
                color="text-primary-700"
                rounded="rounded-full"
                className="text-xs font-medium"
              >
                New
              </Box>
            </Stack>
            
            <Box as="p" color="text-grey-600" className="text-sm">
              This is a card layout example using Stack components for consistent spacing
              and alignment.
            </Box>
            
            <Stack direction="horizontal" spacing="gap-2">
              <button className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700">
                Learn More
              </button>
              <button className="rounded-md border border-grey-300 px-4 py-2 text-sm font-medium text-grey-700 hover:bg-grey-50">
                Dismiss
              </button>
            </Stack>
          </Stack>
        </Box>
      ))}
    </Stack>
  ),
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    direction: 'vertical',
    spacing: 'gap-4',
    align: undefined,
    justify: undefined,
    wrap: undefined,
    fullWidth: false,
    fullHeight: false,
  },
  render: (args) => (
    <Stack {...args}>
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-md">Item 1</Box>
      <Box p="p-4" bg="bg-secondary-100" rounded="rounded-md">Item 2</Box>
      <Box p="p-4" bg="bg-success-100" rounded="rounded-md">Item 3</Box>
    </Stack>
  ),
};
