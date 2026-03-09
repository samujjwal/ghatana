/**
 * Box Component Stories (Tailwind CSS)
 */

import { Box } from './Box.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Layout/Box (Tailwind)',
  component: Box,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A flexible container component built with Tailwind CSS. Supports polymorphic rendering and comprehensive styling props for layout, spacing, colors, and positioning.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    as: {
      control: 'select',
      options: ['div', 'section', 'article', 'main', 'aside', 'header', 'footer', 'nav'],
      description: 'HTML element to render',
    },
    display: {
      control: 'select',
      options: ['block', 'flex', 'grid', 'inline-block', 'inline-flex'],
      description: 'Display mode',
    },
  },
} satisfies Meta<typeof Box>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default box
 */
export const Default: Story = {
  render: () => (
    <Box p="p-4" bg="bg-grey-100" rounded="rounded-md">
      This is a Box component
    </Box>
  ),
};

/**
 * Padding variants
 */
export const Padding: Story = {
  render: () => (
    <div className="space-y-4">
      <Box p="p-2" bg="bg-primary-100" rounded="rounded">
        Small padding (p-2)
      </Box>
      
      <Box p="p-4" bg="bg-primary-100" rounded="rounded">
        Medium padding (p-4)
      </Box>
      
      <Box p="p-8" bg="bg-primary-100" rounded="rounded">
        Large padding (p-8)
      </Box>
      
      <Box px="px-8" py="py-2" bg="bg-secondary-100" rounded="rounded">
        Horizontal padding (px-8, py-2)
      </Box>
    </div>
  ),
};

/**
 * Background colors
 */
export const BackgroundColors: Story = {
  render: () => (
    <div className="space-y-4">
      <Box p="p-4" bg="bg-white" border="border border-grey-300" rounded="rounded-md">
        White background
      </Box>
      
      <Box p="p-4" bg="bg-primary-50" rounded="rounded-md">
        Primary light background
      </Box>
      
      <Box p="p-4" bg="bg-primary-500" color="text-white" rounded="rounded-md">
        Primary background with white text
      </Box>
      
      <Box p="p-4" bg="bg-success-100" color="text-success-800" rounded="rounded-md">
        Success background
      </Box>
      
      <Box p="p-4" bg="bg-error-100" color="text-error-800" rounded="rounded-md">
        Error background
      </Box>
      
      <Box p="p-4" bg="bg-warning-100" color="text-warning-800" rounded="rounded-md">
        Warning background
      </Box>
    </div>
  ),
};

/**
 * Border and shadow
 */
export const BorderAndShadow: Story = {
  render: () => (
    <div className="space-y-4">
      <Box p="p-4" border="border border-grey-300" rounded="rounded-md">
        With border
      </Box>
      
      <Box p="p-4" border="border-2 border-primary-500" rounded="rounded-lg">
        Thick primary border
      </Box>
      
      <Box p="p-4" bg="bg-white" shadow="shadow" rounded="rounded-md">
        Small shadow
      </Box>
      
      <Box p="p-4" bg="bg-white" shadow="shadow-md" rounded="rounded-lg">
        Medium shadow
      </Box>
      
      <Box p="p-4" bg="bg-white" shadow="shadow-lg" rounded="rounded-xl">
        Large shadow
      </Box>
    </div>
  ),
};

/**
 * Border radius variants
 */
export const BorderRadius: Story = {
  render: () => (
    <div className="space-y-4">
      <Box p="p-4" bg="bg-primary-100" rounded="rounded">
        Small radius
      </Box>
      
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-md">
        Medium radius
      </Box>
      
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-lg">
        Large radius
      </Box>
      
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-xl">
        Extra large radius
      </Box>
      
      <Box p="p-4" bg="bg-primary-100" rounded="rounded-full">
        Full radius (pill)
      </Box>
    </div>
  ),
};

/**
 * Width and height
 */
export const Dimensions: Story = {
  render: () => (
    <div className="space-y-4">
      <Box p="p-4" w="w-full" bg="bg-primary-100" rounded="rounded-md">
        Full width
      </Box>
      
      <Box p="p-4" w="w-1/2" bg="bg-secondary-100" rounded="rounded-md">
        Half width
      </Box>
      
      <Box p="p-4" w="w-64" bg="bg-success-100" rounded="rounded-md">
        Fixed width (256px)
      </Box>
      
      <Box p="p-4" w="w-full" h="h-32" bg="bg-warning-100" rounded="rounded-md">
        Full width, fixed height (128px)
      </Box>
    </div>
  ),
};

/**
 * Flex container
 */
export const FlexContainer: Story = {
  render: () => (
    <div className="space-y-4">
      <Box display="flex" className="gap-4 items-center" p="p-4" bg="bg-grey-100" rounded="rounded-md">
        <Box p="p-3" bg="bg-primary-500" color="text-white" rounded="rounded">
          Item 1
        </Box>
        <Box p="p-3" bg="bg-secondary-500" color="text-white" rounded="rounded">
          Item 2
        </Box>
        <Box p="p-3" bg="bg-success-500" color="text-white" rounded="rounded">
          Item 3
        </Box>
      </Box>
      
      <Box display="flex" className="gap-4 justify-between" p="p-4" bg="bg-grey-100" rounded="rounded-md">
        <Box p="p-3" bg="bg-primary-500" color="text-white" rounded="rounded">
          Left
        </Box>
        <Box p="p-3" bg="bg-secondary-500" color="text-white" rounded="rounded">
          Center
        </Box>
        <Box p="p-3" bg="bg-success-500" color="text-white" rounded="rounded">
          Right
        </Box>
      </Box>
    </div>
  ),
};

