/**
 * Risk API Client
 *
 * HTTP client for interacting with the Java backend risk assessment endpoints.
 * Provides typed methods for risk evaluation, alerts, and escalation workflows.
 *
 * @doc.type class
 * @doc.purpose HTTP client for risk assessment API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type { DashboardApiConfig, ApiResponse, PaginationParams, PaginatedResponse } from './types';

// ============================================================================
// Types
// ============================================================================

/**
 * Risk severity levels
 */
export type RiskSeverity = 'low' | 'medium' | 'high' | 'critical';

/**
 * Risk category
 */
export type RiskCategory = 'security' | 'performance' | 'reliability' | 'compliance' | 'operational';

/**
 * Risk status
 */
export type RiskStatus = 'open' | 'mitigating' | 'mitigated' | 'escalated' | 'ignored';

/**
 * Risk alert interface
 */
export interface RiskAlert {
  id: string;
  title: string;
  description: string;
  severity: RiskSeverity;
  category: RiskCategory;
  status: RiskStatus;
  projectId: string;
  taskId?: string;
  affectedComponents: string[];
  createdAt: string;
  updatedAt: string;
  dueDate?: string;
  escalatedTo?: string;
  mitigationPlan?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Decision queue item interface
 */
export interface DecisionQueueItem {
  id: string;
  title: string;
  description: string;
  type: 'approval' | 'review' | 'escalation';
  priority: RiskSeverity;
  projectId: string;
  taskId?: string;
  requestedBy: string;
  requestedAt: string;
  dueDate?: string;
  status: 'pending' | 'approved' | 'rejected' | 'deferred';
  context?: Record<string, unknown>;
}

/**
 * List risk alerts request
 */
export interface ListRiskAlertsRequest extends PaginationParams {
  projectId?: string;
  severity?: RiskSeverity;
  category?: RiskCategory;
  status?: RiskStatus;
  [key: string]: unknown;
}

/**
 * Create risk alert request
 */
export interface CreateRiskAlertRequest {
  title: string;
  description: string;
  severity: RiskSeverity;
  category: RiskCategory;
  projectId: string;
  taskId?: string;
  affectedComponents: string[];
  dueDate?: string;
  mitigationPlan?: string;
}

/**
 * Update risk status request
 */
export interface UpdateRiskStatusRequest {
  status: RiskStatus;
  escalatedTo?: string;
  mitigationPlan?: string;
}

/**
 * Escalate risk request
 */
export interface EscalateRiskRequest {
  escalateTo: string;
  reason: string;
  priority?: RiskSeverity;
}

/**
 * List decision queue request
 */
export interface ListDecisionQueueRequest extends PaginationParams {
  projectId?: string;
  type?: 'approval' | 'review' | 'escalation';
  status?: 'pending' | 'approved' | 'rejected' | 'deferred';
  [key: string]: unknown;
}

/**
 * Process decision request
 */
export interface ProcessDecisionRequest {
  decision: 'approve' | 'reject' | 'defer';
  reason?: string;
}

// ============================================================================
// Client Implementation
// ============================================================================

/**
 * Risk API Client
 *
 * Provides methods for:
 * - Listing and creating risk alerts
 * - Updating risk status and escalation
 * - Managing decision queues
 * - Processing approval decisions
 */
export class RiskApiClient extends BaseDashboardApiClient {
  constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
    super(config, mode);
  }

  // ========== Risk Alert Operations ==========

  /**
   * List risk alerts with filtering
   *
   * @param request The list request with filters
   * @returns Promise of paginated risk alerts
   */
  async listRiskAlerts(
    request: ListRiskAlertsRequest = {}
  ): Promise<ApiResponse<PaginatedResponse<RiskAlert>>> {
    if (this.mode === 'mock') {
      return this.createMockResponse({
        items: this.getMockRiskAlerts(request),
        totalItems: 5,
        page: request.page || 1,
        pageSize: request.pageSize || 10,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      });
    }

    return this.get<PaginatedResponse<RiskAlert>>('/risks/alerts', request);
  }

  /**
   * Get a single risk alert by ID
   *
   * @param alertId The risk alert ID
   * @returns Promise of risk alert details
   */
  async getRiskAlert(alertId: string): Promise<ApiResponse<RiskAlert>> {
    if (this.mode === 'mock') {
      const mockAlert = this.getMockRiskAlerts({}).find(a => a.id === alertId);
      if (!mockAlert) {
        return this.createMockErrorResponse('NOT_FOUND', 'Risk alert not found');
      }
      return this.createMockResponse(mockAlert);
    }

    return this.get<RiskAlert>(`/risks/alerts/${alertId}`);
  }

