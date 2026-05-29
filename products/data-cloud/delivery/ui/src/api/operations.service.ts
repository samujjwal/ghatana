/**
 * Operations API Service
 *
 * P10.4: Link UI pages to operation details.
 * Provides API client methods for fetching operation details, dead-letter items,
 * policy decisions, and runtime dependencies for observability.
 *
 * @doc.type service
 * @doc.purpose Operations API client for UI
 * @doc.layer frontend
 */

const API_BASE = '/api/v1/operations';

export interface Operation {
  id: string;
  operationType: string;
  tenantId: string;
  status: string;
  initiatedBy: string;
  startedAt: string;
  completedAt: string | null;
  metadata: Record<string, unknown>;
  events: OperationEvent[];
  dependencies: Record<string, RuntimeDependency>;
}

export interface OperationEvent {
  timestamp: string;
  eventType: string;
  message: string;
  data: Record<string, unknown>;
}

export interface RuntimeDependency {
  name: string;
  status: string;
  lastChecked: string;
}

export interface OperationDetails {
  operation: Operation;
  retries: RetryAttempt[];
  failures: FailureInfo[];
  runtimeDependencies: Record<string, RuntimeDependencyStatus>;
}

export interface RetryAttempt {
  attemptNumber: number;
  timestamp: string;
  reason: string;
  success: boolean;
}

export interface FailureInfo {
  timestamp: string;
  errorType: string;
  errorMessage: string;
  recoverable: boolean;
  recoveryAction: string | null;
}

export interface RuntimeDependencyStatus {
  name: string;
  type: string;
  status: string;
  lastChecked: string;
  lastHealthy: string;
  uptimePercentage: number;
  metadata: Record<string, string>;
}

export interface DeadLetterItem {
  id: string;
  operationId: string;
  tenantId: string;
  operationType: string;
  reason: string;
  payload: Record<string, unknown>;
  createdAt: string;
  expiresAt: string | null;
  recoveryStatus: string;
}

export interface RecoveryAttempt {
  attemptId: string;
  itemId: string;
  timestamp: string;
  strategy: string;
  success: boolean;
  errorMessage: string | null;
}

export interface PolicyDecision {
  decisionId: string;
  policyId: string;
  tenantId: string;
  decision: string;
  reason: string;
  context: Record<string, unknown>;
  timestamp: string;
  reviewedBy: string | null;
}

export interface OperationSummary {
  totalOperations: number;
  successfulOperations: number;
  failedOperations: number;
  retriedOperations: number;
  deadLetterCount: number;
  averageDurationMs: number;
  successRate: number;
}

export interface PolicyDecisionFilters {
  policyId?: string;
  decision?: string;
  startDate?: string;
  endDate?: string;
}

export interface TimeRange {
  start: string;
  end: string;
}

/**
 * Operations API service
 */
export const operationsService = {
  /**
   * Get operation details by ID
   */
  async getOperationDetails(operationId: string): Promise<OperationDetails> {
    const response = await fetch(`${API_BASE}/${operationId}`);
    if (!response.ok) {
      throw new Error(`Failed to fetch operation details: ${response.statusText}`);
    }
    return response.json();
  },

  /**
   * Get dead-letter items for a tenant
   */
  async getDeadLetterItems(tenantId: string, limit = 50): Promise<DeadLetterItem[]> {
    const response = await fetch(`${API_BASE}/dead-letter?tenantId=${tenantId}&limit=${limit}`);
    if (!response.ok) {
      throw new Error(`Failed to fetch dead-letter items: ${response.statusText}`);
    }
    return response.json();
  },

  /**
   * Get recovery attempts for a dead-letter item
   */
  async getRecoveryAttempts(itemId: string): Promise<RecoveryAttempt[]> {
    const response = await fetch(`${API_BASE}/dead-letter/${itemId}/recovery-attempts`);
    if (!response.ok) {
      throw new Error(`Failed to fetch recovery attempts: ${response.statusText}`);
    }
    return response.json();
  },

  /**
   * Get policy decisions for a tenant
   */
  async getPolicyDecisions(
    tenantId: string,
    filters?: PolicyDecisionFilters
  ): Promise<PolicyDecision[]> {
    const params = new URLSearchParams({ tenantId });
    if (filters?.policyId) params.set('policyId', filters.policyId);
    if (filters?.decision) params.set('decision', filters.decision);
    if (filters?.startDate) params.set('startDate', filters.startDate);
    if (filters?.endDate) params.set('endDate', filters.endDate);

    const response = await fetch(`${API_BASE}/policy-decisions?${params}`);
    if (!response.ok) {
      throw new Error(`Failed to fetch policy decisions: ${response.statusText}`);
    }
    return response.json();
  },

  /**
   * Get runtime dependencies for a tenant
   */
  async getRuntimeDependencies(tenantId: string): Promise<RuntimeDependencyStatus[]> {
    const response = await fetch(`${API_BASE}/dependencies?tenantId=${tenantId}`);
    if (!response.ok) {
      throw new Error(`Failed to fetch runtime dependencies: ${response.statusText}`);
    }
    return response.json();
  },

  /**
   * Get operation summary for a tenant
   */
  async getOperationSummary(tenantId: string, timeRange: TimeRange): Promise<OperationSummary> {
    const params = new URLSearchParams({
      tenantId,
      start: timeRange.start,
      end: timeRange.end,
    });
    const response = await fetch(`${API_BASE}/summary?${params}`);
    if (!response.ok) {
      throw new Error(`Failed to fetch operation summary: ${response.statusText}`);
    }
    return response.json();
  },

  /**
   * Retry a dead-letter item
   */
  async retryDeadLetterItem(itemId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/dead-letter/${itemId}/retry`, {
      method: 'POST',
    });
    if (!response.ok) {
      throw new Error(`Failed to retry dead-letter item: ${response.statusText}`);
    }
  },

  /**
   * Cancel an operation
   */
  async cancelOperation(operationId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${operationId}/cancel`, {
      method: 'POST',
    });
    if (!response.ok) {
      throw new Error(`Failed to cancel operation: ${response.statusText}`);
    }
  },
};
