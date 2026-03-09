/**
 * Operations Phase React Hooks
 *
 * @description Custom hooks for operations/observability phase including
 * incident management, alerting, dashboards, runbooks, and on-call.
 *
 * @doc.type hooks
 * @doc.purpose Operations phase data management
 * @doc.layer integration
 * @doc.phase operations
 */

import { useCallback, useMemo, useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useQuery, useMutation, useSubscription, useLazyQuery } from '@apollo/client';

import {
  incidentsAtom,
  activeIncidentAtom,
  alertsAtom,
  dashboardsAtom,
  activeDashboardAtom,
  runbooksAtom,
  onCallScheduleAtom,
  serviceHealthAtom,
  metricsAtom,
} from '@ghatana/yappc-canvas';

import {
  GET_INCIDENT,
  GET_INCIDENTS,
  GET_INCIDENT_TIMELINE,
  GET_ALERT,
  GET_ALERTS,
  GET_DASHBOARD,
  GET_DASHBOARDS,
  GET_RUNBOOK,
  GET_RUNBOOKS,
  GET_SERVICE_HEALTH,
  GET_ON_CALL_SCHEDULE,
  GET_METRICS,
  CREATE_INCIDENT,
  UPDATE_INCIDENT,
  ACKNOWLEDGE_INCIDENT,
  RESOLVE_INCIDENT,
  ESCALATE_INCIDENT,
  ADD_INCIDENT_TIMELINE_ENTRY,
  ACKNOWLEDGE_ALERT,
  SNOOZE_ALERT,
  CREATE_DASHBOARD,
  UPDATE_DASHBOARD,
  DELETE_DASHBOARD,
  ADD_DASHBOARD_WIDGET,
  UPDATE_DASHBOARD_WIDGET,
  REMOVE_DASHBOARD_WIDGET,
  EXECUTE_RUNBOOK,
  INCIDENT_UPDATES_SUBSCRIPTION,
  ALERT_STREAM_SUBSCRIPTION,
  METRICS_STREAM_SUBSCRIPTION,
  LOG_STREAM_SUBSCRIPTION,
  type Incident,
  type IncidentInput,
  type Alert,
  type Dashboard,
  type DashboardInput,
  type Widget,
  type WidgetInput,
  type Runbook,
  type ServiceHealth,
  type OnCallSchedule,
  type MetricPoint,
} from '@ghatana/yappc-api';

// =============================================================================
// Incident Hooks
// =============================================================================

/**
 * Hook for fetching a single incident with real-time updates
 */
