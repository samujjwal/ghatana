/**
 * @ghatana/yappc-ide - IDE CRDT Handler
 * 
 * Handles IDE-specific CRDT operations and state management.
 * Integrates with existing CRDT core infrastructure.
 * 
 * @doc.type module
 * @doc.purpose IDE CRDT operation handler
 * @doc.layer product
 * @doc.pattern CRDT Handler
 */

import { Text } from 'yjs';
// Use local CRDT types to avoid coupling IDE package to top-level CRDT build during type-check
import type { CRDTOperation } from '../../../crdt-ide/src';
import type { IDECRDTState } from './ide-schema';
import type { IDECRDTOperationType } from './ide-operations';

/**
 * IDE CRDT handler
 * 
 * Processes IDE-specific CRDT operations and updates state.
 */
export class IDECRDTHandler {
  private state: IDECRDTState;
  private listeners: Map<string, (state: IDECRDTState) => void> = new Map();

  constructor(initialState: IDECRDTState) {
    this.state = initialState;
  }

  /**
   * Apply IDE CRDT operation
   * 
   * @doc.param operation - CRDT operation to apply
   * @doc.returns Operation result
   */
  applyOperation(operation: CRDTOperation): {
    success: boolean;
    error?: string;
    conflicts?: string[];
  } {
    try {
      const type = operation.type as IDECRDTOperationType;

      switch (type) {
        case 'ide:createFile':
          return this.handleCreateFile(operation);
        case 'ide:updateFileContent':
          return this.handleUpdateFileContent(operation);
        case 'ide:deleteFile':
          return this.handleDeleteFile(operation);
        case 'ide:renameFile':
          return this.handleRenameFile(operation);
        case 'ide:createFolder':
          return this.handleCreateFolder(operation);
        case 'ide:deleteFolder':
          return this.handleDeleteFolder(operation);
        case 'ide:renameFolder':
          return this.handleRenameFolder(operation);
        case 'ide:updateEditorState':
          return this.handleUpdateEditorState(operation);
        case 'ide:updatePresence':
          return this.handleUpdatePresence(operation);
        case 'ide:createTab':
          return this.handleCreateTab(operation);
        case 'ide:closeTab':
          return this.handleCloseTab(operation);
        case 'ide:updateTab':
          return this.handleUpdateTab(operation);
        case 'ide:moveTab':
          return this.handleMoveTab(operation);
        default:
          return { success: false, error: `Unknown operation type: ${type}` };
      }
    } catch (error) {
      return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
    }
  }

