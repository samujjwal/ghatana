/**
 * Test Setup Helper
 * 
 * Provides utilities for integration test setup and teardown.
 * This module is used by the end-to-end and extended test suites.
 * 
 * @doc.type test-helper
 * @doc.purpose Integration test infrastructure
 * @doc.layer testing
 */

export interface TestEnvironment {
  api: MockApi;
  helpers: TestHelpers;
  websocket: WebSocketMock;
}

export interface MockApi {
  users: UserApi;
  workspaces: WorkspaceApi;
  projects: ProjectApi;
  canvas: CanvasApi;
  agents: AgentApi;
  compliance: ComplianceApi;
  tenants: TenantApi;
  onboarding: OnboardingApi;
  scaffold: ScaffoldApi;
  cicd: CiCdApi;
  git: GitApi;
  security: SecurityApi;
  featureFlags: FeatureFlagApi;
  pipelines: PipelineApi;
  backup: BackupApi;
  analytics: AnalyticsApi;
  reporting: ReportingApi;
  traffic: TrafficApi;
}

// Mock API interfaces
interface UserApi {
  create: (data: any) => Promise<any>;
}

interface WorkspaceApi {
  create: (data: any, options?: any) => Promise<any>;
  list: (userId: string) => Promise<any[]>;
  get: (id: string, options?: any) => Promise<any>;
}

interface ProjectApi {
  create: (data: any) => Promise<any>;
}

interface CanvasApi {
  create: (data: any) => Promise<any>;
  addNode: (canvasId: string, node: any) => Promise<any>;
  addEdge: (canvasId: string, edge: any) => Promise<any>;
  getState: (canvasId: string) => Promise<any>;
}

interface AgentApi {
  execute: (data: any) => Promise<any>;
  createWorkflow: (data: any) => Promise<any>;
  executeWorkflow: (workflowId: string, input: any) => Promise<any>;
}

interface ComplianceApi {
  runAudit: (data: any) => Promise<any>;
  getAuditStatus: (auditId: string) => Promise<any>;
}

interface TenantApi {
  create: (data: any) => Promise<any>;
}

interface OnboardingApi {
  completeStep: (userId: string, step: string) => Promise<any>;
  getStatus: (userId: string) => Promise<any>;
}

interface ScaffoldApi {
  execute: (data: any) => Promise<any>;
}

interface CiCdApi {
  createBuild: (data: any) => Promise<any>;
  triggerBuild: (data: any) => Promise<any>;
  getBuildStatus: (buildId: string) => Promise<any>;
  deploy: (data: any) => Promise<any>;
  getDeploymentStatus: (deploymentId: string) => Promise<any>;
}

interface GitApi {
  createCommit: (data: any) => Promise<any>;
}

interface SecurityApi {
  scanDependencies: (data: any) => Promise<any>;
  applyFix: (data: any) => Promise<any>;
}

interface FeatureFlagApi {
  create: (data: any) => Promise<any>;
  toggle: (flagId: string, enabled: boolean) => Promise<any>;
  get: (flagId: string) => Promise<any>;
  checkAccess: (data: any) => Promise<boolean>;
  evaluate: (data: any) => Promise<any>;
  getMetrics: (flagId: string) => Promise<any>;
}

interface PipelineApi {
  create: (data: any) => Promise<any>;
  execute: (pipelineId: string) => Promise<any>;
  getRunStatus: (runId: string) => Promise<any>;
}

interface BackupApi {
  create: (data: any) => Promise<any>;
  trigger: (backupId: string) => Promise<any>;
  getStatus: (backupId: string) => Promise<any>;
  createFull: (data: any) => Promise<any>;
  restore: (data: any) => Promise<any>;
  getRestoreStatus: (restoreId: string) => Promise<any>;
}

interface AnalyticsApi {
  trackEvent: (data: any) => Promise<any>;
  getReport: (data: any) => Promise<any>;
}

interface ReportingApi {
  createDashboard: (data: any) => Promise<any>;
  getDashboardData: (dashboardId: string) => Promise<any>;
}

interface TrafficApi {
  configureRouting: (data: any) => Promise<any>;
  routeRequest: (data: any) => Promise<any>;
}

// Test helpers
interface TestHelpers {
  poll: <T>(
    fn: () => Promise<T>,
    condition: (result: T) => boolean,
    options: { timeout?: number; interval?: number }
  ) => Promise<T>;
}

// WebSocket mock
interface WebSocketMock {
  connect: (channel: string, userId: string) => Promise<WebSocketClient>;
}

interface WebSocketClient {
  send: (message: any) => Promise<void>;
  waitForMessage: (filter: any) => Promise<any>;
  disconnect: () => Promise<void>;
}

/**
 * Setup test environment with mocked APIs
 */