export function useIncident(incidentId?: string) {
  const [activeIncident, setActiveIncident] = useAtom(activeIncidentAtom);

  const { data, loading, error, refetch } = useQuery(GET_INCIDENT, {
    variables: { incidentId },
    skip: !incidentId,
    onCompleted: (data) => {
      if (data?.incident) {
        setActiveIncident(data.incident);
      }
    },
  });

  // Subscribe to incident updates
  useSubscription(INCIDENT_UPDATES_SUBSCRIPTION, {
    variables: { incidentId },
    skip: !incidentId,
    onData: ({ data }) => {
      if (data?.data?.incidentUpdates) {
        setActiveIncident((prev) =>
          prev ? { ...prev, ...data.data.incidentUpdates } : data.data.incidentUpdates
        );
      }
    },
  });

  return {
    incident: activeIncident || data?.incident,
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for fetching incidents list with filters
 */
export function useIncidents(filters?: {
  status?: string[];
  severity?: string[];
  service?: string;
  startDate?: string;
  endDate?: string;
}) {
  const [incidents, setIncidents] = useAtom(incidentsAtom);

  const { data, loading, error, refetch, fetchMore } = useQuery(GET_INCIDENTS, {
    variables: { filters, first: 20 },
    onCompleted: (data) => {
      if (data?.incidents?.nodes) {
        setIncidents(data.incidents.nodes);
      }
    },
  });

  const loadMore = useCallback(async () => {
    if (!data?.incidents?.pageInfo?.hasNextPage) return;
    const result = await fetchMore({
      variables: {
        after: data.incidents.pageInfo.endCursor,
      },
    });
    if (result.data?.incidents?.nodes) {
      setIncidents((prev) => [...prev, ...result.data.incidents.nodes]);
    }
  }, [data, fetchMore, setIncidents]);

  // Subscribe to new incidents
  useSubscription(INCIDENT_UPDATES_SUBSCRIPTION, {
    variables: { incidentId: null }, // Subscribe to all incidents
    onData: ({ data }) => {
      if (data?.data?.incidentUpdates) {
        const update = data.data.incidentUpdates;
        setIncidents((prev) => {
          const exists = prev.find((i) => i.id === update.id);
          if (exists) {
            return prev.map((i) => (i.id === update.id ? { ...i, ...update } : i));
          }
          return [update, ...prev];
        });
      }
    },
  });

  return {
    incidents: incidents || data?.incidents?.nodes || [],
    pageInfo: data?.incidents?.pageInfo,
    isLoading: loading,
    error,
    refetch,
    loadMore,
  };
}

/**
 * Hook for incident mutations
 */
export function useIncidentMutations() {
  const setIncidents = useSetAtom(incidentsAtom);
  const setActiveIncident = useSetAtom(activeIncidentAtom);

  const [create] = useMutation(CREATE_INCIDENT);
  const [update] = useMutation(UPDATE_INCIDENT);
  const [acknowledge] = useMutation(ACKNOWLEDGE_INCIDENT);
  const [resolve] = useMutation(RESOLVE_INCIDENT);
  const [escalate] = useMutation(ESCALATE_INCIDENT);
  const [addTimeline] = useMutation(ADD_INCIDENT_TIMELINE_ENTRY);

  const createIncident = useCallback(
    async (input: IncidentInput) => {
      const result = await create({ variables: { input } });
      if (result.data?.createIncident) {
        setIncidents((prev) => [result.data.createIncident, ...prev]);
        return result.data.createIncident;
      }
      return null;
    },
    [create, setIncidents]
  );

  const updateIncident = useCallback(
    async (incidentId: string, input: Partial<IncidentInput>) => {
      const result = await update({ variables: { incidentId, input } });
      if (result.data?.updateIncident) {
        setIncidents((prev) =>
          prev.map((i) => (i.id === incidentId ? result.data.updateIncident : i))
        );
        setActiveIncident((prev) =>
          prev?.id === incidentId ? result.data.updateIncident : prev
        );
        return result.data.updateIncident;
      }
      return null;
    },
    [update, setIncidents, setActiveIncident]
  );

  const acknowledgeIncident = useCallback(
    async (incidentId: string, message?: string) => {
      const result = await acknowledge({ variables: { incidentId, message } });
      if (result.data?.acknowledgeIncident) {
        const updated = result.data.acknowledgeIncident;
        setIncidents((prev) => prev.map((i) => (i.id === incidentId ? updated : i)));
        setActiveIncident((prev) => (prev?.id === incidentId ? updated : prev));
        return updated;
      }
      return null;
    },
    [acknowledge, setIncidents, setActiveIncident]
  );

  const resolveIncident = useCallback(
    async (incidentId: string, resolution: string, rootCause?: string) => {
      const result = await resolve({
        variables: { incidentId, resolution, rootCause },
      });
      if (result.data?.resolveIncident) {
        const updated = result.data.resolveIncident;
        setIncidents((prev) => prev.map((i) => (i.id === incidentId ? updated : i)));
        setActiveIncident((prev) => (prev?.id === incidentId ? updated : prev));
        return updated;
      }
      return null;
    },
    [resolve, setIncidents, setActiveIncident]
  );

  const escalateIncident = useCallback(
    async (incidentId: string, escalationLevel: string, reason: string) => {
      const result = await escalate({
        variables: { incidentId, escalationLevel, reason },
      });
      return result.data?.escalateIncident;
    },
    [escalate]
  );

  const addTimelineEntry = useCallback(
    async (incidentId: string, type: string, content: string) => {
      const result = await addTimeline({
        variables: { incidentId, input: { type, content } },
      });
      if (result.data?.addIncidentTimelineEntry) {
        setActiveIncident((prev) => {
          if (prev?.id !== incidentId) return prev;
          return {
            ...prev,
            timeline: [...(prev.timeline || []), result.data.addIncidentTimelineEntry],
          };
        });
        return result.data.addIncidentTimelineEntry;
      }
      return null;
    },
    [addTimeline, setActiveIncident]
  );

  return {
    createIncident,
    updateIncident,
    acknowledgeIncident,
    resolveIncident,
    escalateIncident,
    addTimelineEntry,
  };
}

// =============================================================================
// Alert Hooks
// =============================================================================

/**
 * Hook for fetching and subscribing to alerts
 */
export function useAlerts(filters?: {
  status?: string[];
  severity?: string[];
  service?: string;
}) {
  const [alerts, setAlerts] = useAtom(alertsAtom);

  const { data, loading, error, refetch } = useQuery(GET_ALERTS, {
    variables: { filters, first: 50 },
    onCompleted: (data) => {
      if (data?.alerts?.nodes) {
        setAlerts(data.alerts.nodes);
      }
    },
  });

  // Subscribe to alert stream
  useSubscription(ALERT_STREAM_SUBSCRIPTION, {
    variables: { filters },
    onData: ({ data }) => {
      if (data?.data?.alertStream) {
        const alert = data.data.alertStream;
        setAlerts((prev) => {
          const exists = prev.find((a) => a.id === alert.id);
          if (exists) {
            return prev.map((a) => (a.id === alert.id ? alert : a));
          }
          return [alert, ...prev].slice(0, 100); // Keep last 100 alerts
        });
      }
    },
  });

  return {
    alerts: alerts || data?.alerts?.nodes || [],
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for alert mutations
 */
export function useAlertMutations() {
  const setAlerts = useSetAtom(alertsAtom);

  const [ack] = useMutation(ACKNOWLEDGE_ALERT);
  const [snooze] = useMutation(SNOOZE_ALERT);

  const acknowledgeAlert = useCallback(
    async (alertId: string, message?: string) => {
      const result = await ack({ variables: { alertId, message } });
      if (result.data?.acknowledgeAlert) {
        setAlerts((prev) =>
          prev.map((a) => (a.id === alertId ? result.data.acknowledgeAlert : a))
        );
        return result.data.acknowledgeAlert;
      }
      return null;
    },
    [ack, setAlerts]
  );

  const snoozeAlert = useCallback(
    async (alertId: string, duration: number, reason?: string) => {
      const result = await snooze({ variables: { alertId, duration, reason } });
      if (result.data?.snoozeAlert) {
        setAlerts((prev) =>
          prev.map((a) => (a.id === alertId ? result.data.snoozeAlert : a))
        );
        return result.data.snoozeAlert;
      }
      return null;
    },
    [snooze, setAlerts]
  );

  return {
    acknowledgeAlert,
    snoozeAlert,
  };
}

// =============================================================================
// Dashboard Hooks
// =============================================================================

/**
 * Hook for fetching dashboards
 */
export function useDashboards(projectId?: string) {
  const [dashboards, setDashboards] = useAtom(dashboardsAtom);

  const { data, loading, error, refetch } = useQuery(GET_DASHBOARDS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.dashboards) {
        setDashboards(data.dashboards);
      }
    },
  });

  return {
    dashboards: dashboards || data?.dashboards || [],
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for active dashboard with widgets
 */
export function useDashboard(dashboardId?: string) {
  const [activeDashboard, setActiveDashboard] = useAtom(activeDashboardAtom);

  const { data, loading, error, refetch } = useQuery(GET_DASHBOARD, {
    variables: { dashboardId },
    skip: !dashboardId,
    onCompleted: (data) => {
      if (data?.dashboard) {
        setActiveDashboard(data.dashboard);
      }
    },
  });

  return {
    dashboard: activeDashboard || data?.dashboard,
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for dashboard mutations
 */
export function useDashboardMutations() {
  const setDashboards = useSetAtom(dashboardsAtom);
  const setActiveDashboard = useSetAtom(activeDashboardAtom);

  const [create] = useMutation(CREATE_DASHBOARD);
  const [update] = useMutation(UPDATE_DASHBOARD);
  const [remove] = useMutation(DELETE_DASHBOARD);
  const [addWidget] = useMutation(ADD_DASHBOARD_WIDGET);
  const [updateWidget] = useMutation(UPDATE_DASHBOARD_WIDGET);
  const [removeWidget] = useMutation(REMOVE_DASHBOARD_WIDGET);

  const createDashboard = useCallback(
    async (input: DashboardInput) => {
      const result = await create({ variables: { input } });
      if (result.data?.createDashboard) {
        setDashboards((prev) => [...prev, result.data.createDashboard]);
        return result.data.createDashboard;
      }
      return null;
    },
    [create, setDashboards]
  );

  const updateDashboard = useCallback(
    async (dashboardId: string, input: Partial<DashboardInput>) => {
      const result = await update({ variables: { dashboardId, input } });
      if (result.data?.updateDashboard) {
        setDashboards((prev) =>
          prev.map((d) => (d.id === dashboardId ? result.data.updateDashboard : d))
        );
        setActiveDashboard((prev) =>
          prev?.id === dashboardId ? result.data.updateDashboard : prev
        );
        return result.data.updateDashboard;
      }
      return null;
    },
    [update, setDashboards, setActiveDashboard]
  );

  const deleteDashboard = useCallback(
    async (dashboardId: string) => {
      await remove({ variables: { dashboardId } });
      setDashboards((prev) => prev.filter((d) => d.id !== dashboardId));
      setActiveDashboard((prev) => (prev?.id === dashboardId ? null : prev));
    },
    [remove, setDashboards, setActiveDashboard]
  );

  const addDashboardWidget = useCallback(
    async (dashboardId: string, widget: WidgetInput) => {
      const result = await addWidget({ variables: { dashboardId, widget } });
      if (result.data?.addDashboardWidget) {
        setActiveDashboard((prev) => {
          if (prev?.id !== dashboardId) return prev;
          return {
            ...prev,
            widgets: [...(prev.widgets || []), result.data.addDashboardWidget],
          };
        });
        return result.data.addDashboardWidget;
      }
      return null;
    },
    [addWidget, setActiveDashboard]
  );

  const updateDashboardWidget = useCallback(
    async (dashboardId: string, widgetId: string, updates: Partial<WidgetInput>) => {
      const result = await updateWidget({
        variables: { dashboardId, widgetId, updates },
      });
      if (result.data?.updateDashboardWidget) {
        setActiveDashboard((prev) => {
          if (prev?.id !== dashboardId) return prev;
          return {
            ...prev,
            widgets: prev.widgets?.map((w) =>
              w.id === widgetId ? result.data.updateDashboardWidget : w
            ),
          };
        });
        return result.data.updateDashboardWidget;
      }
      return null;
    },
    [updateWidget, setActiveDashboard]
  );

  const removeDashboardWidget = useCallback(
    async (dashboardId: string, widgetId: string) => {
      await removeWidget({ variables: { dashboardId, widgetId } });
      setActiveDashboard((prev) => {
        if (prev?.id !== dashboardId) return prev;
        return {
          ...prev,
          widgets: prev.widgets?.filter((w) => w.id !== widgetId),
        };
      });
    },
    [removeWidget, setActiveDashboard]
  );

  return {
    createDashboard,
    updateDashboard,
    deleteDashboard,
    addDashboardWidget,
    updateDashboardWidget,
    removeDashboardWidget,
  };
}

// =============================================================================
// Runbook Hooks
// =============================================================================

/**
 * Hook for fetching runbooks
 */
export function useRunbooks(serviceId?: string) {
  const [runbooks, setRunbooks] = useAtom(runbooksAtom);

  const { data, loading, error, refetch } = useQuery(GET_RUNBOOKS, {
    variables: { serviceId },
    onCompleted: (data) => {
      if (data?.runbooks) {
        setRunbooks(data.runbooks);
      }
    },
  });

  const [execute] = useMutation(EXECUTE_RUNBOOK);

  const executeRunbook = useCallback(
    async (runbookId: string, params?: Record<string, unknown>) => {
      const result = await execute({ variables: { runbookId, params } });
      return result.data?.executeRunbook;
    },
    [execute]
  );

  return {
    runbooks: runbooks || data?.runbooks || [],
    isLoading: loading,
    error,
    executeRunbook,
    refetch,
  };
}

// =============================================================================
// Service Health Hooks
// =============================================================================

/**
 * Hook for service health status
 */
export function useServiceHealth(projectId?: string) {
  const [serviceHealth, setServiceHealth] = useAtom(serviceHealthAtom);

  const { data, loading, error, refetch } = useQuery(GET_SERVICE_HEALTH, {
    variables: { projectId },
    skip: !projectId,
    pollInterval: 30000, // Poll every 30 seconds
    onCompleted: (data) => {
      if (data?.serviceHealth) {
        setServiceHealth(data.serviceHealth);
      }
    },
  });

  return {
    services: serviceHealth || data?.serviceHealth || [],
    isLoading: loading,
    error,
    refetch,
  };
}

// =============================================================================
// On-Call Hooks
// =============================================================================

/**
 * Hook for on-call schedule
 */
export function useOnCallSchedule(teamId?: string) {
  const [schedule, setSchedule] = useAtom(onCallScheduleAtom);

  const { data, loading, error, refetch } = useQuery(GET_ON_CALL_SCHEDULE, {
    variables: { teamId },
    skip: !teamId,
    onCompleted: (data) => {
      if (data?.onCallSchedule) {
        setSchedule(data.onCallSchedule);
      }
    },
  });

  return {
    schedule: schedule || data?.onCallSchedule,
    isLoading: loading,
    error,
    refetch,
  };
}

// =============================================================================
// Metrics Hooks
// =============================================================================

/**
 * Hook for metrics with streaming
 */
export function useMetrics(
  query: string,
  options?: {
    startTime?: string;
    endTime?: string;
    step?: string;
    stream?: boolean;
  }
) {
  const [metrics, setMetrics] = useAtom(metricsAtom);

  const { data, loading, error, refetch } = useQuery(GET_METRICS, {
    variables: { query, ...options },
    skip: !query,
    onCompleted: (data) => {
      if (data?.metrics) {
        setMetrics(data.metrics);
      }
    },
  });

  // Stream metrics in real-time if enabled
  useSubscription(METRICS_STREAM_SUBSCRIPTION, {
    variables: { query },
    skip: !query || !options?.stream,
    onData: ({ data }) => {
      if (data?.data?.metricsStream) {
        setMetrics((prev) => {
          const newPoint = data.data.metricsStream;
          // Keep last 100 points
          return [...(prev || []), newPoint].slice(-100);
        });
      }
    },
  });

  return {
    metrics: metrics || data?.metrics || [],
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for log streaming
 */
export function useLogStream(filters?: {
  service?: string;
  level?: string[];
  search?: string;
}) {
  const [logs, setLogs] = useState<Array<{
    id: string;
    timestamp: string;
    level: string;
    message: string;
    service: string;
    metadata?: Record<string, unknown>;
  }>>([]);

  useSubscription(LOG_STREAM_SUBSCRIPTION, {
    variables: { filters },
    onData: ({ data }) => {
      if (data?.data?.logStream) {
        setLogs((prev) => [data.data.logStream, ...prev].slice(0, 500));
      }
    },
  });

  const clearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  return {
    logs,
    clearLogs,
  };
}

// Need to import useState for useLogStream
import { useState } from 'react';

export default {
  useIncident,
  useIncidents,
  useIncidentMutations,
  useAlerts,
  useAlertMutations,
  useDashboards,
  useDashboard,
  useDashboardMutations,
  useRunbooks,
  useServiceHealth,
  useOnCallSchedule,
  useMetrics,
  useLogStream,
};
