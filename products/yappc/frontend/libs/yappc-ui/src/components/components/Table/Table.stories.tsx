import type { Meta, StoryObj } from '@storybook/react';

import { useState } from 'react';

import { Table } from './Table';


const meta: Meta<typeof Table> = {
  title: 'Components/Table',
  component: Table,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['default', 'striped', 'bordered'],
    },
    size: {
      control: 'select',
      options: ['default', 'compact'],
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Table>;

// Sample data
/**
 *
 */
interface User {
  id: number;
  name: string;
  email: string;
  role: string;
  status: 'active' | 'inactive';
  joinDate: string;
}

const sampleUsers: User[] = [
  { id: 1, name: 'Alice Johnson', email: 'alice@example.com', role: 'Admin', status: 'active', joinDate: '2023-01-15' },
  { id: 2, name: 'Bob Smith', email: 'bob@example.com', role: 'Editor', status: 'active', joinDate: '2023-03-22' },
  { id: 3, name: 'Carol White', email: 'carol@example.com', role: 'Viewer', status: 'inactive', joinDate: '2023-05-10' },
  { id: 4, name: 'David Brown', email: 'david@example.com', role: 'Editor', status: 'active', joinDate: '2023-07-05' },
  { id: 5, name: 'Eve Davis', email: 'eve@example.com', role: 'Admin', status: 'active', joinDate: '2023-09-18' },
];

const basicColumns: TableColumn<User>[] = [
  { id: 'name', label: 'Name', accessor: 'name' },
  { id: 'email', label: 'Email', accessor: 'email' },
  { id: 'role', label: 'Role', accessor: 'role' },
];

/**
 * Default basic table
 */
export const Default: Story = {
  args: {
    data: sampleUsers,
    columns: basicColumns,
  },
};

/**
 * All visual variants
 */
export const Variants: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <h3 className="text-lg font-semibold mb-3">Default</h3>
        <Table data={sampleUsers} columns={basicColumns} />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3">Striped</h3>
        <Table data={sampleUsers} columns={basicColumns} variant="striped" />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3">Bordered</h3>
        <Table data={sampleUsers} columns={basicColumns} variant="bordered" />
      </div>
    </div>
  ),
};

/**
 * Compact vs default size
 */
export const Sizes: Story = {
  render: () => (
    <div className="space-y-8">
      <div>
        <h3 className="text-lg font-semibold mb-3">Default Size</h3>
        <Table data={sampleUsers} columns={basicColumns} variant="striped" />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3">Compact Size</h3>
        <Table data={sampleUsers} columns={basicColumns} variant="striped" size="compact" />
      </div>
    </div>
  ),
};

/**
 * Hover effect on rows
 */
export const WithHover: Story = {
  args: {
    data: sampleUsers,
    columns: basicColumns,
    hover: true,
    variant: 'striped',
  },
};

/**
 * Clickable rows
 */
export const ClickableRows: Story = {
  render: () => {
    const [clicked, setClicked] = useState<string>('');

    return (
      <div>
        <Table
          data={sampleUsers}
          columns={basicColumns}
          hover
          onRowClick={(user) => setClicked(user.name)}
        />
        {clicked && (
          <div className="mt-4 p-3 bg-blue-50 rounded border border-blue-200">
            Clicked: <strong>{clicked}</strong>
          </div>
        )}
      </div>
    );
  },
};

/**
 * Column alignment
 */
export const Alignment: Story = {
  render: () => {
    const columns: TableColumn<User>[] = [
      { id: 'id', label: 'ID', accessor: 'id', align: 'center', width: 80 },
      { id: 'name', label: 'Name', accessor: 'name', align: 'left' },
      { id: 'email', label: 'Email', accessor: 'email', align: 'left' },
      { id: 'role', label: 'Role', accessor: 'role', align: 'center' },
      { id: 'status', label: 'Status', accessor: 'status', align: 'right' },
    ];

    return <Table data={sampleUsers} columns={columns} variant="bordered" />;
  },
};

/**
 * Custom cell rendering
 */
export const CustomRendering: Story = {
  render: () => {
    const columns: TableColumn<User>[] = [
      { id: 'name', label: 'Name', accessor: 'name' },
      { id: 'email', label: 'Email', accessor: 'email' },
      {
        id: 'role',
        label: 'Role',
        accessor: 'role',
        render: (value) => {
          const colors: Record<string, string> = {
            Admin: 'bg-purple-100 text-purple-700',
            Editor: 'bg-blue-100 text-blue-700',
            Viewer: 'bg-grey-100 text-grey-700',
          };
          return (
            <span className={`px-2 py-1 rounded text-xs font-semibold ${colors[value] || ''}`}>
              {value}
            </span>
          );
        },
      },
      {
        id: 'status',
        label: 'Status',
        accessor: 'status',
        align: 'center',
        render: (value) => (
          <span className={`inline-block w-2 h-2 rounded-full ${value === 'active' ? 'bg-green-500' : 'bg-grey-400'}`} />
        ),
      },
    ];

    return <Table data={sampleUsers} columns={columns} hover variant="striped" />;
  },
};

