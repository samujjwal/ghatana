import type { Meta, StoryObj } from '@storybook/react';
import FilterToolbar from './FilterToolbar';

const meta: Meta<typeof FilterToolbar> = {
  title: 'Common/FilterToolbar',
  component: FilterToolbar,
};

export default meta;

type Story = StoryObj<typeof FilterToolbar>;

export const Default: Story = {
  args: {
    title: 'Filters',
    searchPlaceholder: 'Search signals',
    activeFilters: [
      { label: 'Severity critical', value: 'critical' },
      { label: 'Type ingest', value: 'ingest' },
    ],
  },
};
