/**
 * Phase Action Service Tests
 * 
 * Tests for phase action handlers that call backend APIs.
 * 
 * @doc.type test
 * @doc.purpose Test phase action handlers
 * @doc.layer product
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  handleCreateVision,
  handleAddUserStory,
  handleDefineRequirement,
  handleAddStakeholder,
  handleCreateGoal,
  handleCreateDiagram,
  handleAddService,
  handleDefineApiContract,
  handleAddDataModel,
  handleCreateComponent,
  handleAddValidationRule,
  handleCreateTestCase,
  handleAddAcceptanceCriteria,
  handleGenerateCode,
  handleCreateScaffold,
  handleGenerateTests,
  handleDeployService,
  handleExecuteTests,
  handleMonitorLogs,
  handleCreateEnhancement,
  handleRefactorCode,
  handleAddFeature,
  PHASE_ACTION_HANDLERS,
} from '../PhaseActionService';

// Mock fetch globally
global.fetch = vi.fn();

describe('PhaseActionService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockContext = {
    projectId: 'test-project-123',
    phase: 'INTENT',
    userId: 'user-123',
  };

  // ============================================================================
  // Intent Phase Handlers
  // ============================================================================

  describe('Intent Phase Handlers', () => {
    it('handleCreateVision calls intent capture API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateVision(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/intent/capture'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('vision'),
        })
      );
    });

    it('handleAddUserStory calls intent capture API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddUserStory(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/intent/capture'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('user-story'),
        })
      );
    });

    it('handleDefineRequirement calls intent capture API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleDefineRequirement(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/intent/capture'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('requirement'),
        })
      );
    });

    it('handleAddStakeholder calls intent capture API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddStakeholder(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/intent/capture'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('stakeholder'),
        })
      );
    });

    it('handleCreateGoal calls intent capture API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateGoal(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/intent/capture'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('goal'),
        })
      );
    });
  });

  // ============================================================================
  // Shape Phase Handlers
  // ============================================================================

  describe('Shape Phase Handlers', () => {
    it('handleCreateDiagram calls shape derive API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateDiagram(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/shape/derive'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('architecture-diagram'),
        })
      );
    });

    it('handleAddService calls shape derive API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddService(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/shape/derive'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('service'),
        })
      );
    });

    it('handleDefineApiContract calls shape derive API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleDefineApiContract(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/shape/derive'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('api-contract'),
        })
      );
    });

    it('handleAddDataModel calls shape model API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddDataModel(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/shape/model'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('data-model'),
        })
      );
    });

    it('handleCreateComponent calls shape derive API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateComponent(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/shape/derive'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('component'),
        })
      );
    });
  });

  // ============================================================================
  // Validate Phase Handlers
  // ============================================================================

  describe('Validate Phase Handlers', () => {
    it('handleAddValidationRule calls validate API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddValidationRule(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/validate'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('validation-rule'),
        })
      );
    });

    it('handleCreateTestCase calls validate API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateTestCase(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/validate'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('test-case'),
        })
      );
    });

    it('handleAddAcceptanceCriteria calls validate API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddAcceptanceCriteria(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/validate'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('acceptance-criteria'),
        })
      );
    });
  });

  // ============================================================================
  // Generate Phase Handlers
  // ============================================================================

  describe('Generate Phase Handlers', () => {
    it('handleGenerateCode calls generate API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleGenerateCode(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/generate'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('code-generation'),
        })
      );
    });

    it('handleCreateScaffold calls generate API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateScaffold(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/generate'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('scaffold'),
        })
      );
    });

    it('handleGenerateTests calls generate API', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleGenerateTests(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/yappc/generate'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('test-generation'),
        })
      );
    });
  });

  // ============================================================================
  // Run Phase Handlers
  // ============================================================================

  describe('Run Phase Handlers', () => {
    it('handleDeployService creates deployment artifact', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleDeployService(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/artifacts'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('deployment-script'),
        })
      );
    });

    it('handleExecuteTests creates test execution artifact', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleExecuteTests(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/artifacts'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('test-results'),
        })
      );
    });

    it('handleMonitorLogs creates log monitoring artifact', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleMonitorLogs(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/artifacts'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('log-monitoring'),
        })
      );
    });
  });

  // ============================================================================
  // Improve Phase Handlers
  // ============================================================================

  describe('Improve Phase Handlers', () => {
    it('handleCreateEnhancement creates enhancement artifact', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleCreateEnhancement(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/artifacts'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('enhancement'),
        })
      );
    });

    it('handleRefactorCode creates refactor artifact', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleRefactorCode(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/artifacts'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('refactor'),
        })
      );
    });

    it('handleAddFeature creates feature artifact', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await handleAddFeature(mockContext);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/artifacts'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('feature'),
        })
      );
    });
  });

  // ============================================================================
  // Handler Registry
  // ============================================================================

  describe('PHASE_ACTION_HANDLERS Registry', () => {
    it('contains all intent phase handlers', () => {
      expect(PHASE_ACTION_HANDLERS['intent-create-vision']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['intent-add-user-story']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['intent-define-requirement']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['intent-add-stakeholder']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['intent-create-goal']).toBeDefined();
    });

    it('contains all shape phase handlers', () => {
      expect(PHASE_ACTION_HANDLERS['shape-create-diagram']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['shape-add-service']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['shape-define-api-contract']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['shape-add-data-model']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['shape-create-component']).toBeDefined();
    });

    it('contains all validate phase handlers', () => {
      expect(PHASE_ACTION_HANDLERS['validate-add-rule']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['validate-create-test-case']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['validate-add-acceptance-criteria']).toBeDefined();
    });

    it('contains all generate phase handlers', () => {
      expect(PHASE_ACTION_HANDLERS['generate-code']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['generate-create-scaffold']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['generate-tests']).toBeDefined();
    });

    it('contains all run phase handlers', () => {
      expect(PHASE_ACTION_HANDLERS['run-deploy-service']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['run-execute-tests']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['run-monitor-logs']).toBeDefined();
    });

    it('contains all improve phase handlers', () => {
      expect(PHASE_ACTION_HANDLERS['improve-create-enhancement']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['improve-refactor-code']).toBeDefined();
      expect(PHASE_ACTION_HANDLERS['improve-add-feature']).toBeDefined();
    });

    it('has 21 handlers total', () => {
      expect(Object.keys(PHASE_ACTION_HANDLERS).length).toBe(21);
    });
  });

  // ============================================================================
  // Error Handling
  // ============================================================================

  describe('Error Handling', () => {
    it('throws error when projectId is missing', async () => {
      await expect(handleCreateVision({ phase: 'INTENT' })).rejects.toThrow('Project ID required');
    });

    it('throws API error when fetch fails', async () => {
      (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({ message: 'Internal Server Error' }),
      });

      await expect(handleCreateVision(mockContext)).rejects.toThrow('API request failed');
    });
  });
});
