/**
 * Dashboard entity types.
 */
export interface Dashboard {
  id: string;
  name: string;
  description?: string;
  persona: DashboardPersona;
  layout: DashboardLayout;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export type DashboardPersona =
  | 'CISO'
  | 'DEVSECOPS_ENGINEER'
  | 'COMPLIANCE_OFFICER'
  | 'CLOUD_ARCHITECT'
  | 'SECURITY_ANALYST';

export type DashboardLayout = 'GRID' | 'MASONRY' | 'FIXED';

/**
 * Widget entity types.
 */
export interface Widget {
  id: string;
  dashboardId: string;
  type: WidgetType;
  title: string;
  config: WidgetConfig;
  x: number;
  y: number;
  width: number;
  height: number;
  isVisible: boolean;
  refreshInterval?: number;
}

export type WidgetType =
  | 'KPI_CARD'
  | 'LINE_CHART'
  | 'BAR_CHART'
  | 'PIE_CHART'
  | 'TABLE'
  | 'STATS_DASHBOARD'
  | 'INCIDENT_LIST'
  | 'VULNERABILITY_LIST'
  | 'COMPLIANCE_SCORE'
  | 'PIPELINE_STATUS'
  | 'CLOUD_MAP';

export interface WidgetConfig {
  [key: string]: unknown;
  dataSource?: string;
  filters?: Record<string, unknown>;
  displayOptions?: Record<string, unknown>;
}

/**
 * Security incident types.
 */
export interface SecurityIncident {
  id: string;
  incidentId: string;
  title: string;
  description: string;
  severity: SecuritySeverity;
  status: IncidentStatus;
  detectedAt: string;
  resolvedAt?: string;
  assignedTo?: string;
  source?: string;
  affectedResources: string[];
}

export type SecuritySeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

/**
 * Vulnerability types.
 */
export interface VulnerabilityFinding {
  id: string;
  cveId: string;
  packageName: string;
  currentVersion: string;
  fixedVersion?: string;
  severity: SecuritySeverity;
  cvssScore: number;
  description: string;
  publishedAt: string;
  affectedServices: string[];
}

/**
 * Compliance types.
 */
export interface ComplianceFramework {
  id: string;
  name: string;
  version: string;
  description?: string;
  totalControls: number;
  implementedControls: number;
  score: number;
  lastAssessedAt: string;
}

export interface ComplianceControl {
  id: string;
  frameworkId: string;
  controlId: string;
  name: string;
  description: string;
  status: ControlStatus;
  implementedAt?: string;
}

export type ControlStatus = 'IMPLEMENTED' | 'IN_PROGRESS' | 'NOT_IMPLEMENTED' | 'NOT_APPLICABLE';

/**
 * Pipeline types.
 */
export interface Pipeline {
  id: string;
  name: string;
  status: PipelineStatus;
  environment: string;
  branch: string;
  commitSha: string;
  commitMessage: string;
  startedAt: string;
  completedAt?: string;
  duration: number;
  stages: PipelineStage[];
}

export type PipelineStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

export interface PipelineStage {
  name: string;
  status: PipelineStatus;
  startedAt?: string;
  completedAt?: string;
  duration?: number;
}

/**
 * Cloud resource types.
 */
export interface CloudResource {
  id: string;
  resourceId: string;
  name: string;
  resourceType: CloudResourceType;
  provider: CloudProvider;
  region: string;
  status: ResourceStatus;
  tags: Record<string, string>;
  monthlyCost?: number;
  hasSecurityIssues: boolean;
  isCompliant: boolean;
  createdAt: string;
}

export type CloudProvider = 'AWS' | 'AZURE' | 'GCP';
export type CloudResourceType = 'COMPUTE' | 'STORAGE' | 'DATABASE' | 'NETWORK' | 'SECURITY';
export type ResourceStatus = 'RUNNING' | 'STOPPED' | 'TERMINATED' | 'PENDING';

/**
 * Notification types.
 */
export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  link?: string;
  isRead: boolean;
  createdAt: string;
}

export type NotificationType = 'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS';

/**
 * User types.
 */
export interface User {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  tenantId: string;
  workspaceIds: string[];
}

export type UserRole = 'ADMIN' | 'USER' | 'VIEWER';

/**
 * GraphQL query result types.
 */
export interface QueryResult<T> {
  data?: T;
  loading: boolean;
  error?: Error;
}

export interface MutationResult<T> {
  data?: T;
  loading: boolean;
  error?: Error;
}

/**
 * Pagination types.
 */
export interface PageInfo {
  hasNextPage: boolean;
  hasPreviousPage: boolean;
  startCursor?: string;
  endCursor?: string;
}

export interface Connection<T> {
  edges: Edge<T>[];
  pageInfo: PageInfo;
  totalCount: number;
}

export interface Edge<T> {
  node: T;
  cursor: string;
}

/**
 * Filter types.
 */
export interface FilterCriteria {
  field: string;
  operator: FilterOperator;
  value: unknown;
}

export type FilterOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'CONTAINS'
  | 'GREATER_THAN'
  | 'LESS_THAN'
  | 'IN'
  | 'NOT_IN';

/**
 * Sort types.
 */
export interface SortCriteria {
  field: string;
  direction: SortDirection;
}

export type SortDirection = 'ASC' | 'DESC';

/**
 * Metric types.
 */
export interface MetricValue {
  timestamp: string;
  value: number;
  labels?: Record<string, string>;
}

export interface TimeSeriesData {
  metric: string;
  values: MetricValue[];
}
