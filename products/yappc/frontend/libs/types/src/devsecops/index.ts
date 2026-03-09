/**
 * DevSecOps Canvas - Complete Type Definitions
 *
 * Comprehensive type system for the DevSecOps Canvas feature covering
 * all phases, items, artifacts, integrations, and analytics.
 *
 * @module types/devsecops
 */

/**
 * DevSecOps phase keys
 */
export type PhaseKey =
  | 'ideation'
  | 'planning'
  | 'development'
  | 'security'
  | 'testing'
  | 'deployment'
  | 'operations';

/**
 * Item priority levels
 */
export type Priority = 'low' | 'medium' | 'high' | 'critical';

/**
 * Item status across the workflow
 */
export type ItemStatus =
  | 'not-started'
  | 'in-progress'
  | 'blocked'
  | 'in-review'
  | 'completed'
  | 'archived';

/**
 * User roles in the system
 */
export type UserRole = 'Executive' | 'PM' | 'Developer' | 'Security' | 'DevOps' | 'QA';

/**
 * Persona types for dashboard personalization
 * Aligned with backend PersonaType enum
 */
export type PersonaType =
  | 'executive'
  | 'leadership'
  | 'manager'
  | 'product-manager'
  | 'architect'
  | 'engineer'
  | 'developer'
  | 'devops'
  | 'operator'
  | 'security-champion'
  | 'security'
  | 'compliance-officer'
  | 'auditor';

/**
 * Artifact types
 */
export type ArtifactType =
  | 'diagram'
  | 'document'
  | 'code'
  | 'test'
  | 'design'
  | 'script'
  | 'report'
  | 'presentation';

/**
 * Integration provider types
 */
export type IntegrationProvider = 'jira' | 'github' | 'gitlab' | 'sonarqube' | 'jenkins' | 'custom';

/**
 * View modes for displaying items
 */
export type ViewMode = 'canvas' | 'kanban' | 'timeline' | 'table';

// ============================================================================
// Core Domain Models
// ============================================================================

/**
 * User information
 */
export interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  role: UserRole;
  teams?: string[];
}

/**
 * DevSecOps Phase
 */
export interface Phase {
  id: string;
  key: PhaseKey;
  title: string;
  description: string;
  order: number;
  color: string;
  icon?: string;
  milestones?: Milestone[];
  kpis?: KPI[];
}

/**
 * Milestone within a phase
 */
export interface Milestone {
  id: string;
  title: string;
  description?: string;
  dueDate: string;
  status: 'pending' | 'in-progress' | 'completed' | 'delayed';
  owner?: User;
}

/**
 * Canvas Item (Feature, Story, Task, etc.)
 */
export interface Item {
  id: string;
  title: string;
  description?: string;
  type: 'feature' | 'story' | 'task' | 'bug' | 'epic';
  priority: Priority;
  status: ItemStatus;
  phaseId: string;
  owners: User[];
  tags: string[];
  createdAt: string;
  updatedAt: string;
  startDate?: string;
  dueDate?: string;
  completedAt?: string;
  estimatedHours?: number;
  actualHours?: number;
  progress: number; // 0-100

  // Relations
  parentId?: string;
  childrenIds?: string[];
  dependsOn?: string[];
  blockedBy?: string[];

  // Artifacts
  artifacts: Artifact[];

  // External integrations
  integrations?: ItemIntegration[];

  // Workflow Integration
  workflowId?: string;

  // Metadata
  metadata?: Record<string, unknown>;
}

/**
 * Artifact attached to an item
 */
export interface Artifact {
  id: string;
  itemId: string;
  type: ArtifactType;
  title: string;
  description?: string;
  url?: string;
  content?: string;
  format?: string;
  version?: string;
  createdAt: string;
  updatedAt: string;
  createdBy: User;
  tags?: string[];
  metadata?: Record<string, unknown>;
}

/**
 * Diagram artifact with additional visualization data
 */
export interface DiagramArtifact extends Artifact {
  type: 'diagram';
  diagramType: 'architecture' | 'sequence' | 'infrastructure' | 'network' | 'flow' | 'erd';
  format: 'svg' | 'mermaid' | 'plantuml' | 'drawio' | 'json';
  layers?: DiagramLayer[];
  nodes?: DiagramNode[];
  edges?: DiagramEdge[];
}

