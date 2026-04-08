/**
 * Risk Assessment Hook
 *
 * React hook for managing risk alerts and decision queues with optimistic updates.
 * Provides CRUD operations, filtering, bulk actions, and comprehensive error handling.
 *
 * @doc.type hook
 * @doc.purpose Risk assessment and decision queue management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  createDashboardClients,
  type DashboardApiConfig,
  type RiskAlert,
  type DecisionQueueItem,
  type RiskSeverity,
  type RiskCategory,
  type RiskStatus,
  type ListRiskAlertsRequest,
  type CreateRiskAlertRequest,
  type UpdateRiskStatusRequest,
  type EscalateRiskRequest,
  type ListDecisionQueueRequest,
  type ProcessDecisionRequest,
} from '../clients/dashboard';

// ============================================================================
// Query Keys
// ============================================================================

export const riskQueryKeys = {
  all: ['risk'] as const,
  alerts: () => [...riskQueryKeys.all, 'alerts'] as const,
  alert: (id: string) => [...riskQueryKeys.alerts(), id] as const,
  alertsList: (filters: ListRiskAlertsRequest) => [...riskQueryKeys.alerts(), 'list', filters] as const,
  decisionQueue: () => [...riskQueryKeys.all, 'decisionQueue'] as const,
  decisionItem: (id: string) => [...riskQueryKeys.decisionQueue(), id] as const,
  decisionQueueList: (filters: ListDecisionQueueRequest) => [...riskQueryKeys.decisionQueue(), 'list', filters] as const,
};

// ============================================================================
// Hook Options
// ============================================================================

interface UseRiskAssessmentOptions {
  config?: Partial<DashboardApiConfig>;
  projectId?: string;
  initialAlertFilters?: ListRiskAlertsRequest;
  initialDecisionFilters?: ListDecisionQueueRequest;
}

interface UseRiskAssessmentResult {
  // Risk Alerts
  alerts: RiskAlert[];
  alertsLoading: boolean;
  alertsError: Error | null;
  refetchAlerts: () => void;
  createAlert: (request: CreateRiskAlertRequest) => Promise<RiskAlert>;
  updateAlertStatus: (alertId: string, request: UpdateRiskStatusRequest) => Promise<void>;
  escalateAlert: (alertId: string, request: EscalateRiskRequest) => Promise<void>;

  // Decision Queue
  decisions: DecisionQueueItem[];
  decisionsLoading: boolean;
  decisionsError: Error | null;
  refetchDecisions: () => void;
  processDecision: (itemId: string, request: ProcessDecisionRequest) => Promise<void>;
  bulkProcessDecisions: (itemIds: string[], decision: 'approve' | 'reject' | 'defer', reason?: string) => Promise<void>;

  // Mutation states
  isCreatingAlert: boolean;
  isUpdatingStatus: boolean;
  isEscalating: boolean;
  isProcessingDecision: boolean;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Hook for managing risk assessment and decision queues
 *
 * Provides optimistic updates for risk and decision operations with automatic rollback on error.
 */
