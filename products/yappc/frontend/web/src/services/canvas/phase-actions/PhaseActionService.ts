/**
 * Phase Action Service
 * 
 * Service for executing phase-specific actions by calling backend APIs.
 * Each handler corresponds to a phase action defined in phase-actions.ts.
 * 
 * @doc.type service
 * @doc.purpose Execute phase-specific actions via backend APIs
 * @doc.layer product
 * @doc.pattern Service
 */

import type { ActionContext } from '@ghatana/canvas';

// API base URL - uses VITE_API_ORIGIN for single-port architecture
const API_BASE_URL = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api/v1`
  : '/api/v1';

class APIError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string
  ) {
    super(message);
    this.name = 'APIError';
  }
}

async function fetchAPI<T>(
  endpoint: string,
  options?: RequestInit
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new APIError(
      error.message || 'API request failed',
      response.status,
      error.code
    );
  }

  return response.json();
}

// ============================================================================
// Intent Phase Handlers
// ============================================================================

export async function handleCreateVision(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/intent/capture', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'vision',
      description: 'Vision statement for the project',
    }),
  });
}

export async function handleAddUserStory(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/intent/capture', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'user-story',
      description: 'User story for the project',
    }),
  });
}

export async function handleDefineRequirement(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/intent/capture', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'requirement',
      description: 'Functional or non-functional requirement',
    }),
  });
}

export async function handleAddStakeholder(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/intent/capture', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'stakeholder',
      description: 'Project stakeholder information',
    }),
  });
}

export async function handleCreateGoal(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/intent/capture', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'goal',
      description: 'Project goal',
    }),
  });
}

// ============================================================================
// Shape Phase Handlers
// ============================================================================

export async function handleCreateDiagram(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/shape/derive', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'architecture-diagram',
      description: 'Architecture diagram',
    }),
  });
}

export async function handleAddService(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/shape/derive', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'service',
      description: 'Service definition',
    }),
  });
}

export async function handleDefineApiContract(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/shape/derive', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'api-contract',
      description: 'API contract definition',
    }),
  });
}

export async function handleAddDataModel(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/shape/model', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'data-model',
      description: 'Data model definition',
    }),
  });
}

export async function handleCreateComponent(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/shape/derive', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'component',
      description: 'Component design',
    }),
  });
}

// ============================================================================
// Validate Phase Handlers
// ============================================================================

export async function handleAddValidationRule(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/validate', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'validation-rule',
      description: 'Validation rule',
    }),
  });
}

export async function handleCreateTestCase(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/validate', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'test-case',
      description: 'Test case',
    }),
  });
}

export async function handleAddAcceptanceCriteria(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/validate', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'acceptance-criteria',
      description: 'Acceptance criteria',
    }),
  });
}

// ============================================================================
// Generate Phase Handlers
// ============================================================================

export async function handleGenerateCode(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/generate', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'code-generation',
      description: 'Generate code from design',
    }),
  });
}

export async function handleCreateScaffold(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/generate', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'scaffold',
      description: 'Create project scaffold',
    }),
  });
}

export async function handleGenerateTests(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/yappc/generate', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'test-generation',
      description: 'Generate test files',
    }),
  });
}

// ============================================================================
// Run Phase Handlers
// ============================================================================

export async function handleDeployService(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  // Deployment operations - for now, create an artifact
  await fetchAPI('/artifacts', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'deployment-script',
      title: 'Service Deployment',
      description: 'Deploy service to environment',
      phase: 'RUN',
      status: 'draft',
    }),
  });
}

export async function handleExecuteTests(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  // Test execution - for now, create an artifact
  await fetchAPI('/artifacts', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'test-results',
      title: 'Test Execution',
      description: 'Execute test suite',
      phase: 'RUN',
      status: 'draft',
    }),
  });
}

export async function handleMonitorLogs(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  // Log monitoring - for now, create an artifact
  await fetchAPI('/artifacts', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'log-monitoring',
      title: 'Log Monitoring',
      description: 'Monitor application logs',
      phase: 'RUN',
      status: 'draft',
    }),
  });
}

// ============================================================================
// Improve Phase Handlers
// ============================================================================

export async function handleCreateEnhancement(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/artifacts', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'enhancement',
      title: 'Enhancement Proposal',
      description: 'Create enhancement proposal',
      phase: 'IMPROVE',
      status: 'draft',
    }),
  });
}

export async function handleRefactorCode(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  // Refactoring - for now, create an artifact
  await fetchAPI('/artifacts', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'refactor',
      title: 'Code Refactoring',
      description: 'Refactor existing code',
      phase: 'IMPROVE',
      status: 'draft',
    }),
  });
}

export async function handleAddFeature(ctx: ActionContext): Promise<void> {
  const { projectId } = ctx;
  if (!projectId) throw new Error('Project ID required');

  await fetchAPI('/artifacts', {
    method: 'POST',
    body: JSON.stringify({
      projectId,
      type: 'feature',
      title: 'New Feature',
      description: 'Add new feature',
      phase: 'IMPROVE',
      status: 'draft',
    }),
  });
}

// ============================================================================
// Handler Registry
// ============================================================================

export const PHASE_ACTION_HANDLERS: Record<string, (ctx: ActionContext) => Promise<void>> = {
  // Intent Phase
  'intent-create-vision': handleCreateVision,
  'intent-add-user-story': handleAddUserStory,
  'intent-define-requirement': handleDefineRequirement,
  'intent-add-stakeholder': handleAddStakeholder,
  'intent-create-goal': handleCreateGoal,

  // Shape Phase
  'shape-create-diagram': handleCreateDiagram,
  'shape-add-service': handleAddService,
  'shape-define-api-contract': handleDefineApiContract,
  'shape-add-data-model': handleAddDataModel,
  'shape-create-component': handleCreateComponent,

  // Validate Phase
  'validate-add-rule': handleAddValidationRule,
  'validate-create-test-case': handleCreateTestCase,
  'validate-add-acceptance-criteria': handleAddAcceptanceCriteria,

  // Generate Phase
  'generate-code': handleGenerateCode,
  'generate-create-scaffold': handleCreateScaffold,
  'generate-tests': handleGenerateTests,

  // Run Phase
  'run-deploy-service': handleDeployService,
  'run-execute-tests': handleExecuteTests,
  'run-monitor-logs': handleMonitorLogs,

  // Improve Phase
  'improve-create-enhancement': handleCreateEnhancement,
  'improve-refactor-code': handleRefactorCode,
  'improve-add-feature': handleAddFeature,
};
