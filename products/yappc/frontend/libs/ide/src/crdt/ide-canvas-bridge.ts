/**
 * @ghatana/yappc-ide - IDE-Canvas Bridge
 * 
 * Bridges IDE CRDT state with canvas CRDT state for seamless collaboration.
 * Handles bidirectional synchronization between IDE and canvas.
 * 
 * @doc.type module
 * @doc.purpose IDE-Canvas synchronization bridge
 * @doc.layer product
 * @doc.pattern Integration Bridge
 */

// Use local lightweight types to avoid hard dependency on the full CRDT core types
import type { CRDTOperation, CanvasNodeLite } from '../../../crdt-ide/src';
import { yMapToRecord } from '../../../crdt-ide/src';
import * as Y from 'yjs';
import type { IDEFile, IDEFolder } from '../types';
import type { IDECRDTState } from './ide-schema';
import { IDECRDTHandler } from './ide-handler';

/**
 * IDE-Canvas bridge for bidirectional synchronization
 */
export class IDECanvasBridge {
  private ideHandler: IDECRDTHandler;
  private canvasState: unknown; // Will be populated with canvas CRDT state
  private syncListeners: Map<string, (operation: CRDTOperation) => void> = new Map();

  constructor(ideHandler: IDECRDTHandler, canvasState: unknown) {
    this.ideHandler = ideHandler;
    this.canvasState = canvasState;

    // Keep reference to handleConflict to ensure it's not tree-shaken/flagged as unused; tests may access this method
    void this.handleConflict;
  }

  /**
   * Initialize bridge and set up listeners
   * 
   * @doc.returns Initialization result
   */
  initialize(): { success: boolean; error?: string } {
    try {
      // Set up IDE state listener
      this.ideHandler.addListener('ide-canvas-bridge', this.handleIDEStateChange.bind(this));

      // Set up canvas state listener (implementation depends on canvas CRDT)
      this.setupCanvasListener();

      // If the canvas exposes an addListener API, register a listener for bridge events
      if (this.canvasState && typeof this.canvasState.addListener === 'function') {
        this.canvasState.addListener('ide-canvas-bridge', (op: CRDTOperation) => this.handleCanvasStateChange(op));
        this.syncListeners.set('canvas', (op: CRDTOperation) => this.handleCanvasStateChange(op));
      }

      return { success: true };
    } catch (error) {
      return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
    }
  }

  /**
   * Handle IDE state changes and sync to canvas
   */
  private handleIDEStateChange(ideState: IDECRDTState): void {
    // Sync files to canvas nodes
    this.syncFilesToCanvas(ideState);

    // Sync folders to canvas structure
    this.syncFoldersToCanvas(ideState);

    // Update IDE canvas nodes
    this.updateIDECanvasNodes(ideState);
  }

  /**
   * Handle canvas state changes and sync to IDE
   */
  private handleCanvasStateChange(operation: CRDTOperation): void {
    // Handle canvas node updates
    if (this.isIDECanvasOperation(operation)) {
      this.syncCanvasToIDE(operation);
    }
  }

  /**
   * Check if operation affects IDE canvas nodes
   */
  private isIDECanvasOperation(operation: CRDTOperation): boolean {
    return operation.targetId === 'ide-node' ||
      operation.type === 'update' &&
      operation.data?.nodeType === 'ide';
  }

  /**
   * Sync files to canvas nodes
   */
  private syncFilesToCanvas(ideState: IDECRDTState): void {
    for (const [, file] of ideState.files) {
      // Create or update canvas node for file
      const canvasNode = this.createFileCanvasNode(file);
      this.updateCanvasNode(canvasNode);
    }
  }

  /**
   * Sync folders to canvas structure
   */
  private syncFoldersToCanvas(ideState: IDECRDTState): void {
    for (const [, folder] of ideState.folders) {
      // Create or update canvas node for folder
      const canvasNode = this.createFolderCanvasNode(folder);
      this.updateCanvasNode(canvasNode);
    }
  }

