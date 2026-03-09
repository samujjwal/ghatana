import { useEffect, useState, useMemo, memo, useCallback } from 'react';
import { useAtom, useAtomValue } from 'jotai';
import { blockEventsAtom, addBlockEventAtom } from '../stores/eventsStore';
import { websocketService, type BlockEvent } from '../services/websocket.service';
import {
  ActivityFeed,
  type StatCardConfig,
  type ColumnConfig,
  type FilterConfig,
  type ToastConfig,
} from '@ghatana/ui';

interface BlockNotificationsProps {
  onEventReceived?: (event: BlockEvent) => void;
}

function BlockNotificationsComponent({ onEventReceived }: BlockNotificationsProps) {
  const blockEvents = useAtomValue(blockEventsAtom);
  const [, addBlockEvent] = useAtom(addBlockEventAtom);
  const [showToast, setShowToast] = useState(false);
  const [latestBlock, setLatestBlock] = useState<BlockEvent | null>(null);

  // Memoize toast hide callback
  const hideToast = useCallback(() => setShowToast(false), []);

  useEffect(() => {
    // Subscribe to block_event events
    const unsubscribe = websocketService.on('block_event', (event) => {
      const blockEvent = event as unknown as BlockEvent;
      addBlockEvent(blockEvent);
      setLatestBlock(blockEvent);
      setShowToast(true);
      onEventReceived?.(blockEvent);

      // Auto-hide toast after 5 seconds
      setTimeout(hideToast, 5000);
    });

    return () => {
      unsubscribe();
    };
  }, [addBlockEvent, onEventReceived, hideToast]);

  // Statistics card configurations
  const statsCards: StatCardConfig<BlockEvent>[] = useMemo(
    () => [
      {
        title: 'Total Blocks',
        calculate: (items) => items.length,
        variant: 'red',
      },
      {
        title: 'Affected Devices',
        calculate: (items) => new Set(items.map((e) => e.device.id)).size,
        variant: 'orange',
      },
      {
        title: 'Recent Activity',
        calculate: (items) => (items.length > 0 ? 'Active' : 'Quiet'),
        variant: 'yellow',
      },
    ],
    []
  );

  // Column configurations
  const columns: ColumnConfig<BlockEvent>[] = useMemo(
    () => [
      {
        header: 'Item Blocked',
        render: (event) => (
          <div>
            <div className="text-sm font-medium text-gray-900">
              {event.blockEvent.blocked_item}
            </div>
            <div className="text-xs text-gray-500">{event.blockEvent.event_type}</div>
          </div>
        ),
      },
      {
        header: 'Reason',
        render: (event) => (
          <div className="text-sm text-gray-900">{event.blockEvent.reason}</div>
        ),
      },
      {
        header: 'Device',
        render: (event) => (
          <div>
            <div className="text-sm text-gray-900">{event.device.name}</div>
            <div className="text-xs text-gray-500">{event.device.type}</div>
          </div>
        ),
      },
      {
        header: 'Policy',
        render: (event) => (
          <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-100 text-red-800">
            {event.blockEvent.device_id}
          </span>
        ),
      },
      {
        header: 'Time',
        render: (event) => (
          <span className="text-sm text-gray-500">
            {new Date(event.blockEvent.timestamp).toLocaleTimeString()}
          </span>
        ),
      },
    ],
    []
  );

  // Filter configurations
  const filters: FilterConfig[] = useMemo(
    () => [
      { name: 'policyId', placeholder: 'Filter by policy ID...' },
      { name: 'deviceId', placeholder: 'Filter by device ID...' },
      { name: 'reason', placeholder: 'Filter by reason...' },
    ],
    []
  );

  // Filter function
  const onFilter = useCallback(
    (items: BlockEvent[], filterValues: Record<string, string>) => {
      return items.filter((event) => {
        if (filterValues.policyId && event.blockEvent.device_id !== filterValues.policyId) {
          return false;
        }
        if (filterValues.deviceId && event.device.id !== filterValues.deviceId) {
          return false;
        }
        if (
          filterValues.reason &&
          !event.blockEvent.reason.toLowerCase().includes(filterValues.reason.toLowerCase())
        ) {
          return false;
        }
        return true;
      });
    },
    []
  );

  // Toast configuration
  const toastConfig: ToastConfig<BlockEvent> = useMemo(
    () => ({
      title: 'Content Blocked',
      message: (event) => event.blockEvent.blocked_item,
      subtitle: (event) => `Reason: ${event.blockEvent.reason}`,
      variant: 'error',
      icon: (
        <svg className="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      ),
    }),
    []
  );

  return (
    <ActivityFeed
      items={blockEvents}
      title="Block Event Notifications"
      tableTitle="Block History"
      statsCards={statsCards}
      columns={columns}
      filters={filters}
      onFilter={onFilter}
      toastConfig={toastConfig}
      latestItem={latestBlock}
      showToast={showToast}
      onToastDismiss={hideToast}
      emptyMessage="No block events yet. Blocks will appear here in real-time."
      keyExtractor={(event) => event.blockEvent.id}
    />
  );
}

// Export memoized component to prevent unnecessary re-renders
export const BlockNotifications = memo(BlockNotificationsComponent);
