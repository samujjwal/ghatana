import type { Meta, StoryObj } from '@storybook/react';
import { Database, Plus, Trash2 } from 'lucide-react';
import { Button } from './Button';

/**
 * Storybook stories for the Button component.
 *
 * Demonstrates all variants, sizes, loading, and disabled states.
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and visual testing
 * @doc.layer frontend
 */

const meta = {
  title: 'Common/Button',
  component: Button,
  parameters: { layout: 'centered' },
  argTypes: {
    variant: {
      control: 'select',
      options: ['primary', 'secondary', 'outline', 'ghost', 'danger'],
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
    },
    isLoading: { control: 'boolean' },
    disabled: { control: 'boolean' },
  },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Primary: Story = {
  args: { variant: 'primary', children: 'Save Changes' },
};

export const Secondary: Story = {
  args: { variant: 'secondary', children: 'Cancel' },
};

export const Outline: Story = {
  args: { variant: 'outline', children: 'Export' },
};

export const Ghost: Story = {
  args: { variant: 'ghost', children: 'View Details' },
};

export const Danger: Story = {
  args: { variant: 'danger', children: 'Delete Collection' },
};

export const Small: Story = {
  args: { variant: 'primary', size: 'sm', children: 'Add Tag' },
};

export const Large: Story = {
  args: { variant: 'primary', size: 'lg', children: 'Get Started' },
};

export const Loading: Story = {
  args: { variant: 'primary', isLoading: true, children: 'Saving...' },
};

export const Disabled: Story = {
  args: { variant: 'primary', disabled: true, children: 'Not Available' },
};

export const WithIcon: Story = {
  args: {
    variant: 'primary',
    children: (
      <>
        <Plus className="h-4 w-4" />
        New Collection
      </>
    ),
  },
};

export const DangerWithIcon: Story = {
  args: {
    variant: 'danger',
    children: (
      <>
        <Trash2 className="h-4 w-4" />
        Delete
      </>
    ),
  },
};

export const AllVariants: Story = {
  args: { children: '' },
  render: () => (
    <div className="flex flex-wrap gap-3 p-4">
      <Button variant="primary">Primary</Button>
      <Button variant="secondary">Secondary</Button>
      <Button variant="outline">Outline</Button>
      <Button variant="ghost">Ghost</Button>
      <Button variant="danger">Danger</Button>
    </div>
  ),
};

export const AllSizes: Story = {
  args: { children: '' },
  render: () => (
    <div className="flex items-center gap-3 p-4">
      <Button size="sm">Small</Button>
      <Button size="md">Medium</Button>
      <Button size="lg">Large</Button>
    </div>
  ),
};