  /**
   * Update IDE canvas nodes
   */
  private updateIDECanvasNodes(ideState: IDECRDTState): void {
    // Find existing IDE canvas nodes
    const ideNodes = this.findIDECanvasNodes();

    // Update each IDE node with current state
    for (const node of ideNodes) {
      const updatedNode = this.updateIDECanvasNode(node, ideState);
      this.updateCanvasNode(updatedNode);
    }
  }

  /**
   * Create canvas node from IDE file
   */
  private createFileCanvasNode(file: unknown): CanvasNodeLite {
    const rawContent = file && file.content;
    const content = typeof rawContent === 'string' ? rawContent : (rawContent && typeof (rawContent as unknown).toString === 'function' ? (rawContent as unknown).toString() : '');

    return {
      id: `file-${file.id}`,
      type: 'file',
      position: { x: 0, y: 0 },
      size: { width: 200, height: 100 },
      data: {
        fileType: file.language,
        fileName: file.path?.split('/').pop(),
        filePath: file.path,
        content,
        metadata: file.metadata,
      },
    };
  }

  /**
   * Create canvas node from IDE folder
   */
  private createFolderCanvasNode(folder: unknown): CanvasNodeLite {
    return {
      id: `folder-${folder.id}`,
      type: 'folder',
      position: { x: 0, y: 0 },
      size: { width: 200, height: 100 },
      data: {
        folderName: folder.path?.split('/').pop(),
        folderPath: folder.path,
        children: folder.children,
        metadata: folder.metadata,
      },
    };
  }

  /**
   * Update IDE canvas node with current state
   */
  private updateIDECanvasNode(node: CanvasNodeLite, ideState: IDECRDTState): CanvasNodeLite {
    const ideNodeData = node.data as IDECanvasNodeData;

    return {
      ...node,
      data: {
        ...ideNodeData,
        ideState: {
          files: this.mapFilesToIDEState(ideState.files),
          folders: this.mapFoldersToIDEState(ideState.folders),
          rootFolderId: ideState.rootFolderId,
          editorState: this.mapEditorState(ideState.editorState),
          layout: ideState.settings, // Simplified for canvas
          settings: ideState.settings,
          presence: this.mapPresence(ideState.presence),
        },
        width: ideNodeData.width,
        height: ideNodeData.height,
        isExpanded: ideNodeData.isExpanded,
      },
    };
  }

  /**
   * Map CRDT files to IDE state format
   */
  private mapFilesToIDEState(files: unknown): Record<string, IDEFile> {
    const result: Record<string, IDEFile> = {};

    for (const [id, file] of files) {
      const rawContent = file && file.content;
      const content = typeof rawContent === 'string' ? rawContent : (rawContent && typeof (rawContent as unknown).toString === 'function' ? (rawContent as unknown).toString() : '');

      result[id] = {
        id,
        path: file.path,
        name: file.path?.split('/').pop() || 'untitled',
        content,
        language: file.language,
        isDirty: false,
        isOpen: false,
        lastModified: file.metadata?.modifiedAt,
        createdAt: file.metadata?.createdAt,
        size: file.metadata?.size,
      };
    }

    return result;
  }

  /**
   * Map CRDT folders to IDE state format
   */
  private mapFoldersToIDEState(folders: IDECRDTState['folders']): Record<string, IDEFolder> {
    const result: Record<string, IDEFolder> = {};

    for (const [id, folder] of folders) {
      result[id] = {
        id,
        path: folder.path,
        name: folder.path.split('/').pop() || 'untitled',
        children: folder.children.map(childId => ({ id: childId }) as unknown),
        isExpanded: false,
        createdAt: folder.metadata.createdAt,
      };
    }

    return result;
  }

  /**
   * Map editor state to simplified format
   */
  private mapEditorState(editorState: IDECRDTState['editorState']): Record<string, unknown> {
    const result: Record<string, unknown> = {};

    for (const [userId, state] of editorState) {
      result[userId] = {
        activeFileId: state.activeFileId,
        cursorPosition: state.cursorPosition,
        selection: state.selection,
        scrollPosition: state.scrollPosition,
        lastActivity: state.lastActivity,
      };
    }

    return result;
  }

