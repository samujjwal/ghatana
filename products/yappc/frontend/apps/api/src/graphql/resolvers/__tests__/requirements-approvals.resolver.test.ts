/**
 * Requirements and Approvals Resolver Tests
 */

const mockPrisma = vi.hoisted(() => {
  const requirement = {
    findMany: vi.fn(),
    findUnique: vi.fn(),
    findUniqueOrThrow: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  };

  const requirementVersion = {
    create: vi.fn(),
  };

  const approvalRequest = {
    findMany: vi.fn(),
    findUniqueOrThrow: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  };

  const agentRun = {
    findMany: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  };

  return {
    requirement,
    requirementVersion,
    approvalRequest,
    agentRun,
    $transaction: vi.fn(),
  };
});

vi.mock('../../../db', () => ({
  default: mockPrisma,
}));

import { requirementsApprovalsResolvers } from '../requirements-approvals.resolver';

describe('requirementsApprovalsResolvers.Query.requirements', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists requirements for a project with status filter', async () => {
    mockPrisma.requirement.findMany.mockResolvedValueOnce([
      {
        id: 'req-1',
        title: 'Requirement',
        status: 'PENDING_APPROVAL',
        versions: [],
        approvalRequests: [],
        agentRuns: [],
      },
    ]);

    const result = await requirementsApprovalsResolvers.Query.requirements(
      undefined,
      { projectId: 'project-1', status: 'PENDING_APPROVAL' },
      { userId: 'user-1' }
    );

    expect(mockPrisma.requirement.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          projectId: 'project-1',
          status: 'PENDING_APPROVAL',
        },
      })
    );
    expect(result).toHaveLength(1);
  });

  it('throws when unauthenticated', async () => {
    await expect(
      requirementsApprovalsResolvers.Query.requirements(
        undefined,
        { projectId: 'project-1' },
        {}
      )
    ).rejects.toThrow('Authentication required');
  });
});

describe('requirementsApprovalsResolvers.Mutation.submitRequirement', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPrisma.$transaction.mockImplementation(async (fn: (tx: typeof mockPrisma) => Promise<string>) => {
      return fn(mockPrisma);
    });
  });

  it('creates requirement, initial version, and pending approval', async () => {
    mockPrisma.requirement.create.mockResolvedValueOnce({ id: 'req-1' });
    mockPrisma.requirement.findUniqueOrThrow.mockResolvedValueOnce({
      id: 'req-1',
      title: 'New requirement',
      versions: [],
      approvalRequests: [],
      agentRuns: [],
    });

    const result = await requirementsApprovalsResolvers.Mutation.submitRequirement(
      undefined,
      {
        projectId: 'project-1',
        title: 'New requirement',
        description: 'Description',
        requiresApproval: true,
      },
      { userId: 'user-1' }
    );

    expect(mockPrisma.requirement.create).toHaveBeenCalled();
    expect(mockPrisma.requirementVersion.create).toHaveBeenCalled();
    expect(mockPrisma.approvalRequest.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          requirementId: 'req-1',
          status: 'PENDING',
        }),
      })
    );
    expect(result.id).toBe('req-1');
  });
});

describe('requirementsApprovalsResolvers.Mutation.approveRequirement', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPrisma.$transaction.mockImplementation(async (fn: (tx: typeof mockPrisma) => Promise<unknown>) => {
      return fn(mockPrisma);
    });
  });

  it('approves pending request and updates requirement status', async () => {
    mockPrisma.approvalRequest.findUniqueOrThrow.mockResolvedValueOnce({
      id: 'approval-1',
      status: 'PENDING',
      requirementId: 'req-1',
    });
    mockPrisma.approvalRequest.update.mockResolvedValueOnce({
      id: 'approval-1',
      status: 'APPROVED',
    });

    const result = await requirementsApprovalsResolvers.Mutation.approveRequirement(
      undefined,
      { approvalRequestId: 'approval-1', reason: 'Looks good' },
      { userId: 'reviewer-1' }
    );

    expect(mockPrisma.approvalRequest.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          status: 'APPROVED',
          reviewerId: 'reviewer-1',
          decisionReason: 'Looks good',
        }),
      })
    );
    expect(mockPrisma.requirement.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'req-1' },
        data: { status: 'APPROVED' },
      })
    );
    expect(result).toEqual({ id: 'approval-1', status: 'APPROVED' });
  });
});

// ── AI-Y2: enrichRequirement — hybrid LLM + rule-fallback ─────────────────────

