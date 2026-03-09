import { render, screen, fireEvent } from '@testing-library/react';
import { DataGrid, type DataGridProps } from '../DataGrid';

interface TestItem {
  id: string;
  name: string;
  status: string;
  type: string;
}

const mockItems: TestItem[] = [
  { id: '1', name: 'Item 1', status: 'active', type: 'typeA' },
  { id: '2', name: 'Item 2', status: 'inactive', type: 'typeB' },
  { id: '3', name: 'Item 3', status: 'active', type: 'typeA' },
];

describe('DataGrid', () => {
  describe('Basic Rendering', () => {
    it('renders with minimal props', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
        />
      );

      expect(screen.getByText('Item 1')).toBeInTheDocument();
      expect(screen.getByText('Item 2')).toBeInTheDocument();
    });

    it('renders title when provided', () => {
      render(
        <DataGrid
          items={mockItems}
          title="Test Grid"
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
        />
      );

      expect(screen.getByText('Test Grid')).toBeInTheDocument();
    });

    it('renders empty state when no items', () => {
      render(
        <DataGrid
          items={[]}
          emptyMessage="No data available"
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
        />
      );

      expect(screen.getByText('No data available')).toBeInTheDocument();
    });

    it('renders loading state', () => {
      render(
        <DataGrid
          items={[]}
          loading={true}
          loadingMessage="Loading data..."
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
        />
      );

      expect(screen.getByText('Loading data...')).toBeInTheDocument();
    });
  });

  describe('Display Modes', () => {
    it('renders table mode by default', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
            { header: 'Status', render: (item: TestItem) => item.status },
          ]}
        />
      );

      expect(screen.getByRole('table')).toBeInTheDocument();
      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    });

    it('renders grid mode with item renderer', () => {
      render(
        <DataGrid
          items={mockItems}
          displayMode="grid"
          itemRenderer={{
            render: (item: TestItem) => (
              <div data-testid={`grid-item-${item.id}`}>{item.name}</div>
            ),
          }}
        />
      );

      expect(screen.getByTestId('grid-item-1')).toBeInTheDocument();
      expect(screen.getByTestId('grid-item-2')).toBeInTheDocument();
      expect(screen.getByTestId('grid-item-3')).toBeInTheDocument();
    });

    it('renders list mode with item renderer', () => {
      render(
        <DataGrid
          items={mockItems}
          displayMode="list"
          itemRenderer={{
            render: (item: TestItem) => (
              <div data-testid={`list-item-${item.id}`}>{item.name}</div>
            ),
          }}
        />
      );

      expect(screen.getByTestId('list-item-1')).toBeInTheDocument();
      expect(screen.getByTestId('list-item-2')).toBeInTheDocument();
    });

    it('shows message when grid mode has no item renderer', () => {
      render(
        <DataGrid items={mockItems} displayMode="grid" />
      );

      expect(screen.getByText(/no item renderer configured/i)).toBeInTheDocument();
    });
  });

  describe('Statistics Cards', () => {
    it('renders statistics cards', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          statsCards={[
            {
              title: 'Total Items',
              calculate: (items) => items.length,
              variant: 'blue',
            },
            {
              title: 'Active Items',
              calculate: (items) => items.filter((i) => i.status === 'active').length,
              variant: 'green',
            },
          ]}
        />
      );

      expect(screen.getByText('Total Items')).toBeInTheDocument();
      expect(screen.getByText('3')).toBeInTheDocument();
      expect(screen.getByText('Active Items')).toBeInTheDocument();
      expect(screen.getByText('2')).toBeInTheDocument();
    });

    it('renders stat card with subtitle', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          statsCards={[
            {
              title: 'Total',
              calculate: (items) => items.length,
              subtitle: 'All items',
            },
          ]}
        />
      );

      expect(screen.getByText('All items')).toBeInTheDocument();
    });
  });

  describe('Filters', () => {
    it('renders text filters', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            { name: 'search', placeholder: 'Search items...' },
          ]}
        />
      );

      expect(screen.getByPlaceholderText('Search items...')).toBeInTheDocument();
    });

    it('renders select filters', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            {
              name: 'status',
              placeholder: 'All Statuses',
              type: 'select',
              options: [
                { label: 'Active', value: 'active' },
                { label: 'Inactive', value: 'inactive' },
              ],
            },
          ]}
        />
      );

      expect(screen.getByText('All Statuses')).toBeInTheDocument();
      expect(screen.getByText('Active')).toBeInTheDocument();
      expect(screen.getByText('Inactive')).toBeInTheDocument();
    });

    it('calls onFilter when filter values change', () => {
      const onFilter = jest.fn((items) => items);

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            { name: 'search', placeholder: 'Search...' },
          ]}
          onFilter={onFilter}
        />
      );

      const searchInput = screen.getByPlaceholderText('Search...');
      fireEvent.change(searchInput, { target: { value: 'Item 1' } });

      expect(onFilter).toHaveBeenCalled();
    });

    it('shows clear filters button when filters are active', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            { name: 'search', placeholder: 'Search...' },
          ]}
        />
      );

      const searchInput = screen.getByPlaceholderText('Search...');
      fireEvent.change(searchInput, { target: { value: 'test' } });

      expect(screen.getByText(/clear all filters/i)).toBeInTheDocument();
    });

    it('clears filters when clear button clicked', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            { name: 'search', placeholder: 'Search...' },
          ]}
        />
      );

      const searchInput = screen.getByPlaceholderText('Search...') as HTMLInputElement;
      fireEvent.change(searchInput, { target: { value: 'test' } });

      const clearButton = screen.getByText(/clear all filters/i);
      fireEvent.click(clearButton);

      expect(searchInput.value).toBe('');
    });

    it('renders filter labels', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            { name: 'search', placeholder: 'Search...', label: 'Search by name' },
          ]}
        />
      );

      expect(screen.getByText('Search by name')).toBeInTheDocument();
    });
  });

  describe('CRUD Operations', () => {
    it('renders create button', () => {
      const onCreate = jest.fn();

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onCreate,
            createButtonText: 'Add Item',
          }}
        />
      );

      expect(screen.getByText('Add Item')).toBeInTheDocument();
    });

    it('calls onCreate when create button clicked', () => {
      const onCreate = jest.fn();

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onCreate,
          }}
        />
      );

      const createButton = screen.getByText('Create');
      fireEvent.click(createButton);

      expect(onCreate).toHaveBeenCalledTimes(1);
    });

    it('renders edit buttons per item', () => {
      const onEdit = jest.fn();

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onEdit,
            showEditButton: true,
          }}
        />
      );

      const editButtons = screen.getAllByText('Edit');
      expect(editButtons).toHaveLength(3);
    });

    it('calls onEdit with item when edit button clicked', () => {
      const onEdit = jest.fn();

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onEdit,
            showEditButton: true,
          }}
        />
      );

      const editButtons = screen.getAllByText('Edit');
      fireEvent.click(editButtons[0]);

      expect(onEdit).toHaveBeenCalledWith(mockItems[0]);
    });

    it('renders delete buttons per item', () => {
      const onDelete = jest.fn();

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onDelete,
            showDeleteButton: true,
          }}
        />
      );

      const deleteButtons = screen.getAllByText('Delete');
      expect(deleteButtons).toHaveLength(3);
    });

    it('calls onDelete with item when delete button clicked', () => {
      const onDelete = jest.fn();

      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onDelete,
            showDeleteButton: true,
          }}
        />
      );

      const deleteButtons = screen.getAllByText('Delete');
      fireEvent.click(deleteButtons[1]);

      expect(onDelete).toHaveBeenCalledWith(mockItems[1]);
    });

    it('renders custom button labels', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          crudConfig={{
            onEdit: jest.fn(),
            onDelete: jest.fn(),
            showEditButton: true,
            showDeleteButton: true,
            editButtonLabel: 'Modify',
            deleteButtonLabel: 'Remove',
          }}
        />
      );

      expect(screen.getAllByText('Modify')).toHaveLength(3);
      expect(screen.getAllByText('Remove')).toHaveLength(3);
    });
  });

  describe('Custom Rendering', () => {
    it('renders custom column content', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            {
              header: 'Name',
              render: (item: TestItem) => (
                <span data-testid={`custom-${item.id}`}>{item.name.toUpperCase()}</span>
              ),
            },
          ]}
        />
      );

      expect(screen.getByTestId('custom-1')).toHaveTextContent('ITEM 1');
    });

    it('applies custom item className in grid mode', () => {
      const { container } = render(
        <DataGrid
          items={mockItems}
          displayMode="grid"
          itemRenderer={{
            render: (item: TestItem) => <div>{item.name}</div>,
            itemClassName: 'custom-class',
          }}
        />
      );

      const items = container.querySelectorAll('.custom-class');
      expect(items).toHaveLength(3);
    });

    it('uses custom keyExtractor', () => {
      const { container } = render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          keyExtractor={(item: TestItem) => item.id}
        />
      );

      const rows = container.querySelectorAll('tbody tr');
      expect(rows[0]).toHaveAttribute('data-key') || expect(rows).toHaveLength(3);
    });
  });

  describe('Grid Layout Configuration', () => {
    it('applies custom grid columns', () => {
      const { container } = render(
        <DataGrid
          items={mockItems}
          displayMode="grid"
          itemRenderer={{
            render: (item: TestItem) => <div>{item.name}</div>,
          }}
          gridCols={{ base: 1, md: 2, lg: 4, xl: 4 }}
        />
      );

      const gridContainer = container.querySelector('.grid');
      expect(gridContainer).toHaveClass('grid-cols-1');
    });
  });

  describe('Accessibility', () => {
    it('table has proper semantic structure', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
        />
      );

      expect(screen.getByRole('table')).toBeInTheDocument();
      expect(screen.getAllByRole('row')).toHaveLength(4); // 1 header + 3 data rows
    });

    it('filters have accessible labels', () => {
      render(
        <DataGrid
          items={mockItems}
          columns={[
            { header: 'Name', render: (item: TestItem) => item.name },
          ]}
          filters={[
            { name: 'search', placeholder: 'Search...', label: 'Search items' },
          ]}
        />
      );

      expect(screen.getByLabelText('Search items')).toBeInTheDocument();
    });
  });
});
