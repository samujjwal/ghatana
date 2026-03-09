/**
 * DataTable Component Stories
 *
 * @module DevSecOps/DataTable/stories
 */

import { Box, Chip, LinearProgress, Typography } from '@ghatana/ui';
import { useState } from 'react';

import { DataTable } from './DataTable';

import type { DataTableColumn, SortConfig, FilterConfig, PaginationConfig } from './types';
import type { Meta, StoryObj } from '@storybook/react';
import type { Item } from '@ghatana/yappc-types/devsecops';

const meta: Meta<typeof DataTable> = {
  title: 'DevSecOps/DataTable',
  component: DataTable,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof DataTable>;

// Mock data - 100 items for performance testing
const generateMockData = (count: number): Item[] => {
  const statuses: Item['status'][] = ['not-started', 'in-progress', 'completed', 'blocked'];
  const priorities: Item['priority'][] = ['low', 'medium', 'high', 'critical'];
  const titles = [
    'Implement authentication',
    'Design UI mockups',
    'Setup CI/CD pipeline',
    'Write unit tests',
    'Configure database',
    'Add error handling',
    'Optimize performance',
    'Update documentation',
    'Fix security vulnerabilities',
    'Refactor legacy code',
  ];

  return Array.from({ length: count }, (_, i) => ({
    id: `item-${i + 1}`,
    title: `${titles[i % titles.length]} ${i + 1}`,
    description: `Description for item ${i + 1}`,
    status: statuses[i % statuses.length],
    priority: priorities[i % priorities.length],
    phaseId: `phase-${(i % 3) + 1}`,
    tags: [`tag-${i % 5}`, `category-${i % 3}`],
    createdAt: new Date(2025, 0, (i % 30) + 1),
    updatedAt: new Date(2025, 0, (i % 30) + 1),
  }));
};

const mockData = generateMockData(10);
const largeMockData = generateMockData(100);

// Basic columns
const basicColumns: DataTableColumn<Item>[] = [
  { id: 'title', label: 'Title', field: 'title', width: '40%' },
  { id: 'status', label: 'Status', field: 'status', width: '20%' },
  { id: 'priority', label: 'Priority', field: 'priority', width: '20%' },
  { id: 'phaseId', label: 'Phase', field: 'phaseId', width: '20%' },
];

// Custom render columns
const customColumns: DataTableColumn<Item>[] = [
  {
    id: 'title',
    label: 'Title',
    field: 'title',
    width: '30%',
    render: (value, row) => (
      <Box>
        <Typography as="p" className="text-sm" fontWeight="medium">
          {String(value)}
        </Typography>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
          {row.description}
        </Typography>
      </Box>
    ),
  },
  {
    id: 'status',
    label: 'Status',
    field: 'status',
    width: '15%',
    render: (value) => {
      const statusColors = {
        'not-started': 'default',
        'in-progress': 'primary',
        'completed': 'success',
        'blocked': 'error',
      } as const;

      return (
        <Chip
          label={String(value)}
          color={statusColors[value as keyof typeof statusColors] || 'default'}
          size="sm"
        />
      );
    },
  },
  {
    id: 'priority',
    label: 'Priority',
    field: 'priority',
    width: '15%',
    render: (value) => {
      const priorityColors = {
        low: 'success',
        medium: 'warning',
        high: 'error',
        critical: 'error',
      } as const;

      return (
        <Chip
          label={String(value).toUpperCase()}
          color={priorityColors[value as keyof typeof priorityColors] || 'default'}
          size="sm"
          variant="outlined"
        />
      );
    },
  },
  {
    id: 'progress',
    label: 'Progress',
    field: 'status',
    width: '20%',
    sortable: false,
    render: (value) => {
      const progress = {
        'not-started': 0,
        'in-progress': 50,
        'completed': 100,
        'blocked': 25,
      }[value as string] || 0;

      return (
        <Box className="flex items-center gap-2">
          <LinearProgress
            variant="determinate"
            value={progress}
            className="grow h-[6px] rounded-xl"
          />
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
            {progress}%
          </Typography>
        </Box>
      );
    },
  },
  {
    id: 'createdAt',
    label: 'Created',
    field: 'createdAt',
    width: '20%',
    format: (value) => (value instanceof Date ? value.toLocaleDateString() : String(value)),
  },
];

/**
 * Default table with basic configuration
 */
export const Default: Story = {
  args: {
    data: mockData,
    columns: basicColumns,
  },
};

/**
 * Table with custom cell rendering
 */
export const CustomRendering: Story = {
  args: {
    data: mockData,
    columns: customColumns,
  },
};

/**
 * Controlled sorting example
 */
export const ControlledSorting: Story = {
  render: () => {
    const [sortConfig, setSortConfig] = useState<SortConfig>({
      column: 'title',
      direction: 'asc',
    });

    return (
      <DataTable
        data={mockData}
        columns={basicColumns}
        sortConfig={sortConfig}
        onSortChange={setSortConfig}
      />
    );
  },
};

/**
 * Controlled pagination example
 */
export const ControlledPagination: Story = {
  render: () => {
    const [paginationConfig, setPaginationConfig] = useState<PaginationConfig>({
      page: 0,
      rowsPerPage: 5,
      totalRows: largeMockData.length,
    });

    return (
      <DataTable
        data={largeMockData}
        columns={basicColumns}
        paginationConfig={paginationConfig}
        onPaginationChange={setPaginationConfig}
      />
    );
  },
};

/**
 * Single row selection
 */
export const SingleSelection: Story = {
  render: () => {
    const [selectedRows, setSelectedRows] = useState<string[]>([]);

    return (
      <Box>
        <Typography as="p" className="text-sm" className="mb-4">
          Selected: {selectedRows.join(', ') || 'None'}
        </Typography>
        <DataTable
          data={mockData}
          columns={basicColumns}
          selectionMode="single"
          selectedRows={selectedRows}
          onSelectionChange={setSelectedRows}
        />
      </Box>
    );
  },
};

/**
 * Multiple row selection
 */
export const MultipleSelection: Story = {
  render: () => {
    const [selectedRows, setSelectedRows] = useState<string[]>([]);

    return (
      <Box>
        <Typography as="p" className="text-sm" className="mb-4">
          Selected {selectedRows.length} row(s): {selectedRows.join(', ') || 'None'}
        </Typography>
        <DataTable
          data={mockData}
          columns={basicColumns}
          selectionMode="multiple"
          selectedRows={selectedRows}
          onSelectionChange={setSelectedRows}
        />
      </Box>
    );
  },
};

/**
 * Controlled filtering example
 */
export const ControlledFiltering: Story = {
  render: () => {
    const [filterConfig, setFilterConfig] = useState<FilterConfig>({
      status: 'in-progress',
    });

    return (
      <Box>
        <Typography as="p" className="text-sm" className="mb-4">
          Filter: status = {filterConfig.status as string}
        </Typography>
        <DataTable
          data={mockData}
          columns={basicColumns}
          filterConfig={filterConfig}
          onFilterChange={setFilterConfig}
        />
      </Box>
    );
  },
};

/**
 * Dense (compact) table
 */
export const Dense: Story = {
  args: {
    data: mockData,
    columns: basicColumns,
    dense: true,
  },
};

/**
 * Table without striping
 */
export const NoStriping: Story = {
  args: {
    data: mockData,
    columns: basicColumns,
    striped: false,
  },
};

/**
 * Table without hover effect
 */
export const NoHover: Story = {
  args: {
    data: mockData,
    columns: basicColumns,
    hoverable: false,
  },
};

/**
 * Loading state
 */
export const Loading: Story = {
  args: {
    data: [],
    columns: basicColumns,
    loading: true,
  },
};

/**
 * Empty state
 */
export const Empty: Story = {
  args: {
    data: [],
    columns: basicColumns,
    emptyMessage: 'No items found. Try adjusting your filters.',
  },
};

/**
 * Without pagination
 */
export const NoPagination: Story = {
  args: {
    data: mockData,
    columns: basicColumns,
    showPagination: false,
  },
};

/**
 * Without header
 */
export const NoHeader: Story = {
  args: {
    data: mockData,
    columns: basicColumns,
    showHeader: false,
  },
};

/**
 * Row click handler
 */
export const RowClick: Story = {
  render: () => {
    const [clickedRow, setClickedRow] = useState<Item | null>(null);

    return (
      <Box>
        <Typography as="p" className="text-sm" className="mb-4">
          Clicked: {clickedRow?.title || 'None'}
        </Typography>
        <DataTable
          data={mockData}
          columns={basicColumns}
          onRowClick={setClickedRow}
        />
      </Box>
    );
  },
};

/**
 * Large dataset (100 items) - Performance test
 */
export const LargeDataset: Story = {
  args: {
    data: largeMockData,
    columns: customColumns,
  },
};

/**
 * Full-featured example with all controls
 */
export const FullFeatured: Story = {
  render: () => {
    const [sortConfig, setSortConfig] = useState<SortConfig | undefined>();
    const [filterConfig, setFilterConfig] = useState<FilterConfig>({});
    const [paginationConfig, setPaginationConfig] = useState<PaginationConfig>({
      page: 0,
      rowsPerPage: 10,
      totalRows: largeMockData.length,
    });
    const [selectedRows, setSelectedRows] = useState<string[]>([]);

    return (
      <Box>
        <Box className="mb-4 flex gap-4 items-center">
          <Typography as="p" className="text-sm">
            Selected: {selectedRows.length} row(s)
          </Typography>
          <Typography as="p" className="text-sm">
            Sort: {sortConfig ? `${sortConfig.column} (${sortConfig.direction})` : 'None'}
          </Typography>
          <Typography as="p" className="text-sm">
            Page: {paginationConfig.page + 1} of {Math.ceil(paginationConfig.totalRows / paginationConfig.rowsPerPage)}
          </Typography>
        </Box>
        <DataTable
          data={largeMockData}
          columns={customColumns}
          sortConfig={sortConfig}
          onSortChange={setSortConfig}
          filterConfig={filterConfig}
          onFilterChange={setFilterConfig}
          paginationConfig={paginationConfig}
          onPaginationChange={setPaginationConfig}
          selectionMode="multiple"
          selectedRows={selectedRows}
          onSelectionChange={setSelectedRows}
          onRowClick={(row) => console.log('Row clicked:', row)}
        />
      </Box>
    );
  },
};
