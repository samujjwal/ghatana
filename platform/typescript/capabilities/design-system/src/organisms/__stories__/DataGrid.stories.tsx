import type { Meta, StoryObj } from '@storybook/react';
import { DataGrid, type DataGridStatConfig, type DataGridColumnConfig, type ItemRendererConfig, type DataGridFilterConfig, type CrudConfig } from '../DataGrid';

/**
 * DataGrid - Multi-mode data grid component
 *
 * Displays data in table, grid, or list modes with statistics, filters, and CRUD operations.
 *
 * ## Features
 * - **Three display modes**: table (rows), grid (cards), list (vertical)
 * - **Statistics cards**: Configurable stats with variant colors
 * - **Filters**: Text and select filters with clear button
 * - **CRUD operations**: Create, edit, delete callbacks
 * - **Loading states**: Customizable loading message
 * - **Empty states**: Customizable empty message
 * - **Responsive**: Grid columns configurable per breakpoint
 *
 * ## Use Cases
 * - Device management (card-based grid)
 * - User management (table with CRUD)
 * - Product catalog (grid with filters)
 * - Task lists (list mode)
 * - Resource management
 */
const meta = {
  title: 'Organisms/DataGrid',
  component: DataGrid,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'A versatile data grid component supporting multiple display modes, statistics, filters, and CRUD operations.',
      },
    },
  },
  tags: ['autodocs'],
} satisfies Meta<typeof DataGrid>;

export default meta;
type Story = StoryObj<typeof meta>;

// Sample data types
interface Device {
  id: string;
  name: string;
  type: 'mobile' | 'tablet' | 'desktop' | 'laptop';
  status: 'online' | 'offline';
  lastSeen: string;
  policies: number;
}

interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  status: 'active' | 'inactive';
  lastLogin: string;
}

interface Product {
  id: string;
  name: string;
  category: string;
  price: number;
  stock: number;
  rating: number;
}

// Sample data
const sampleDevices: Device[] = [
  { id: '1', name: "John's iPhone", type: 'mobile', status: 'online', lastSeen: '2 min ago', policies: 3 },
  { id: '2', name: "Sarah's MacBook", type: 'laptop', status: 'online', lastSeen: '5 min ago', policies: 2 },
  { id: '3', name: "Mike's iPad", type: 'tablet', status: 'offline', lastSeen: '2 hours ago', policies: 1 },
  { id: '4', name: "Office Desktop", type: 'desktop', status: 'online', lastSeen: 'Just now', policies: 5 },
  { id: '5', name: "Lisa's Phone", type: 'mobile', status: 'offline', lastSeen: '1 day ago', policies: 2 },
  { id: '6', name: "Dev Laptop", type: 'laptop', status: 'online', lastSeen: '10 min ago', policies: 4 },
];

const sampleUsers: User[] = [
  { id: '1', name: 'Alice Johnson', email: 'alice@example.com', role: 'Admin', status: 'active', lastLogin: '2024-01-15' },
  { id: '2', name: 'Bob Smith', email: 'bob@example.com', role: 'User', status: 'active', lastLogin: '2024-01-14' },
  { id: '3', name: 'Carol White', email: 'carol@example.com', role: 'Editor', status: 'inactive', lastLogin: '2024-01-10' },
  { id: '4', name: 'David Brown', email: 'david@example.com', role: 'User', status: 'active', lastLogin: '2024-01-15' },
];

const sampleProducts: Product[] = [
  { id: '1', name: 'Wireless Mouse', category: 'Electronics', price: 29.99, stock: 150, rating: 4.5 },
  { id: '2', name: 'Laptop Stand', category: 'Accessories', price: 49.99, stock: 75, rating: 4.8 },
  { id: '3', name: 'USB-C Cable', category: 'Electronics', price: 12.99, stock: 300, rating: 4.2 },
  { id: '4', name: 'Desk Lamp', category: 'Furniture', price: 39.99, stock: 50, rating: 4.6 },
  { id: '5', name: 'Keyboard', category: 'Electronics', price: 79.99, stock: 0, rating: 4.7 },
];

// Helper functions
const getTypeIcon = (type: string) => {
  switch (type) {
    case 'mobile': return '📱';
    case 'tablet': return '📱';
    case 'desktop': return '🖥️';
    case 'laptop': return '💻';
    default: return '📱';
  }
};

