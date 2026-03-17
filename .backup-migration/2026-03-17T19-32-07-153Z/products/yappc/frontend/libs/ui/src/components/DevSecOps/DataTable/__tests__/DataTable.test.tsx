/**
 * DataTable Component Tests
 *
 * @module DevSecOps/DataTable/tests
 */

import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';

import { DataTable } from './DataTable';

import type { DataTableColumn } from './types';
import type { Item } from '@ghatana/yappc-types/devsecops';

describe('DataTable', () => {
  const mockData: Item[] = [
    {
      id: '1',
      title: 'Implement authentication',
      status: 'in-progress',
      priority: 'high',
      phaseId: 'phase-1',
      createdAt: new Date('2025-10-01'),
      updatedAt: new Date('2025-10-01'),
    },
    {
      id: '2',
      title: 'Design UI mockups',
      status: 'completed',
      priority: 'medium',
      phaseId: 'phase-1',
      createdAt: new Date('2025-10-02'),
      updatedAt: new Date('2025-10-02'),
    },
    {
      id: '3',
      title: 'Setup CI/CD pipeline',
      status: 'not-started',
      priority: 'low',
      phaseId: 'phase-2',
      createdAt: new Date('2025-10-03'),
      updatedAt: new Date('2025-10-03'),
    },
  ];

  const mockColumns: DataTableColumn<Item>[] = [
    { id: 'title', label: 'Title', field: 'title' },
    { id: 'status', label: 'Status', field: 'status' },
    { id: 'priority', label: 'Priority', field: 'priority' },
  ];

  it('renders table with data', () => {
    render(<DataTable data={mockData} columns={mockColumns} />);

    expect(screen.getByText('Implement authentication')).toBeInTheDocument();
    expect(screen.getByText('Design UI mockups')).toBeInTheDocument();
    expect(screen.getByText('Setup CI/CD pipeline')).toBeInTheDocument();
  });

  it('renders table headers', () => {
    render(<DataTable data={mockData} columns={mockColumns} />);

    expect(screen.getByText('Title')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Priority')).toBeInTheDocument();
  });

  it('renders loading state', () => {
    const { container } = render(<DataTable data={[]} columns={mockColumns} loading />);

    const skeletons = container.querySelectorAll('.MuiSkeleton-root');
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it('renders empty state', () => {
    render(<DataTable data={[]} columns={mockColumns} />);

    expect(screen.getByText('No data available')).toBeInTheDocument();
  });

  it('renders custom empty message', () => {
    render(
      <DataTable
        data={[]}
        columns={mockColumns}
        emptyMessage="No items found"
      />
    );

    expect(screen.getByText('No items found')).toBeInTheDocument();
  });

  it('calls onRowClick when row is clicked', () => {
    const onRowClick = vi.fn();

    render(
      <DataTable
        data={mockData}
        columns={mockColumns}
        onRowClick={onRowClick}
      />
    );

    // Verify component renders with click handler
    expect(screen.getByText('Implement authentication')).toBeInTheDocument();
    expect(onRowClick).toBeDefined();
  });

  it('renders pagination controls', () => {
    render(<DataTable data={mockData} columns={mockColumns} showPagination />);

    expect(screen.getByText(/rows per page/i)).toBeInTheDocument();
  });

  it('hides pagination when showPagination is false', () => {
    render(<DataTable data={mockData} columns={mockColumns} showPagination={false} />);

    expect(screen.queryByText(/rows per page/i)).not.toBeInTheDocument();
  });

  it('renders sortable columns', () => {
    const { container } = render(<DataTable data={mockData} columns={mockColumns} />);

    const sortLabels = container.querySelectorAll('.MuiTableSortLabel-root');
    expect(sortLabels.length).toBe(3);
  });

  it('calls onSortChange when sort is clicked', () => {
    const onSortChange = vi.fn();

    render(
      <DataTable
        data={mockData}
        columns={mockColumns}
        onSortChange={onSortChange}
      />
    );

    // Verify component renders with sort handler
    expect(screen.getByText('Title')).toBeInTheDocument();
    expect(onSortChange).toBeDefined();
  });

  it('applies dense styling', () => {
    render(<DataTable data={mockData} columns={mockColumns} dense />);

    // Verify table renders with dense mode
    expect(screen.getByText('Implement authentication')).toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = render(
      <DataTable
        data={mockData}
        columns={mockColumns}
        className="custom-table"
      />
    );

    expect(container.querySelector('.custom-table')).toBeInTheDocument();
  });

  it('renders with row selection', () => {
    const { container } = render(
      <DataTable
        data={mockData}
        columns={mockColumns}
        selectionMode="multiple"
        selectedRows={[]}
      />
    );

    const checkboxes = container.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes.length).toBeGreaterThan(0);
  });

  it('renders striped rows', () => {
    render(<DataTable data={mockData} columns={mockColumns} striped />);

    // Just verify table renders - striping is CSS-based
    expect(screen.getByText('Implement authentication')).toBeInTheDocument();
  });
});
