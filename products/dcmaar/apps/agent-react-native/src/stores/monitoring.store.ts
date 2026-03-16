/**
 * Monitoring & Analytics Store - Jotai Atoms
 *
 * Manages real-time monitoring of child device activity including:
 * - Event tracking and history
 * - App usage analytics
 * - Screen time metrics
 * - Anomaly detection
 * - Activity summaries
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose Monitoring and analytics state
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { subscribeAtom, unsubscribeAtom } from './websocket.store';

/**
 * Individual monitoring event representing app or system activity.
 *
 * @interface MonitoringEvent
 * @property {string} id - Unique event identifier
 * @property {string} deviceId - Device where event occurred
 * @property {string} appId - Package ID of app (if applicable)
 * @property {'app_open' | 'app_close' | 'screen_time' | 'anomaly'} eventType - Type of event
 * @property {Record<string, any>} metadata - Event metadata
 * @property {Date} timestamp - When event occurred
 */
export interface MonitoringEvent {
  id: string;
  deviceId: string;
  appId?: string;
  eventType: 'app_open' | 'app_close' | 'screen_time' | 'anomaly';
  metadata: Record<string, any>;
  timestamp: Date;
}

/**
 * App usage metrics for a specific app.
 *
 * @interface AppUsageMetrics
 * @property {string} appId - Package ID
 * @property {string} appName - Display name
 * @property {number} totalScreenTime - Minutes in app today
 * @property {number} sessionCount - Number of sessions today
 * @property {number} averageSessionDuration - Minutes per session
 * @property {Date} lastUsed - Last time app was opened
 */
export interface AppUsageMetrics {
  appId: string;
  appName: string;
  totalScreenTime: number;
  sessionCount: number;
  averageSessionDuration: number;
  lastUsed: Date;
}

/**
 * Daily monitoring summary for a device.
 *
 * @interface DailySummary
 * @property {string} deviceId - Device identifier
 * @property {Date} date - Date of summary
 * @property {number} totalScreenTime - Total screen time in minutes
 * @property {AppUsageMetrics[]} topApps - Most used apps
 * @property {number} anomalyCount - Number of anomalies detected
 * @property {string[]} flaggedActivities - Activities flagged by policy
 */
export interface DailySummary {
  deviceId: string;
  date: Date;
  totalScreenTime: number;
  topApps: AppUsageMetrics[];
  anomalyCount: number;
  flaggedActivities: string[];
}

/**
 * Monitoring system state.
 *
 * @interface MonitoringState
 * @property {MonitoringEvent[]} events - All recent events (last 1000)
 * @property {Record<string, AppUsageMetrics[]>} appMetrics - Metrics by deviceId
 * @property {Record<string, DailySummary>} dailySummaries - Daily summaries by deviceId
 * @property {'idle' | 'monitoring' | 'paused' | 'error'} status - Monitoring status
 * @property {string | null} error - Error message if status is 'error'
 * @property {boolean} isRealtime - Whether real-time monitoring is active
 */
export interface MonitoringState {
  events: MonitoringEvent[];
  appMetrics: Record<string, AppUsageMetrics[]>;
  dailySummaries: Record<string, DailySummary>;
  status: 'idle' | 'monitoring' | 'paused' | 'error';
  error: string | null;
  isRealtime: boolean;
}

/**
 * Initial monitoring state.
 *
 * GIVEN: App initialization
 * WHEN: monitoringAtom is first accessed
 * THEN: Events list starts empty, monitoring starts in idle state
 */
const initialMonitoringState: MonitoringState = {
  events: [],
  appMetrics: {},
  dailySummaries: {},
  status: 'idle',
  error: null,
  isRealtime: false,
};

/**
 * Core monitoring atom.
 *
 * Holds complete monitoring state including:
 * - Real-time event stream
 * - App usage analytics
 * - Daily summaries
 * - Monitoring status
 *
 * Usage (in components):
 * `const [state, setState] = useAtom(monitoringAtom);`
 */
export const monitoringAtom = atom<MonitoringState>(initialMonitoringState);

/**
 * Derived atom: Recent events (last 50).
 *
 * GIVEN: monitoringAtom with events
 * WHEN: recentEventsAtom is read
 * THEN: Returns most recent 50 events
 *
 * Usage (in components):
 * `const [events] = useAtom(recentEventsAtom);`
 * Display recent activity in timeline
 */
