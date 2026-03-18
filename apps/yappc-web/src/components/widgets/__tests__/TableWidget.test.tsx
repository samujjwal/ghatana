import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { TableWidget } from '../TableWidget';

/**
 * Unit tests for TableWidget component.
 *
 * Tests validate:
 * - Table rendering with data
 * - Column sorting
 * - Pagination
 * - Row selection
 * - Filtering
 * - Custom cell rendering
 * - Export functionality
 *
 * @doc.type test
 * @doc.purpose Test table widget component
 * @doc.layer product
 * @doc.pattern Component Test
 */

describe('TableWidget', () => {
  const user = userEvent.setup();

  const mockData = [
    { id: 1, name: 'Alice', email: 'alice@example.com', status: 'Active' },
    { id: 2, name: 'Bob', email: 'bob@example.com', status: 'Inactive' },
    { id: 3, name: 'Charlie', email: 'charlie@example.com', status: 'Active' },
    { id: 4, name: 'Diana', email: 'diana@example.com', status: 'Active' },
    { id: 5, name: 'Eve', email: 'eve@example.com', status: 'Inactive' },
  ];

  const mockColumns = [
    { key: 'name', label: 'Name', sortable: true },
    { key: 'email', label: 'Email', sortable: true },
    { key: 'status', label: 'Status', sortable: true },
  ];

  /**
   * Verifies table renders all rows and columns.
   *
   * GIVEN: Table with 5 rows and 3 columns
   * WHEN: Component renders
   * THEN: All data is displayed
   */
  it('should render table with all rows', () => {
    // GIVEN: Table config
    const config = {
      data: mockData,
      columns: mockColumns,
    };

    // WHEN: Render table
    render(<TableWidget {...config} />);

    // THEN: All rows visible
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('Charlie')).toBeInTheDocument();
    expect(screen.getByText('Diana')).toBeInTheDocument();
    expect(screen.getByText('Eve')).toBeInTheDocument();
  });

  /**
   * Verifies column sorting.
   *
   * GIVEN: Unsorted table
   * WHEN: User clicks column header
   * THEN: Table is sorted by that column
   */
  it('should sort table by column', async () => {
    // GIVEN: Table with data
    const config = {
      data: mockData,
      columns: mockColumns,
    };

    render(<TableWidget {...config} />);

    // WHEN: Click Name header
    const nameHeader = screen.getByRole('button', { name: /name/i });
    await user.click(nameHeader);

    // THEN: Sorted alphabetically
    await waitFor(() => {
      const rows = screen.getAllByRole('row');
      expect(rows[1]).toHaveTextContent('Alice');
      expect(rows[2]).toHaveTextContent('Bob');
      expect(rows[3]).toHaveTextContent('Charlie');
    });
  });

  /**
   * Verifies pagination.
   *
   * GIVEN: Table with pageSize=2
   * WHEN: User navigates pages
   * THEN: Correct rows are shown
   */
  it('should paginate table data', async () => {
    // GIVEN: Paginated table
    const config = {
      data: mockData,
      columns: mockColumns,
      pageSize: 2,
    };

    render(<TableWidget {...config} />);

    // THEN: Only first 2 rows visible
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.queryByText('Charlie')).not.toBeInTheDocument();

    // WHEN: Click next page
    const nextButton = screen.getByRole('button', { name: /next/i });
    await user.click(nextButton);

    // THEN: Next 2 rows visible
    await waitFor(() => {
      expect(screen.queryByText('Alice')).not.toBeInTheDocument();
      expect(screen.getByText('Charlie')).toBeInTheDocument();
      expect(screen.getByText('Diana')).toBeInTheDocument();
    });
  });

  /**
   * Verifies row selection.
   *
   * GIVEN: Selectable table
   * WHEN: User clicks checkbox
   * THEN: Row is selected
   */
  it('should select rows', async () => {
    // GIVEN: Selectable table
    const onSelectionChange = vi.fn();
    const config = {
      data: mockData,
      columns: mockColumns,
      selectable: true,
      onSelectionChange,
    };

    render(<TableWidget {...config} />);

    // WHEN: Click row checkbox
    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[1]); // First data row

    // THEN: Selection callback fired
    expect(onSelectionChange).toHaveBeenCalledWith([1]);
  });

  /**
   * Verifies filtering.
   *
   * GIVEN: Table with filter input
   * WHEN: User types filter text
   * THEN: Only matching rows shown
   */
  it('should filter table data', async () => {
    // GIVEN: Filterable table
    const config = {
      data: mockData,
      columns: mockColumns,
      filterable: true,
    };

    render(<TableWidget {...config} />);

    // WHEN: Type in filter
    const filterInput = screen.getByPlaceholderText(/filter/i);
    await user.type(filterInput, 'Alice');

    // THEN: Only Alice shown
    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeInTheDocument();
      expect(screen.queryByText('Bob')).not.toBeInTheDocument();
      expect(screen.queryByText('Charlie')).not.toBeInTheDocument();
    });
  });

  /**
   * Verifies empty state.
   *
   * GIVEN: Table with no data
   * WHEN: Component renders
   * THEN: Empty state message shown
   */
  it('should show empty state', () => {
    // GIVEN: Empty table
    const config = {
      data: [],
      columns: mockColumns,
    };

    // WHEN: Render table
    render(<TableWidget {...config} />);

    // THEN: Empty message visible
    expect(screen.getByText(/no data available/i)).toBeInTheDocument();
  });

  /**
   * Verifies custom cell renderer.
   *
   * GIVEN: Column with custom renderer
   * WHEN: Component renders
   * THEN: Custom content is displayed
   */
  it('should render custom cell content', () => {
    // GIVEN: Custom column
    const customColumns = [
      ...mockColumns,
      {
        key: 'actions',
        label: 'Actions',
        render: (row: unknown) => (
          <button data-testid={`delete-${row.id}`}>Delete</button>
        ),
      },
    ];

    const config = {
      data: mockData,
      columns: customColumns,
    };

    // WHEN: Render table
    render(<TableWidget {...config} />);

    // THEN: Custom buttons visible
    expect(screen.getByTestId('delete-1')).toBeInTheDocument();
    expect(screen.getByTestId('delete-2')).toBeInTheDocument();
  });

  /**
   * Verifies export functionality.
   *
   * GIVEN: Table with export enabled
   * WHEN: User clicks export button
   * THEN: Data is exported as CSV
   */
  it('should export data as CSV', async () => {
    // GIVEN: Exportable table
    const mockExport = vi.fn();
    const config = {
      data: mockData,
      columns: mockColumns,
      exportable: true,
      onExport: mockExport,
    };

    render(<TableWidget {...config} />);

    // WHEN: Click export
    const exportButton = screen.getByRole('button', { name: /export/i });
    await user.click(exportButton);

    // THEN: Export callback fired
    expect(mockExport).toHaveBeenCalledWith(mockData);
  });
});