/**
 * Diagram layer for toggling visibility
 */
export interface DiagramLayer {
  id: string;
  name: string;
  visible: boolean;
  order: number;
}

/**
 * Clickable diagram node
 */
export interface DiagramNode {
  id: string;
  label: string;
  type: string;
  layerId?: string;
  linkedItemId?: string;
  linkedArtifactId?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Diagram edge/connection
 */
export interface DiagramEdge {
  id: string;
  from: string;
  to: string;
  label?: string;
  type?: string;
}

/**
 * Implementation plan for an item
 */
export interface ImplementationPlan {
  id: string;
  itemId: string;
  title: string;
  description?: string;
  tasks: ImplementationTask[];
  owner: User;
  startDate?: string;
  endDate?: string;
  status: 'draft' | 'approved' | 'in-progress' | 'completed';
  acceptanceCriteria?: string[];
  metadata?: Record<string, unknown>;
}

/**
 * Task within an implementation plan
 */
export interface ImplementationTask {
  id: string;
  planId: string;
  title: string;
  description?: string;
  owner?: User;
  estimatedHours?: number;
  actualHours?: number;
  status: ItemStatus;
  order: number;
  dependencies?: string[];
  linkedPR?: string;
  linkedIssue?: string;
  linkedCommits?: string[];
  acceptanceCriteria?: string[];
}

/**
 * Key Performance Indicator
 */
export interface KPI {
  id: string;
  name: string;
  description?: string;
  category: 'velocity' | 'quality' | 'security' | 'operations' | 'custom';
  value: number;
  unit?: string;
  target?: number;
  threshold?: {
    warning: number;
    critical: number;
  };
  trend?: {
    direction: 'up' | 'down' | 'neutral';
    percentage: number;
  };
  historicalData?: {
    timestamp: string;
    value: number;
  }[];
  phaseId?: string;
  metadata?: Record<string, unknown>;
}

// ============================================================================
// Integration Models
// ============================================================================

/**
 * Integration configuration
 */
export interface IntegrationConfig {
  id: string;
  provider: IntegrationProvider;
  name: string;
  enabled: boolean;
  credentials?: Record<string, string>;
  settings?: Record<string, unknown>;
  lastSyncedAt?: string;
  syncStatus?: 'success' | 'error' | 'pending';
  syncError?: string;
}

/**
 * Item integration linking
 */
export interface ItemIntegration {
  id: string;
  itemId: string;
  provider: IntegrationProvider;
  externalId: string;
  externalUrl?: string;
  syncedAt: string;
  metadata?: Record<string, unknown>;
}

/**
 * Jira integration data
 */
export interface JiraIntegration extends ItemIntegration {
  provider: 'jira';
  issueKey: string;
  projectKey: string;
  issueType: string;
  status: string;
  assignee?: string;
  reporter?: string;
  labels?: string[];
}

/**
 * GitHub integration data
 */
export interface GitHubIntegration extends ItemIntegration {
  provider: 'github';
  repository: string;
  pullRequests?: GitHubPullRequest[];
  commits?: GitHubCommit[];
  issues?: GitHubIssue[];
}

/**
 * GitHub Pull Request
 */
export interface GitHubPullRequest {
  number: number;
  title: string;
  state: 'open' | 'closed' | 'merged';
  url: string;
  author: string;
  createdAt: string;
  updatedAt: string;
  mergedAt?: string;
  reviewers?: string[];
  checks?: {
    name: string;
    status: 'pending' | 'success' | 'failure';
    conclusion?: string;
  }[];
}

/**
 * GitHub Commit
 */
export interface GitHubCommit {
  sha: string;
  message: string;
  author: string;
  timestamp: string;
  url: string;
}

/**
 * GitHub Issue
 */
export interface GitHubIssue {
  number: number;
  title: string;
  state: 'open' | 'closed';
  url: string;
  assignees?: string[];
  labels?: string[];
}

/**
 * SonarQube integration data
 */
export interface SonarQubeIntegration extends ItemIntegration {
  provider: 'sonarqube';
  projectKey: string;
  metrics: {
    coverage?: number;
    bugs?: number;
    vulnerabilities?: number;
    codeSmells?: number;
    technicalDebt?: string;
    qualityGateStatus?: 'OK' | 'ERROR' | 'WARN';
  };
  lastAnalysis?: string;
}

// ============================================================================
// View & Filter Models
// ============================================================================

/**
 * Filter configuration for items
 */
export interface ItemFilter {
  phaseIds?: string[];
  status?: ItemStatus[];
  priority?: Priority[];
  ownerIds?: string[];
  tags?: string[];
  search?: string;
  dateRange?: {
    start: string;
    end: string;
  };
  hasIntegration?: IntegrationProvider[];
  customFilters?: Record<string, unknown>;
}

/**
 * Sort configuration
 */
export interface SortConfig {
  field: keyof Item | string;
  direction: 'asc' | 'desc';
}

/**
 * Pagination configuration
 */
export interface PaginationConfig {
  page: number;
  pageSize: number;
  total?: number;
}

/**
 * View configuration
 */
export interface ViewConfig {
  mode: ViewMode;
  filter?: ItemFilter;
  sort?: SortConfig;
  pagination?: PaginationConfig;
  groupBy?: keyof Item | string;
}

// ============================================================================
// API Response Models
// ============================================================================

/**
 * API response wrapper
 */
export interface ApiResponse<T> {
  data: T;
  success: boolean;
  error?: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  metadata?: {
    timestamp: string;
    requestId: string;
    [key: string]: unknown;
  };
}

/**
 * Paginated response
 */
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}

