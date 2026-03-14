import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { useState } from 'react';
import {
  ActivityFeed,
  type StatCardConfig,
  type ColumnConfig,
  type FilterConfig,
  type ToastConfig,
} from './ActivityFeed';

/**
 * ActivityFeed displays real-time activities with statistics, filters, and notifications.
 *
 * ## Features
 * - Real-time toast notifications
 * - Statistics cards with custom calculations
 * - Filterable activity table
 * - Reverse chronological order
 * - Responsive design
 * - Customizable columns and stats
 *
 * ## Usage
 *
 * ```tsx
 * <ActivityFeed
 *   items={activities}
 *   statsCards={[{ title: 'Total', calculate: (items) => items.length }]}
 *   columns={[{ header: 'Message', render: (item) => item.message }]}
 * />
 * ```
 */
const meta = {
  title: 'Organisms/ActivityFeed',
  component: ActivityFeed,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'Generic activity feed for monitoring dashboards. Displays real-time events with statistics, filtering, and toast notifications.',
      },
    },
  },
  tags: ['autodocs'],
} satisfies Meta<typeof ActivityFeed>;

export default meta;
type Story = StoryObj<typeof meta>;

// ============================================================================
// Mock Data Types
// ============================================================================

interface SecurityEvent {
  id: string;
  message: string;
  user: string;
  ip: string;
  timestamp: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
}

interface BlockEvent {
  id: string;
  blockedItem: string;
  reason: string;
  deviceName: string;
  deviceType: string;
  policyId: string;
  timestamp: string;
}

// ============================================================================
// Story 1: Security Event Feed
// ============================================================================

const securityEvents: SecurityEvent[] = [
  {
    id: '1',
    message: 'User logged in',
    user: 'john@example.com',
    ip: '192.168.1.100',
    timestamp: '2025-11-08T10:00:00Z',
    severity: 'low',
  },
  {
    id: '2',
    message: 'Failed login attempt',
    user: 'jane@example.com',
    ip: '192.168.1.101',
    timestamp: '2025-11-08T10:05:00Z',
    severity: 'medium',
  },
  {
    id: '3',
    message: 'Password changed',
    user: 'bob@example.com',
    ip: '192.168.1.102',
    timestamp: '2025-11-08T10:10:00Z',
    severity: 'high',
  },
  {
    id: '4',
    message: 'Unauthorized access attempt',
    user: 'attacker@evil.com',
    ip: '10.0.0.1',
    timestamp: '2025-11-08T10:15:00Z',
    severity: 'critical',
  },
];

const securityStatsCards: StatCardConfig<SecurityEvent>[] = [
  {
    title: 'Total Events',
    calculate: (items) => items.length,
    variant: 'blue',
  },
  {
    title: 'Critical Alerts',
    calculate: (items) => items.filter((i) => i.severity === 'critical').length,
    variant: 'red',
  },
  {
    title: 'Unique Users',
    calculate: (items) => new Set(items.map((i) => i.user)).size,
    variant: 'green',
  },
];

