import { useEffect, useState, useMemo, memo, useCallback } from 'react';
import { useAtom, useAtomValue } from 'jotai';
import {
  ActivityFeed,
  type StatCardConfig,
  type ColumnConfig,
  type FilterConfig,
  type ToastConfig,
} from '@ghatana/ui';
import { usageEventsAtom, addUsageEventAtom } from '../stores/eventsStore';
import { websocketService, type UsageEvent } from '../services/websocket.service';

/**
 * Guardian-specific wrapper for real-time usage monitoring.
 * 
 * Integrates ActivityFeed with Guardian's:
 * - Usage event WebSocket subscriptions
 * - Jotai state management
 * - Usage statistics calculations
 * - Usage event formatting
 * 
 * @example
 * ```tsx
 * <UsageMonitorNew onEventReceived={(event) => console.log('New usage:', event)} />
 * ```
 */

interface UsageMonitorProps {
  onEventReceived?: (event: UsageEvent) => void;
}

interface UsageEventDisplay {
  id: string;
  timestamp: string;
  itemName: string;
  deviceName: string;
  deviceType: string;
  sessionType: string;
  durationSeconds: number;
  childId: string;
  deviceId: string;
  usageEvent: UsageEvent;
}

function UsageMonitorNewComponent({ onEventReceived }: UsageMonitorProps) {
  // Guardian state management
  const usageEvents = useAtomValue(usageEventsAtom);
  const [, addUsageEvent] = useAtom(addUsageEventAtom);
  const [showToast, setShowToast] = useState(false);
  const [latestUsage, setLatestUsage] = useState<UsageEventDisplay | null>(null);

  // Transform usage events to display format
  const transformUsageEvent = useCallback((event: UsageEvent): UsageEventDisplay => {
    return {
      id: `${event.usageSession.device_id}-${event.usageSession.timestamp}`,
      timestamp: event.usageSession.timestamp,
      itemName: event.usageSession.item_name,
      deviceName: event.device.name,
      deviceType: event.device.type,
      sessionType: event.usageSession.session_type,
      durationSeconds: event.usageSession.duration_seconds,
      childId: event.usageSession.device_id,
      deviceId: event.device.id,
      usageEvent: event,
    };
  }, []);

  // Subscribe to WebSocket events
  useEffect(() => {
    const unsubscribe = websocketService.on('usage_data', (event) => {
      const usageEvent = event as unknown as UsageEvent;
      addUsageEvent(usageEvent);
      
      const display = transformUsageEvent(usageEvent);
      setLatestUsage(display);
      setShowToast(true);
      onEventReceived?.(usageEvent);
      
      // Auto-hide toast after 5 seconds
      setTimeout(() => setShowToast(false), 5000);
    });

    return () => {
      unsubscribe();
    };
  }, [addUsageEvent, onEventReceived, transformUsageEvent]);

  

  // Prepare display data
  const displayEvents = useMemo(() => {
    return usageEvents.map(transformUsageEvent);
  }, [usageEvents, transformUsageEvent]);

  // Calculate statistics
  const { totalDuration, avgDuration } = useMemo(() => {
    const total = usageEvents.reduce((sum, event) => sum + event.usageSession.duration_seconds, 0);
    const avg = usageEvents.length > 0 ? total / usageEvents.length : 0;
    return { totalDuration: total, avgDuration: avg };
  }, [usageEvents]);

  // Statistics cards configuration
  const statCards: StatCardConfig<UsageEventDisplay>[] = [
    {
      title: 'Total Sessions',
      calculate: (items) => items.length,
      variant: 'blue',
    },
    {
      title: 'Total Duration',
      calculate: () => `${Math.floor(totalDuration / 60)}m`,
      variant: 'green',
    },
    {
      title: 'Avg Session',
      calculate: () => `${Math.floor(avgDuration / 60)}m`,
      variant: 'purple',
    },
  ];

  // Column configuration
  const columns: ColumnConfig<UsageEventDisplay>[] = [
    {
      header: 'App/Website',
      render: (item) => (
        <div>
          <div className="text-sm font-medium text-gray-900">{item.itemName}</div>
          <div className="text-xs text-gray-500">{item.sessionType}</div>
        </div>
      ),
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
      header: 'Duration',
      render: (item) => (
        <span className="inline-block px-3 py-1 bg-indigo-100 text-indigo-800 rounded-full text-sm font-medium">
          {Math.floor(item.durationSeconds / 60)}m {item.durationSeconds % 60}s
        </span>
      ),
    },
    {
      header: 'Time',
      render: (item) => (
        <div className="text-sm text-gray-900">
          {new Date(item.timestamp).toLocaleTimeString()}
        </div>
      ),
    },
  ];

  // Filter configuration
  const filters: FilterConfig[] = [
    {
      name: 'childId',
      placeholder: 'Filter by child ID...',
    },
    {
      name: 'deviceId',
      placeholder: 'Filter by device ID...',
    },
    {
      name: 'itemName',
      placeholder: 'Filter by app/website...',
    },
  ];

  // Toast dismiss handler
  const hideToast = useCallback(() => {
    setShowToast(false);
  }, []);

  // Toast configuration
  const toastConfig: ToastConfig<UsageEventDisplay> = {
    title: 'New Usage Session',
    message: (item: UsageEventDisplay) => item.itemName,
    subtitle: (item: UsageEventDisplay) => `${item.deviceName} - ${Math.floor(item.durationSeconds / 60)}m ${item.durationSeconds % 60}s`,
    duration: 5000,
    variant: 'info',
    icon: (
      <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
      </svg>
    ),
  };

  // Custom filter function to handle nested properties
  const handleFilter = useCallback((items: UsageEventDisplay[], filterValues: Record<string, string>) => {
    return items.filter((item) => {
      if (filterValues.childId && !item.childId.toLowerCase().includes(filterValues.childId.toLowerCase())) {
        return false;
      }
      if (filterValues.deviceId && !item.deviceId.toLowerCase().includes(filterValues.deviceId.toLowerCase())) {
        return false;
      }
      if (filterValues.itemName && !item.itemName.toLowerCase().includes(filterValues.itemName.toLowerCase())) {
        return false;
      }
      return true;
    });
  }, []);

  // Render ActivityFeed with Guardian-specific configuration
  return (
    <ActivityFeed
      title="Real-Time Usage Monitoring"
      tableTitle="Recent Activity"
      items={displayEvents}
      columns={columns}
      filters={filters}
      onFilter={handleFilter}
      statsCards={statCards}
      emptyMessage="No usage events yet. Events will appear here in real-time."
      showToast={showToast}
      latestItem={latestUsage}
      onToastDismiss={hideToast}
      toastConfig={toastConfig}
      reverseOrder={true}
      keyExtractor={(item) => item.id}
    />
  );
}

// Export memoized component to prevent unnecessary re-renders
export const UsageMonitorNew = memo(UsageMonitorNewComponent);
