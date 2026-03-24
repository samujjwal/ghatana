import { Chip } from './Chip.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Chip (Tailwind)',
  component: Chip,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Chip>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: 'Chip',
  },
};

export const Colors: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Chip label="Default" color="default" />
      <Chip label="Primary" color="primary" />
      <Chip label="Secondary" color="secondary" />
      <Chip label="Error" color="error" />
      <Chip label="Warning" color="warning" />
      <Chip label="Info" color="info" />
      <Chip label="Success" color="success" />
    </div>
  ),
};

export const Variants: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex gap-4">
      <div className="flex flex-col gap-2">
        <p className="text-sm font-semibold mb-1">Filled</p>
        <Chip label="Primary" color="primary" variant="filled" />
        <Chip label="Success" color="success" variant="filled" />
        <Chip label="Error" color="error" variant="filled" />
      </div>
      <div className="flex flex-col gap-2">
        <p className="text-sm font-semibold mb-1">Outlined</p>
        <Chip label="Primary" color="primary" variant="outlined" />
        <Chip label="Success" color="success" variant="outlined" />
        <Chip label="Error" color="error" variant="outlined" />
      </div>
    </div>
  ),
};

export const Sizes: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex items-center gap-3">
      <Chip label="Small Chip" size="small" color="primary" />
      <Chip label="Medium Chip" size="medium" color="primary" />
    </div>
  ),
};

export const WithDelete: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Chip label="Delete me" onDelete={() => alert('Deleted!')} />
      <Chip label="Primary" color="primary" onDelete={() => alert('Deleted!')} />
      <Chip label="Success" color="success" onDelete={() => alert('Deleted!')} variant="outlined" />
    </div>
  ),
};

export const Clickable: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Chip label="Click me" onClick={() => alert('Clicked!')} color="primary" />
      <Chip label="Outlined" onClick={() => alert('Clicked!')} variant="outlined" />
      <Chip 
        label="With delete" 
        onClick={() => alert('Chip clicked!')} 
        onDelete={() => alert('Deleted!')}
        color="success"
      />
    </div>
  ),
};

export const WithIcon: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Chip 
        label="React" 
        icon={
          <svg className="w-full h-full" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
        }
        color="primary"
      />
      <Chip 
        label="TypeScript" 
        icon={
          <svg className="w-full h-full" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
        }
        color="info"
        variant="outlined"
        onDelete={() => alert('Deleted!')}
      />
    </div>
  ),
};

export const Disabled: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Chip label="Disabled" disabled />
      <Chip label="Disabled" disabled color="primary" />
      <Chip label="Disabled" disabled onClick={() => {}} color="success" />
      <Chip label="Disabled" disabled onDelete={() => {}} color="error" />
    </div>
  ),
};

export const TagsExample: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="p-4 border rounded-lg">
      <p className="text-sm font-semibold mb-2">Technologies:</p>
      <div className="flex flex-wrap gap-2">
        <Chip label="React" color="primary" onDelete={() => {}} />
        <Chip label="TypeScript" color="info" onDelete={() => {}} />
        <Chip label="Tailwind CSS" color="success" onDelete={() => {}} />
        <Chip label="Vite" color="warning" onDelete={() => {}} />
        <Chip label="Storybook" color="secondary" onDelete={() => {}} />
      </div>
    </div>
  ),
};

export const StatusChips: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <span className="text-sm w-24">Active:</span>
        <Chip label="Active" color="success" size="small" />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-24">Pending:</span>
        <Chip label="Pending" color="warning" size="small" />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-24">Error:</span>
        <Chip label="Error" color="error" size="small" />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-24">Draft:</span>
        <Chip label="Draft" color="default" size="small" />
      </div>
    </div>
  ),
};

export const FilterChips: Story = {
  args: { label: 'Chip' },
  render: () => (
    <div className="p-4 border rounded-lg">
      <p className="text-sm font-semibold mb-2">Filters:</p>
      <div className="flex flex-wrap gap-2">
        <Chip 
          label="Category: Frontend" 
          onClick={() => {}}
          onDelete={() => {}}
          color="primary"
          variant="outlined"
        />
        <Chip 
          label="Status: Active" 
          onClick={() => {}}
          onDelete={() => {}}
          color="success"
          variant="outlined"
        />
        <Chip 
          label="Priority: High" 
          onClick={() => {}}
          onDelete={() => {}}
          color="error"
          variant="outlined"
        />
      </div>
    </div>
  ),
};

export const Playground: Story = {
  args: {
    label: 'Playground Chip',
    color: 'primary',
    variant: 'filled',
    size: 'medium',
    disabled: false,
  },
};
