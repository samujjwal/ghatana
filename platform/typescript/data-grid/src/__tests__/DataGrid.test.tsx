/**
 * @group unit
 * @tier U, I-br
 *
 * Tests for @ghatana/data-grid — DataGrid component with sorting, filtering,
 * pagination, and accessibility.
 */
import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { DataGrid, type ColumnDef } from '../DataGrid';

// ─── Fixtures ────────────────────────────────────────────────────────────────

interface User {
  id: string;
  name: string;
  email: string;
  age: number;
}

const USERS: User[] = [
  { id: '1', name: 'Alice', email: 'alice@example.com', age: 30 },
  { id: '2', name: 'Bob', email: 'bob@example.com', age: 25 },
  { id: '3', name: 'Charlie', email: 'charlie@example.com', age: 35 },
  { id: '4', name: 'Diana', email: 'diana@example.com', age: 28 },
  { id: '5', name: 'Eve', email: 'eve@example.com', age: 22 },
];

const COLUMNS: ColumnDef<User>[] = [
  { key: 'name', header: 'Name', sortable: true, filterable: true },
  { key: 'email', header: 'Email', sortable: false },
  { key: 'age', header: 'Age', sortable: true },
];

// ─── Rendering ───────────────────────────────────────────────────────────────

describe('DataGrid — rendering', () => {
  it('renders column headers', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Email')).toBeInTheDocument();
    expect(screen.getByText('Age')).toBeInTheDocument();
  });

  it('renders a row for each data item', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    USERS.forEach((u) => expect(screen.getByText(u.name)).toBeInTheDocument());
  });

  it('renders as a <table> element', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('applies the provided aria-label to the table', () => {
    render(
      <DataGrid data={USERS} columns={COLUMNS} aria-label="Users table" />,
    );
    expect(screen.getByRole('table', { name: 'Users table' })).toBeInTheDocument();
  });

  it('shows loading state when isLoading is true', () => {
    render(<DataGrid data={[]} columns={COLUMNS} isLoading />);
    // Loading indicator should be visible
    const loading = screen.queryByText(/loading/i) ?? screen.queryByRole('status');
    expect(loading ?? screen.queryByRole('progressbar')).not.toBeNull();
  });

  it('shows empty-state message when data is empty and not loading', () => {
    render(
      <DataGrid
        data={[]}
        columns={COLUMNS}
        emptyMessage="No users found"
      />,
    );
    expect(screen.getByText('No users found')).toBeInTheDocument();
  });

  it('uses a default empty-state message when none is provided', () => {
    render(<DataGrid data={[]} columns={COLUMNS} />);
    const empty =
      screen.queryByText(/no data|no results|empty/i) ??
      screen.queryByRole('cell');
    expect(empty).not.toBeNull();
  });

  it('supports a custom render function for a column cell', () => {
    const columns: ColumnDef<User>[] = [
      { key: 'name', header: 'Name', render: (_, row) => <strong data-testid={`name-${row.id}`}>{row.name}</strong> },
    ];
    render(<DataGrid data={[USERS[0]!]} columns={columns} />);
    expect(screen.getByTestId('name-1')).toBeInTheDocument();
    expect(screen.getByTestId('name-1').tagName.toLowerCase()).toBe('strong');
  });
});

// ─── Sorting ─────────────────────────────────────────────────────────────────

