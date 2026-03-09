import { useEffect, useState, useMemo, memo, useCallback } from 'react';
import { useAtom, useAtomValue } from 'jotai';
import { blockEventsAtom, addBlockEventAtom } from '../stores/eventsStore';
import { websocketService, type BlockEvent } from '../services/websocket.service';

interface BlockNotificationsProps {
  onEventReceived?: (event: BlockEvent) => void;
}

function BlockNotificationsComponent({ onEventReceived }: BlockNotificationsProps) {
  const blockEvents = useAtomValue(blockEventsAtom);
  const [, addBlockEvent] = useAtom(addBlockEventAtom);
  const [filter, setFilter] = useState({
    policyId: '',
    deviceId: '',
    reason: '',
  });
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

  // Memoize filtered events
  const filteredEvents = useMemo(() => {
    return blockEvents.filter((event) => {
      if (filter.policyId && event.blockEvent.device_id !== filter.policyId) {
        return false;
      }
      if (filter.deviceId && event.device.id !== filter.deviceId) {
        return false;
      }
      if (filter.reason && !event.blockEvent.reason.toLowerCase().includes(filter.reason.toLowerCase())) {
        return false;
      }
      return true;
    });
  }, [blockEvents, filter.policyId, filter.deviceId, filter.reason]);

  // Memoize statistics calculations
  const { totalBlocks, uniqueDevices } = useMemo(() => {
    const blocksByDevice = filteredEvents.reduce((acc, event) => {
      const deviceId = event.device.id;
      acc[deviceId] = (acc[deviceId] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);
    
    return {
      totalBlocks: filteredEvents.length,
      uniqueDevices: Object.keys(blocksByDevice).length,
    };
  }, [filteredEvents]);

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {showToast && latestBlock && (
        <div className="fixed top-4 right-4 z-50 animate-slide-in">
          <div className="bg-red-500 text-white px-6 py-4 rounded-lg shadow-lg max-w-md">
            <div className="flex items-start">
              <div className="flex-shrink-0">
                <svg className="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <div className="ml-3 flex-1">
                <h3 className="text-sm font-medium">Content Blocked</h3>
                <p className="mt-1 text-sm">{latestBlock.blockEvent.blocked_item}</p>
                <p className="mt-1 text-xs opacity-90">Reason: {latestBlock.blockEvent.reason}</p>
              </div>
              <button
                onClick={() => setShowToast(false)}
                className="ml-4 flex-shrink-0 text-white hover:text-gray-200"
              >
                <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-4">Block Event Notifications</h2>
        
        {/* Statistics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="bg-red-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-red-600">Total Blocks</h3>
            <p className="text-3xl font-bold text-red-900">{totalBlocks}</p>
          </div>
          <div className="bg-orange-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-orange-600">Affected Devices</h3>
            <p className="text-3xl font-bold text-orange-900">{uniqueDevices}</p>
          </div>
          <div className="bg-yellow-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-yellow-600">Recent Activity</h3>
            <p className="text-3xl font-bold text-yellow-900">
              {filteredEvents.length > 0 ? 'Active' : 'Quiet'}
            </p>
          </div>
        </div>

        {/* Filters */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <input
            type="text"
            placeholder="Filter by policy ID..."
            value={filter.policyId}
            onChange={(e) => setFilter({ ...filter, policyId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500"
          />
          <input
            type="text"
            placeholder="Filter by device ID..."
            value={filter.deviceId}
            onChange={(e) => setFilter({ ...filter, deviceId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500"
          />
          <input
            type="text"
            placeholder="Filter by reason..."
            value={filter.reason}
            onChange={(e) => setFilter({ ...filter, reason: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500"
          />
        </div>

        {/* Clear Filters */}
        {(filter.policyId || filter.deviceId || filter.reason) && (
          <button
            onClick={() => setFilter({ policyId: '', deviceId: '', reason: '' })}
            className="mb-4 text-sm text-red-600 hover:text-red-800"
          >
            Clear all filters
          </button>
        )}

        {/* Block History Table */}
        <div className="space-y-3">
          <h3 className="text-lg font-semibold text-gray-900">Block History</h3>
          {filteredEvents.length === 0 ? (
            <p className="text-gray-500 text-center py-8">No block events yet. Blocks will appear here in real-time.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Item Blocked
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Reason
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Device
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Policy
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Time
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {filteredEvents.slice().reverse().map((event, index) => (
                    <tr key={index} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm font-medium text-gray-900">{event.blockEvent.blocked_item}</div>
                        <div className="text-xs text-gray-500">{event.blockEvent.event_type}</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="text-sm text-gray-900">{event.blockEvent.reason}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">{event.device.name}</div>
                        <div className="text-xs text-gray-500">{event.device.type}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-100 text-red-800">
                          {event.blockEvent.device_id}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {new Date(event.blockEvent.timestamp).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// Export memoized component to prevent unnecessary re-renders
export const BlockNotifications = memo(BlockNotificationsComponent);
