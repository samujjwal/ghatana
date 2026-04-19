/**
 * RequirementConfig Schema
 *
 * Declarative configuration for structured requirements with acceptance criteria.
 *
 * @packageDocumentation
 */
import { z } from 'zod';
/**
 * AcceptanceCriteria schema
 */
export const AcceptanceCriteriaSchema = z.object({
    id: z.string(),
    criteria: z.string().min(1),
    priority: z.enum(['must', 'should', 'could']),
    status: z.enum(['pending', 'in-progress', 'completed', 'blocked']),
});
/**
 * RequirementConfig schema - structured requirements with acceptance criteria
 */
export const RequirementConfigSchema = z.object({
    id: z.string(),
    version: z.string(),
    // Requirement content
    title: z.string().min(1),
    description: z.string().min(1),
    // Classification
    type: z.enum(['functional', 'non-functional', 'constraint', 'user-story']),
    priority: z.enum(['critical', 'high', 'medium', 'low']),
    status: z.enum(['draft', 'proposed', 'approved', 'in-progress', 'completed', 'rejected']),
    // Acceptance criteria
    acceptanceCriteria: z.array(AcceptanceCriteriaSchema),
    // Intent linkage
    intentId: z.string().optional(),
    // Linked artifacts
    linkedPageIds: z.array(z.string()),
    linkedComponentIds: z.array(z.string()),
    // Metadata
    createdAt: z.string(),
    updatedAt: z.string(),
    author: z.string(),
    assignee: z.string().optional(),
    tags: z.array(z.string()),
}).strict();
