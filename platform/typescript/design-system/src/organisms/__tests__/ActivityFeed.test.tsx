import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  ActivityFeed,
  type StatCardConfig,
  type ColumnConfig,
  type FilterConfig,
  type ToastConfig,
} from '../ActivityFeed';

/**
 * Unit tests for ActivityFeed component.
 *
 * Tests validate:
 * - Empty state rendering
 * - Statistics card display
 * - Table rendering with columns
 * - Filter functionality
 * - Toast notifications
 * - Reverse chronological order
 *
 * @see ActivityFeed
 */
describe('ActivityFeed Component', () => {
  interface TestActivity {
    id: string;
    message: string;
    user: string;
    timestamp: string;
    type: 'info' | 'warning' | 'error';
  }

  const mockActivities: TestActivity[] = [
    {
      id: '1',
      message: 'User logged in',
      user: 'john@example.com',
      timestamp: '2025-11-08T10:00:00Z',
      type: 'info',
    },
    {
      id: '2',
      message: 'Failed login attempt',
      user: 'jane@example.com',
      timestamp: '2025-11-08T10:05:00Z',
      type: 'warning',
    },
    {
      id: '3',
      message: 'System error occurred',
      user: 'system',
      timestamp: '2025-11-08T10:10:00Z',
      type: 'error',
    },
  ];

  const columns: ColumnConfig<TestActivity>[] = [
    {
      header: 'Message',
      render: (item) => item.message,
    },
    {
      header: 'User',
      render: (item) => item.user,
    },
    {
      header: 'Type',
      render: (item) => item.type,
    },
  ];

  /**
   * Verifies empty state rendering.
   *
   * GIVEN: ActivityFeed with no items
   * WHEN: Component renders
   * THEN: Empty message is displayed
   */
  it('should display empty message when no items', () => {
    render(<ActivityFeed items={[]} columns={columns} />);

    expect(
      screen.getByText(/no activities yet/i)
    ).toBeInTheDocument();
  });

  /**
   * Verifies custom empty message.
   *
   * GIVEN: ActivityFeed with custom emptyMessage
   * WHEN: Component renders with no items
   * THEN: Custom message is displayed
   */
  it('should display custom empty message', () => {
    render(
      <ActivityFeed
        items={[]}
        columns={columns}
        emptyMessage="No events found"
      />
    );

    expect(screen.getByText('No events found')).toBeInTheDocument();
  });

  /**
   * Verifies table headers rendering.
   *
   * GIVEN: ActivityFeed with column configs
   * WHEN: Component renders with items
   * THEN: All column headers are visible
   */
  it('should render table headers', () => {
    render(<ActivityFeed items={mockActivities} columns={columns} />);

    expect(screen.getByText('Message')).toBeInTheDocument();
    expect(screen.getByText('User')).toBeInTheDocument();
    expect(screen.getByText('Type')).toBeInTheDocument();
  });

  /**
   * Verifies activity items rendering.
   *
   * GIVEN: ActivityFeed with mock activities
   * WHEN: Component renders
   * THEN: All activity messages are displayed
   */
  it('should render activity items', () => {
    render(<ActivityFeed items={mockActivities} columns={columns} />);

    expect(screen.getByText('User logged in')).toBeInTheDocument();
    expect(screen.getByText('Failed login attempt')).toBeInTheDocument();
    expect(screen.getByText('System error occurred')).toBeInTheDocument();
  });

  /**
   * Verifies statistics cards rendering.
   *
   * GIVEN: ActivityFeed with stats card configs
   * WHEN: Component renders
   * THEN: Stats cards display calculated values
   */
  it('should render statistics cards', () => {
    const statsCards: StatCardConfig<TestActivity>[] = [
      {
        title: 'Total Activities',
        calculate: (items) => items.length,
        variant: 'blue',
      },
      {
        title: 'Errors',
        calculate: (items) => items.filter((i) => i.type === 'error').length,
        variant: 'red',
      },
    ];

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        statsCards={statsCards}
      />
    );

    expect(screen.getByText('Total Activities')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument(); // Total count

    expect(screen.getByText('Errors')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument(); // Error count
  });

  /**
   * Verifies filter inputs rendering.
   *
   * GIVEN: ActivityFeed with filter configs
   * WHEN: Component renders
   * THEN: All filter inputs are visible
   */
  it('should render filter inputs', () => {
    const filters: FilterConfig[] = [
      { name: 'user', placeholder: 'Filter by user...' },
      { name: 'type', placeholder: 'Filter by type...' },
    ];

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        filters={filters}
      />
    );

    expect(screen.getByPlaceholderText('Filter by user...')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Filter by type...')).toBeInTheDocument();
  });

  /**
   * Verifies filtering functionality.
   *
   * GIVEN: ActivityFeed with filter function
   * WHEN: User enters filter text
   * THEN: Items are filtered accordingly
   */
  it('should filter items based on filter values', async () => {
    const filters: FilterConfig[] = [
      { name: 'user', placeholder: 'Filter by user...' },
    ];

    const onFilter = (items: TestActivity[], filterValues: Record<string, string>) => {
      if (!filterValues.user) return items;
      return items.filter((item) =>
        item.user.toLowerCase().includes(filterValues.user.toLowerCase())
      );
    };

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        filters={filters}
        onFilter={onFilter}
      />
    );

    // Initially all items visible
    expect(screen.getByText('User logged in')).toBeInTheDocument();
    expect(screen.getByText('Failed login attempt')).toBeInTheDocument();

    // Filter by user
    const filterInput = screen.getByPlaceholderText('Filter by user...');
    await userEvent.type(filterInput, 'john');

    // Only john's activity should be visible
    expect(screen.getByText('User logged in')).toBeInTheDocument();
    expect(screen.queryByText('Failed login attempt')).not.toBeInTheDocument();
  });

  /**
   * Verifies clear filters functionality.
   *
   * GIVEN: ActivityFeed with active filters
   * WHEN: User clicks clear filters
   * THEN: All filters are reset
   */
  it('should clear all filters when clear button clicked', async () => {
    const filters: FilterConfig[] = [
      { name: 'user', placeholder: 'Filter by user...' },
    ];

    const onFilter = (items: TestActivity[], filterValues: Record<string, string>) => {
      if (!filterValues.user) return items;
      return items.filter((item) =>
        item.user.toLowerCase().includes(filterValues.user.toLowerCase())
      );
    };

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        filters={filters}
        onFilter={onFilter}
      />
    );

    // Apply filter
    const filterInput = screen.getByPlaceholderText('Filter by user...');
    await userEvent.type(filterInput, 'john');

    // Clear button should appear
    const clearButton = screen.getByText(/clear all filters/i);
    expect(clearButton).toBeInTheDocument();

    // Click clear
    fireEvent.click(clearButton);

    // Filter input should be empty
    expect(filterInput).toHaveValue('');
  });

  /**
   * Verifies toast notification display.
   *
   * GIVEN: ActivityFeed with toast config
   * WHEN: showToast is true with latestItem
   * THEN: Toast notification is visible
   */
  it('should display toast notification', () => {
    const toastConfig: ToastConfig<TestActivity> = {
      title: 'New Activity',
      message: (item) => item.message,
      variant: 'info',
    };

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        toastConfig={toastConfig}
        latestItem={mockActivities[0]}
        showToast={true}
      />
    );

    expect(screen.getByText('New Activity')).toBeInTheDocument();
    expect(screen.getByText('User logged in')).toBeInTheDocument();
  });

  /**
   * Verifies toast dismiss functionality.
   *
   * GIVEN: ActivityFeed with toast and dismiss callback
   * WHEN: User clicks dismiss button
   * THEN: onToastDismiss is called
   */
  it('should call onToastDismiss when toast dismissed', () => {
    const onToastDismiss = vi.fn();
    const toastConfig: ToastConfig<TestActivity> = {
      title: 'New Activity',
      message: (item) => item.message,
    };

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        toastConfig={toastConfig}
        latestItem={mockActivities[0]}
        showToast={true}
        onToastDismiss={onToastDismiss}
      />
    );

    const dismissButton = screen.getByLabelText(/dismiss notification/i);
    fireEvent.click(dismissButton);

    expect(onToastDismiss).toHaveBeenCalled();
  });

  /**
   * Verifies reverse chronological order.
   *
   * GIVEN: ActivityFeed with reverseOrder=true
   * WHEN: Component renders
   * THEN: Items are displayed newest first
   */
  it('should display items in reverse order when reverseOrder=true', () => {
    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        reverseOrder={true}
      />
    );

    const rows = screen.getAllByRole('row');
    // Skip header row
    const firstDataRow = rows[1];
    expect(firstDataRow).toHaveTextContent('System error occurred'); // Latest item
  });

  /**
   * Verifies normal chronological order.
   *
   * GIVEN: ActivityFeed with reverseOrder=false
   * WHEN: Component renders
   * THEN: Items are displayed oldest first
   */
  it('should display items in normal order when reverseOrder=false', () => {
    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        reverseOrder={false}
      />
    );

    const rows = screen.getAllByRole('row');
    const firstDataRow = rows[1];
    expect(firstDataRow).toHaveTextContent('User logged in'); // First item
  });

  /**
   * Verifies custom title rendering.
   *
   * GIVEN: ActivityFeed with custom title
   * WHEN: Component renders
   * THEN: Custom title is displayed
   */
  it('should display custom title', () => {
    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        title="Security Events"
      />
    );

    expect(screen.getByText('Security Events')).toBeInTheDocument();
  });

  /**
   * Verifies custom table title rendering.
   *
   * GIVEN: ActivityFeed with custom tableTitle
   * WHEN: Component renders
   * THEN: Custom table title is displayed
   */
  it('should display custom table title', () => {
    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        tableTitle="Event Log"
      />
    );

    expect(screen.getByText('Event Log')).toBeInTheDocument();
  });

  /**
   * Verifies select filter rendering.
   *
   * GIVEN: ActivityFeed with select filter
   * WHEN: Component renders
   * THEN: Select dropdown with options is visible
   */
  it('should render select filter with options', () => {
    const filters: FilterConfig[] = [
      {
        name: 'type',
        placeholder: 'Filter by type...',
        type: 'select',
        options: [
          { label: 'Info', value: 'info' },
          { label: 'Warning', value: 'warning' },
          { label: 'Error', value: 'error' },
        ],
      },
    ];

    render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        filters={filters}
      />
    );

    const select = screen.getByRole('combobox');
    expect(select).toBeInTheDocument();

    // Check options (including placeholder)
    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(4); // Placeholder + 3 options
  });

  /**
   * Verifies custom key extractor.
   *
   * GIVEN: ActivityFeed with keyExtractor function
   * WHEN: Component renders
   * THEN: Custom keys are used for items
   */
  it('should use custom keyExtractor for item keys', () => {
    const keyExtractor = (item: TestActivity) => item.id;

    const { container } = render(
      <ActivityFeed
        items={mockActivities}
        columns={columns}
        keyExtractor={keyExtractor}
      />
    );

    // Verify rows are rendered (key is internal, can't directly test)
    const rows = container.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(3);
  });
});