describe('DataGrid — sorting', () => {
  it('renders a sort button for sortable columns', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    // Name and Age are sortable; Email is not — find at least one sort trigger
    const nameHeader = screen.getByText('Name').closest('th');
    expect(nameHeader).not.toBeNull();
    const button = within(nameHeader!).queryByRole('button');
    expect(button).not.toBeNull();
  });

  it('does not render a sort button for non-sortable columns', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    const emailHeader = screen.getByText('Email').closest('th');
    expect(emailHeader).not.toBeNull();
    const button = within(emailHeader!).queryByRole('button');
    expect(button).toBeNull();
  });

  it('calls onSortChange with correct column and direction on first click', async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();
    render(
      <DataGrid data={USERS} columns={COLUMNS} onSortChange={onSortChange} />,
    );
    const nameHeader = screen.getByText('Name').closest('th')!;
    const sortBtn = within(nameHeader).getByRole('button');
    await user.click(sortBtn);
    expect(onSortChange).toHaveBeenCalledWith({ column: 'name', direction: 'asc' });
  });

  it('toggles sort direction on second click', async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();
    render(
      <DataGrid data={USERS} columns={COLUMNS} onSortChange={onSortChange} />,
    );
    const nameHeader = screen.getByText('Name').closest('th')!;
    const sortBtn = within(nameHeader).getByRole('button');
    await user.click(sortBtn);
    await user.click(sortBtn);
    expect(onSortChange).toHaveBeenLastCalledWith({ column: 'name', direction: 'desc' });
  });

  it('sets aria-sort="ascending" on the sorted column header', async () => {
    const user = userEvent.setup();
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    const nameHeader = screen.getByText('Name').closest('th')!;
    const sortBtn = within(nameHeader).getByRole('button');
    await user.click(sortBtn);
    expect(nameHeader).toHaveAttribute('aria-sort', 'ascending');
  });

  it('sets aria-sort="descending" after toggling', async () => {
    const user = userEvent.setup();
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    const nameHeader = screen.getByText('Name').closest('th')!;
    const sortBtn = within(nameHeader).getByRole('button');
    await user.click(sortBtn);
    await user.click(sortBtn);
    expect(nameHeader).toHaveAttribute('aria-sort', 'descending');
  });
});

// ─── Filtering ───────────────────────────────────────────────────────────────

describe('DataGrid — filtering', () => {
  it('renders a filter input for filterable columns', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} />);
    const filterInput = screen.queryByRole('searchbox');
    expect(filterInput).not.toBeNull();
  });

  it('calls onFilterChange when filter text changes', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(
      <DataGrid data={USERS} columns={COLUMNS} onFilterChange={onFilterChange} />,
    );
    const filterInput = screen.getByRole('searchbox');
    await user.type(filterInput, 'Ali');
    expect(onFilterChange).toHaveBeenLastCalledWith(
      expect.arrayContaining([expect.objectContaining({ column: 'name', value: 'Ali' })]),
    );
  });

  it('hides rows that do not match the filter', async () => {
    const user = userEvent.setup();
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={10} />);
    const filterInput = screen.getByRole('searchbox');
    await user.type(filterInput, 'Alice');
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.queryByText('Bob')).not.toBeInTheDocument();
  });
});

// ─── Pagination ──────────────────────────────────────────────────────────────

describe('DataGrid — pagination', () => {
  it('does not render pagination when all data fits on one page', () => {
    render(<DataGrid data={USERS.slice(0, 3)} columns={COLUMNS} pageSize={5} />);
    const nav = screen.queryByRole('navigation', { name: /pagination/i });
    expect(nav).toBeNull();
  });

  it('renders pagination when data exceeds pageSize', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={2} />);
    const nav = screen.getByRole('navigation', { name: /pagination/i });
    expect(nav).toBeInTheDocument();
  });

  it('shows only the first page of rows initially', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={2} />);
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.queryByText('Charlie')).not.toBeInTheDocument();
  });

  it('navigates to next page and shows correct rows', async () => {
    const user = userEvent.setup();
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={2} />);
    const nextBtn = screen.getByRole('button', { name: /next/i });
    await user.click(nextBtn);
    expect(screen.getByText('Charlie')).toBeInTheDocument();
    expect(screen.queryByText('Alice')).not.toBeInTheDocument();
  });

  it('calls onPageChange with the new page index', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(
      <DataGrid data={USERS} columns={COLUMNS} pageSize={2} onPageChange={onPageChange} />,
    );
    const nextBtn = screen.getByRole('button', { name: /next/i });
    await user.click(nextBtn);
    expect(onPageChange).toHaveBeenCalledWith(
      expect.objectContaining({ page: expect.any(Number) }),
    );
  });

  it('disables Previous button on the first page', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={2} />);
    const prevBtn = screen.getByRole('button', { name: /prev/i });
    expect(prevBtn).toBeDisabled();
  });

  it('disables Next button on the last page', async () => {
    const user = userEvent.setup();
    // 5 items / pageSize 3 = 2 pages
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={3} />);
    const nextBtn = screen.getByRole('button', { name: /next/i });
    await user.click(nextBtn);
    expect(nextBtn).toBeDisabled();
  });

  it('shows page indicator text', () => {
    render(<DataGrid data={USERS} columns={COLUMNS} pageSize={2} />);
    const nav = screen.getByRole('navigation', { name: /pagination/i });
    // Should mention "Page 1" or "1 / 3" or similar
    expect(nav.textContent).toMatch(/1/);
  });
});
