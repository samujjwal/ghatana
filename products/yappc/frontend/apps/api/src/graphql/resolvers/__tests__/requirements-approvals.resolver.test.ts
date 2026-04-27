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
