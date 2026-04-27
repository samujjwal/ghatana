import { describe, it, expect } from 'vitest';
import { GraphQLError } from 'graphql';
import {
  validateInput,
  CreateWorkspaceSchema,
  UpdateWorkspaceSchema,
  CreateProjectSchema,
  StartAgentRunSchema,
  UpdateAgentRunSchema,
  ApproveRequirementSchema,
  RejectRequirementSchema,
  BulkApproveRejectSchema,
  EnrichRequirementSchema,
  CreateCanvasDocumentSchema,
} from '../validation.js';

// ---------------------------------------------------------------------------
// validateInput helper
// ---------------------------------------------------------------------------

describe('validateInput', () => {
  it('returns parsed value on valid input', () => {
    const result = validateInput(CreateWorkspaceSchema, {
      name: 'My Workspace',
    });
    expect(result).toEqual({ name: 'My Workspace' });
  });

  it('throws GraphQLError on invalid input', () => {
    expect(() => validateInput(CreateWorkspaceSchema, { name: '' })).toThrow(
      GraphQLError
    );
  });

  it('throws with code BAD_USER_INPUT', () => {
    try {
      validateInput(CreateWorkspaceSchema, { name: '' });
    } catch (err) {
      expect(err).toBeInstanceOf(GraphQLError);
      const gqlErr = err as GraphQLError;
      expect(gqlErr.extensions?.['code']).toBe('BAD_USER_INPUT');
    }
  });

  it('includes Zod issues in extensions', () => {
    try {
      validateInput(CreateWorkspaceSchema, { name: '' });
    } catch (err) {
      const gqlErr = err as GraphQLError;
      expect(Array.isArray(gqlErr.extensions?.['issues'])).toBe(true);
    }
  });

  it('rejects unknown extra fields (strict mode)', () => {
    expect(() =>
      validateInput(CreateWorkspaceSchema, {
        name: 'Acme',
        unknownField: 'oops',
      })
    ).toThrow(GraphQLError);
  });

  it('strips and trims the name field', () => {
    const result = validateInput(CreateWorkspaceSchema, { name: '  Trimmed  ' });
    expect(result.name).toBe('Trimmed');
  });
});

// ---------------------------------------------------------------------------
// Workspace schemas
// ---------------------------------------------------------------------------

describe('CreateWorkspaceSchema', () => {
  it('accepts valid name', () => {
    const result = validateInput(CreateWorkspaceSchema, { name: 'Workspace A' });
    expect(result.name).toBe('Workspace A');
  });

  it('accepts optional description', () => {
    const result = validateInput(CreateWorkspaceSchema, {
      name: 'WS',
      description: 'Some description',
    });
    expect(result.description).toBe('Some description');
  });

  it('rejects empty name', () => {
    expect(() =>
      validateInput(CreateWorkspaceSchema, { name: '' })
    ).toThrow(GraphQLError);
  });

  it('rejects name exceeding 100 chars', () => {
    expect(() =>
      validateInput(CreateWorkspaceSchema, { name: 'x'.repeat(101) })
    ).toThrow(GraphQLError);
  });
});

describe('UpdateWorkspaceSchema', () => {
  it('accepts id with optional fields', () => {
    const result = validateInput(UpdateWorkspaceSchema, {
      id: 'ws-123',
      name: 'Updated',
    });
    expect(result.id).toBe('ws-123');
    expect(result.name).toBe('Updated');
  });

  it('rejects missing id', () => {
    expect(() =>
      validateInput(UpdateWorkspaceSchema, { name: 'New name' })
    ).toThrow(GraphQLError);
  });
});

// ---------------------------------------------------------------------------
// Project schemas
// ---------------------------------------------------------------------------

describe('CreateProjectSchema', () => {
  it('accepts valid project creation args', () => {
    const result = validateInput(CreateProjectSchema, {
      workspaceId: 'ws-1',
      name: 'Project Alpha',
    });
    expect(result.workspaceId).toBe('ws-1');
    expect(result.name).toBe('Project Alpha');
  });

  it('accepts valid visibility values', () => {
    for (const vis of ['private', 'public', 'internal'] as const) {
      const result = validateInput(CreateProjectSchema, {
        workspaceId: 'ws-1',
        name: 'P',
        visibility: vis,
      });
      expect(result.visibility).toBe(vis);
    }
  });

  it('rejects invalid visibility', () => {
    expect(() =>
      validateInput(CreateProjectSchema, {
        workspaceId: 'ws-1',
        name: 'P',
        visibility: 'secret',
      })
    ).toThrow(GraphQLError);
  });
});