  /**
   * Create a new risk alert
   *
   * @param request The create request
   * @returns Promise of created risk alert
   */
  async createRiskAlert(request: CreateRiskAlertRequest): Promise<ApiResponse<RiskAlert>> {
    if (this.mode === 'mock') {
      const newAlert: RiskAlert = {
        id: `risk-${Date.now()}`,
        ...request,
        status: 'open',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      return this.createMockResponse(newAlert);
    }

    return this.post<RiskAlert>('/risks/alerts', request);
  }

  /**
   * Update risk alert status
   *
   * @param alertId The risk alert ID
   * @param request The status update request
   * @returns Promise of updated risk alert
   */
  async updateRiskStatus(
    alertId: string,
    request: UpdateRiskStatusRequest
  ): Promise<ApiResponse<RiskAlert>> {
    if (this.mode === 'mock') {
      const mockAlerts = this.getMockRiskAlerts({});
      const alertIndex = mockAlerts.findIndex(a => a.id === alertId);
      if (alertIndex === -1) {
        return this.createMockErrorResponse('NOT_FOUND', 'Risk alert not found');
      }
      const updatedAlert = {
        ...mockAlerts[alertIndex],
        ...request,
        updatedAt: new Date().toISOString(),
      };
      return this.createMockResponse(updatedAlert);
    }

    return this.put<RiskAlert>(`/risks/alerts/${alertId}/status`, request);
  }

  /**
   * Escalate a risk alert
   *
   * @param alertId The risk alert ID
   * @param request The escalation request
   * @returns Promise of escalated risk alert
   */
  async escalateRisk(alertId: string, request: EscalateRiskRequest): Promise<ApiResponse<RiskAlert>> {
    if (this.mode === 'mock') {
      const mockAlerts = this.getMockRiskAlerts({});
      const alertIndex = mockAlerts.findIndex(a => a.id === alertId);
      if (alertIndex === -1) {
        return this.createMockErrorResponse('NOT_FOUND', 'Risk alert not found');
      }
      const escalatedAlert = {
        ...mockAlerts[alertIndex],
        status: 'escalated' as RiskStatus,
        escalatedTo: request.escalateTo,
        severity: request.priority || mockAlerts[alertIndex].severity,
        updatedAt: new Date().toISOString(),
      };
      return this.createMockResponse(escalatedAlert);
    }

    return this.post<RiskAlert>(`/risks/alerts/${alertId}/escalate`, request);
  }

  // ========== Decision Queue Operations ==========

  /**
   * List decision queue items
   *
   * @param request The list request with filters
   * @returns Promise of paginated decision queue items
   */
  async listDecisionQueue(
    request: ListDecisionQueueRequest = {}
  ): Promise<ApiResponse<PaginatedResponse<DecisionQueueItem>>> {
    if (this.mode === 'mock') {
      return this.createMockResponse({
        items: this.getMockDecisionQueue(request),
        totalItems: 3,
        page: request.page || 1,
        pageSize: request.pageSize || 10,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      });
    }

    return this.get<PaginatedResponse<DecisionQueueItem>>('/decisions/queue', request);
  }

  /**
   * Get a single decision queue item by ID
   *
   * @param itemId The decision queue item ID
   * @returns Promise of decision queue item details
   */
  async getDecisionItem(itemId: string): Promise<ApiResponse<DecisionQueueItem>> {
    if (this.mode === 'mock') {
      const mockItem = this.getMockDecisionQueue({}).find(i => i.id === itemId);
      if (!mockItem) {
        return this.createMockErrorResponse('NOT_FOUND', 'Decision item not found');
      }
      return this.createMockResponse(mockItem);
    }

    return this.get<DecisionQueueItem>(`/decisions/queue/${itemId}`);
  }

  /**
   * Process a decision (approve/reject/defer)
   *
   * @param itemId The decision queue item ID
   * @param request The decision request
   * @returns Promise of updated decision queue item
   */
  async processDecision(
    itemId: string,
    request: ProcessDecisionRequest
  ): Promise<ApiResponse<DecisionQueueItem>> {
    if (this.mode === 'mock') {
      const mockItems = this.getMockDecisionQueue({});
      const itemIndex = mockItems.findIndex(i => i.id === itemId);
      if (itemIndex === -1) {
        return this.createMockErrorResponse('NOT_FOUND', 'Decision item not found');
      }
      const status: 'pending' | 'approved' | 'rejected' | 'deferred' = 
        request.decision === 'approve' ? 'approved' : 
        request.decision === 'reject' ? 'rejected' : 'deferred';
      const updatedItem: DecisionQueueItem = {
        ...mockItems[itemIndex],
        status,
      };
      return this.createMockResponse(updatedItem);
    }

    try {
      return await this.post<DecisionQueueItem>(`/decisions/queue/${itemId}/process`, request);
    } catch (err: unknown) {
      // 409 means the approval was already decided — return the current state
      // so callers can replay safely without treating this as an error (C-Y6).
      const status = (err as { response?: { status?: number } }).response?.status;
      if (status === 409) {
        return this.get<DecisionQueueItem>(`/decisions/queue/${itemId}`);
      }
      throw err;
    }
  }

  /**
   * Bulk process decisions
   *
   * @param itemIds The decision queue item IDs
   * @param decision The decision to apply
   * @param reason Optional reason for the decision
   * @returns Promise of bulk processing result
   */
  async bulkProcessDecisions(
    itemIds: string[],
    decision: 'approve' | 'reject' | 'defer',
    reason?: string
  ): Promise<ApiResponse<{ succeeded: string[]; failed: Array<{ itemId: string; error: string }> }>> {
    if (this.mode === 'mock') {
      return this.createMockResponse({
        succeeded: itemIds,
        failed: [],
      });
    }

    return this.post('/decisions/queue/bulk-process', { itemIds, decision, reason });
  }

  // ========== Helper Methods ==========

  private createMockResponse<T>(data: T): ApiResponse<T> {
    return {
      success: true,
      data,
      timestamp: new Date().toISOString(),
    };
  }

  private createMockErrorResponse(code: string, message: string): ApiResponse<never> {
    return {
      success: false,
      error: {
        code,
        message,
      },
      timestamp: new Date().toISOString(),
    };
  }

  private getMockRiskAlerts(request: ListRiskAlertsRequest): RiskAlert[] {
    const baseAlerts: RiskAlert[] = [
      {
        id: 'risk-1',
        title: 'Security Vulnerability Detected',
        description: 'Critical SQL injection vulnerability found in authentication module',
        severity: 'critical',
        category: 'security',
        status: 'open',
        projectId: 'proj-1',
        taskId: 'task-1',
        affectedComponents: ['auth-service', 'user-api'],
        createdAt: new Date(Date.now() - 86400000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
        dueDate: new Date(Date.now() + 86400000).toISOString(),
      },
      {
        id: 'risk-2',
        title: 'Performance Degradation',
        description: 'API response times exceeding SLA thresholds',
        severity: 'high',
        category: 'performance',
        status: 'mitigating',
        projectId: 'proj-1',
        affectedComponents: ['api-gateway', 'cache-layer'],
        createdAt: new Date(Date.now() - 172800000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
        mitigationPlan: 'Implement caching and optimize database queries',
      },
      {
        id: 'risk-3',
        title: 'Compliance Issue',
        description: 'GDPR data retention policy not being followed',
        severity: 'medium',
        category: 'compliance',
        status: 'open',
        projectId: 'proj-1',
        affectedComponents: ['data-storage'],
        createdAt: new Date(Date.now() - 259200000).toISOString(),
        updatedAt: new Date(Date.now() - 259200000).toISOString(),
      },
    ];

    let filtered = [...baseAlerts];

    if (request.severity) {
      filtered = filtered.filter(a => a.severity === request.severity);
    }
    if (request.category) {
      filtered = filtered.filter(a => a.category === request.category);
    }
    if (request.status) {
      filtered = filtered.filter(a => a.status === request.status);
    }
    if (request.projectId) {
      filtered = filtered.filter(a => a.projectId === request.projectId);
    }

    return filtered;
  }

  private getMockDecisionQueue(request: ListDecisionQueueRequest): DecisionQueueItem[] {
    const baseItems: DecisionQueueItem[] = [
      {
        id: 'decision-1',
        title: 'Approval Required: Production Deployment',
        description: 'Requires approval for deployment to production environment',
        type: 'approval',
        priority: 'high',
        projectId: 'proj-1',
        taskId: 'task-2',
        requestedBy: 'devops-team',
        requestedAt: new Date(Date.now() - 3600000).toISOString(),
        dueDate: new Date(Date.now() + 7200000).toISOString(),
        status: 'pending',
      },
      {
        id: 'decision-2',
        title: 'Code Review Required',
        description: 'Critical security changes require senior engineer review',
        type: 'review',
        priority: 'critical',
        projectId: 'proj-1',
        taskId: 'task-1',
        requestedBy: 'security-team',
        requestedAt: new Date(Date.now() - 7200000).toISOString(),
        dueDate: new Date(Date.now() + 3600000).toISOString(),
        status: 'pending',
      },
      {
        id: 'decision-3',
        title: 'Escalation: Database Migration',
        description: 'Database migration requires DBA approval due to complexity',
        type: 'escalation',
        priority: 'high',
        projectId: 'proj-1',
        requestedBy: 'backend-team',
        requestedAt: new Date(Date.now() - 14400000).toISOString(),
        dueDate: new Date(Date.now() + 86400000).toISOString(),
        status: 'pending',
      },
    ];

    let filtered = [...baseItems];

    if (request.type) {
      filtered = filtered.filter(i => i.type === request.type);
    }
    if (request.status) {
      filtered = filtered.filter(i => i.status === request.status);
    }
    if (request.projectId) {
      filtered = filtered.filter(i => i.projectId === request.projectId);
    }

    return filtered;
  }
}
