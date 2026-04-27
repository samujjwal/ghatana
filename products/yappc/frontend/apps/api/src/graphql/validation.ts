/**
 * GraphQL input validation schemas for YAPPC API.
 *
 * All GraphQL mutations validate their arguments through a Zod schema at the
 * boundary before any resolver logic runs. This ensures:
 *   - Type-safe argument access inside resolvers
 *   - Consistent error shapes surfaced as GraphQL BAD_USER_INPUT errors
 *   - A single source of truth for input constraints
 *
 * Usage in a resolver:
 * ```ts
 * import { validateInput, CreateWorkspaceSchema } from '../validation.js';
 *
 * async createWorkspace(_: unknown, args: unknown, ctx: ResolverContext) {
 *   const input = validateInput(CreateWorkspaceSchema, args);
 *   // input is now fully typed: { name: string; description?: string }
 * }
 * ```
 */

import { z } from 'zod';
import { GraphQLError } from 'graphql';

// ---------------------------------------------------------------------------
// Primitive reusable types
// ---------------------------------------------------------------------------

/** Non-empty CUID or UUID (GraphQL ID scalar). */
const IdField = z.string().min(1);

/** Non-empty trimmed string. */
const NonEmptyString = z.string().min(1).trim();

// ---------------------------------------------------------------------------
// Workspace Mutations
// ---------------------------------------------------------------------------

export const CreateWorkspaceSchema = z
  .object({
    name: z.string().min(1).max(100).trim(),
    description: z.string().max(500).optional(),
  })
  .strict();

export type CreateWorkspaceInput = z.infer<typeof CreateWorkspaceSchema>;

export const UpdateWorkspaceSchema = z
  .object({
    id: IdField,
    name: z.string().min(1).max(100).trim().optional(),
    description: z.string().max(500).optional(),
  })
  .strict();

export type UpdateWorkspaceInput = z.infer<typeof UpdateWorkspaceSchema>;

// ---------------------------------------------------------------------------
// Project Mutations
// ---------------------------------------------------------------------------

export const CreateProjectSchema = z
  .object({
    workspaceId: IdField,
    name: z.string().min(1).max(150).trim(),
    description: z.string().max(1000).optional(),
    type: z.string().optional(),
    visibility: z.enum(['private', 'public', 'internal']).optional(),
  })
  .strict();

export type CreateProjectInput = z.infer<typeof CreateProjectSchema>;

export const UpdateProjectSchema = z
  .object({
    id: IdField,
    name: z.string().min(1).max(150).trim().optional(),
    description: z.string().max(1000).optional(),
    type: z.string().optional(),
    visibility: z.enum(['private', 'public', 'internal']).optional(),
  })
  .strict();

export type UpdateProjectInput = z.infer<typeof UpdateProjectSchema>;

// ---------------------------------------------------------------------------
// Agent Run Mutations
// ---------------------------------------------------------------------------

export const StartAgentRunSchema = z
  .object({
    projectId: IdField,
    agentName: NonEmptyString,
    requirementId: IdField.optional(),
    input: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();

export type StartAgentRunInput = z.infer<typeof StartAgentRunSchema>;

export const UpdateAgentRunSchema = z
  .object({
    id: IdField,
    status: z
      .enum(['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'])
      .optional(),
    output: z.record(z.string(), z.unknown()).optional(),
    error: z.string().max(2000).optional(),
  })
  .strict();

export type UpdateAgentRunInput = z.infer<typeof UpdateAgentRunSchema>;

// ---------------------------------------------------------------------------
// Approval Mutations
// ---------------------------------------------------------------------------

export const ApproveRequirementSchema = z
  .object({
    approvalRequestId: IdField,
    reason: z.string().max(2000).optional(),
  })
  .strict();

export type ApproveRequirementInput = z.infer<typeof ApproveRequirementSchema>;

export const RejectRequirementSchema = z
  .object({
    approvalRequestId: IdField,
    reason: z.string().min(1).max(2000),
  })
  .strict();

export type RejectRequirementInput = z.infer<typeof RejectRequirementSchema>;

export const BulkApproveRejectSchema = z
  .object({
    requirementIds: z.array(IdField).min(1).max(200),
    reason: z.string().max(2000).optional(),
  })
  .strict();

export type BulkApproveRejectInput = z.infer<typeof BulkApproveRejectSchema>;

// ---------------------------------------------------------------------------
// Requirement Mutations
// ---------------------------------------------------------------------------

export const EnrichRequirementSchema = z
  .object({
    requirementId: IdField,
  })
  .strict();

export type EnrichRequirementInput = z.infer<typeof EnrichRequirementSchema>;

// ---------------------------------------------------------------------------
// Canvas Mutations
// ---------------------------------------------------------------------------

export const CreateCanvasDocumentSchema = z
  .object({
    projectId: IdField,
    title: z.string().min(1).max(200).trim(),
    content: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();

export type CreateCanvasDocumentInput = z.infer<
  typeof CreateCanvasDocumentSchema
>;

// ---------------------------------------------------------------------------
// Core validator function
// ---------------------------------------------------------------------------

/**
 * Parse and validate {@code input} against a Zod {@code schema}.
 *
 * On success returns the fully-typed parsed value.
 * On failure throws a {@link GraphQLError} with:
 *   - `extensions.code` = `"BAD_USER_INPUT"`
 *   - `extensions.issues` = the Zod issue array for field-level detail
 *
 * @param schema - Zod schema to validate against
 * @param input  - Raw resolver argument object (type `unknown`)
 * @returns Parsed and typed value
 * @throws GraphQLError on validation failure
 */
export function validateInput<T>(schema: z.ZodSchema<T>, input: unknown): T {
  const result = schema.safeParse(input);
  if (result.success) {
    return result.data;
  }
  throw new GraphQLError('Invalid input', {
    extensions: {
      code: 'BAD_USER_INPUT',
      issues: result.error.issues,
    },
  });
}
