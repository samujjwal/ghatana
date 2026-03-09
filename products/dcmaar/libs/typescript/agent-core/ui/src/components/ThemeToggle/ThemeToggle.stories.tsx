import type { Meta, StoryObj } from '@storybook/react';
import { ThemeProvider } from '../../theme/ThemeProvider';
import { ThemeToggle } from './ThemeToggle';

const meta: Meta<typeof ThemeToggle> = {
  title: 'Components/ThemeToggle',
  component: ThemeToggle,
  decorators: [
    (Story) => (
      <ThemeProvider>
        <div className="flex items-center justify-center p-8">
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;

type Story = StoryObj<typeof ThemeToggle>;

export const Default: Story = {};

export const WithCustomClassName: Story = {
  args: {
    className: 'h-10 w-10',
  },
};

export const WithCustomIcons: Story = {
  render: () => (
    <div className="flex flex-col items-center gap-4">
      <p className="text-sm text-muted-foreground">Click to toggle theme</p>
      <ThemeToggle />
    </div>
  ),
};

export const InHeader: Story = {
  render: () => (
    <div className="w-full border-b">
      <div className="container flex h-16 items-center justify-between">
        <h1 className="text-lg font-semibold">DCMaar UI</h1>
        <ThemeToggle />
      </div>
    </div>
  ),
  parameters: {
    layout: 'fullscreen',
  },
};