/**
 * Bulk operation result
 */
export interface BulkOperationResult {
  successCount: number;
  failureCount: number;
  errors?: {
    itemId: string;
    error: string;
  }[];
}

// ============================================================================
// Activity & Analytics Models
// ============================================================================

/**
 * Activity log entry
 */
export interface ActivityLog {
  id: string;
  itemId?: string;
  phaseId?: string;
  userId: string;
  action: string;
  description: string;
  timestamp: string;
  metadata?: Record<string, unknown>;
}

/**
 * Dashboard personas for DevSecOps overview experiences.
 */
export type DashboardPersona =
  | 'CISO'
  | 'DEVSECOPS_ENGINEER'
  | 'COMPLIANCE_OFFICER'
  | 'CLOUD_ARCHITECT'
  | 'SECURITY_ANALYST';

/**
 * KPI entry shown on persona dashboards.
 */
export interface PersonaDashboardKpi {
  id: string;
  label: string;
  value: string;
  trend?: 'up' | 'down' | 'neutral';
  change?: string;
}

/**
 * Task specific to a persona dashboard
 */
export interface PersonaTask {
  id: string;
  title: string;
  description?: string;
  workflowId?: string;
  route?: string;
  status?: 'pending' | 'completed';
}

/**
 * Summary card for a persona-specific DevSecOps dashboard.
 */
export interface PersonaDashboardSummary {
  persona: DashboardPersona;
  slug: string;
  title: string;
  description: string;
  focusAreas: string[];
  tasks?: PersonaTask[];
  kpis: PersonaDashboardKpi[];
  insights: string[];
  primaryAction?: {
    label: string;
    href: string;
  };
}

/**
 * Canonical payload returned by the DevSecOps overview endpoint.
 *
 * Used as the ApiResponse data shape for GET /api/devsecops/overview.
 */
export interface DevSecOpsOverview {
  phases: Phase[];
  items: Item[];
  milestones: Milestone[];
  kpis: KPI[];
  activity: ActivityLog[];
  personaDashboards: PersonaDashboardSummary[];
}

/**
 * Analytics data point
 */
export interface AnalyticsDataPoint {
  timestamp: string;
  value: number;
  label?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Report configuration
 */
export interface ReportConfig {
  id: string;
  name: string;
  type: 'kpi' | 'velocity' | 'quality' | 'security' | 'custom';
  dateRange: {
    start: string;
    end: string;
  };
  phaseIds?: string[];
  metrics: string[];
  format: 'pdf' | 'excel' | 'csv' | 'json';
}

// ============================================================================
// Exports
// ============================================================================

export type {
  // Re-export commonly used types for convenience
  PhaseKey as DevSecOpsPhase,
  ViewMode as CanvasViewMode,
};
