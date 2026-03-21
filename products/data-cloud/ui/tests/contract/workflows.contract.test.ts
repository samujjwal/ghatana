import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  WorkflowSchema,
  PaginatedWorkflowResponseSchema,
  CreateWorkflowRequestSchema,
  ExecutionSchema,
  paginatedSchema,
} from '../../src/contracts/schemas';

/**
 * Workflows API Contract Tests
 * 
 * Validates workflow API responses match expected schema.
 * Schemas are imported from the shared contracts module.
 * 
 * @doc.type test
 * @doc.purpose API contract validation for workflows
 * @doc.layer testing
 */

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

      const PaginatedExecutionResponseSchema = paginatedSchema(ExecutionSchema);

      const result = PaginatedExecutionResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });
  });
});
