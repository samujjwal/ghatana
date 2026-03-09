import { useEffect, useState, useMemo, memo } from 'react';
import { useAtom, useAtomValue } from 'jotai';
import { usageEventsAtom, addUsageEventAtom } from '../stores/eventsStore';
import { websocketService, type UsageEvent } from '../services/websocket.service';
import { useUsageOverview } from '@ghatana/dcmaar-dashboard-core';

interface UsageMonitorProps {
  onEventReceived?: (event: UsageEvent) => void;
}

function UsageMonitorComponent({ onEventReceived }: UsageMonitorProps) {
  const usageEvents = useAtomValue(usageEventsAtom);
  const [, addUsageEvent] = useAtom(addUsageEventAtom);
  const [filter, setFilter] = useState({
    childId: '',
    deviceId: '',
    itemName: '',
  });

  // Initialize shared usage overview query (for role-based access and future aggregations)
  useUsageOverview();

  useEffect(() => {
    // Subscribe to usage_data events
    const unsubscribe = websocketService.on('usage_data', (event) => {
      const usageEvent = event as unknown as UsageEvent;
      addUsageEvent(usageEvent);
      onEventReceived?.(usageEvent);
    });

    return () => {
      unsubscribe();
    };
  }, [addUsageEvent, onEventReceived]);

  // Memoize filtered events to avoid recalculation on every render
  const filteredEvents = useMemo(() => {
    return usageEvents.filter((event) => {
      if (filter.childId && event.usageSession.device_id !== filter.childId) {
        return false;
      }
      if (filter.deviceId && event.device.id !== filter.deviceId) {
        return false;
      }
      if (filter.itemName && !event.usageSession.item_name.toLowerCase().includes(filter.itemName.toLowerCase())) {
        return false;
      }
      return true;
    });
  }, [usageEvents, filter.childId, filter.deviceId, filter.itemName]);

  // Memoize calculations to avoid recalculation on every render
  const { totalDuration, avgDuration } = useMemo(() => {
    const total = filteredEvents.reduce((sum, event) => sum + event.usageSession.duration_seconds, 0);
    const avg = filteredEvents.length > 0 ? total / filteredEvents.length : 0;
    return { totalDuration: total, avgDuration: avg };
  }, [filteredEvents]);

  return (
    <div className="space-y-6">
      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-4">Real-Time Usage Monitoring</h2>

        {/* Statistics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="bg-indigo-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-indigo-600">Total Sessions</h3>
            <p className="text-3xl font-bold text-indigo-900">{filteredEvents.length}</p>
          </div>
          <div className="bg-green-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-green-600">Total Duration</h3>
            <p className="text-3xl font-bold text-green-900">{Math.floor(totalDuration / 60)}m</p>
          </div>
          <div className="bg-purple-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-purple-600">Avg Session</h3>
            <p className="text-3xl font-bold text-purple-900">{Math.floor(avgDuration / 60)}m</p>
          </div>
        </div>

        {/* Filters */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <input
            type="text"
            placeholder="Filter by child ID..."
            value={filter.childId}
            onChange={(e) => setFilter({ ...filter, childId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <input
            type="text"
            placeholder="Filter by device ID..."
            value={filter.deviceId}
            onChange={(e) => setFilter({ ...filter, deviceId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <input
            type="text"
            placeholder="Filter by app/website..."
            value={filter.itemName}
            onChange={(e) => setFilter({ ...filter, itemName: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        {/* Clear Filters */}
        {(filter.childId || filter.deviceId || filter.itemName) && (
          <button
            onClick={() => setFilter({ childId: '', deviceId: '', itemName: '' })}
            className="mb-4 text-sm text-indigo-600 hover:text-indigo-800"
          >
            Clear all filters
          </button>
        )}

        {/* Usage Timeline */}
        <div className="space-y-3">
          <h3 className="text-lg font-semibold text-gray-900">Recent Activity</h3>
          {filteredEvents.length === 0 ? (
            <p className="text-gray-500 text-center py-8">No usage events yet. Events will appear here in real-time.</p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {filteredEvents.slice().reverse().map((event, index) => (
                <div key={index} className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <h4 className="font-semibold text-gray-900">{event.usageSession.item_name}</h4>
                      <p className="text-sm text-gray-600">
                        {event.device.name} ({event.device.type})
                      </p>
                      <p className="text-xs text-gray-500 mt-1">
                        Session Type: {event.usageSession.session_type}
                      </p>
                    </div>
                    <div className="text-right">
                      <span className="inline-block px-3 py-1 bg-indigo-100 text-indigo-800 rounded-full text-sm font-medium">
                        {Math.floor(event.usageSession.duration_seconds / 60)}m {event.usageSession.duration_seconds % 60}s
                      </span>
                      <p className="text-xs text-gray-500 mt-1">
                        {new Date(event.usageSession.timestamp).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// Export memoized component to prevent unnecessary re-renders
export const UsageMonitor = memo(UsageMonitorComponent);