export const recentEventsAtom = atom<MonitoringEvent[]>((get) => {
  const state = get(monitoringAtom);
  return state.events.slice(-50);
});

/**
 * Derived atom: Total events count.
 *
 * GIVEN: monitoringAtom with events
 * WHEN: eventCountAtom is read
 * THEN: Returns total number of events
 *
 * Usage (in components):
 * `const [count] = useAtom(eventCountAtom);`
 * Display "347 events recorded" in UI
 */
export const eventCountAtom = atom<number>((get) => {
  return get(monitoringAtom).events.length;
});

/**
 * Derived atom: App usage metrics for selected device.
 *
 * GIVEN: monitoringAtom with appMetrics keyed by deviceId
 * WHEN: appUsageAtom is read with deviceId
 * THEN: Returns usage metrics for that device
 *
 * Usage (in components):
 * `const getUsage = useAtomValue(appUsageAtom);`
 * `const metrics = getUsage('device-123');`
 */
export const appUsageAtom = atom((get) => {
  const state = get(monitoringAtom);
  return (deviceId: string) => {
    return state.appMetrics[deviceId] || [];
  };
});

/**
 * Derived atom: Flagged activities (policy violations).
 *
 * GIVEN: monitoringAtom with events
 * WHEN: flaggedActivitiesAtom is read for a device
 * THEN: Returns events that violate policies
 *
 * Usage (in components):
 * `const getFlagged = useAtomValue(flaggedActivitiesAtom);`
 * `const violations = getFlagged('device-123');`
 */
export const flaggedActivitiesAtom = atom((get) => {
  const state = get(monitoringAtom);
  return (deviceId: string) => {
    return state.events.filter(
      (e) => e.deviceId === deviceId && e.eventType === 'anomaly'
    );
  };
});

/**
 * Action atom: Record a new monitoring event.
 *
 * GIVEN: New app activity on monitored device
 * WHEN: recordEventAtom action is called
 * THEN: Adds event to events array
 *       Updates app metrics
 *       Updates daily summary
 *
 * Usage (in components):
 * `const [, recordEvent] = useAtom(recordEventAtom);`
 * await recordEvent({
 *   deviceId: 'device-123',
 *   appId: 'com.example.app',
 *   eventType: 'app_open'
 * });
 */
export const recordEventAtom = atom<
  null,
  [Omit<MonitoringEvent, 'id' | 'timestamp'>],
  Promise<MonitoringEvent>
