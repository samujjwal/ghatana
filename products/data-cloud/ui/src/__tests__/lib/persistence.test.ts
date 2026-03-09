import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { NodeType } from '@/types/workflow.types';
import type { WorkflowDefinition } from '@/types/workflow.types';
import {
  saveWorkflowState,
  loadWorkflowState,
  saveHistory,
  loadHistory,
  saveHistoryIndex,
  loadHistoryIndex,
  clearHistory,
  exportWorkflow,
  importWorkflow,
  getStorageStats,
  isStorageAvailable,
} from '@/lib/persistence';
import { withMockLocalStorage } from '../test-utils/localStorage';

/**
 * Tests for persistence service.
 *
 * Tests validate:
 * - Workflow state persistence
 * - History management
 * - State recovery
 * - Export/import functionality
 * - Error handling
 *
 * @see persistence.ts
 */

const createWorkflow = (overrides: Partial<WorkflowDefinition> = {}): WorkflowDefinition => {
  const base: WorkflowDefinition = {
    id: 'workflow-123',
    tenantId: 'tenant-1',
    collectionId: 'collection-1',
    name: 'Test Workflow',
    description: 'A test workflow',
    status: 'DRAFT',
    version: 1,
    active: true,
    nodes: [
      {
        id: 'node-1',
        type: 'START',
        position: { x: 0, y: 0 },
        data: { label: 'Start' },
      } as WorkflowNode,
    ],
    edges: [],
    triggers: [],
    variables: {},
    tags: [],
    createdBy: 'user-1',
    updatedBy: 'user-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  return { ...base, ...overrides };
};

describe('Persistence Service', () => {
  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  // ============ Workflow State Tests ============

  describe('Workflow State', () => {
    const mockWorkflow = createWorkflow();

    it('should save workflow state', () => {
      // When
      saveWorkflowState(mockWorkflow);

      // Then
      const saved = localStorage.getItem('workflow:current');
      expect(saved).toBeDefined();
      const parsed = JSON.parse(saved!);
      expect(parsed.id).toBe('workflow-123');
      expect(parsed.name).toBe('Test Workflow');
    });

    it('should load workflow state', () => {
      // Given
      saveWorkflowState(mockWorkflow);

      // When
      const loaded = loadWorkflowState();

      // Then
      expect(loaded).toBeDefined();
      expect(loaded?.id).toBe('workflow-123');
      expect(loaded?.name).toBe('Test Workflow');
    });

    it('should return null when no workflow is saved', () => {
      // When
      const loaded = loadWorkflowState();

      // Then
      expect(loaded).toBeNull();
    });

    it('should overwrite existing workflow state', () => {
      // Given
      saveWorkflowState(mockWorkflow);

      // When
      const updated = { ...mockWorkflow, name: 'Updated Workflow' };
      saveWorkflowState(updated);

      // Then
      const loaded = loadWorkflowState();
      expect(loaded?.name).toBe('Updated Workflow');
    });
  });

  // ============ History Tests ============

  describe('History Management', () => {
    const mockWorkflows: WorkflowDefinition[] = Array.from({ length: 3 }, (_, i) =>
      createWorkflow({
        id: `workflow-${i}`,
        name: `Workflow ${i}`,
        nodes: [],
        edges: [],
      })
    );

    it('should save history', () => {
      // When
      saveHistory(mockWorkflows);

      // Then
      const saved = localStorage.getItem('workflow:history');
      expect(saved).toBeDefined();
      const parsed = JSON.parse(saved!);
      expect(parsed).toHaveLength(3);
    });

    it('should load history', () => {
      // Given
      saveHistory(mockWorkflows);

      // When
      const loaded = loadHistory();

      // Then
      expect(loaded).toHaveLength(3);
      expect(loaded[0].id).toBe('workflow-0');
      expect(loaded[2].id).toBe('workflow-2');
    });

    it('should return empty array when no history is saved', () => {
      // When
      const loaded = loadHistory();

      // Then
      expect(loaded).toEqual([]);
    });

    it('should enforce max history size', () => {
      // Given
      const largeHistory = Array.from({ length: 100 }, (_, i) =>
        createWorkflow({
          id: `workflow-${i}`,
          name: `Workflow ${i}`,
          nodes: [],
          edges: [],
        })
      );

      // When
      saveHistory(largeHistory);

      // Then
      const loaded = loadHistory();
      expect(loaded.length).toBeLessThanOrEqual(50);
      // Should keep the last 50 items
      expect(loaded[0].id).toBe('workflow-50');
    });
  });

  // ============ History Index Tests ============

  describe('History Index', () => {
    it('should save history index', () => {
      // When
      saveHistoryIndex(5);

      // Then
      const saved = localStorage.getItem('workflow:index');
      expect(saved).toBe('5');
    });

    it('should load history index', () => {
      // Given
      saveHistoryIndex(5);

      // When
      const loaded = loadHistoryIndex();

      // Then
      expect(loaded).toBe(5);
    });

    it('should return -1 when no index is saved', () => {
      // When
      const loaded = loadHistoryIndex();

      // Then
      expect(loaded).toBe(-1);
    });

    it('should overwrite existing index', () => {
      // Given
      saveHistoryIndex(5);

      // When
      saveHistoryIndex(10);

      // Then
      const loaded = loadHistoryIndex();
      expect(loaded).toBe(10);
    });
  });

  // ============ Clear History Tests ============

  describe('Clear History', () => {
    it('should clear all workflow state', () => {
      // Given
      const workflow = createWorkflow({
        id: 'test',
        name: 'Test',
        nodes: [],
        edges: [],
      });
      saveWorkflowState(workflow);
      saveHistory([workflow]);
      saveHistoryIndex(0);

      // When
      clearHistory();

      // Then
      expect(loadWorkflowState()).toBeNull();
      expect(loadHistory()).toEqual([]);
      expect(loadHistoryIndex()).toBe(-1);
    });
  });

  // ============ Export/Import Tests ============

  describe('Export/Import', () => {
    const mockWorkflow: WorkflowDefinition = {
      id: 'workflow-123',
      name: 'Test Workflow',
      description: 'A test workflow',
      nodes: [
        {
          id: 'node-1',
          type: 'start',
          label: 'Start',
          position: { x: 0, y: 0 },
          data: {},
        },
      ],
      edges: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    it('should export workflow as JSON string', () => {
      // When
      const exported = exportWorkflow(mockWorkflow);

      // Then
      expect(typeof exported).toBe('string');
      const parsed = JSON.parse(exported);
      expect(parsed.id).toBe('workflow-123');
      expect(parsed.name).toBe('Test Workflow');
    });

    it('should import workflow from JSON string', () => {
      // Given
      const json = JSON.stringify(mockWorkflow);

      // When
      const imported = importWorkflow(json);

      // Then
      expect(imported.id).toBe('workflow-123');
      expect(imported.name).toBe('Test Workflow');
      expect(imported.nodes).toHaveLength(1);
    });

    it('should throw error for invalid JSON', () => {
      // When & Then
      expect(() => importWorkflow('invalid json')).toThrow();
    });

    it('should throw error for missing required fields', () => {
      // When & Then
      expect(() => importWorkflow(JSON.stringify({ name: 'Test' }))).toThrow(
        /Missing required fields/
      );
    });

    it('should throw error for invalid workflow structure', () => {
      // When & Then
      expect(() =>
        importWorkflow(
          JSON.stringify({
            id: 'test',
            name: 'Test',
            tenantId: 'tenant-1',
            collectionId: 'collection-1',
            status: 'DRAFT',
            version: 1,
            active: true,
            nodes: 'not-an-array',
            edges: [],
            triggers: [],
            variables: {},
            createdBy: 'user',
            updatedBy: 'user',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          })
        )
      ).toThrow(/Invalid workflow structure/);
    });

    it('should round-trip workflow correctly', () => {
      // When
      const exported = exportWorkflow(mockWorkflow);
      const imported = importWorkflow(exported);

      // Then
      expect(imported).toEqual(mockWorkflow);
    });
  });

  // ============ Storage Stats Tests ============

  describe('Storage Stats', () => {
    it('should return storage statistics', () => {
      // Given
      const workflow = createWorkflow({
        id: 'test',
        name: 'Test',
        nodes: [],
        edges: [],
      });
      saveWorkflowState(workflow);
      saveHistory([workflow, workflow]);

      // When
      const stats = getStorageStats();

      // Then
      expect(stats.currentSize).toBeGreaterThan(0);
      expect(stats.historySize).toBeGreaterThan(0);
      expect(stats.totalSize).toBe(stats.currentSize + stats.historySize);
      expect(stats.maxHistorySize).toBe(50);
    });

    it('should return zero for empty storage', () => {
      // When
      const stats = getStorageStats();

      // Then
      expect(stats.currentSize).toBe(0);
      expect(stats.historySize).toBe(0);
      expect(stats.totalSize).toBe(0);
    });
  });

  // ============ Storage Availability Tests ============

  describe('Storage Availability', () => {
    it('should return true when localStorage is available', () => {
      // When
      const available = isStorageAvailable();

      // Then
      expect(available).toBe(true);
    });

    it('should return false when localStorage is not available', () => {
      // Given - use helper to swap global.localStorage to a mock that throws on setItem
      withMockLocalStorage(
        {
          getItem: () => null,
          setItem: () => {
            throw new Error('QuotaExceededError');
          },
          removeItem: () => undefined,
          clear: () => undefined,
        },
        () => {
          // When
          const available = isStorageAvailable();

          // Then
          expect(available).toBe(false);
        }
      );
    });
  });

  // ============ Error Handling Tests ============

  describe('Error Handling', () => {
    it('should handle corrupted JSON gracefully', () => {
      // Given
      localStorage.setItem('workflow:current', '{invalid json}');

      // When
      const loaded = loadWorkflowState();

      // Then
      expect(loaded).toBeNull();
    });

    it('should handle missing fields in loaded workflow', () => {
      // Given
      localStorage.setItem('workflow:current', JSON.stringify({ id: 'test' }));

      // When
      const loaded = loadWorkflowState();

      // Then
      expect(loaded).toEqual({ id: 'test' });
    });
  });
});