const getStatusColor = (status: string) => {
  return status === 'online' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800';
};

/**
 * Device Management - Grid mode with card-based display
 *
 * Shows devices in a responsive grid with statistics, filters, and create action.
 */
export const DeviceManagement: Story = {
  args: {
    items: sampleDevices,
    displayMode: 'grid',
    title: 'Device Management',
    statsCards: [
      { title: 'Total Devices', calculate: (items: Device[]) => items.length, variant: 'blue' },
      { title: 'Online', calculate: (items: Device[]) => items.filter(d => d.status === 'online').length, variant: 'green' },
      { title: 'Offline', calculate: (items: Device[]) => items.filter(d => d.status === 'offline').length, variant: 'gray' },
      { title: 'Total Policies', calculate: (items: Device[]) => items.reduce((sum, d) => sum + d.policies, 0), variant: 'purple' },
    ] as DataGridStatConfig<Device>[],
    itemRenderer: {
      render: (device: Device) => (
        <div className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow h-full">
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className="text-2xl">{getTypeIcon(device.type)}</span>
              <div>
                <h3 className="text-lg font-semibold text-gray-900">{device.name}</h3>
                <p className="text-sm text-gray-500 capitalize">{device.type}</p>
              </div>
            </div>
            <span className={`px-2 py-1 rounded-full text-xs font-semibold ${getStatusColor(device.status)}`}>
              {device.status}
            </span>
          </div>
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm text-gray-600">
              <span className={`h-2 w-2 rounded-full ${device.status === 'online' ? 'bg-green-500' : 'bg-gray-400'}`}></span>
              <span>Last seen: {device.lastSeen}</span>
            </div>
            <p className="text-sm text-gray-600">Policies: {device.policies}</p>
          </div>
        </div>
      ),
    } as ItemRendererConfig<Device>,
    filters: [
      { name: 'search', placeholder: 'Search devices...', type: 'text' },
      { name: 'status', placeholder: 'All Statuses', type: 'select', options: [
        { label: 'All', value: '' },
        { label: 'Online', value: 'online' },
        { label: 'Offline', value: 'offline' },
      ]},
      { name: 'type', placeholder: 'All Types', type: 'select', options: [
        { label: 'All', value: '' },
        { label: 'Mobile', value: 'mobile' },
        { label: 'Tablet', value: 'tablet' },
        { label: 'Desktop', value: 'desktop' },
        { label: 'Laptop', value: 'laptop' },
      ]},
    ] as DataGridFilterConfig[],
    onFilter: (items: Device[], filterValues: Record<string, string>) => {
      return items.filter(device => {
        const searchTerm = filterValues.search?.toLowerCase() || '';
        const matchesSearch = device.name.toLowerCase().includes(searchTerm);
        const matchesStatus = !filterValues.status || device.status === filterValues.status;
        const matchesType = !filterValues.type || device.type === filterValues.type;
        return matchesSearch && matchesStatus && matchesType;
      });
    },
    crudConfig: {
      onCreate: () => alert('Register new device'),
      createButtonText: 'Register Device',
    } as CrudConfig<Device>,
    gridCols: { base: 1, md: 2, lg: 3 },
    keyExtractor: (device: Device) => device.id,
  },
};

/**
 * User Management - Table mode with CRUD operations
 *
 * Shows users in a table with edit and delete actions per row.
 */