  /**
   * Map presence to IDE state format
   */
  private mapPresence(presence: IDECRDTState['presence']): Record<string, unknown> {
    const result: Record<string, unknown> = {};

    for (const [userId, presenceData] of presence) {
      result[userId] = {
        userId,
        userName: presenceData.userName,
        userColor: presenceData.userColor,
        activeFileId: presenceData.activeFileId,
        cursorPosition: presenceData.cursorPosition,
        selection: presenceData.selection,
        lastActivity: presenceData.lastActivity,
      };
    }

    return result;
  }

  /**
   * Sync canvas changes to IDE
   */
  private syncCanvasToIDE(operation: CRDTOperation): void {
    // Handle canvas node updates that affect IDE
    if (operation.type === 'update' && operation.data?.nodeType === 'ide') {
      // Update IDE state from canvas node
      this.updateIDEFromCanvasNode(operation.data);
    }
  }

  /**
   * Update IDE state from canvas node
   */
  private updateIDEFromCanvasNode(canvasNode: unknown): void {
    const ideNodeData = canvasNode.data as IDECanvasNodeData;

    if (ideNodeData?.ideState) {
      // Update IDE handler state
      const ideState = this.ideHandler.getState();

      // Sync files
      if (ideNodeData.ideState.files) {
        const canvasFiles = (ideNodeData.ideState.files instanceof Y.Map)
          ? (yMapToRecord(ideNodeData.ideState.files) as Record<string, IDEFile>)
          : (ideNodeData.ideState.files as Record<string, IDEFile>);
        this.syncCanvasFilesToIDE(canvasFiles, ideState);
      }

      // Sync folders
      if (ideNodeData.ideState.folders) {
        const canvasFolders = (ideNodeData.ideState.folders instanceof Y.Map)
          ? (yMapToRecord(ideNodeData.ideState.folders) as Record<string, IDEFolder>)
          : (ideNodeData.ideState.folders as Record<string, IDEFolder>);
        this.syncCanvasFoldersToIDE(canvasFolders, ideState);
      }
    }
  }

  /**
   * Sync canvas files to IDE
   */
  private syncCanvasFilesToIDE(canvasFiles: Record<string, IDEFile>, ideState: unknown): void {
    for (const [fileId, file] of Object.entries(canvasFiles)) {
      const existingFile = ideState.files.get(fileId);

      if (!existingFile) {
        // Create new file with plain content for test-friendly behavior
        ideState.files.set(fileId, {
          id: fileId,
          path: file.path,
          content: file.content || '',
          language: file.language,
          metadata: {
            createdAt: file.createdAt,
            modifiedAt: file.lastModified,
            size: file.size,
            createdBy: 'canvas',
            modifiedBy: 'canvas',
          },
        });
      } else {
        // Update existing file (support string or Y.Text)
        const current = existingFile.content;
        if (typeof current === 'string') {
          existingFile.content = file.content;
          existingFile.metadata.modifiedAt = file.lastModified;
          existingFile.metadata.modifiedBy = 'canvas';
          existingFile.metadata.size = file.size;
        } else if ((current as unknown)?.delete && (current as unknown)?.insert) {
          // Y.Text-like
          (current as unknown).delete(0, (current as unknown).length);
          (current as unknown).insert(0, file.content);
          existingFile.metadata.modifiedAt = file.lastModified;
          existingFile.metadata.modifiedBy = 'canvas';
          existingFile.metadata.size = file.size;
        }
      }
    }
  }

  /**
   * Sync canvas folders to IDE
   */
  private syncCanvasFoldersToIDE(canvasFolders: Record<string, IDEFolder>, ideState: IDECRDTState): void {
    for (const [folderId, folder] of Object.entries(canvasFolders)) {
      const existingFolder = ideState.folders.get(folderId);

      if (!existingFolder) {
        // Create new folder
        ideState.folders.set(folderId, {
          id: folderId,
          path: folder.path,
          children: folder.children.map(child => child.id),
          metadata: {
            createdAt: folder.createdAt,
            createdBy: 'canvas',
          },
        });
      }
    }
  }