>(
  null,
  async (get, set, eventData) => {
    const state = get(monitoringAtom);

    try {
      // Mock implementation - in production, this would call API
      const newEvent: MonitoringEvent = {
        id: 'event-' + Date.now(),
        ...eventData,
        timestamp: new Date(),
      };

      // Keep only last 1000 events
      const events = [
        ...state.events.slice(-999),
        newEvent,
      ];

      // Update app metrics
      const appMetrics = { ...state.appMetrics };
      if (eventData.appId) {
        if (!appMetrics[eventData.deviceId]) {
          appMetrics[eventData.deviceId] = [];
        }
        // Update or add metric
        const existing = appMetrics[eventData.deviceId].find(
          (m) => m.appId === eventData.appId
        );
        if (existing && eventData.eventType === 'screen_time') {
          existing.totalScreenTime += (eventData.metadata?.duration || 0) / 60;
        }
      }

      set(monitoringAtom, {
        ...state,
        events,
        appMetrics,
        status: 'monitoring',
        error: null,
      });

      return newEvent;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to record event';

      set(monitoringAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Fetch daily summary for device.
 *
 * GIVEN: Valid device ID
 * WHEN: fetchDailySummaryAtom is called
 * THEN: Retrieves or calculates daily summary
 *       Updates dailySummaries
 *
 * Usage (in components):
 * `const [, fetchSummary] = useAtom(fetchDailySummaryAtom);`
 * const summary = await fetchSummary('device-123');
 */
export const fetchDailySummaryAtom = atom<
  null,
  [deviceId: string, date?: Date],
  Promise<DailySummary>
>(
  null,
  async (get, set, deviceId: string, date?: Date) => {
    const state = get(monitoringAtom);
    const targetDate = date || new Date();
    const key = `${deviceId}-${targetDate.toDateString()}`;

    try {
      // Check if already cached
      if (state.dailySummaries[key]) {
        return state.dailySummaries[key];
      }

      // Mock implementation - calculate from events
      const dayEvents = state.events.filter(
        (e) =>
          e.deviceId === deviceId &&
          e.timestamp.toDateString() === targetDate.toDateString()
      );

      const metrics = state.appMetrics[deviceId] || [];
      const topApps = metrics
        .sort((a, b) => b.totalScreenTime - a.totalScreenTime)
        .slice(0, 5);

      const summary: DailySummary = {
        deviceId,
        date: targetDate,
        totalScreenTime: metrics.reduce((sum, m) => sum + m.totalScreenTime, 0),
        topApps,
        anomalyCount: dayEvents.filter((e) => e.eventType === 'anomaly').length,
        flaggedActivities: dayEvents
          .filter((e) => e.eventType === 'anomaly')
          .map((e) => e.appId || 'system'),
      };

      set(monitoringAtom, {
        ...state,
        dailySummaries: {
          ...state.dailySummaries,
          [key]: summary,
        },
      });

      return summary;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to fetch summary';

      set(monitoringAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Clear event history.
 *
 * GIVEN: User requests history clearance
 * WHEN: clearEventsAtom is called
 * THEN: Deletes all events (or events before cutoff date)
 *
 * Usage (in components):
 * `const [, clearEvents] = useAtom(clearEventsAtom);`
 * await clearEvents(); // Clear all
 * await clearEvents(new Date(Date.now() - 7*24*60*60*1000)); // Clear old
 */
export const clearEventsAtom = atom<null, [beforeDate?: Date], Promise<void>>(
  null,
  async (get, set, beforeDate?: Date) => {
    const state = get(monitoringAtom);

    try {
      let events = state.events;

      if (beforeDate) {
        // Clear events before cutoff
        events = events.filter((e) => e.timestamp > beforeDate);
      } else {
        // Clear all
        events = [];
      }

      set(monitoringAtom, {
        ...state,
        events,
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to clear events';

      set(monitoringAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Start real-time monitoring.
 *
 * GIVEN: Monitoring infrastructure ready
 * WHEN: startMonitoringAtom is called
 * THEN: Enables real-time event streaming
 *       Sets status to 'monitoring'
 *       Connects to WebSocket for live events
 *
 * Usage (in components):
 * `const [, startMonitoring] = useAtom(startMonitoringAtom);`
 * await startMonitoring();
 */
export const startMonitoringAtom = atom<null, [], Promise<void>>(
  null,
  async (get, set) => {
    const state = get(monitoringAtom);

    try {
      // Subscribe to the 'monitoring' WebSocket channel to receive live events.
      // Components should watch lastMessageAtom and dispatch recordEventAtom
      // for messages with channel === 'monitoring'.
      await set(subscribeAtom, 'monitoring', 'monitoring');

      set(monitoringAtom, {
        ...state,
        status: 'monitoring',
        isRealtime: true,
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to start monitoring';

      set(monitoringAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Pause monitoring.
 *
 * GIVEN: Monitoring is active
 * WHEN: pauseMonitoringAtom is called
 * THEN: Stops recording new events
 *       Sets status to 'paused'
 *       Disconnects from WebSocket
 *
 * Usage (in components):
 * `const [, pauseMonitoring] = useAtom(pauseMonitoringAtom);`
 * await pauseMonitoring();
 */
export const pauseMonitoringAtom = atom<null, [], Promise<void>>(
  null,
  async (get, set) => {
    const state = get(monitoringAtom);

    try {
      // Unsubscribe from the 'monitoring' WebSocket channel
      await set(unsubscribeAtom, 'monitoring');

      set(monitoringAtom, {
        ...state,
        status: 'paused',
        isRealtime: false,
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to pause monitoring';

      set(monitoringAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Clear monitoring error.
 *
 * GIVEN: Error displayed to user
 * WHEN: clearMonitoringErrorAtom is called
 * THEN: Clears error message from state
 *
 * Usage (in components):
 * `const [, clearError] = useAtom(clearMonitoringErrorAtom);`
 * clearError();
 */
export const clearMonitoringErrorAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(monitoringAtom);
    set(monitoringAtom, {
      ...state,
      error: null,
    });
  }
);