// ---------------------------------------------------------------------------
// Agent Run schemas
// ---------------------------------------------------------------------------

describe('StartAgentRunSchema', () => {
  it('accepts minimal valid args', () => {
    const result = validateInput(StartAgentRunSchema, {
      projectId: 'proj-1',
      agentName: 'CodeGenAgent',
    });
    expect(result.projectId).toBe('proj-1');
    expect(result.agentName).toBe('CodeGenAgent');
  });

  it('accepts optional requirementId and input', () => {
    const result = validateInput(StartAgentRunSchema, {
      projectId: 'proj-1',
      agentName: 'Analyzer',
      requirementId: 'req-99',
      input: { mode: 'deep' },
    });
    expect(result.requirementId).toBe('req-99');
    expect(result.input).toEqual({ mode: 'deep' });
  });

  it('rejects empty agentName', () => {
    expect(() =>
      validateInput(StartAgentRunSchema, {
        projectId: 'proj-1',
        agentName: '',
      })
    ).toThrow(GraphQLError);
  });
});

describe('UpdateAgentRunSchema', () => {
  it('accepts valid status update', () => {
    const result = validateInput(UpdateAgentRunSchema, {
      id: 'run-1',
      status: 'COMPLETED',
    });
    expect(result.status).toBe('COMPLETED');
  });

  it('rejects invalid status', () => {
    expect(() =>
      validateInput(UpdateAgentRunSchema, { id: 'run-1', status: 'UNKNOWN' })
    ).toThrow(GraphQLError);
  });
});

// ---------------------------------------------------------------------------
// Approval schemas
// ---------------------------------------------------------------------------

describe('ApproveRequirementSchema', () => {
  it('accepts valid approval with optional reason', () => {
    const result = validateInput(ApproveRequirementSchema, {
      approvalRequestId: 'apr-1',
      reason: 'LGTM',
    });
    expect(result.approvalRequestId).toBe('apr-1');
  });
});

describe('RejectRequirementSchema', () => {
  it('accepts valid rejection with required reason', () => {
    const result = validateInput(RejectRequirementSchema, {
      approvalRequestId: 'apr-1',
      reason: 'Not ready',
    });
    expect(result.reason).toBe('Not ready');
  });

  it('rejects empty reason', () => {
    expect(() =>
      validateInput(RejectRequirementSchema, {
        approvalRequestId: 'apr-1',
        reason: '',
      })
    ).toThrow(GraphQLError);
  });
});

describe('BulkApproveRejectSchema', () => {
  it('accepts array of requirement IDs', () => {
    const result = validateInput(BulkApproveRejectSchema, {
      requirementIds: ['r1', 'r2', 'r3'],
    });
    expect(result.requirementIds).toHaveLength(3);
  });

  it('rejects empty requirementIds array', () => {
    expect(() =>
      validateInput(BulkApproveRejectSchema, { requirementIds: [] })
    ).toThrow(GraphQLError);
  });

  it('rejects arrays exceeding 200 items', () => {
    expect(() =>
      validateInput(BulkApproveRejectSchema, {
        requirementIds: Array.from({ length: 201 }, (_, i) => `r${i}`),
      })
    ).toThrow(GraphQLError);
  });
});

// ---------------------------------------------------------------------------
// Other mutations
// ---------------------------------------------------------------------------

describe('EnrichRequirementSchema', () => {
  it('accepts valid requirementId', () => {
    const result = validateInput(EnrichRequirementSchema, {
      requirementId: 'req-42',
    });
    expect(result.requirementId).toBe('req-42');
  });
});

describe('CreateCanvasDocumentSchema', () => {
  it('accepts valid canvas document args', () => {
    const result = validateInput(CreateCanvasDocumentSchema, {
      projectId: 'proj-1',
      title: 'Architecture Canvas',
    });
    expect(result.title).toBe('Architecture Canvas');
  });

  it('rejects empty title', () => {
    expect(() =>
      validateInput(CreateCanvasDocumentSchema, {
        projectId: 'proj-1',
        title: '',
      })
    ).toThrow(GraphQLError);
  });
});
