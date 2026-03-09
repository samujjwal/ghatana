/**
 * Unified API client exports
 *
 * <p><b>Purpose</b><br>
 * Central export point for all API service modules. Makes imports clean and organized
 * across the application.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { kpisApi, agentsApi, modelsApi } from '@/services/api';
 *
 * const kpis = await kpisApi.getOrgKpis();
 * const actions = await agentsApi.getPendingActions();
 * ```
 *
 * @doc.type utility
 * @doc.purpose API client exports
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

export { apiClient, createApiClient } from './index';
export { kpisApi, type KpiResponse, type KpiTrendResponse } from './kpisApi';
export { workflowsApi, type WorkflowEvent, type WorkflowNode, type WorkflowEdge } from './workflowsApi';
export { agentsApi, type AgentAction, type ActionPriority, type ActionStatus } from './agentsApi';
export { eventsApi, type EventSchema, type DryRunResponse } from './eventsApi';
export { reportsApi, type MetricResponse, type IncidentStat, type ReportTemplate } from './reportsApi';
export { securityApi, type Vulnerability, type AuditEvent, type UserAccess, type ComplianceItem } from './securityApi';
export { modelsApi, type Model, type ModelVersion, type TestCase, type ABTestResult } from './modelsApi';
export { departmentApi, type Department, type DepartmentDetail, type DepartmentKpi } from './departmentApi';
export { workItemsApi } from './workItemsApi';
export { rootApi, type RootUserSearchResult, type SuspendUserResponse } from './rootApi';
export {
    growthPlansApi,
    type GrowthPlanResponse,
    type ListGrowthPlansResponse,
    type CreateGrowthPlanRequest,
    type UpdateGrowthPlanRequest,
    type JsonValue,
} from './growthPlansApi';