  /**
   * Find existing canvas nodes whose type is 'ide' (IDE embedded panels).
   * Traverses whichever node storage the canvas CRDT exposes:
   * - `canvasState.nodes` (array)
   * - `canvasState.nodeMap` (Map<string, CanvasNodeLite>)
   */
  private findIDECanvasNodes(): CanvasNodeLite[] {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const canvasAny = this.canvasState as any;
    const nodeSource = canvasAny?.nodes ?? canvasAny?.nodeMap;
    if (!nodeSource) return [];

    const nodeList: CanvasNodeLite[] = Array.isArray(nodeSource)
      ? nodeSource
      : [...(nodeSource.values?.() ?? [])];

    return nodeList.filter((n) => (n as CanvasNodeLite).type === 'ide') as CanvasNodeLite[];
  }

  /**
   * Persist a canvas node update through whichever mutation API the canvas CRDT exposes.
   * Tries the following strategies in order:
   * 1. `canvasState.updateNode(node)` — dedicated mutation method
   * 2. `canvasState.emit('nodeUpdate', node)` — event-based mutation
   * 3. Direct mutation of `canvasState.nodeMap` (Y.js Map)
   * 4. Direct splice into `canvasState.nodes` (plain array)
   */
  private updateCanvasNode(node: CanvasNodeLite): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const canvasAny = this.canvasState as any;