  /**
   * Handle create file operation
   */
  private handleCreateFile(operation: CRDTOperation): { success: boolean; error?: string } {
    const { path, content, language, metadata } = operation.data;

    // Check if file already exists
    for (const [, file] of this.state.files) {
      if (file.path === path) {
        return { success: false, error: `File already exists: ${path}` };
      }
    }

    // Create file with collaborative text
    const fileId = operation.id;
    // For unit tests and simple scenarios, store plain string content.
    // In a full collaborative environment this should be a Y.Text attached to a Y.Doc.
    this.state.files.set(fileId, {
      id: fileId,
      path,
      content: content || '',
      language,
      metadata: {
        ...metadata,
        createdBy: operation.replicaId,
        modifiedBy: operation.replicaId,
      },
    });

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle update file content operation
   */
  private handleUpdateFileContent(operation: CRDTOperation): { success: boolean; error?: string } {
    const { fileId, changes } = operation.data;
    const file = this.state.files.get(fileId);

    if (!file) {
      return { success: false, error: `File not found: ${fileId}` };
    }

    // Apply changes to either a plain string or Yjs Text
    const currentContent = file.content;

    if (typeof currentContent === 'string') {
      let s = currentContent;
      for (const change of changes) {
        if (change.delete) {
          s = s.slice(0, change.index) + s.slice(change.index + change.delete);
        }
        if (change.insert) {
          s = s.slice(0, change.index) + change.insert + s.slice(change.index);
        }
      }
      file.content = s;
      // Update metadata
      file.metadata.modifiedAt = operation.timestamp;
      file.metadata.modifiedBy = operation.replicaId;
      file.metadata.size = (file.content as string).length;
    } else if (currentContent instanceof Text) {
      for (const change of changes) {
        if (change.delete) {
          currentContent.delete(change.index, change.delete);
        }
        if (change.insert) {
          currentContent.insert(change.index, change.insert);
        }
      }
      file.metadata.modifiedAt = operation.timestamp;
      file.metadata.modifiedBy = operation.replicaId;
      file.metadata.size = (currentContent as Text).length;
    }

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle delete file operation
   */
  private handleDeleteFile(operation: CRDTOperation): { success: boolean; error?: string } {
    const { fileId } = operation.data;
    const file = this.state.files.get(fileId);

    if (!file) {
      return { success: false, error: `File not found: ${fileId}` };
    }

    // Remove file
    this.state.files.delete(fileId);

    // Remove from any open tabs
    for (const [, editorState] of this.state.editorState) {
      if (editorState.activeFileId === fileId) {
        editorState.activeFileId = null;
      }
      editorState.openTabs = editorState.openTabs.filter(id => id !== fileId);
    }

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle rename file operation
   */
  private handleRenameFile(operation: CRDTOperation): { success: boolean; error?: string } {
    const { fileId, newPath, newLanguage } = operation.data;
    const file = this.state.files.get(fileId);

    if (!file) {
      return { success: false, error: `File not found: ${fileId}` };
    }

    // Check if new path conflicts
    for (const [, existingFile] of this.state.files) {
      if (existingFile.id !== fileId && existingFile.path === newPath) {
        return { success: false, error: `File already exists: ${newPath}` };
      }
    }

    // Update file
    file.path = newPath;
    if (newLanguage) {
      file.language = newLanguage;
    }
    file.metadata.modifiedAt = operation.timestamp;
    file.metadata.modifiedBy = operation.replicaId;

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle create folder operation
   */
  private handleCreateFolder(operation: CRDTOperation): { success: boolean; error?: string } {
    const { path, metadata } = operation.data;

    // Check if folder already exists
    for (const [, folder] of this.state.folders) {
      if (folder.path === path) {
        return { success: false, error: `Folder already exists: ${path}` };
      }
    }

    // Create folder
    const folderId = operation.id;
    this.state.folders.set(folderId, {
      id: folderId,
      path,
      children: [],
      metadata: {
        ...metadata,
        createdBy: operation.replicaId,
      },
    });

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle delete folder operation
   */
  private handleDeleteFolder(operation: CRDTOperation): { success: boolean; error?: string } {
    const { folderId } = operation.data;
    const folder = this.state.folders.get(folderId);

    if (!folder) {
      return { success: false, error: `Folder not found: ${folderId}` };
    }

    // Check if folder is not empty
    if (folder.children.length > 0) {
      return { success: false, error: `Folder not empty: ${folder.path}` };
    }

    // Remove folder
    this.state.folders.delete(folderId);

    // Update root folder reference if needed
    if (this.state.rootFolderId === folderId) {
      this.state.rootFolderId = null;
    }

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle rename folder operation
   */
  private handleRenameFolder(operation: CRDTOperation): { success: boolean; error?: string } {
    const { folderId, newPath } = operation.data;
    const folder = this.state.folders.get(folderId);

    if (!folder) {
      return { success: false, error: `Folder not found: ${folderId}` };
    }

    // Check if new path conflicts
    for (const [id, existingFolder] of this.state.folders) {
      if (id !== folderId && existingFolder.path === newPath) {
        return { success: false, error: `Folder already exists: ${newPath}` };
      }
    }

    // Update folder
    folder.path = newPath;

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle update editor state operation
   */
  private handleUpdateEditorState(operation: CRDTOperation): { success: boolean; error?: string } {
    const { userId, activeFileId, cursorPosition, selection } = operation.data;

    // Update or create editor state
    const editorState = this.state.editorState.get(userId) || {
      activeFileId: null,
      cursorPosition: { line: 0, column: 0 },
      selection: null,
      openTabs: [],
      scrollPosition: { top: 0, left: 0 },
      lastActivity: Date.now(),
    };

    editorState.activeFileId = activeFileId;
    editorState.cursorPosition = cursorPosition;
    editorState.selection = selection;
    editorState.lastActivity = operation.timestamp;

    this.state.editorState.set(userId, editorState);

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle update presence operation
   */
  private handleUpdatePresence(operation: CRDTOperation): { success: boolean; error?: string } {
    const { userId, userName, userColor, activeFileId, cursorPosition, lastActivity } = operation.data;

    // Update presence
    this.state.presence.set(userId, {
      userId,
      userName,
      userColor,
      activeFileId,
      cursorPosition,
      selection: null, // Will be updated via editor state
      lastActivity,
      isOnline: true,
    });

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle create tab operation
   */
  private handleCreateTab(operation: CRDTOperation): { success: boolean; error?: string } {
    const { tabId, fileId } = operation.data;

    // Allow creating tabs even if the file is not yet present in the IDE state.
    // Add to user's open tabs
    const userId = operation.replicaId;
    const editorState = this.state.editorState.get(userId) || {
      activeFileId: null,
      cursorPosition: { line: 0, column: 0 },
      selection: null,
      openTabs: [],
      scrollPosition: { top: 0, left: 0 },
      lastActivity: Date.now(),
    };

    if (!editorState.openTabs.includes(tabId)) {
      editorState.openTabs.push(tabId);
    }

    editorState.activeFileId = fileId;
    editorState.lastActivity = operation.timestamp;

    this.state.editorState.set(userId, editorState);

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle close tab operation
   */
  private handleCloseTab(operation: CRDTOperation): { success: boolean; error?: string } {
    const { tabId } = operation.data;
    const userId = operation.replicaId;
    const editorState = this.state.editorState.get(userId);

    if (!editorState) {
      return { success: false, error: `No editor state for user: ${userId}` };
    }

    // Remove from open tabs
    editorState.openTabs = editorState.openTabs.filter(id => id !== tabId);

    // Update active file if needed
    if (editorState.activeFileId === tabId) {
      const remainingTabs = editorState.openTabs;
      editorState.activeFileId = remainingTabs.length > 0 ? remainingTabs[remainingTabs.length - 1] : null;
    }

    editorState.lastActivity = operation.timestamp;

    this.state.editorState.set(userId, editorState);

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle update tab operation
   */
  private handleUpdateTab(operation: CRDTOperation): { success: boolean; error?: string } {
    const { tabId: _tabId, updates: _updates } = operation.data;

    // Tab updates are handled at the component level
    // This is a placeholder for future tab state synchronization

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Handle move tab operation
   */
  private handleMoveTab(operation: CRDTOperation): { success: boolean; error?: string } {
    const { tabId, newIndex } = operation.data;
    const userId = operation.replicaId;
    const editorState = this.state.editorState.get(userId);

    if (!editorState) {
      return { success: false, error: `No editor state for user: ${userId}` };
    }

    // Move tab in open tabs array
    const currentIndex = editorState.openTabs.indexOf(tabId);
    if (currentIndex !== -1) {
      const [tab] = editorState.openTabs.splice(currentIndex, 1);
      editorState.openTabs.splice(newIndex, 0, tab);
    }

    editorState.lastActivity = operation.timestamp;

    this.state.editorState.set(userId, editorState);

    this.notifyListeners();
    return { success: true };
  }

  /**
   * Get current state
   * 
   * @doc.returns Current IDE CRDT state
   */
  getState(): IDECRDTState {
    return this.state;
  }

  /**
   * Add state change listener
   * 
   * @doc.param id - Listener ID
   * @doc.param listener - Listener function
   */
  addListener(id: string, listener: (state: IDECRDTState) => void): void {
    this.listeners.set(id, listener);
  }

  /**
   * Remove state change listener
   * 
   * @doc.param id - Listener ID
   */
  removeListener(id: string): void {
    this.listeners.delete(id);
  }

  /**
   * Notify all listeners of state change
   */
  private notifyListeners(): void {
    for (const listener of this.listeners.values()) {
      listener(this.state);
    }
  }

  /**
   * Get file content as string
   * 
   * @doc.param fileId - File ID
   * @doc.returns File content
   */
  getFileContent(fileId: string): string {
    const file = this.state.files.get(fileId);
    if (!file) return '';

    const content = file.content;
    if (typeof content === 'string') return content;
    if (content instanceof Text) return content.toString();

    return '';
  }

  /**
   * Set file content
   * 
   * @doc.param fileId - File ID
   * @doc.param content - New content
   */
  setFileContent(fileId: string, content: string): void {
    const file = this.state.files.get(fileId);
    if (!file) return;

    if (file.content instanceof Text) {
      file.content.delete(0, file.content.length);
      file.content.insert(0, content);
      file.metadata.modifiedAt = Date.now();
      file.metadata.size = content.length;
    }

    this.notifyListeners();
  }
}