/**
 * Sorting functionality
 */
export const Sortable: Story = {
  render: () => {
    const [sortBy, setSortBy] = useState<string>('name');
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
    const [sortedData, setSortedData] = useState(sampleUsers);

    const columns: TableColumn<User>[] = [
      { id: 'name', label: 'Name', accessor: 'name', sortable: true },
      { id: 'email', label: 'Email', accessor: 'email', sortable: true },
      { id: 'role', label: 'Role', accessor: 'role', sortable: true },
      { id: 'joinDate', label: 'Join Date', accessor: 'joinDate', sortable: true },
    ];

    const handleSort = (columnId: string, direction: 'asc' | 'desc') => {
      setSortBy(columnId);
      setSortDirection(direction);

      const sorted = [...sampleUsers].sort((a, b) => {
        const aVal = a[columnId as keyof User];
        const bVal = b[columnId as keyof User];

        if (aVal < bVal) return direction === 'asc' ? -1 : 1;
        if (aVal > bVal) return direction === 'asc' ? 1 : -1;
        return 0;
      });

      setSortedData(sorted);
    };

    return (
      <Table
        data={sortedData}
        columns={columns}
        sortBy={sortBy}
        sortDirection={sortDirection}
        onSort={handleSort}
        variant="striped"
        hover
      />
    );
  },
};

/**
 * Sticky header with scrolling
 */
export const StickyHeader: Story = {
  render: () => {
    const manyUsers = Array.from({ length: 20 }, (_, i) => ({
      ...sampleUsers[i % sampleUsers.length],
      id: i + 1,
    }));

    return (
      <Table
        data={manyUsers}
        columns={basicColumns}
        stickyHeader
        maxHeight={400}
        variant="striped"
        hover
      />
    );
  },
};

/**
 * Custom column widths
 */
export const ColumnWidths: Story = {
  render: () => {
    const columns: TableColumn<User>[] = [
      { id: 'id', label: 'ID', accessor: 'id', width: 60 },
      { id: 'name', label: 'Name', accessor: 'name', width: 200 },
      { id: 'email', label: 'Email', accessor: 'email', width: 250 },
      { id: 'role', label: 'Role', accessor: 'role', width: 100 },
      { id: 'status', label: 'Status', accessor: 'status', width: 100 },
    ];

    return <Table data={sampleUsers} columns={columns} variant="bordered" />;
  },
};

/**
 * Custom row styling
 */
export const CustomRowStyling: Story = {
  render: () => {
    return (
      <Table
        data={sampleUsers}
        columns={basicColumns}
        rowClassName={(user) =>
          user.status === 'inactive' ? 'opacity-50 bg-grey-100 dark:bg-grey-800' : ''
        }
        hover
      />
    );
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
    emptyMessage: 'No users found',
  },
};

/**
 * Complex data with actions
 */
export const WithActions: Story = {
  render: () => {
    const columns: TableColumn<User>[] = [
      { id: 'name', label: 'Name', accessor: 'name' },
      { id: 'email', label: 'Email', accessor: 'email' },
      {
        id: 'role',
        label: 'Role',
        accessor: 'role',
        render: (value) => {
          const colors: Record<string, string> = {
            Admin: 'bg-purple-100 text-purple-700',
            Editor: 'bg-blue-100 text-blue-700',
            Viewer: 'bg-grey-100 text-grey-700',
          };
          return (
            <span className={`px-2 py-1 rounded text-xs font-semibold ${colors[value] || ''}`}>
              {value}
            </span>
          );
        },
      },
      {
        id: 'actions',
        label: 'Actions',
        align: 'right',
        render: (_, user) => (
          <div className="flex gap-2 justify-end">
            <button
              onClick={(e) => {
                e.stopPropagation();
                alert(`Edit ${user.name}`);
              }}
              className="px-2 py-1 text-xs text-blue-600 hover:bg-blue-50 rounded"
            >
              Edit
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                alert(`Delete ${user.name}`);
              }}
              className="px-2 py-1 text-xs text-red-600 hover:bg-red-50 rounded"
            >
              Delete
            </button>
          </div>
        ),
      },
    ];

    return <Table data={sampleUsers} columns={columns} hover variant="striped" />;
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => (
    <div className="dark">
      <Table
        data={sampleUsers}
        columns={basicColumns}
        variant="striped"
        hover
      />
    </div>
  ),
};

/**
 * With row selection
 */
export const WithSelection: Story = {
  render: () => {
    const [selected, setSelected] = useState<(string | number)[]>([]);

    return (
      <div>
        <Table
          data={sampleUsers}
          columns={basicColumns}
          selectable
          selected={selected}
          onSelectionChange={setSelected}
          rowId="id"
          hover
        />
        <div className="mt-4 p-3 bg-grey-100 rounded text-sm">
          Selected IDs: {selected.length > 0 ? selected.join(', ') : 'None'}
        </div>
      </div>
    );
  },
};

