import type { Meta, StoryObj } from '@storybook/react';
import { Database, FileX, Search, Wifi } from 'lucide-react';
import { EmptyState } from './EmptyState';
import { Button } from './Button';

/**
 * Storybook stories for the EmptyState component.
 *
 * Demonstrates empty state patterns used across Data Cloud pages
 * when collections, connectors, or query results are empty.
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and visual testing
 * @doc.layer frontend
 */

const meta = {
  title: 'Common/EmptyState',
  component: EmptyState,
  parameters: { layout: 'centered' },
} satisfies Meta<typeof EmptyState>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    title: 'No data available',
    description: 'There is nothing to display right now.',
  },
};

export const WithIcon: Story = {
  args: {
    title: 'No collections',
    description: 'Get started by creating your first collection.',
    icon: <Database className="h-12 w-12" />,
  },
};

export const WithAction: Story = {
  args: {
    title: 'No collections',
    description: 'Get started by creating your first data collection.',
    icon: <Database className="h-12 w-12" />,
    action: <Button variant="primary">Create Collection</Button>,
  },
};

export const NoSearchResults: Story = {
  args: {
    title: 'No results found',
    description: 'Try adjusting your search or filter to find what you\'re looking for.',
    icon: <Search className="h-12 w-12" />,
    action: <Button variant="outline">Clear Filters</Button>,
  },
};

export const NoConnectors: Story = {
  args: {
    title: 'No connectors configured',
    description: 'Connect your data sources to start ingesting data.',
    icon: <Wifi className="h-12 w-12" />,
    action: <Button variant="primary">Add Connector</Button>,
  },
};

export const ErrorState: Story = {
  args: {
    title: 'Failed to load data',
    description: 'An error occurred while fetching data. Please try again.',
    icon: <FileX className="h-12 w-12" />,
    action: <Button variant="outline">Retry</Button>,
  },
};