const securityColumns: ColumnConfig<SecurityEvent>[] = [
  {
    header: 'Message',
    render: (item) => (
      <div>
        <div className="text-sm font-medium text-gray-900">{item.message}</div>
        <div className="text-xs text-gray-500">{item.user}</div>
      </div>
    ),
  },
  {
    header: 'Severity',
    render: (item) => {
      const colors = {
        low: 'bg-green-100 text-green-800',
        medium: 'bg-yellow-100 text-yellow-800',
        high: 'bg-orange-100 text-orange-800',
        critical: 'bg-red-100 text-red-800',
      };
      return (
        <span
          className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${colors[item.severity]}`}
        >
          {item.severity.toUpperCase()}
        </span>
      );
    },
  },
  {
    header: 'IP Address',
    render: (item) => <span className="text-sm text-gray-900">{item.ip}</span>,
  },
  {
    header: 'Time',
    render: (item) => (
      <span className="text-sm text-gray-500">
        {new Date(item.timestamp).toLocaleTimeString()}
      </span>
    ),
  },
];

const securityFilters: FilterConfig[] = [
  { name: 'user', placeholder: 'Filter by user...' },
  { name: 'ip', placeholder: 'Filter by IP...' },
  {
    name: 'severity',
    placeholder: 'Filter by severity...',
    type: 'select',
    options: [
      { label: 'Low', value: 'low' },
      { label: 'Medium', value: 'medium' },
      { label: 'High', value: 'high' },
      { label: 'Critical', value: 'critical' },
    ],
  },
];

const securityOnFilter = (
  items: SecurityEvent[],
  filterValues: Record<string, string>
) => {
  return items.filter((item) => {
    if (
      filterValues.user &&
      !item.user.toLowerCase().includes(filterValues.user.toLowerCase())
    ) {
      return false;
    }
    if (
      filterValues.ip &&
      !item.ip.toLowerCase().includes(filterValues.ip.toLowerCase())
    ) {
      return false;
    }
    if (filterValues.severity && item.severity !== filterValues.severity) {
      return false;
    }
    return true;
  });
};

/**
 * Security event monitoring dashboard.
 * Demonstrates real-time security event tracking with severity-based statistics.
 */
export const SecurityEventFeed: Story = {
  args: {
    items: securityEvents,
    title: 'Security Event Monitoring',
    tableTitle: 'Security Event Log',
    statsCards: securityStatsCards,
    columns: securityColumns,
    filters: securityFilters,
    onFilter: securityOnFilter,
    emptyMessage: 'No security events recorded.',
  },
};

// ============================================================================
// Story 2: Block Event Feed (Guardian Use Case)
// ============================================================================

const blockEvents: BlockEvent[] = [
  {
    id: '1',
    blockedItem: 'facebook.com',
    reason: 'Social media blocked during school hours',
    deviceName: "John's iPhone",
    deviceType: 'mobile',
    policyId: 'policy-123',
    timestamp: '2025-11-08T10:00:00Z',
  },
  {
    id: '2',
    blockedItem: 'TikTok',
    reason: 'App blocked by parental control',
    deviceName: "Sarah's iPad",
    deviceType: 'tablet',
    policyId: 'policy-456',
    timestamp: '2025-11-08T10:05:00Z',
  },
  {
    id: '3',
    blockedItem: 'adult-content-site.com',
    reason: 'Content filter: inappropriate content',
    deviceName: "Mike's Laptop",
    deviceType: 'desktop',
    policyId: 'policy-789',
    timestamp: '2025-11-08T10:10:00Z',
  },
];

const blockStatsCards: StatCardConfig<BlockEvent>[] = [
  {
    title: 'Total Blocks',
    calculate: (items) => items.length,
    variant: 'red',
  },
  {
    title: 'Affected Devices',
    calculate: (items) => new Set(items.map((i) => i.deviceName)).size,
    variant: 'orange',
  },
  {
    title: 'Active Policies',
    calculate: (items) => new Set(items.map((i) => i.policyId)).size,
    variant: 'yellow',
  },
];

const blockColumns: ColumnConfig<BlockEvent>[] = [
  {
    header: 'Item Blocked',
    render: (item) => (
      <div>
        <div className="text-sm font-medium text-gray-900">{item.blockedItem}</div>
        <div className="text-xs text-gray-500">Blocked content</div>
      </div>
    ),
  },
  {
    header: 'Reason',
    render: (item) => <div className="text-sm text-gray-900">{item.reason}</div>,
  },
  {
    header: 'Device',
    render: (item) => (
      <div>
        <div className="text-sm text-gray-900">{item.deviceName}</div>
        <div className="text-xs text-gray-500">{item.deviceType}</div>
      </div>
    ),
  },
  {
    header: 'Policy',
    render: (item) => (
      <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-100 text-red-800">
        {item.policyId}
      </span>
    ),
  },
  {
    header: 'Time',
    render: (item) => (
      <span className="text-sm text-gray-500">
        {new Date(item.timestamp).toLocaleTimeString()}
      </span>
    ),
  },
];

const blockFilters: FilterConfig[] = [
  { name: 'deviceName', placeholder: 'Filter by device...' },
  { name: 'policyId', placeholder: 'Filter by policy...' },
  { name: 'reason', placeholder: 'Filter by reason...' },
];

const blockOnFilter = (items: BlockEvent[], filterValues: Record<string, string>) => {
  return items.filter((item) => {
    if (
      filterValues.deviceName &&
      !item.deviceName.toLowerCase().includes(filterValues.deviceName.toLowerCase())
    ) {
      return false;
    }
    if (filterValues.policyId && item.policyId !== filterValues.policyId) {
      return false;
    }
    if (
      filterValues.reason &&
      !item.reason.toLowerCase().includes(filterValues.reason.toLowerCase())
    ) {
      return false;
    }
    return true;
  });
};

/**
 * Content blocking dashboard (Guardian use case).
 * Demonstrates real-time monitoring of blocked content with device statistics.
 */
export const BlockEventFeed: Story = {
  args: {
    items: blockEvents,
    title: 'Block Event Notifications',
    tableTitle: 'Block History',
    statsCards: blockStatsCards,
    columns: blockColumns,
    filters: blockFilters,
    onFilter: blockOnFilter,
    emptyMessage: 'No block events yet. Blocks will appear here in real-time.',
  },
};

// ============================================================================
// Story 3: Feed with Toast Notifications
// ============================================================================

function FeedWithToastWrapper() {
  const [showToast, setShowToast] = useState(true);
  const [latestEvent] = useState<SecurityEvent>(securityEvents[3]);

  const toastConfig: ToastConfig<SecurityEvent> = {
    title: 'Security Alert',
    message: (item) => item.message,
    subtitle: (item) => `User: ${item.user}`,
    variant: 'error',
  };

  return (
    <ActivityFeed
      items={securityEvents}
      title="Security Events with Notifications"
      statsCards={securityStatsCards}
      columns={securityColumns}
      toastConfig={toastConfig}
      latestItem={latestEvent}
      showToast={showToast}
      onToastDismiss={() => setShowToast(false)}
    />
  );
}

/**
 * Feed with toast notification for new events.
 * Demonstrates real-time alert system.
 */
export const FeedWithToast: Story = {
  render: () => <FeedWithToastWrapper />,
};

// ============================================================================
// Story 4: Empty State
// ============================================================================

/**
 * Empty state when no activities exist.
 * Useful for initial setup or filtered views with no results.
 */
export const EmptyState: Story = {
  args: {
    items: [],
    columns: securityColumns,
    emptyMessage: 'No activities to display. Start monitoring to see events here.',
  },
};

// ============================================================================
// Story 5: Minimal Feed (No Stats, No Filters)
// ============================================================================

/**
 * Minimal activity feed with just table.
 * Demonstrates basic usage without statistics or filters.
 */
export const MinimalFeed: Story = {
  args: {
    items: securityEvents,
    title: 'Simple Activity Log',
    columns: [
      {
        header: 'Event',
        render: (item: SecurityEvent) => item.message,
      },
      {
        header: 'User',
        render: (item: SecurityEvent) => item.user,
      },
      {
        header: 'Time',
        render: (item: SecurityEvent) =>
          new Date(item.timestamp).toLocaleString(),
      },
    ],
  },
};

// ============================================================================
// Story 6: Normal Chronological Order
// ============================================================================

/**
 * Feed with oldest items first (reverseOrder=false).
 * Useful for audit logs or historical views.
 */
export const ChronologicalOrder: Story = {
  args: {
    items: securityEvents,
    title: 'Audit Log (Oldest First)',
    columns: securityColumns,
    reverseOrder: false,
  },
};

// ============================================================================
// Story 7: Custom Styling
// ============================================================================

/**
 * Feed with custom CSS classes.
 * Demonstrates styling customization.
 */
export const CustomStyling: Story = {
  args: {
    items: securityEvents,
    title: 'Styled Activity Feed',
    columns: securityColumns,
    className: 'max-w-6xl mx-auto',
  },
};
