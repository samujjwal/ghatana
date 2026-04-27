/**
 * Workflow Resolver Tests
 *
 * Covers: workflow / workflows / myWorkflows / workflowTemplates queries,
 * createWorkflow, updateStepData, deleteWorkflow mutations, and field resolvers.
 */

// ---------------------------------------------------------------------------
// Database mock — all sub-mocks inside a single vi.hoisted() call so that
// references are resolved before any module under test is imported.
// ---------------------------------------------------------------------------

const { mockWorkflow, mockWorkflowTemplate, mockWorkflowContributor, mockWorkflowAudit, mockPrisma } =
  vi.hoisted(() => {
    const wf = {
      findUnique: vi.fn(),
      findMany: vi.fn(),
      count: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    };
    const wfTemplate = {
      findUnique: vi.fn(),
      findMany: vi.fn(),
      create: vi.fn(),
    };
    const wfContributor = {
      create: vi.fn(),
      deleteMany: vi.fn(),
      findMany: vi.fn(),
    };
    const wfAudit = { findMany: vi.fn() };
    const prisma = {
      workflow: wf,
      workflowTemplate: wfTemplate,
      workflowContributor: wfContributor,
      workflowAudit: wfAudit,
    };
    return {
      mockWorkflow: wf,
      mockWorkflowTemplate: wfTemplate,
      mockWorkflowContributor: wfContributor,
      mockWorkflowAudit: wfAudit,
      mockPrisma: prisma,
    };
  });

vi.mock('../../../database/client', () => ({
  getPrismaClient: () => mockPrisma,
}));

// ---------------------------------------------------------------------------
// Import subject under test
// ---------------------------------------------------------------------------

import { workflowResolvers } from '../workflow.resolver';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface TestContext {
  userId: string;
  userName: string;
}

function makeContext(overrides?: Partial<TestContext>): TestContext {
  return {
    userId: 'user-1',
    userName: 'Alice',
    ...overrides,
  };
}

function makeWorkflow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'wf-1',
    name: 'Feature Workflow',
    description: 'A test workflow',
    workflowType: 'FEATURE',
    status: 'DRAFT',
    aiMode: 'ASSIST',
    ownerId: 'user-1',
    ownerName: 'Alice',
    projectId: 'proj-1',
    templateId: null,
    steps: {
      intent: { status: 'NOT_STARTED', data: { intentType: 'FEATURE', goal: '', successCriteria: [] }, revisitCount: 0 },
      context: { status: 'NOT_STARTED', data: { systemsImpacted: [], constraints: [], references: [] }, revisitCount: 0 },
      plan: { status: 'NOT_STARTED', data: { selectedPlan: [], alternatives: [], riskAssessment: { level: 'LOW', factors: [], mitigations: [], rollbackPlan: '' } }, revisitCount: 0 },
      execute: { status: 'NOT_STARTED', data: { changes: [], executors: [], artifacts: [] }, revisitCount: 0 },
      verify: { status: 'NOT_STARTED', data: { verificationStatus: 'PENDING', evidence: [], acceptanceChecklist: [] }, revisitCount: 0 },
      observe: { status: 'NOT_STARTED', data: { metricsDelta: { before: {}, after: {}, percentChange: {} }, anomalies: [], observationWindow: { startedAt: new Date().toISOString(), durationHours: 24, status: 'ACTIVE' } }, revisitCount: 0 },
      learn: { status: 'NOT_STARTED', data: { lessons: [], rootCauses: [] }, revisitCount: 0 },
      institutionalize: { status: 'NOT_STARTED', data: { institutionalActions: [], owners: [] }, revisitCount: 0 },
    },
    metrics: { stepDurations: {}, revisitCount: 0, aiSuggestionsAccepted: 0, aiSuggestionsRejected: 0, blockedCount: 0 },
    contributors: [],
    auditEntries: [],
    template: null,
    createdAt: new Date('2026-04-01'),
    updatedAt: new Date('2026-04-01'),
    ...overrides,
  };
}