/**
 * With row expansion
 */
export const WithExpansion: Story = {
  render: () => {
    const [expanded, setExpanded] = useState<(string | number)[]>([]);

    return (
      <Table
        data={sampleUsers}
        columns={basicColumns}
        expandable
        expanded={expanded}
        onExpansionChange={setExpanded}
        rowId="id"
        renderExpanded={(user) => (
          <div className="p-4 space-y-2">
            <div className="font-semibold">Additional Details</div>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span className="text-grey-600">ID:</span> {user.id}
              </div>
              <div>
                <span className="text-grey-600">Status:</span> {user.status}
              </div>
              <div>
                <span className="text-grey-600">Join Date:</span> {user.joinDate}
              </div>
              <div>
                <span className="text-grey-600">Email:</span> {user.email}
              </div>
            </div>
          </div>
        )}
        hover
      />
    );
  },
};

/**
 * With pagination
 */
export const WithPagination: Story = {
  render: () => {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(3);

    // Generate more data
    const moreUsers = Array.from({ length: 25 }, (_, i) => ({
      id: i + 1,
      name: `User ${i + 1}`,
      email: `user${i + 1}@example.com`,
      role: ['Admin', 'Editor', 'Viewer'][i % 3],
      status: i % 2 === 0 ? 'active' : 'inactive',
      joinDate: `2023-${String(Math.floor(i / 2) + 1).padStart(2, '0')}-01`,
    })) as User[];

    return (
      <Table
        data={moreUsers}
        columns={basicColumns}
        paginated
        page={page}
        pageSize={pageSize}
        onPageChange={setPage}
        onPageSizeChange={(size) => {
          setPageSize(size);
          setPage(0);
        }}
        hover
      />
    );
  },
};

/**
 * Full featured table (selection + expansion + pagination + sorting)
 */
export const FullFeatured: Story = {
  render: () => {
    const [selected, setSelected] = useState<(string | number)[]>([]);
    const [expanded, setExpanded] = useState<(string | number)[]>([]);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(5);
    const [sortBy, setSortBy] = useState<string>('name');
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

    // Generate more data
    const moreUsers = Array.from({ length: 20 }, (_, i) => ({
      id: i + 1,
      name: `User ${String.fromCharCode(65 + (i % 26))}${i + 1}`,
      email: `user${i + 1}@example.com`,
      role: ['Admin', 'Editor', 'Viewer'][i % 3],
      status: i % 2 === 0 ? 'active' : 'inactive',
      joinDate: `2023-${String(Math.floor(i / 2) + 1).padStart(2, '0')}-01`,
    })) as User[];

    const sortableColumns: TableColumn<User>[] = [
      { id: 'name', label: 'Name', accessor: 'name', sortable: true },
      { id: 'email', label: 'Email', accessor: 'email', sortable: true },
      { id: 'role', label: 'Role', accessor: 'role', sortable: true },
      {
        id: 'status',
        label: 'Status',
        accessor: 'status',
        sortable: true,
        render: (value) => (
          <span
            className={cn(
              'px-2 py-1 text-xs font-medium rounded-full',
              value === 'active'
                ? 'bg-success-100 text-success-700'
                : 'bg-grey-100 text-grey-700'
            )}
          >
            {value}
          </span>
        ),
      },
    ];

    // Client-side sorting
    const sortedData = [...moreUsers].sort((a, b) => {
      const aVal = a[sortBy as keyof User];
      const bVal = b[sortBy as keyof User];
      if (aVal < bVal) return sortDirection === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });

    return (
      <div>
        <div className="mb-4 flex gap-4">
          <div className="text-sm">
            <strong>Selected:</strong> {selected.length} rows
          </div>
          <div className="text-sm">
            <strong>Expanded:</strong> {expanded.length} rows
          </div>
        </div>

        <Table
          data={sortedData}
          columns={sortableColumns}
          selectable
          selected={selected}
          onSelectionChange={setSelected}
          expandable
          expanded={expanded}
          onExpansionChange={setExpanded}
          rowId="id"
          paginated
          page={page}
          pageSize={pageSize}
          onPageChange={setPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            setPage(0);
          }}
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={(col, dir) => {
            setSortBy(col);
            setSortDirection(dir);
          }}
          renderExpanded={(user) => (
            <div className="p-4 space-y-2">
              <div className="font-semibold">User Details</div>
              <div className="grid grid-cols-3 gap-4 text-sm">
                <div>
                  <span className="text-grey-600">ID:</span> {user.id}
                </div>
                <div>
                  <span className="text-grey-600">Join Date:</span> {user.joinDate}
                </div>
                <div>
                  <span className="text-grey-600">Full Email:</span> {user.email}
                </div>
              </div>
            </div>
          )}
          hover
          variant="striped"
        />
      </div>
    );
  },
};

// Import cn for the story
import { cn } from '../../utils/cn';

import type { TableColumn } from './Table';
