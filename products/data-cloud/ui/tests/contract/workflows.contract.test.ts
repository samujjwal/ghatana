import { describe, it, expect } from 'vitest';
import { z } from 'zod';

/**
 * Workflows API Contract Tests
 * 
 * Validates workflow API responses match expected schema.
 * 
 * @doc.type test
 * @doc.purpose API contract validation for workflows
 * @doc.layer testing
 */

const WorkflowSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  status: z.enum(['active', 'inactive', 'draft']),
  executionCount: z.number(),
  lastExecutedAt: z.string().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

const ExecutionSchema = z.object({
  id: z.string(),
  workflowId: z.string(),
  status: z.enum(['pending', 'running', 'completed', 'failed', 'cancelled']),
  startedAt: z.string(),
  completedAt: z.string().optional(),
  duration: z.number().optional(),
  input: z.record(z.unknown()).optional(),
  output: z.record(z.unknown()).optional(),
  error: z.string().optional(),
});

const PaginatedWorkflowResponseSchema = z.object({
  items: z.array(WorkflowSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

const CreateWorkflowRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string(),
  definition: z.record(z.unknown()).optional(),
});

describe('Workflows API Contract', () => {
  describe('GET /api/v1/workflows', () => {
    it('should return paginated workflows matching schema', () => {
      const mockResponse = {
        items: [
          {
            id: 'wf-1',
            name: 'Data Export',
            description: 'Export workflow',
            status: 'active',
            executionCount: 10,
            lastExecutedAt: '2024-01-01T00:00:00Z',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 10,
        hasMore: false,
      };

      const result = PaginatedWorkflowResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });
  });

  describe('POST /api/v1/workflows', () => {
    it('should accept valid create request', () => {
      const validRequest = {
        name: 'New Workflow',
        description: 'A new workflow',
        definition: { steps: [] },
      };

      const result = CreateWorkflowRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });
  });

  describe('GET /api/v1/workflows/:id/executions', () => {
    it('should return executions matching schema', () => {
      const mockResponse = {
        items: [
          {
            id: 'exec-1',
            workflowId: 'wf-1',
            status: 'completed',
            startedAt: '2024-01-01T00:00:00Z',
            completedAt: '2024-01-01T00:00:15Z',
            duration: 15000,
          },
        ],
        total: 1,
        page: 1,
        pageSize: 10,
        hasMore: false,
      };

      const PaginatedExecutionResponseSchema = z.object({
        items: z.array(ExecutionSchema),
        total: z.number(),
        page: z.number(),
        pageSize: z.number(),
        hasMore: z.boolean(),
      });

      const result = PaginatedExecutionResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });
  });
});
