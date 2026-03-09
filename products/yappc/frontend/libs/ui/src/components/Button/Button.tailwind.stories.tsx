/**
 * Storybook stories for Tailwind CSS Button
 * 
 * Demonstrates all button variants, sizes, colors, and states.
 * Includes accessibility testing and interaction examples.
 */
import { Button } from './Button.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Button> = {
  title: 'Components/Button (Tailwind)',
  component: Button,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: `
Accessible button component built with Tailwind CSS.

**Key Features:**
- ✅ Semantic HTML (\`<button>\`)
- ✅ Full keyboard support (Space/Enter)
- ✅ Focus ring (WCAG 2.1 AA compliant)
- ✅ Loading state with spinner
- ✅ Icon support (left/right)
- ✅ Customizable via className

**Base UI Migration:** This component follows Base UI philosophy - unstyled primitives with Tailwind styling.
        `,
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['solid', 'outline', 'ghost', 'link'],
      description: 'Visual style variant',
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Size variant',
    },
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Color scheme',
    },
    fullWidth: {
      control: 'boolean',
      description: 'Full width button',
    },
    isLoading: {
      control: 'boolean',
      description: 'Loading state',
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Button>;

/**
 * Default solid button with primary color
 */
export const Default: Story = {
  args: {
    children: 'Button',
  },
};

/**
 * All variant styles
 */
export const Variants: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <div className="flex gap-3">
        <Button variant="solid">Solid</Button>
        <Button variant="outline">Outline</Button>
        <Button variant="ghost">Ghost</Button>
        <Button variant="link">Link</Button>
      </div>
    </div>
  ),
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex items-center gap-3">
      <Button size="sm">Small</Button>
      <Button size="md">Medium</Button>
      <Button size="lg">Large</Button>
    </div>
  ),
};

/**
 * All color schemes (solid variant)
 */
export const ColorSchemes: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <div className="flex gap-3">
        <Button colorScheme="primary">Primary</Button>
        <Button colorScheme="secondary">Secondary</Button>
        <Button colorScheme="success">Success</Button>
      </div>
      <div className="flex gap-3">
        <Button colorScheme="error">Error</Button>
        <Button colorScheme="warning">Warning</Button>
        <Button colorScheme="grey">Grey</Button>
      </div>
    </div>
  ),
};

/**
 * Outline variant with all colors
 */
export const OutlineColors: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <div className="flex gap-3">
        <Button variant="outline" colorScheme="primary">Primary</Button>
        <Button variant="outline" colorScheme="secondary">Secondary</Button>
        <Button variant="outline" colorScheme="success">Success</Button>
      </div>
      <div className="flex gap-3">
        <Button variant="outline" colorScheme="error">Error</Button>
        <Button variant="outline" colorScheme="warning">Warning</Button>
        <Button variant="outline" colorScheme="grey">Grey</Button>
      </div>
    </div>
  ),
};

/**
 * Button states: normal, disabled, loading
 */
export const States: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <div className="flex gap-3">
        <Button>Normal</Button>
        <Button disabled>Disabled</Button>
        <Button isLoading>Loading</Button>
      </div>
      <div className="flex gap-3">
        <Button variant="outline">Normal</Button>
        <Button variant="outline" disabled>Disabled</Button>
        <Button variant="outline" isLoading>Loading</Button>
      </div>
    </div>
  ),
};

/**
 * Buttons with icons
 */
export const WithIcons: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <div className="flex gap-3">
        <Button
          leftIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
          }
        >
          Add Item
        </Button>
        
        <Button
          variant="outline"
          rightIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          }
        >
          Next
        </Button>
        
        <Button
          variant="ghost"
          leftIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          }
          rightIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          }
        >
          Both
        </Button>
      </div>
    </div>
  ),
};

/**
 * Full width button
 */
export const FullWidth: Story = {
  render: () => (
    <div className="w-96">
      <Button fullWidth>Full Width Button</Button>
    </div>
  ),
};

/**
 * Custom styling with className
 */
export const CustomStyles: Story = {
  render: () => (
    <div className="flex gap-3">
      <Button className="shadow-xl hover:scale-105 transform transition-transform">
        With Shadow & Scale
      </Button>
      
      <Button className="rounded-full">
        Rounded Full
      </Button>
      
      <Button className="uppercase tracking-wider">
        Custom Text
      </Button>
    </div>
  ),
};

/**
 * Accessibility testing story
 */
export const Accessibility: Story = {
  render: () => (
    <div className="flex flex-col gap-4 p-4">
      <h3 className="text-lg font-semibold mb-2">Keyboard Navigation</h3>
      <p className="text-sm text-grey-600 mb-4">
        Press Tab to focus buttons, Space or Enter to activate.
      </p>
      
      <div className="flex gap-3">
        <Button>Focusable 1</Button>
        <Button disabled>Disabled (Not Focusable)</Button>
        <Button>Focusable 2</Button>
      </div>
      
      <h3 className="text-lg font-semibold mb-2 mt-4">Focus Ring (WCAG 2.1 AA)</h3>
      <p className="text-sm text-grey-600 mb-4">
        Focus ring is 2px, primary color, with 2px offset.
      </p>
      
      <div className="flex gap-3">
        <Button variant="solid">Solid Focus</Button>
        <Button variant="outline">Outline Focus</Button>
        <Button variant="ghost">Ghost Focus</Button>
      </div>
    </div>
  ),
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    children: 'Playground Button',
    variant: 'solid',
    size: 'md',
    colorScheme: 'primary',
    fullWidth: false,
    isLoading: false,
    disabled: false,
  },
};