    if (typeof canvasAny?.updateNode === 'function') {
      canvasAny.updateNode(node);
    } else if (typeof canvasAny?.emit === 'function') {
      canvasAny.emit('nodeUpdate', node);
    } else if (canvasAny?.nodeMap instanceof Map) {
      canvasAny.nodeMap.set(node.id, node);
    } else if (Array.isArray(canvasAny?.nodes)) {
      const idx = (canvasAny.nodes as CanvasNodeLite[]).findIndex((n) => n.id === node.id);
      if (idx >= 0) {
        canvasAny.nodes[idx] = node;
      } else {
        canvasAny.nodes.push(node);
      }
    }
  }

  /**
   * Subscribe to canvas CRDT change events and route them to {@link handleCanvasStateChange}.
   * Supports the following canvas event APIs:
   * - `observe(callback)` — Y.js / CRDT core style
   * - `on('change', callback)` — Node.js EventEmitter style
   */
  private setupCanvasListener(): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const canvasAny = this.canvasState as any;

    if (typeof canvasAny?.observe === 'function') {
      canvasAny.observe((event: unknown) => {
        this.handleCanvasStateChange(event as CRDTOperation);
      });
    } else if (typeof canvasAny?.on === 'function') {
      canvasAny.on('change', (op: CRDTOperation) => {
        this.handleCanvasStateChange(op);
      });
    }
  }

  /**
   * Generate code from canvas node
   * 
   * @doc.param canvasNode - Canvas node to generate code from
   * @doc.returns Generated code
   */
  generateCodeFromCanvas(canvasNode: CanvasNodeLite): string {
    // Highest priority: node already carries a code snippet
    if (canvasNode.code) return canvasNode.code;

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const data = canvasNode.data as any;

    // File nodes: return raw content
    if (canvasNode.type === 'file' && data?.content != null) {
      return typeof data.content === 'string'
        ? data.content
        : String(data.content);
    }

    // Structural nodes: generate a minimal code scaffold from metadata
    const name = (data?.label ?? data?.name ?? data?.fileName ?? canvasNode.id) as string;
    const sanitized = name.replace(/[^a-zA-Z0-9_$]/g, '_');

    switch (canvasNode.type) {
      case 'component':
        return [
          `import React from 'react';`,
          ``,
          `export const ${sanitized}: React.FC = () => {`,
          `  return <div>{/* ${sanitized} */}</div>;`,
          `};`,
          ``,
        ].join('\n');

      case 'service':
        return [
          `export class ${sanitized} {`,
          `  constructor() {`,
          `    // Initialise dependencies`,
          `  }`,
          ``,
          `  async execute(): Promise<void> {`,
          `    // TODO: implement`,
          `  }`,
          `}`,
          ``,
        ].join('\n');

      case 'api':
        return [
          `import type { Request, Response } from 'express';`,
          ``,
          `export async function ${sanitized}(req: Request, res: Response): Promise<void> {`,
          `  try {`,
          `    res.json({ message: 'ok' });`,
          `  } catch (err) {`,
          `    res.status(500).json({ error: 'Internal server error' });`,
          `  }`,
          `}`,
          ``,
        ].join('\n');

      case 'folder':
        return `// ${sanitized}\n`;

      default:
        return `// ${sanitized}\n`;
    }
  }

  /**
   * Update canvas from code changes
   * 
   * @doc.param fileId - File ID
   * @doc.param code - Updated code
   */
  updateCanvasFromCode(fileId: string, code: string): void {
    // Find canvas nodes that reference this file
    const relatedNodes = this.findCanvasNodesForFile(fileId);

    // Update canvas nodes with new code
    for (const node of relatedNodes) {
      this.updateCanvasNodeCode(node, code);
    }
  }

  /**
   * Locate canvas nodes that are linked to a specific IDE file.
   * Matches on `data.fileId`, `data.filePath`, or the node id convention `file-{fileId}`.
   */
  private findCanvasNodesForFile(fileId: string): CanvasNodeLite[] {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const canvasAny = this.canvasState as any;
    const nodeSource = canvasAny?.nodes ?? canvasAny?.nodeMap;
    if (!nodeSource) return [];

    const nodeList: CanvasNodeLite[] = Array.isArray(nodeSource)
      ? nodeSource
      : [...(nodeSource.values?.() ?? [])];

    return nodeList.filter((n) => {
      const node = n as CanvasNodeLite;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data = node.data as any;
      return (
        data?.fileId === fileId ||
        data?.filePath?.includes(fileId) ||
        node.id === `file-${fileId}`
      );
    }) as CanvasNodeLite[];
  }

  /**
   * Write a new code snapshot to a canvas node and push the mutation into the canvas CRDT.
   * Updates both the top-level {@code code} field and {@code data.content} for consumer compatibility.
   */
  private updateCanvasNodeCode(node: CanvasNodeLite, code: string): void {
    const updatedNode: CanvasNodeLite = {
      ...node,
      code,
      data: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ...(node.data as any),
        content: code,
      },
    };
    this.updateCanvasNode(updatedNode);
  }

  /**
   * Handle conflicts between IDE and canvas
   */
  private handleConflict(ideOperation: CRDTOperation, canvasOperation: CRDTOperation): {
    resolved: boolean;
    winner: 'ide' | 'canvas' | 'merge';
    result?: CRDTOperation;
  } {
    // Simple conflict resolution based on timestamps
    if (ideOperation.timestamp > canvasOperation.timestamp) {
      return { resolved: true, winner: 'ide', result: ideOperation };
    } else if (canvasOperation.timestamp > ideOperation.timestamp) {
      return { resolved: true, winner: 'canvas', result: canvasOperation };
    } else {
      // Same timestamp, prefer IDE operations
      return { resolved: true, winner: 'ide', result: ideOperation };
    }
  }

  /**
   * Get bridge statistics
   */
  getStatistics(): {
    filesSynced: number;
    foldersSynced: number;
    ideNodesUpdated: number;
    conflictsResolved: number;
    lastSyncTime: number;
  } {
    const ideState = this.ideHandler.getState();

    return {
      filesSynced: ideState.files.size,
      foldersSynced: ideState.folders.size,
      ideNodesUpdated: this.findIDECanvasNodes().length,
      conflictsResolved: 0, // Track conflicts in implementation
      lastSyncTime: Date.now(),
    };
  }

  /**
   * Cleanup bridge
   */
  cleanup(): void {
    this.ideHandler.removeListener('ide-canvas-bridge');
    this.syncListeners.clear();
  }
}

/**
 * IDE canvas node data interface
 */
interface IDECanvasNodeData {
  type: 'ide';
  ideState: IDECRDTState;
  width: number;
  height: number;
  isExpanded: boolean;
}
