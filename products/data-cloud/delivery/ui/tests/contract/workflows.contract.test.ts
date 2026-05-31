import { describe,expect,it } from 'vitest';
import {
CreateWorkflowRequestSchema,
ExecutionSchema,
paginatedSchema,
PaginatedWorkflowResponseSchema
} from '../../src/contracts/schemas';

/**
 * Pipelines API Contract Tests
 *
 * Validates pipeline API responses match expected schema.
 * Schemas are imported from the shared contracts module.
 *
 * @doc.type test
 * @doc.purpose API contract validation for pipelines
 * @doc.layer testing
 */

describe('Pipelines API Contract', () => {
  describe('GET /api/v1/pipelines', () => {
    it('should return paginated pipelines matching schema', () => {
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

  describe('POST /api/v1/pipelines', () => {
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

  describe('GET /api/v1/pipelines/:id/executions', () => {
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