export function useRiskAssessment(
  options: UseRiskAssessmentOptions = {}
): UseRiskAssessmentResult {
  const { config, projectId, initialAlertFilters = {}, initialDecisionFilters = {} } = options;
  const queryClient = useQueryClient();

  // Create API clients
  const clients = useMemo(() => {
    const defaultConfig: DashboardApiConfig = {
      baseUrl: config?.baseUrl || import.meta.env.VITE_API_ORIGIN || '/api',
      timeout: config?.timeout || 10000,
      maxRetries: config?.maxRetries || 3,
      logRequests: config?.logRequests || false,
      logResponses: config?.logResponses || false,
      tenantId: config?.tenantId || '',
      authToken: config?.authToken || '',
    };
    return createDashboardClients(defaultConfig);
  }, [config]);
  const riskClient = clients.risk;

  // Default filters
  const alertFilters = useMemo<ListRiskAlertsRequest>(
    () => ({
      ...initialAlertFilters,
      projectId: projectId || initialAlertFilters.projectId,
    }),
    [initialAlertFilters, projectId]
  );

  const decisionFilters = useMemo<ListDecisionQueueRequest>(
    () => ({
      ...initialDecisionFilters,
      projectId: projectId || initialDecisionFilters.projectId,
    }),
    [initialDecisionFilters, projectId]
  );

  // Query for risk alerts
  const {
    data: alertsResponse,
    isLoading: alertsLoading,
    error: alertsError,
    refetch: refetchAlerts,
  } = useQuery({
    queryKey: riskQueryKeys.alertsList(alertFilters),
    queryFn: () => riskClient.listRiskAlerts(alertFilters),
    select: (response) => response.data?.items || [],
    staleTime: 30000,
  });

  const alerts = alertsResponse || [];

  // Query for decision queue
  const {
    data: decisionsResponse,
    isLoading: decisionsLoading,
    error: decisionsError,
    refetch: refetchDecisions,
  } = useQuery({
    queryKey: riskQueryKeys.decisionQueueList(decisionFilters),
    queryFn: () => riskClient.listDecisionQueue(decisionFilters),
    select: (response) => response.data?.items || [],
    staleTime: 30000,
  });

  const decisions = decisionsResponse || [];

  // Create alert mutation with optimistic update
  const createAlertMutation = useMutation({
    mutationFn: (request: CreateRiskAlertRequest) => riskClient.createRiskAlert(request),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: riskQueryKeys.alertsList(alertFilters) });
    },
  });

  // Update alert status mutation with optimistic update
  const updateAlertStatusMutation = useMutation({
    mutationFn: ({ alertId, request }: { alertId: string; request: UpdateRiskStatusRequest }) =>
      riskClient.updateRiskStatus(alertId, request),
    onMutate: async ({ alertId, request }) => {
      await queryClient.cancelQueries({ queryKey: riskQueryKeys.alertsList(alertFilters) });
      const previousAlerts = queryClient.getQueryData(riskQueryKeys.alertsList(alertFilters));

      queryClient.setQueryData(riskQueryKeys.alertsList(alertFilters), (old: RiskAlert[] | undefined) =>
        old?.map((alert) =>
          alert.id === alertId
            ? { ...alert, ...request, updatedAt: new Date().toISOString() }
            : alert
        )
      );

      return { previousAlerts };
    },
    onError: (error, variables, context) => {
      if (context?.previousAlerts) {
        queryClient.setQueryData(riskQueryKeys.alertsList(alertFilters), context.previousAlerts);
      }
      console.error('Failed to update alert status:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: riskQueryKeys.alertsList(alertFilters) });
    },
  });

  // Escalate alert mutation with optimistic update
  const escalateAlertMutation = useMutation({
    mutationFn: ({ alertId, request }: { alertId: string; request: EscalateRiskRequest }) =>
      riskClient.escalateRisk(alertId, request),
    onMutate: async ({ alertId, request }) => {
      await queryClient.cancelQueries({ queryKey: riskQueryKeys.alertsList(alertFilters) });
      const previousAlerts = queryClient.getQueryData(riskQueryKeys.alertsList(alertFilters));

      queryClient.setQueryData(riskQueryKeys.alertsList(alertFilters), (old: RiskAlert[] | undefined) =>
        old?.map((alert) =>
          alert.id === alertId
            ? {
                ...alert,
                status: 'escalated' as RiskStatus,
                escalatedTo: request.escalateTo,
                severity: request.priority || alert.severity,
                updatedAt: new Date().toISOString(),
              }
            : alert
        )
      );

      return { previousAlerts };
    },
    onError: (error, variables, context) => {
      if (context?.previousAlerts) {
        queryClient.setQueryData(riskQueryKeys.alertsList(alertFilters), context.previousAlerts);
      }
      console.error('Failed to escalate alert:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: riskQueryKeys.alertsList(alertFilters) });
    },
  });

  // Process decision mutation with optimistic update
  const processDecisionMutation = useMutation({
    mutationFn: ({ itemId, request }: { itemId: string; request: ProcessDecisionRequest }) =>
      riskClient.processDecision(itemId, request),
    onMutate: async ({ itemId, request }) => {
      await queryClient.cancelQueries({ queryKey: riskQueryKeys.decisionQueueList(decisionFilters) });
      const previousDecisions = queryClient.getQueryData(riskQueryKeys.decisionQueueList(decisionFilters));

      const status = request.decision === 'approve' ? 'approved' :
                    request.decision === 'reject' ? 'rejected' : 'deferred';

      queryClient.setQueryData(riskQueryKeys.decisionQueueList(decisionFilters), (old: DecisionQueueItem[] | undefined) =>
        old?.map((decision) =>
          decision.id === itemId
            ? { ...decision, status }
            : decision
        )
      );

      return { previousDecisions };
    },
    onError: (error, variables, context) => {
      if (context?.previousDecisions) {
        queryClient.setQueryData(riskQueryKeys.decisionQueueList(decisionFilters), context.previousDecisions);
      }
      console.error('Failed to process decision:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: riskQueryKeys.decisionQueueList(decisionFilters) });
    },
  });

  // Bulk process decision mutation with optimistic update
  const bulkProcessDecisionMutation = useMutation({
    mutationFn: (params: { itemIds: string[]; decision: 'approve' | 'reject' | 'defer'; reason?: string }) =>
      riskClient.bulkProcessDecisions(params.itemIds, params.decision, params.reason),
    onMutate: async (params) => {
      await queryClient.cancelQueries({ queryKey: riskQueryKeys.decisionQueueList(decisionFilters) });
      const previousDecisions = queryClient.getQueryData(riskQueryKeys.decisionQueueList(decisionFilters));

      const status = params.decision === 'approve' ? 'approved' :
                    params.decision === 'reject' ? 'rejected' : 'deferred';

      queryClient.setQueryData(riskQueryKeys.decisionQueueList(decisionFilters), (old: DecisionQueueItem[] | undefined) =>
        old?.map((decision) =>
          params.itemIds.includes(decision.id)
            ? { ...decision, status }
            : decision
        )
      );

      return { previousDecisions };
    },
    onError: (error, variables, context) => {
      if (context?.previousDecisions) {
        queryClient.setQueryData(riskQueryKeys.decisionQueueList(decisionFilters), context.previousDecisions);
      }
      console.error('Failed to bulk process decisions:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: riskQueryKeys.decisionQueueList(decisionFilters) });
    },
  });

  // Memoized mutation functions
  const createAlert = useCallback(
    async (request: CreateRiskAlertRequest): Promise<RiskAlert> => {
      const response = await createAlertMutation.mutateAsync(request);
      return response.data!;
    },
    [createAlertMutation]
  );

  const updateAlertStatus = useCallback(
    async (alertId: string, request: UpdateRiskStatusRequest) => {
      await updateAlertStatusMutation.mutateAsync({ alertId, request });
    },
    [updateAlertStatusMutation]
  );

  const escalateAlert = useCallback(
    async (alertId: string, request: EscalateRiskRequest) => {
      await escalateAlertMutation.mutateAsync({ alertId, request });
    },
    [escalateAlertMutation]
  );

  const processDecision = useCallback(
    async (itemId: string, request: ProcessDecisionRequest) => {
      await processDecisionMutation.mutateAsync({ itemId, request });
    },
    [processDecisionMutation]
  );

  const bulkProcessDecisions = useCallback(
    async (itemIds: string[], decision: 'approve' | 'reject' | 'defer', reason?: string) => {
      await bulkProcessDecisionMutation.mutateAsync({ itemIds, decision, reason });
    },
    [bulkProcessDecisionMutation]
  );

  return {
    alerts,
    alertsLoading,
    alertsError,
    refetchAlerts,
    createAlert,
    updateAlertStatus,
    escalateAlert,
    decisions,
    decisionsLoading,
    decisionsError,
    refetchDecisions,
    processDecision,
    bulkProcessDecisions,
    isCreatingAlert: createAlertMutation.isPending,
    isUpdatingStatus: updateAlertStatusMutation.isPending,
    isEscalating: escalateAlertMutation.isPending,
    isProcessingDecision: processDecisionMutation.isPending,
  };
}