export async function setupTestEnvironment(): Promise<TestEnvironment> {
  // Initialize mock APIs
  const api: MockApi = {
    users: {
      create: async (data) => ({ id: `user-${Date.now()}`, ...data }),
    },
    workspaces: {
      create: async (data, options) => ({ 
        id: `ws-${Date.now()}`, 
        isDefault: true,
        ...data 
      }),
      list: async (userId) => [{ id: 'ws-1', isDefault: true }],
      get: async (id, options) => {
        if (options?.tenantId && options.tenantId !== 'correct-tenant') {
          throw new Error('Not Found');
        }
        return { id, name: 'Test Workspace' };
      },
    },
    projects: {
      create: async (data) => ({ 
        id: `proj-${Date.now()}`,
        status: 'DRAFT',
        ...data 
      }),
    },
    canvas: {
      create: async (data) => ({ id: `canvas-${Date.now()}`, ...data }),
      addNode: async (canvasId, node) => ({ id: `node-${Date.now()}`, ...node }),
      addEdge: async (canvasId, edge) => ({ id: `edge-${Date.now()}`, ...edge }),
      getState: async (canvasId) => ({ nodes: [], edges: [] }),
    },
    agents: {
      execute: async (data) => ({ 
        status: 'success', 
        output: {},
        agentId: data.agentId,
        tenantId: data.tenantId,
      }),
      createWorkflow: async (data) => ({ id: `workflow-${Date.now()}`, ...data }),
      executeWorkflow: async (workflowId, input) => ({
        status: 'completed',
        workflowId,
        steps: [],
        results: [],
      }),
    },
    compliance: {
      runAudit: async (data) => ({ id: `audit-${Date.now()}`, status: 'running' }),
      getAuditStatus: async (auditId) => ({
        id: auditId,
        status: 'completed',
        findings: [],
        score: 85,
      }),
    },
    tenants: {
      create: async (data) => ({ id: `tenant-${Date.now()}`, ...data }),
    },
    onboarding: {
      completeStep: async (userId, step) => ({ completed: true, step }),
      getStatus: async (userId) => ({
        completed: true,
        steps: ['profile', 'workspace', 'first-project'],
      }),
    },
    scaffold: {
      execute: async (data) => ({
        completed: true,
        files: 15,
      }),
    },
    cicd: {
      createBuild: async (data) => ({ id: `build-${Date.now()}`, ...data }),
      triggerBuild: async (data) => ({ id: `build-${Date.now()}`, status: 'running' }),
      getBuildStatus: async (buildId) => ({ id: buildId, status: 'completed' }),
      deploy: async (data) => ({ id: `deploy-${Date.now()}`, status: 'deploying' }),
      getDeploymentStatus: async (deploymentId) => ({
        id: deploymentId,
        status: 'deployed',
        url: 'https://example.com',
        healthCheck: 'passing',
      }),
    },
    git: {
      createCommit: async (data) => ({ id: `commit-${Date.now()}`, ...data }),
    },
    security: {
      scanDependencies: async (data) => ({
        id: `scan-${Date.now()}`,
        vulnerabilities: data.mockVulnerabilities || [],
      }),
      applyFix: async (data) => ({ applied: true }),
    },
    featureFlags: {
      create: async (data) => ({ id: `flag-${Date.now()}`, ...data }),
      toggle: async (flagId, enabled) => ({ id: flagId, enabled }),
      get: async (flagId) => ({ id: flagId, enabled: true }),
      checkAccess: async (data) => {
        return ['user-1', 'user-2', 'user-3'].includes(data.userId);
      },
      evaluate: async (data) => ({ result: true }),
      getMetrics: async (flagId) => ({
        totalEvaluations: 100,
        enabledCount: 80,
        disabledCount: 20,
      }),
    },
    pipelines: {
      create: async (data) => ({ id: `pipeline-${Date.now()}`, ...data }),
      execute: async (pipelineId) => ({ id: `run-${Date.now()}`, status: 'running' }),
      getRunStatus: async (runId) => ({
        id: runId,
        status: 'completed',
        recordsProcessed: 1000,
        errors: [],
      }),
    },
    backup: {
      create: async (data) => ({ id: `backup-${Date.now()}`, status: 'scheduled' }),
      trigger: async (backupId) => ({ id: backupId, status: 'running' }),
      getStatus: async (backupId) => ({
        id: backupId,
        status: 'completed',
        size: 1024000,
        checksum: 'abc123',
      }),
      createFull: async (data) => ({ id: `backup-${Date.now()}`, status: 'running' }),
      restore: async (data) => ({ id: `restore-${Date.now()}`, status: 'running' }),
      getRestoreStatus: async (restoreId) => ({
        id: restoreId,
        status: 'completed',
        projectsRestored: ['proj-1'],
      }),
    },
    analytics: {
      trackEvent: async (data) => ({ tracked: true }),
      getReport: async (data) => ({
        total: 100,
        breakdown: {},
      }),
    },
    reporting: {
      createDashboard: async (data) => ({ id: `dashboard-${Date.now()}`, ...data }),
      getDashboardData: async (dashboardId) => ({
        kpis: [],
        charts: [],
      }),
    },
    traffic: {
      configureRouting: async (data) => ({ configured: true }),
      routeRequest: async (data) => ({
        region: data.origin?.lat > 45 ? 'eu-west-1' : 'us-east-1',
      }),
    },
  };

  const helpers: TestHelpers = {
    poll: async <T,>(
      fn: () => Promise<T>,
      condition: (result: T) => boolean,
      options: { timeout?: number; interval?: number } = {}
    ): Promise<T> => {
      const { timeout = 30000, interval = 1000 } = options;
      const startTime = Date.now();

      while (Date.now() - startTime < timeout) {
        const result = await fn();
        if (condition(result)) {
          return result;
        }
        await new Promise(resolve => setTimeout(resolve, interval));
      }

      throw new Error(`Polling timeout after ${timeout}ms`);
    },
  };

  const websocket: WebSocketMock = {
    connect: async (channel, userId) => ({
      send: async (message) => {},
      waitForMessage: async (filter) => ({ type: 'node-added', data: {} }),
      disconnect: async () => {},
    }),
  };

  return { api, helpers, websocket };
}

/**
 * Teardown test environment
 */
export async function teardownTestEnvironment(env: TestEnvironment): Promise<void> {
  // Cleanup logic if needed
  console.log('Test environment cleaned up');
}

// Set global test environment for integration tests
declare global {
  var testEnv: TestEnvironment | undefined;
}