export const UserManagement: Story = {
  args: {
    items: sampleUsers,
    displayMode: 'table',
    title: 'User Management',
    statsCards: [
      { title: 'Total Users', calculate: (items: User[]) => items.length, variant: 'blue' },
      { title: 'Active', calculate: (items: User[]) => items.filter(u => u.status === 'active').length, variant: 'green' },
      { title: 'Inactive', calculate: (items: User[]) => items.filter(u => u.status === 'inactive').length, variant: 'gray' },
    ] as DataGridStatConfig<User>[],
    columns: [
      { header: 'Name', render: (user: User) => <span className="font-medium">{user.name}</span> },
      { header: 'Email', render: (user: User) => <span className="text-gray-600">{user.email}</span> },
      { header: 'Role', render: (user: User) => <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs">{user.role}</span> },
      { header: 'Status', render: (user: User) => <span className={`px-2 py-1 rounded text-xs ${user.status === 'active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>{user.status}</span> },
      { header: 'Last Login', render: (user: User) => <span className="text-gray-500 text-sm">{user.lastLogin}</span> },
    ] as DataGridColumnConfig<User>[],
    filters: [
      { name: 'search', placeholder: 'Search users...', type: 'text' },
      { name: 'role', placeholder: 'All Roles', type: 'select', options: [
        { label: 'All', value: '' },
        { label: 'Admin', value: 'Admin' },
        { label: 'Editor', value: 'Editor' },
        { label: 'User', value: 'User' },
      ]},
    ] as DataGridFilterConfig[],
    onFilter: (items: User[], filterValues: Record<string, string>) => {
      return items.filter(user => {
        const searchTerm = filterValues.search?.toLowerCase() || '';
        const matchesSearch = user.name.toLowerCase().includes(searchTerm) || user.email.toLowerCase().includes(searchTerm);
        const matchesRole = !filterValues.role || user.role === filterValues.role;
        return matchesSearch && matchesRole;
      });
    },
    crudConfig: {
      onCreate: () => alert('Create new user'),
      onEdit: (user: User) => alert(`Edit user: ${user.name}`),
      onDelete: (user: User) => alert(`Delete user: ${user.name}`),
      createButtonText: 'Add User',
      showEditButton: true,
      showDeleteButton: true,
    } as CrudConfig<User>,
    keyExtractor: (user: User) => user.id,
  },
};

/**
 * Product Catalog - Grid mode with pricing
 *
 * Shows products in a grid with stock and rating information.
 */
export const ProductCatalog: Story = {
  args: {
    items: sampleProducts,
    displayMode: 'grid',
    title: 'Product Catalog',
    statsCards: [
      { title: 'Total Products', calculate: (items: Product[]) => items.length, variant: 'blue' },
      { title: 'In Stock', calculate: (items: Product[]) => items.filter(p => p.stock > 0).length, variant: 'green' },
      { title: 'Out of Stock', calculate: (items: Product[]) => items.filter(p => p.stock === 0).length, variant: 'red' },
      { title: 'Avg Rating', calculate: (items: Product[]) => (items.reduce((sum, p) => sum + p.rating, 0) / items.length).toFixed(1), variant: 'yellow' },
    ] as DataGridStatConfig<Product>[],
    itemRenderer: {
      render: (product: Product) => (
        <div className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow h-full">
          <h3 className="text-lg font-semibold text-gray-900 mb-2">{product.name}</h3>
          <p className="text-sm text-gray-500 mb-2">{product.category}</p>
          <div className="flex items-center justify-between mb-2">
            <span className="text-xl font-bold text-blue-600">${product.price}</span>
            <span className="text-sm text-yellow-600">⭐ {product.rating}</span>
          </div>
          <p className={`text-sm ${product.stock > 0 ? 'text-green-600' : 'text-red-600'}`}>
            {product.stock > 0 ? `${product.stock} in stock` : 'Out of stock'}
          </p>
        </div>
      ),
    } as ItemRendererConfig<Product>,
    filters: [
      { name: 'search', placeholder: 'Search products...', type: 'text' },
      { name: 'category', placeholder: 'All Categories', type: 'select', options: [
        { label: 'All', value: '' },
        { label: 'Electronics', value: 'Electronics' },
        { label: 'Accessories', value: 'Accessories' },
        { label: 'Furniture', value: 'Furniture' },
      ]},
    ] as DataGridFilterConfig[],
    onFilter: (items: Product[], filterValues: Record<string, string>) => {
      return items.filter(product => {
        const searchTerm = filterValues.search?.toLowerCase() || '';
        const matchesSearch = product.name.toLowerCase().includes(searchTerm);
        const matchesCategory = !filterValues.category || product.category === filterValues.category;
        return matchesSearch && matchesCategory;
      });
    },
    gridCols: { base: 1, md: 2, lg: 3, xl: 4 },
    keyExtractor: (product: Product) => product.id,
  },
};

/**
 * Task List - List mode (vertical)
 *
 * Shows items in a vertical list layout.
 */
export const TaskList: Story = {
  args: {
    items: sampleUsers,
    displayMode: 'list',
    title: 'Task Assignments',
    itemRenderer: {
      render: (user: User) => (
        <div className="border border-gray-200 rounded-lg p-4 hover:shadow-sm transition-shadow">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold text-gray-900">{user.name}</h3>
              <p className="text-sm text-gray-600">{user.email}</p>
            </div>
            <span className={`px-3 py-1 rounded text-sm ${user.status === 'active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
              {user.status}
            </span>
          </div>
        </div>
      ),
    } as ItemRendererConfig<User>,
    keyExtractor: (user: User) => user.id,
  },
};

/**
 * With Filters - Table with text and select filters
 *
 * Demonstrates filtering functionality with clear button.
 */
export const WithFilters: Story = {
  args: {
    items: sampleUsers,
    displayMode: 'table',
    columns: [
      { header: 'Name', render: (user: User) => user.name },
      { header: 'Email', render: (user: User) => user.email },
      { header: 'Role', render: (user: User) => user.role },
    ] as DataGridColumnConfig<User>[],
    filters: [
      { name: 'search', placeholder: 'Search...', type: 'text', label: 'Search Users' },
      { name: 'role', placeholder: 'Filter by role', type: 'select', label: 'Role', options: [
        { label: 'All', value: '' },
        { label: 'Admin', value: 'Admin' },
        { label: 'User', value: 'User' },
      ]},
    ] as DataGridFilterConfig[],
    onFilter: (items: User[], filterValues: Record<string, string>) => {
      return items.filter(user => {
        const matchesSearch = user.name.toLowerCase().includes(filterValues.search?.toLowerCase() || '');
        const matchesRole = !filterValues.role || user.role === filterValues.role;
        return matchesSearch && matchesRole;
      });
    },
    keyExtractor: (user: User) => user.id,
  },
};

/**
 * With Statistics - Grid with stat cards
 *
 * Shows statistics cards above the data grid.
 */
export const WithStatistics: Story = {
  args: {
    items: sampleProducts,
    displayMode: 'grid',
    statsCards: [
      { title: 'Products', calculate: (items: Product[]) => items.length, variant: 'blue' },
      { title: 'Total Value', calculate: (items: Product[]) => `$${items.reduce((sum, p) => sum + p.price * p.stock, 0).toFixed(2)}`, variant: 'green' },
      { title: 'Categories', calculate: (items: Product[]) => new Set(items.map(p => p.category)).size, variant: 'purple' },
    ] as DataGridStatConfig<Product>[],
    itemRenderer: {
      render: (product: Product) => (
        <div className="border rounded p-3">
          <h4 className="font-semibold">{product.name}</h4>
          <p className="text-sm text-gray-600">${product.price}</p>
        </div>
      ),
    } as ItemRendererConfig<Product>,
    keyExtractor: (product: Product) => product.id,
  },
};

/**
 * Empty State - No items to display
 *
 * Shows custom empty message when no items are present.
 */
export const EmptyState: Story = {
  args: {
    items: [],
    displayMode: 'table',
    columns: [
      { header: 'Name', render: (user: User) => user.name },
      { header: 'Email', render: (user: User) => user.email },
    ] as DataGridColumnConfig<User>[],
    emptyMessage: 'No users found. Create your first user to get started.',
    crudConfig: {
      onCreate: () => alert('Create user'),
      createButtonText: 'Create User',
    } as CrudConfig<User>,
  },
};

/**
 * Loading State - Shows loading indicator
 *
 * Displays custom loading message while data is being fetched.
 */
export const LoadingState: Story = {
  args: {
    items: [],
    displayMode: 'table',
    columns: [
      { header: 'Name', render: (user: User) => user.name },
    ] as DataGridColumnConfig<User>[],
    loading: true,
    loadingMessage: 'Loading users... Please wait.',
  },
};

/**
 * Minimal Configuration - Just items
 *
 * Shows DataGrid with minimal props (items only).
 */
export const MinimalConfiguration: Story = {
  args: {
    items: sampleUsers,
    columns: [
      { header: 'Name', render: (user: User) => user.name },
      { header: 'Email', render: (user: User) => user.email },
    ] as DataGridColumnConfig<User>[],
    keyExtractor: (user: User) => user.id,
  },
};
