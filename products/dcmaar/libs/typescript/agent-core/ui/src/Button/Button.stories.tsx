import type { Meta, StoryObj } from '@storybook/react';
import { Button, buttonVariants } from './Button';
import { cn } from '../utils/cn';

const meta: Meta<typeof Button> = {
  title: 'Components/Button',
  component: Button,
  argTypes: {
    variant: {
      control: { type: 'select' },
      options: Object.keys(buttonVariants.variants.variant),
    },
    size: {
      control: { type: 'select' },
      options: Object.keys(buttonVariants.variants.size),
    },
    onClick: { action: 'clicked' },
  },
  args: {
    children: 'Button',
    variant: 'default',
    size: 'default',
  },
};

export default meta;

type Story = StoryObj<typeof Button>;

export const Default: Story = {};

export const Secondary: Story = {
  args: {
    variant: 'secondary',
    children: 'Secondary',
  },
};

export const Outline: Story = {
  args: {
    variant: 'outline',
    children: 'Outline',
  },
};

export const Destructive: Story = {
  args: {
    variant: 'destructive',
    children: 'Destructive',
  },
};

export const Ghost: Story = {
  args: {
    variant: 'ghost',
    children: 'Ghost',
  },
};

export const Link: Story = {
  args: {
    variant: 'link',
    children: 'Link',
  },
};

export const Loading: Story = {
  args: {
    isLoading: true,
    children: 'Loading...',
  },
};

const variants = Object.keys(buttonVariants.variants.variant);
const sizes = Object.keys(buttonVariants.variants.size);

export const AllVariants: Story = {
  render: () => (
    <div className="space-y-4">
      {variants.map((variant) => (
        <div key={variant} className="space-y-2">
          <h3 className="text-lg font-semibold capitalize">{variant}</h3>
          <div className="flex flex-wrap gap-2">
            {sizes.map((size) => (
              <Button
                key={`${variant}-${size}`}
                variant={variant as any}
                size={size as any}
                className="capitalize"
              >
                {variant} - {size}
              </Button>
            ))}
          </div>
        </div>
      ))}
    </div>
  ),
};

// Example of a custom button using the buttonVariants function
export const CustomButton: Story = {
  render: () => (
    <button
      className={cn(
        buttonVariants({ variant: 'outline', size: 'lg' }),
        'w-full max-w-xs'
      )}
    >
      Custom Styled Button
    </button>
  ),
};