/**
 * Positioning
 */
export const Positioning: Story = {
  render: () => (
    <Box position="relative" w="w-full" h="h-64" bg="bg-grey-100" rounded="rounded-md">
      <Box
        position="absolute"
        top="top-4"
        left="left-4"
        p="p-3"
        bg="bg-primary-500"
        color="text-white"
        rounded="rounded"
      >
        Top Left
      </Box>
      
      <Box
        position="absolute"
        top="top-4"
        right="right-4"
        p="p-3"
        bg="bg-secondary-500"
        color="text-white"
        rounded="rounded"
      >
        Top Right
      </Box>
      
      <Box
        position="absolute"
        bottom="bottom-4"
        left="left-4"
        p="p-3"
        bg="bg-success-500"
        color="text-white"
        rounded="rounded"
      >
        Bottom Left
      </Box>
      
      <Box
        position="absolute"
        bottom="bottom-4"
        right="right-4"
        p="p-3"
        bg="bg-warning-500"
        color="text-white"
        rounded="rounded"
      >
        Bottom Right
      </Box>
    </Box>
  ),
};

/**
 * Card example
 */
export const CardExample: Story = {
  render: () => (
    <Box
      maxW="max-w-sm"
      bg="bg-white"
      shadow="shadow-lg"
      rounded="rounded-xl"
      overflow="overflow-hidden"
    >
      <Box h="h-48" bg="bg-gradient-to-r from-primary-500 to-secondary-500" />
      
      <Box p="p-6">
        <Box as="h3" className="text-xl font-bold text-grey-900 mb-2">
          Card Title
        </Box>
        <Box as="p" color="text-grey-600" className="mb-4">
          This is a card component built entirely with the Box component.
          It demonstrates how versatile Box can be for creating complex layouts.
        </Box>
        <Box display="flex" className="gap-2">
          <Box
            as="button"
            p="px-4 py-2"
            bg="bg-primary-600"
            color="text-white"
            rounded="rounded-md"
            className="hover:bg-primary-700 transition-colors"
          >
            Action
          </Box>
          <Box
            as="button"
            p="px-4 py-2"
            border="border border-grey-300"
            color="text-grey-700"
            rounded="rounded-md"
            className="hover:bg-grey-50 transition-colors"
          >
            Cancel
          </Box>
        </Box>
      </Box>
    </Box>
  ),
};

/**
 * Polymorphic rendering
 */
export const PolymorphicElements: Story = {
  render: () => (
    <div className="space-y-4">
      <Box as="header" p="p-4" bg="bg-primary-600" color="text-white" rounded="rounded-md">
        This is a header element
      </Box>
      
      <Box as="section" p="p-4" bg="bg-grey-100" rounded="rounded-md">
        This is a section element
      </Box>
      
      <Box as="article" p="p-4" bg="bg-white" border="border border-grey-300" rounded="rounded-md">
        This is an article element
      </Box>
      
      <Box as="footer" p="p-4" bg="bg-grey-800" color="text-white" rounded="rounded-md">
        This is a footer element
      </Box>
    </div>
  ),
};

/**
 * Complex layout example
 */
export const ComplexLayout: Story = {
  render: () => (
    <Box display="flex" className="gap-4" h="h-96">
      {/* Sidebar */}
      <Box
        w="w-64"
        bg="bg-grey-900"
        color="text-white"
        p="p-4"
        rounded="rounded-lg"
        display="flex"
        className="flex-col gap-2"
      >
        <Box as="h3" className="text-lg font-bold mb-2">
          Sidebar
        </Box>
        <Box p="p-2" rounded="rounded" className="hover:bg-grey-800 cursor-pointer">
          Menu Item 1
        </Box>
        <Box p="p-2" rounded="rounded" className="hover:bg-grey-800 cursor-pointer">
          Menu Item 2
        </Box>
        <Box p="p-2" rounded="rounded" className="hover:bg-grey-800 cursor-pointer">
          Menu Item 3
        </Box>
      </Box>
      
      {/* Main content */}
      <Box display="flex" className="flex-1 flex-col gap-4">
        {/* Header */}
        <Box p="p-4" bg="bg-white" shadow="shadow" rounded="rounded-lg">
          <Box as="h2" className="text-2xl font-bold text-grey-900">
            Main Content Header
          </Box>
        </Box>
        
        {/* Content area */}
        <Box
          className="flex-1"
          p="p-6"
          bg="bg-white"
          shadow="shadow"
          rounded="rounded-lg"
          overflow="overflow-auto"
        >
          <Box as="p" color="text-grey-700">
            This is the main content area. The Box component is used for the entire layout structure,
            demonstrating its flexibility for building complex UIs.
          </Box>
        </Box>
      </Box>
    </Box>
  ),
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    as: 'div',
    p: 'p-4',
    bg: 'bg-primary-100',
    rounded: 'rounded-md',
    children: 'Playground Box',
  },
};