function makeTemplate(overrides: Record<string, unknown> = {}) {
  return {
    id: 'tmpl-1',
    name: 'Feature Template',
    description: 'Standard feature workflow',
    workflowType: 'FEATURE',
    defaultIntent: { intentType: 'FEATURE', goal: 'Deliver new feature', successCriteria: [] },
    requiredFields: {},
    defaultRisks: [],
    defaultMetrics: [],
    isSystem: true,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Query: workflow
// ---------------------------------------------------------------------------

describe('workflowResolvers.Query.workflow', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('returns a workflow by id with includes', async () => {
    const wf = makeWorkflow();
    mockWorkflow.findUnique.mockResolvedValueOnce(wf);

    const result = await workflowResolvers.Query.workflow(undefined, { id: 'wf-1' }, makeContext());

    expect(result).toEqual(wf);
    expect(mockWorkflow.findUnique).toHaveBeenCalledWith({
      where: { id: 'wf-1' },
      include: {
        contributors: true,
        auditEntries: { orderBy: { timestamp: 'desc' } },
        template: true,
      },
    });
  });

  it('returns null for unknown workflow id', async () => {
    mockWorkflow.findUnique.mockResolvedValueOnce(null);
    const result = await workflowResolvers.Query.workflow(undefined, { id: 'unknown' }, makeContext());
    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Query: workflows
// ---------------------------------------------------------------------------

describe('workflowResolvers.Query.workflows', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('returns paginated list with total and hasMore=false when all fit', async () => {
    const wfList = [makeWorkflow()];
    mockWorkflow.findMany.mockResolvedValueOnce(wfList);
    mockWorkflow.count.mockResolvedValueOnce(1);

    const result = await workflowResolvers.Query.workflows(
      undefined,
      {},
      makeContext()
    );

    expect(result).toEqual({ workflows: wfList, total: 1, hasMore: false });
  });

  it('hasMore is true when more items exist beyond current page', async () => {
    const wfList = [makeWorkflow(), makeWorkflow({ id: 'wf-2' })];
    mockWorkflow.findMany.mockResolvedValueOnce(wfList);
    mockWorkflow.count.mockResolvedValueOnce(10);

    const result = await workflowResolvers.Query.workflows(
      undefined,
      { pagination: { limit: 2, offset: 0 } },
      makeContext()
    );

    expect(result.hasMore).toBe(true);
    expect(result.total).toBe(10);
  });

  it('applies workflowType filter', async () => {
    mockWorkflow.findMany.mockResolvedValueOnce([]);
    mockWorkflow.count.mockResolvedValueOnce(0);

    await workflowResolvers.Query.workflows(
      undefined,
      { filter: { workflowType: 'BUG_FIX' } },
      makeContext()
    );

    const callArgs = mockWorkflow.findMany.mock.calls[0]?.[0] as { where: Record<string, unknown> };
    expect(callArgs?.where.workflowType).toBe('BUG_FIX');
  });
});

// ---------------------------------------------------------------------------
// Query: myWorkflows
// ---------------------------------------------------------------------------

describe('workflowResolvers.Query.myWorkflows', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('scopes to the authenticated user', async () => {
    mockWorkflow.findMany.mockResolvedValueOnce([]);
    mockWorkflow.count.mockResolvedValueOnce(0);

    await workflowResolvers.Query.myWorkflows(undefined, {}, makeContext({ userId: 'user-42' }));

    const callArgs = mockWorkflow.findMany.mock.calls[0]?.[0] as { where: Record<string, unknown> };
    expect(callArgs?.where.ownerId).toBe('user-42');
  });
});

// ---------------------------------------------------------------------------
// Query: workflowTemplates
// ---------------------------------------------------------------------------

describe('workflowResolvers.Query.workflowTemplates', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('returns all templates when no filter given', async () => {
    const templates = [makeTemplate()];
    mockWorkflowTemplate.findMany.mockResolvedValueOnce(templates);

    const result = await workflowResolvers.Query.workflowTemplates(undefined, {}, makeContext());

    expect(result).toEqual(templates);
    const callArgs = mockWorkflowTemplate.findMany.mock.calls[0]?.[0] as { where: Record<string, unknown> };
    expect(Object.keys(callArgs?.where ?? {})).toHaveLength(0);
  });

  it('filters by workflowType when provided', async () => {
    mockWorkflowTemplate.findMany.mockResolvedValueOnce([]);

    await workflowResolvers.Query.workflowTemplates(undefined, { workflowType: 'BUG_FIX' }, makeContext());

    const callArgs = mockWorkflowTemplate.findMany.mock.calls[0]?.[0] as { where: Record<string, unknown> };
    expect(callArgs?.where.workflowType).toBe('BUG_FIX');
  });
});

// ---------------------------------------------------------------------------
// Mutation: createWorkflow
// ---------------------------------------------------------------------------

describe('workflowResolvers.Mutation.createWorkflow', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('creates a workflow with default steps and metrics', async () => {
    const wf = makeWorkflow();
    mockWorkflow.create.mockResolvedValueOnce(wf);

    const result = await workflowResolvers.Mutation.createWorkflow(
      undefined,
      { input: { name: 'Feature Workflow', workflowType: 'FEATURE' } },
      makeContext()
    );

    expect(result).toEqual(wf);
    const createData = mockWorkflow.create.mock.calls[0]?.[0] as { data: Record<string, unknown> };
    expect(createData?.data.ownerId).toBe('user-1');
    expect(createData?.data.ownerName).toBe('Alice');
    expect(createData?.data.name).toBe('Feature Workflow');
    expect(createData?.data.steps).toBeDefined();
    expect(createData?.data.metrics).toBeDefined();
  });

  it('merges template intent into default steps when templateId is provided', async () => {
    const template = makeTemplate();
    mockWorkflowTemplate.findUnique.mockResolvedValueOnce(template);
    const wf = makeWorkflow({ templateId: 'tmpl-1' });
    mockWorkflow.create.mockResolvedValueOnce(wf);

    await workflowResolvers.Mutation.createWorkflow(
      undefined,
      { input: { name: 'Templated Workflow', workflowType: 'FEATURE', templateId: 'tmpl-1' } },
      makeContext()
    );

    expect(mockWorkflowTemplate.findUnique).toHaveBeenCalledWith({ where: { id: 'tmpl-1' } });
    const createData = mockWorkflow.create.mock.calls[0]?.[0] as { data: Record<string, unknown> };
    const steps = createData?.data.steps as Record<string, { data: Record<string, unknown> }>;
    // Template defaultIntent goal should be merged
    expect(steps.intent.data.goal).toBe('Deliver new feature');
  });

  it('creates an audit entry for CREATED action', async () => {
    const wf = makeWorkflow();
    mockWorkflow.create.mockResolvedValueOnce(wf);

    await workflowResolvers.Mutation.createWorkflow(
      undefined,
      { input: { name: 'Audit Test Workflow', workflowType: 'FEATURE' } },
      makeContext()
    );

    const createData = mockWorkflow.create.mock.calls[0]?.[0] as {
      data: { auditEntries: { create: { action: string } } };
    };
    expect(createData?.data.auditEntries.create.action).toBe('CREATED');
  });
});

// ---------------------------------------------------------------------------
// Mutation: updateStepData
// ---------------------------------------------------------------------------

describe('workflowResolvers.Mutation.updateStepData', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('throws when workflow is not found', async () => {
    mockWorkflow.findUnique.mockResolvedValueOnce(null);

    await expect(
      workflowResolvers.Mutation.updateStepData(
        undefined,
        { workflowId: 'missing', input: { step: 'INTENT', data: { goal: 'test' } } },
        makeContext()
      )
    ).rejects.toThrow('Workflow not found');
  });

  it('transitions step from NOT_STARTED to IN_PROGRESS', async () => {
    const wf = makeWorkflow();
    mockWorkflow.findUnique.mockResolvedValueOnce(wf);
    const updatedWf = makeWorkflow();
    mockWorkflow.update.mockResolvedValueOnce(updatedWf);

    await workflowResolvers.Mutation.updateStepData(
      undefined,
      { workflowId: 'wf-1', input: { step: 'INTENT', data: { goal: 'Build feature X' } } },
      makeContext()
    );

    const updateData = mockWorkflow.update.mock.calls[0]?.[0] as {
      data: { steps: Record<string, { status: string; data: unknown }> };
    };
    expect(updateData?.data.steps.intent.status).toBe('IN_PROGRESS');
    expect(updateData?.data.steps.intent.data).toEqual({ goal: 'Build feature X' });
  });

  it('preserves step status when already IN_PROGRESS', async () => {
    const wf = makeWorkflow({
      steps: {
        intent: { status: 'IN_PROGRESS', data: { intentType: 'FEATURE', goal: '', successCriteria: [] }, revisitCount: 0 },
        context: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
        plan: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
        execute: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
        verify: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
        observe: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
        learn: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
        institutionalize: { status: 'NOT_STARTED', data: {}, revisitCount: 0 },
      },
    });
    mockWorkflow.findUnique.mockResolvedValueOnce(wf);
    mockWorkflow.update.mockResolvedValueOnce(makeWorkflow());

    await workflowResolvers.Mutation.updateStepData(
      undefined,
      { workflowId: 'wf-1', input: { step: 'INTENT', data: { goal: 'Updated' } } },
      makeContext()
    );

    const updateData = mockWorkflow.update.mock.calls[0]?.[0] as {
      data: { steps: Record<string, { status: string }> };
    };
    // Already IN_PROGRESS — must not regress to NOT_STARTED
    expect(updateData?.data.steps.intent.status).toBe('IN_PROGRESS');
  });

  it('transitions workflow status from DRAFT to IN_PROGRESS', async () => {
    const wf = makeWorkflow({ status: 'DRAFT' });
    mockWorkflow.findUnique.mockResolvedValueOnce(wf);
    mockWorkflow.update.mockResolvedValueOnce(makeWorkflow());

    await workflowResolvers.Mutation.updateStepData(
      undefined,
      { workflowId: 'wf-1', input: { step: 'INTENT', data: {} } },
      makeContext()
    );

    const updateData = mockWorkflow.update.mock.calls[0]?.[0] as {
      data: { status: string };
    };
    expect(updateData?.data.status).toBe('IN_PROGRESS');
  });
});

// ---------------------------------------------------------------------------
// Mutation: deleteWorkflow
// ---------------------------------------------------------------------------

describe('workflowResolvers.Mutation.deleteWorkflow', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('calls prisma delete and returns true', async () => {
    mockWorkflow.delete.mockResolvedValueOnce({ id: 'wf-1' });

    const result = await workflowResolvers.Mutation.deleteWorkflow(
      undefined,
      { id: 'wf-1' },
      makeContext()
    );

    expect(result).toBe(true);
    expect(mockWorkflow.delete).toHaveBeenCalledWith({ where: { id: 'wf-1' } });
  });
});