describe('requirementsApprovalsResolvers.Mutation.enrichRequirement', () => {
  const ctx = { userId: 'user-enricher' };

  const baseRequirement = {
    id: 'req-enrich-1',
    projectId: 'proj-1',
    title: 'User login via SSO',
    description: 'Authenticate users with corporate SSO using SAML 2.0 tokens.',
    status: 'DRAFT',
    versions: [{ version: 1 }],
    approvalRequests: [],
    agentRuns: [],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllEnvs();
    vi.stubGlobal('fetch', vi.fn());
    mockPrisma.$transaction.mockImplementation(
      async (fn: (tx: typeof mockPrisma) => Promise<unknown>) => fn(mockPrisma)
    );
    mockPrisma.agentRun.create.mockResolvedValue({ id: 'run-1' });
    mockPrisma.requirementVersion.create.mockResolvedValue({});
    mockPrisma.requirement.update.mockResolvedValue({});
    mockPrisma.agentRun.update.mockResolvedValue({});
    mockPrisma.requirement.findUniqueOrThrow
      .mockResolvedValueOnce(baseRequirement)   // guard read
      .mockResolvedValueOnce({ ...baseRequirement, status: 'PENDING_APPROVAL' }); // final return
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses LLM (MODEL) when OPENAI_API_KEY is set and API succeeds', async () => {
    vi.stubEnv('OPENAI_API_KEY', 'sk-test-key');

    const llmPayload = {
      normalizedTitle: 'Authenticate users via SSO',
      acceptanceCriteria: ['Given a valid SAML token, user is authenticated'],
      storyTrace: 'As a user, I want to log in via SSO so that I can access the platform.',
      confidence: 0.92,
      rationale: 'SSO/auth keywords detected; SAML specifics extracted.',
    };

    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        choices: [{ message: { role: 'assistant', content: JSON.stringify(llmPayload) }, finish_reason: 'stop' }],
      }),
    } as Response);

    await requirementsApprovalsResolvers.Mutation.enrichRequirement(
      undefined,
      { requirementId: 'req-enrich-1' },
      ctx
    );

    // AgentRun output recorded with MODEL source
    expect(mockPrisma.agentRun.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          output: expect.objectContaining({ source: 'MODEL', confidence: 0.92 }),
        }),
      })
    );

    // Version metadata also carries MODEL
    expect(mockPrisma.requirementVersion.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          metadata: expect.objectContaining({ source: 'MODEL' }),
        }),
      })
    );
  });

  it('falls back to RULE when OPENAI_API_KEY is absent', async () => {
    // No API key stubbed → callLlmForEnrichment throws immediately
    vi.mocked(fetch).mockRejectedValueOnce(new Error('should not be called'));

    await requirementsApprovalsResolvers.Mutation.enrichRequirement(
      undefined,
      { requirementId: 'req-enrich-1' },
      ctx
    );

    expect(mockPrisma.agentRun.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          output: expect.objectContaining({ source: 'RULE' }),
        }),
      })
    );
  });

  it('falls back to RULE when LLM returns non-ok HTTP status', async () => {
    vi.stubEnv('OPENAI_API_KEY', 'sk-test-key');

    vi.mocked(fetch).mockResolvedValueOnce({
      ok: false,
      status: 429,
      statusText: 'Too Many Requests',
      json: async () => ({}),
    } as Response);

    await requirementsApprovalsResolvers.Mutation.enrichRequirement(
      undefined,
      { requirementId: 'req-enrich-1' },
      ctx
    );

    expect(mockPrisma.agentRun.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          output: expect.objectContaining({ source: 'RULE' }),
        }),
      })
    );
  });

  it('falls back to RULE when LLM returns malformed JSON', async () => {
    vi.stubEnv('OPENAI_API_KEY', 'sk-test-key');

    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        choices: [{ message: { role: 'assistant', content: 'not-valid-json' }, finish_reason: 'stop' }],
      }),
    } as Response);

    await requirementsApprovalsResolvers.Mutation.enrichRequirement(
      undefined,
      { requirementId: 'req-enrich-1' },
      ctx
    );

    expect(mockPrisma.agentRun.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          output: expect.objectContaining({ source: 'RULE' }),
        }),
      })
    );
  });

  it('throws when unauthenticated', async () => {
    await expect(
      requirementsApprovalsResolvers.Mutation.enrichRequirement(
        undefined,
        { requirementId: 'req-enrich-1' },
        {}
      )
    ).rejects.toThrow('Authentication required');
  });
});
