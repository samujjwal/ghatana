/**
 * @ghatana/yappc-ide - CRDT Integration Tests
 * 
 * Comprehensive integration tests for IDE CRDT operations,
 * handler processing, and IDE-canvas synchronization.
 */

// Using Jest test globals (describe/it/expect/beforeEach/afterEach) - originally written for Vitest

import { v4 as uuidv4 } from 'uuid';
import { Text } from 'yjs';
import type { CRDTOperation, VectorClock } from '../../crdt-ide/src';
import { IDECRDTHandler } from '../crdt/ide-handler';
import { IDECanvasBridge } from '../crdt/ide-canvas-bridge';
import { createInitialIDEState } from '../crdt/ide-schema';
import {
  createFileOperation,
  updateFileContentOperation,
  deleteFileOperation,
  renameFileOperation,
  createFolderOperation,
  deleteFolderOperation,
  renameFolderOperation,
  updateEditorStateOperation,
  updatePresenceOperation,
  createTabOperation,
  closeTabOperation,
  updateTabOperation,
  moveTabOperation,
} from '../crdt/ide-operations';

describe('IDE CRDT Integration Tests', () => {
  let ideHandler: IDECRDTHandler;
  let canvasBridge: IDECanvasBridge;
  let mockCanvasState: unknown;
  let mockVectorClock: VectorClock;

  beforeEach(() => {
    // Initialize test environment
    const initialState = createInitialIDEState();
    ideHandler = new IDECRDTHandler(initialState);

    mockCanvasState = {
      nodes: new Map(),
      listeners: new Map(),
      addListener: (id: string, listener: unknown) => mockCanvasState.listeners.set(id, listener),
      removeListener: (id: string) => mockCanvasState.listeners.delete(id),
      updateNode: (node: unknown) => mockCanvasState.nodes.set(node.id, node),
    };

    canvasBridge = new IDECanvasBridge(ideHandler, mockCanvasState);

    mockVectorClock = {
      id: 'test-replica',
      values: new Map([['test-replica', 1]]),
      timestamp: Date.now(),
    };
  });

  afterEach(() => {
    canvasBridge?.cleanup();
  });

  describe('IDE CRDT Handler Integration', () => {
    describe('File Operations', () => {
      it('should create file and update state', () => {
        const operation = createFileOperation(
          {
            path: '/src/index.ts',
            content: 'console.log("hello");',
            language: 'typescript',
            createdAt: Date.now(),
            size: 23,
          },
          'test-replica',
          mockVectorClock
        );

        const result = ideHandler.applyOperation(operation);

        expect(result.success).toBe(true);
        expect(result.error).toBeUndefined();

        const state = ideHandler.getState();
        expect(state.files.size).toBe(1);

        const file = Array.from(state.files.values())[0];
        expect(file.path).toBe('/src/index.ts');
        expect(file.language).toBe('typescript');
        expect(file.content.toString()).toBe('console.log("hello");');
      });

      it('should update file content with Yjs Text', () => {
        // Create file first
        const createOp = createFileOperation(
          {
            path: '/src/test.ts',
            content: 'initial',
            language: 'typescript',
            createdAt: Date.now(),
            size: 8,
          },
          'test-replica',
          mockVectorClock
        );

        ideHandler.applyOperation(createOp);

        // Update file content
        const updateOp = updateFileContentOperation(
          Array.from(ideHandler.getState().files.keys())[0],
          [
            { index: 0, delete: 8 },
            { index: 0, insert: 'updated content' },
          ],
          'test-replica',
          { ...mockVectorClock, timestamp: Date.now() + 1 }
        );

        const result = ideHandler.applyOperation(updateOp);

        expect(result.success).toBe(true);
        const content = ideHandler.getFileContent(Array.from(ideHandler.getState().files.keys())[0]);
        expect(content).toBe('updated content');
      });

      it('should delete file and clean up references', () => {
        // Create file first
        const createOp = createFileOperation(
          {
            path: '/src/delete.ts',
            content: 'to be deleted',
            language: 'typescript',
            createdAt: Date.now(),
            size: 14,
          },
          'test-replica',
          mockVectorClock
        );

        ideHandler.applyOperation(createOp);
        const fileId = Array.from(ideHandler.getState().files.keys())[0];

        // Delete file
        const deleteOp = deleteFileOperation(fileId, 'test-replica', mockVectorClock);
        const result = ideHandler.applyOperation(deleteOp);

        expect(result.success).toBe(true);
        expect(ideHandler.getState().files.size).toBe(0);
      });

      it('should rename file with conflict detection', () => {
        // Create two files
        const createOp1 = createFileOperation(
          {
            path: '/src/old.ts',
            content: 'content1',
            language: 'typescript',
            createdAt: Date.now(),
            size: 8,
          },
          'test-replica',
          mockVectorClock
        );

        const createOp2 = createFileOperation(
          {
            path: '/src/new.ts',
            content: 'content2',
            language: 'typescript',
            createdAt: Date.now(),
            size: 8,
          },
          'test-replica',
          { ...mockVectorClock, timestamp: Date.now() + 1 }
        );

        ideHandler.applyOperation(createOp1);
        ideHandler.applyOperation(createOp2);

        // Try to rename to existing file
        const fileId = Array.from(ideHandler.getState().files.keys())[0];
        const renameOp = renameFileOperation(
          fileId,
          '/src/new.ts',
          'test-replica',
          { ...mockVectorClock, timestamp: Date.now() + 2 }
        );

        const result = ideHandler.applyOperation(renameOp);

        expect(result.success).toBe(false);
        expect(result.error).toContain('already exists');
      });
    });

    describe('Folder Operations', () => {
      it('should create folder and update state', () => {
        const operation = createFolderOperation(
          {
            path: '/src/components',
            createdAt: Date.now(),
          },
          'test-replica',
          mockVectorClock
        );

        const result = ideHandler.applyOperation(operation);

        expect(result.success).toBe(true);
        expect(result.error).toBeUndefined();

        const state = ideHandler.getState();
        expect(state.folders.size).toBe(1);

        const folder = Array.from(state.folders.values())[0];
        expect(folder.path).toBe('/src/components');
        expect(folder.children).toEqual([]);
      });

      it('should delete folder with validation', () => {
        // Create folder first
        const createOp = createFolderOperation(
          {
            path: '/src/empty',
            createdAt: Date.now(),
          },
          'test-replica',
          mockVectorClock
        );

        ideHandler.applyOperation(createOp);
        const folderId = Array.from(ideHandler.getState().folders.keys())[0];

        // Delete empty folder
        const deleteOp = deleteFolderOperation(folderId, 'test-replica', mockVectorClock);
        const result = ideHandler.applyOperation(deleteOp);

        expect(result.success).toBe(true);
        expect(ideHandler.getState().folders.size).toBe(0);
      });

      it('should prevent deletion of non-empty folder', () => {
        // Create folder with child
        const createFolderOp = createFolderOperation(
          {
            path: '/src/parent',
            createdAt: Date.now(),
          },
          'test-replica',
          mockVectorClock
        );

        ideHandler.applyOperation(createFolderOp);
        const folderId = Array.from(ideHandler.getState().folders.keys())[0];

        // Manually add child to simulate non-empty folder
        const state = ideHandler.getState();
        const folder = state.folders.get(folderId);
        if (folder) {
          folder.children.push('child-id');
        }

        // Try to delete non-empty folder
        const deleteOp = deleteFolderOperation(folderId, 'test-replica', mockVectorClock);
        const result = ideHandler.applyOperation(deleteOp);

        expect(result.success).toBe(false);
        expect(result.error).toContain('not empty');
      });
    });

    describe('Editor State Operations', () => {
      it('should update editor state for user', () => {
        const operation = updateEditorStateOperation(
          {
            userId: 'user-1',
            activeFileId: 'file-1',
            cursorPosition: { line: 10, column: 5 },
            selection: {
              start: { line: 10, column: 5 },
              end: { line: 10, column: 15 },
            },
          },
          'test-replica',
          mockVectorClock
        );

        const result = ideHandler.applyOperation(operation);

        expect(result.success).toBe(true);

        const state = ideHandler.getState();
        expect(state.editorState.size).toBe(1);

        const editorState = state.editorState.get('user-1');
        expect(editorState?.activeFileId).toBe('file-1');
        expect(editorState?.cursorPosition).toEqual({ line: 10, column: 5 });
        expect(editorState?.selection).toEqual({
          start: { line: 10, column: 5 },
          end: { line: 10, column: 15 },
        });
      });

      it('should update presence information', () => {
        const operation = updatePresenceOperation(
          {
            userId: 'user-1',
            userName: 'Alice',
            userColor: '#ff0000',
            activeFileId: 'file-1',
            cursorPosition: { line: 5, column: 10 },
            lastActivity: Date.now(),
          },
          'test-replica',
          mockVectorClock
        );

        const result = ideHandler.applyOperation(operation);

        expect(result.success).toBe(true);

        const state = ideHandler.getState();
        expect(state.presence.size).toBe(1);

        const presence = state.presence.get('user-1');
        expect(presence?.userName).toBe('Alice');
        expect(presence?.userColor).toBe('#ff0000');
        expect(presence?.activeFileId).toBe('file-1');
        expect(presence?.isOnline).toBe(true);
      });
    });

    describe('Tab Operations', () => {
      it('should create tab for user', () => {
        const operation = createTabOperation(
          {
            id: 'tab-1',
            fileId: 'file-1',
            title: 'index.ts',
            isPinned: false,
          },
          'test-replica',
          mockVectorClock
        );

        const result = ideHandler.applyOperation(operation);

        expect(result.success).toBe(true);

        const state = ideHandler.getState();
        const editorState = state.editorState.get('test-replica');
        expect(editorState?.openTabs).toContain('tab-1');
        expect(editorState?.activeFileId).toBe('file-1');
      });

      it('should close tab and update active file', () => {
        // Create two tabs first
        const createTab1 = createTabOperation(
          {
            id: 'tab-1',
            fileId: 'file-1',
            title: 'index.ts',
            isPinned: false,
          },
          'test-replica',
          mockVectorClock
        );

        const createTab2 = createTabOperation(
          {
            id: 'tab-2',
            fileId: 'file-2',
            title: 'app.ts',
            isPinned: false,
          },
          'test-replica',
          { ...mockVectorClock, timestamp: Date.now() + 1 }
        );

        ideHandler.applyOperation(createTab1);
        ideHandler.applyOperation(createTab2);

        // Close first tab
        const closeOp = closeTabOperation('tab-1', 'test-replica', mockVectorClock);
        const result = ideHandler.applyOperation(closeOp);

        expect(result.success).toBe(true);

        const editorState = ideHandler.getState().editorState.get('test-replica');
        expect(editorState?.openTabs).not.toContain('tab-1');
        expect(editorState?.openTabs).toContain('tab-2');
        expect(editorState?.activeFileId).toBe('file-2');
      });

      it('should move tab to new position', () => {
        // Create three tabs
        const tabs = ['tab-1', 'tab-2', 'tab-3'].map((id, index) =>
          createTabOperation(
            {
              id,
              fileId: `file-${index}`,
              title: `file-${index}.ts`,
              isPinned: false,
            },
            'test-replica',
            { ...mockVectorClock, timestamp: Date.now() + index }
          )
        );

        tabs.forEach(op => ideHandler.applyOperation(op));

        // Move tab-3 to position 0
        const moveOp = moveTabOperation('tab-3', 0, 'test-replica', mockVectorClock);
        const result = ideHandler.applyOperation(moveOp);

        expect(result.success).toBe(true);

        const editorState = ideHandler.getState().editorState.get('test-replica');
        expect(editorState?.openTabs[0]).toBe('tab-3');
        expect(editorState?.openTabs[1]).toBe('tab-1');
        expect(editorState?.openTabs[2]).toBe('tab-2');
      });
    });
  });

  describe('IDE-Canvas Bridge Integration', () => {
    it('should initialize bridge successfully', () => {
      const result = canvasBridge.initialize();

      expect(result.success).toBe(true);
      expect(result.error).toBeUndefined();
    });

    it('should sync files to canvas on IDE state change', () => {
      // Initialize bridge
      canvasBridge.initialize();

      // Create file in IDE
      const operation = createFileOperation(
        {
          path: '/src/canvas-test.ts',
          content: 'canvas sync test',
          language: 'typescript',
          createdAt: Date.now(),
          size: 17,
        },
        'test-replica',
        mockVectorClock
      );

      ideHandler.applyOperation(operation);

      // Check that canvas was notified (mock implementation)
      expect(mockCanvasState.listeners.size).toBeGreaterThan(0);
    });

    it('should handle conflicts between IDE and canvas', () => {
      const ideOperation = createFileOperation(
        {
          path: '/src/conflict.ts',
          content: 'ide content',
          language: 'typescript',
          createdAt: Date.now(),
          size: 12,
        },
        'ide-replica',
        { ...mockVectorClock, timestamp: 1000 }
      );

      const canvasOperation = {
        id: uuidv4(),
        replicaId: 'canvas-replica',
        type: 'update',
        targetId: 'ide-node',
        vectorClock: { ...mockVectorClock, timestamp: 900 },
        data: { content: 'canvas content' },
        timestamp: 900,
        parents: [],
      } as CRDTOperation;

      // Test conflict resolution (IDE should win due to later timestamp)
      const conflict = canvasBridge['handleConflict'](ideOperation, canvasOperation);

      expect(conflict.resolved).toBe(true);
      expect(conflict.winner).toBe('ide');
      expect(conflict.result).toBe(ideOperation);
    });

    it('should provide accurate statistics', () => {
      // Create some test data
      const fileOp = createFileOperation(
        {
          path: '/src/stats.ts',
          content: 'statistics test',
          language: 'typescript',
          createdAt: Date.now(),
          size: 17,
        },
        'test-replica',
        mockVectorClock
      );

      const folderOp = createFolderOperation(
        {
          path: '/src/utils',
          createdAt: Date.now(),
        },
        'test-replica',
        mockVectorClock
      );

      ideHandler.applyOperation(fileOp);
      ideHandler.applyOperation(folderOp);

      const stats = canvasBridge.getStatistics();

      expect(stats.filesSynced).toBe(1);
      expect(stats.foldersSynced).toBe(1);
      expect(stats.lastSyncTime).toBeGreaterThan(0);
    });
  });

  describe('State Consistency Tests', () => {
    it('should maintain state consistency across multiple operations', () => {
      // Create file
      const createFile = createFileOperation(
        {
          path: '/src/consistency.ts',
          content: 'initial',
          language: 'typescript',
          createdAt: Date.now(),
          size: 8,
        },
        'test-replica',
        mockVectorClock
      );

      // Update content
      const updateContent = updateFileContentOperation(
        'file-id', // Will be replaced with actual ID
        [
          { index: 0, delete: 8 },
          { index: 0, insert: 'updated content' },
        ],
        'test-replica',
        { ...mockVectorClock, timestamp: Date.now() + 1 }
      );

      // Create tab
      const createTab = createTabOperation(
        {
          id: 'tab-1',
          fileId: 'file-id',
          title: 'consistency.ts',
          isPinned: false,
        },
        'test-replica',
        { ...mockVectorClock, timestamp: Date.now() + 2 }
      );

      // Apply operations
      const createResult = ideHandler.applyOperation(createFile);
      expect(createResult.success).toBe(true);

      const fileId = Array.from(ideHandler.getState().files.keys())[0];

      // Update the operation with actual file ID
      (updateContent.data as unknown).fileId = fileId;
      (createTab.data as unknown).fileId = fileId;

      const updateResult = ideHandler.applyOperation(updateContent);
      expect(updateResult.success).toBe(true);

      const tabResult = ideHandler.applyOperation(createTab);
      expect(tabResult.success).toBe(true);

      // Verify state consistency
      const state = ideHandler.getState();
      expect(state.files.size).toBe(1);
      expect(state.editorState.size).toBe(1);

      const file = Array.from(state.files.values())[0];
      expect(file.content.toString()).toBe('updated content');

      const editorState = state.editorState.get('test-replica');
      expect(editorState?.openTabs).toContain('tab-1');
      expect(editorState?.activeFileId).toBe(fileId);
    });

    it('should handle concurrent operations correctly', () => {
      // Simulate concurrent file operations
      const operations = [
        createFileOperation(
          {
            path: '/src/concurrent1.ts',
            content: 'content1',
            language: 'typescript',
            createdAt: Date.now(),
            size: 8,
          },
          'replica-1',
          { ...mockVectorClock, id: 'replica-1' }
        ),
        createFileOperation(
          {
            path: '/src/concurrent2.ts',
            content: 'content2',
            language: 'typescript',
            createdAt: Date.now(),
            size: 8,
          },
          'replica-2',
          { ...mockVectorClock, id: 'replica-2' }
        ),
        createFileOperation(
          {
            path: '/src/concurrent3.ts',
            content: 'content3',
            language: 'typescript',
            createdAt: Date.now(),
            size: 8,
          },
          'replica-3',
          { ...mockVectorClock, id: 'replica-3' }
        ),
      ];

      // Apply operations concurrently
      const results = operations.map(op => ideHandler.applyOperation(op));

      // All should succeed
      results.forEach(result => {
        expect(result.success).toBe(true);
      });

      // State should be consistent
      const state = ideHandler.getState();
      expect(state.files.size).toBe(3);

      const paths = Array.from(state.files.values()).map(f => f.path);
      expect(paths).toContain('/src/concurrent1.ts');
      expect(paths).toContain('/src/concurrent2.ts');
      expect(paths).toContain('/src/concurrent3.ts');
    });
  });

  describe('Error Handling Tests', () => {
    it('should handle invalid operations gracefully', () => {
      const invalidOperation = {
        id: uuidv4(),
        replicaId: 'test-replica',
        type: 'invalid-type',
        targetId: 'test-target',
        vectorClock: mockVectorClock,
        data: {},
        timestamp: Date.now(),
        parents: [],
      } as CRDTOperation;

      const result = ideHandler.applyOperation(invalidOperation);

      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
    });

    it('should handle missing file operations', () => {
      const deleteOp = deleteFileOperation('non-existent-file', 'test-replica', mockVectorClock);
      const result = ideHandler.applyOperation(deleteOp);

      expect(result.success).toBe(false);
      expect(result.error).toContain('not found');
    });

    it('should handle missing folder operations', () => {
      const deleteOp = deleteFolderOperation('non-existent-folder', 'test-replica', mockVectorClock);
      const result = ideHandler.applyOperation(deleteOp);

      expect(result.success).toBe(false);
      expect(result.error).toContain('not found');
    });
  });

  describe('Performance Tests', () => {
    it('should handle large file operations efficiently', () => {
      const largeContent = 'x'.repeat(10000);
      const startTime = Date.now();

      const operation = createFileOperation(
        {
          path: '/src/large.ts',
          content: largeContent,
          language: 'typescript',
          createdAt: Date.now(),
          size: largeContent.length,
        },
        'test-replica',
        mockVectorClock
      );

      const result = ideHandler.applyOperation(operation);
      const endTime = Date.now();

      expect(result.success).toBe(true);
      expect(endTime - startTime).toBeLessThan(100); // Should complete in <100ms

      const content = ideHandler.getFileContent(Array.from(ideHandler.getState().files.keys())[0]);
      expect(content).toBe(largeContent);
    });

    it('should handle many small operations efficiently', () => {
      const operationCount = 100;
      const startTime = Date.now();

      for (let i = 0; i < operationCount; i++) {
        const operation = updatePresenceOperation(
          {
            userId: `user-${i}`,
            userName: `User ${i}`,
            userColor: '#ff0000',
            activeFileId: `file-${i}`,
            cursorPosition: { line: i, column: i },
            lastActivity: Date.now(),
          },
          'test-replica',
          mockVectorClock
        );

        const result = ideHandler.applyOperation(operation);
        expect(result.success).toBe(true);
      }

      const endTime = Date.now();
      const averageTime = (endTime - startTime) / operationCount;

      expect(averageTime).toBeLessThan(10); // Average <10ms per operation
      expect(ideHandler.getState().presence.size).toBe(operationCount);
    });
  });
});
