/**
 * @ghatana/yappc-ide - IDE CRDT Operations
 * 
 * CRDT operations specific to IDE functionality including file operations,
 * editor state, and presence tracking.
 * 
 * @doc.type module
 * @doc.purpose IDE-specific CRDT operations
 * @doc.layer product
 * @doc.pattern CRDT Operations
 */

import { v4 as uuidv4 } from 'uuid';
import type { CRDTOperation, VectorClock } from '../../../crdt-ide/src';
import type { IDEFile, IDEFolder, IDETab } from '../types';

/**
 * IDE-specific CRDT operation types
 */
export type IDECRDTOperationType =
  | 'ide:createFile'
  | 'ide:updateFileContent'
  | 'ide:deleteFile'
  | 'ide:renameFile'
  | 'ide:createFolder'
  | 'ide:deleteFolder'
  | 'ide:renameFolder'
  | 'ide:updateEditorState'
  | 'ide:updatePresence'
  | 'ide:createTab'
  | 'ide:closeTab'
  | 'ide:updateTab'
  | 'ide:moveTab';

/**
 * IDE CRDT operation data types
 */
export interface IDECreateFileData {
  path: string;
  content: string;
  language: string;
  metadata: {
    createdAt: number;
    size: number;
  };
}

export interface IDEUpdateFileContentData {
  fileId: string;
  changes: Array<{
    index: number;
    delete?: number;
    insert?: string;
    retain?: number;
  }>;
}

export interface IDEDeleteFileData {
  fileId: string;
}

export interface IDERenameFileData {
  fileId: string;
  newPath: string;
  newLanguage?: string;
}

export interface IDECreateFolderData {
  path: string;
  metadata: {
    createdAt: number;
  };
}

export interface IDEDeleteFolderData {
  folderId: string;
}

export interface IIDERenameFolderData {
  folderId: string;
  newPath: string;
}

export interface IDEUpdateEditorStateData {
  userId: string;
  activeFileId: string | null;
  cursorPosition: { line: number; column: number };
  selection: {
    start: { line: number; column: number };
    end: { line: number; column: number };
  } | null;
}

export interface IDEUpdatePresenceData {
  userId: string;
  userName: string;
  userColor: string;
  activeFileId: string | null;
  cursorPosition: { line: number; column: number } | null;
  lastActivity: number;
}

export interface IDECreateTabData {
  tabId: string;
  fileId: string;
  title: string;
  isPinned: boolean;
}

export interface IDECloseTabData {
  tabId: string;
}

export interface IDEUpdateTabData {
  tabId: string;
  updates: Partial<{
    title: string;
    isDirty: boolean;
    isActive: boolean;
    isPinned: boolean;
  }>;
}

export interface IDEMoveTabData {
  tabId: string;
  newIndex: number;
}

/**
 * Create IDE CRDT operation
 * 
 * @doc.param type - Operation type
 * @doc.param data - Operation data
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.param parents - Parent operation IDs
 * @doc.returns CRDT operation
 */
export function createIDECRDTOperation<T extends IDECRDTOperationType>(
  type: T,
  data: T extends 'ide:createFile' ? IDECreateFileData :
    T extends 'ide:updateFileContent' ? IDEUpdateFileContentData :
    T extends 'ide:deleteFile' ? IDEDeleteFileData :
    T extends 'ide:renameFile' ? IDERenameFileData :
    T extends 'ide:createFolder' ? IDECreateFolderData :
    T extends 'ide:deleteFolder' ? IDEDeleteFolderData :
    T extends 'ide:renameFolder' ? IIDERenameFolderData :
    T extends 'ide:updateEditorState' ? IDEUpdateEditorStateData :
    T extends 'ide:updatePresence' ? IDEUpdatePresenceData :
    T extends 'ide:createTab' ? IDECreateTabData :
    T extends 'ide:closeTab' ? IDECloseTabData :
    T extends 'ide:updateTab' ? IDEUpdateTabData :
    T extends 'ide:moveTab' ? IDEMoveTabData :
    never,
  replicaId: string,
  vectorClock: VectorClock,
  parents: string[] = []
): CRDTOperation {
  return {
    id: uuidv4(),
    replicaId,
    type,
    targetId: 'ide-workspace',
    vectorClock,
    data,
    timestamp: Date.now(),
    parents,
  };
}

/**
 * Create file operation
 * 
 * @doc.param file - File to create
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function createFileOperation(
  file: Omit<IDEFile, 'id' | 'lastModified'>,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:createFile',
    {
      path: file.path,
      content: file.content,
      language: file.language,
      metadata: {
        createdAt: file.createdAt,
        size: file.content.length,
      },
    },
    replicaId,
    vectorClock
  );
}

/**
 * Update file content operation
 * 
 * @doc.param fileId - File ID
 * @doc.param changes - Text changes
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function updateFileContentOperation(
  fileId: string,
  changes: IDEUpdateFileContentData['changes'],
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:updateFileContent',
    { fileId, changes },
    replicaId,
    vectorClock
  );
}

/**
 * Delete file operation
 * 
 * @doc.param fileId - File ID
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function deleteFileOperation(
  fileId: string,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:deleteFile',
    { fileId },
    replicaId,
    vectorClock
  );
}

/**
 * Rename file operation
 * 
 * @doc.param fileId - File ID
 * @doc.param newPath - New file path
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function renameFileOperation(
  fileId: string,
  newPath: string,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:renameFile',
    { fileId, newPath },
    replicaId,
    vectorClock
  );
}

/**
 * Create folder operation
 * 
 * @doc.param folder - Folder to create
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function createFolderOperation(
  folder: Omit<IDEFolder, 'id'>,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:createFolder',
    {
      path: folder.path,
      metadata: {
        createdAt: folder.createdAt,
      },
    },
    replicaId,
    vectorClock
  );
}

/**
 * Delete folder operation
 * 
 * @doc.param folderId - Folder ID
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function deleteFolderOperation(
  folderId: string,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:deleteFolder',
    { folderId },
    replicaId,
    vectorClock
  );
}

/**
 * Rename folder operation
 * 
 * @doc.param folderId - Folder ID
 * @doc.param newPath - New folder path
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function renameFolderOperation(
  folderId: string,
  newPath: string,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:renameFolder',
    { folderId, newPath },
    replicaId,
    vectorClock
  );
}

/**
 * Update editor state operation
 * 
 * @doc.param editorState - Editor state to update
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function updateEditorStateOperation(
  editorState: IDEUpdateEditorStateData,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:updateEditorState',
    editorState,
    replicaId,
    vectorClock
  );
}

/**
 * Update presence operation
 * 
 * @doc.param presence - Presence data
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function updatePresenceOperation(
  presence: IDEUpdatePresenceData,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:updatePresence',
    presence,
    replicaId,
    vectorClock
  );
}

/**
 * Create tab operation
 * 
 * @doc.param tab - Tab to create
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function createTabOperation(
  tab: Omit<IDETab, 'id'>,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:createTab',
    {
      tabId: (tab as unknown).id || uuidv4(),
      fileId: tab.fileId,
      title: tab.title,
      isPinned: tab.isPinned,
    },
    replicaId,
    vectorClock
  );
}

/**
 * Close tab operation
 * 
 * @doc.param tabId - Tab ID
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function closeTabOperation(
  tabId: string,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:closeTab',
    { tabId },
    replicaId,
    vectorClock
  );
}

/**
 * Update tab operation
 * 
 * @doc.param tabId - Tab ID
 * @doc.param updates - Tab updates
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function updateTabOperation(
  tabId: string,
  updates: IDEUpdateTabData['updates'],
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:updateTab',
    { tabId, updates },
    replicaId,
    vectorClock
  );
}

/**
 * Move tab operation
 * 
 * @doc.param tabId - Tab ID
 * @doc.param newIndex - New index
 * @doc.param replicaId - Replica ID
 * @doc.param vectorClock - Vector clock
 * @doc.returns CRDT operation
 */
export function moveTabOperation(
  tabId: string,
  newIndex: number,
  replicaId: string,
  vectorClock: VectorClock
): CRDTOperation {
  return createIDECRDTOperation(
    'ide:moveTab',
    { tabId, newIndex },
    replicaId,
    vectorClock
  );
}

/**
 * Validate IDE CRDT operation
 * 
 * @doc.param operation - Operation to validate
 * @doc.returns Validation result
 */
export function validateIDECRDTOperation(operation: CRDTOperation): {
  valid: boolean;
  error?: string;
} {
  // Basic validation
  if (!operation.id || !operation.replicaId || !operation.targetId) {
    return { valid: false, error: 'Missing required fields' };
  }

  // Type-specific validation
  switch (operation.type) {
    case 'ide:createFile':
      const createData = operation.data as IDECreateFileData;
      if (!createData.path || !createData.language) {
        return { valid: false, error: 'Missing file path or language' };
      }
      break;

    case 'ide:updateFileContent':
      const updateData = operation.data as IDEUpdateFileContentData;
      if (!updateData.fileId || !updateData.changes) {
        return { valid: false, error: 'Missing file ID or changes' };
      }
      break;

    case 'ide:deleteFile':
    case 'ide:deleteFolder':
      const deleteData = operation.data as IDEDeleteFileData | IDEDeleteFolderData;
      if (!('fileId' in deleteData) && !('folderId' in deleteData)) {
        return { valid: false, error: 'Missing file or folder ID' };
      }
      break;

    case 'ide:renameFile':
      const renameData = operation.data as IDERenameFileData;
      if (!renameData.fileId || !renameData.newPath) {
        return { valid: false, error: 'Missing file ID or new path' };
      }
      break;

    case 'ide:updateEditorState':
      const editorData = operation.data as IDEUpdateEditorStateData;
      if (!editorData.userId) {
        return { valid: false, error: 'Missing user ID' };
      }
      break;

    case 'ide:updatePresence':
      const presenceData = operation.data as IDEUpdatePresenceData;
      if (!presenceData.userId || !presenceData.userName) {
        return { valid: false, error: 'Missing user ID or name' };
      }
      break;

    default:
      return { valid: false, error: 'Unknown operation type' };
  }

  return { valid: true };
}

/**
 * Get operation priority for conflict resolution
 * 
 * @doc.param operation - CRDT operation
 * @doc.returns Operation priority
 */
export function getOperationPriority(operation: CRDTOperation): number {
  switch (operation.type) {
    case 'ide:deleteFile':
    case 'ide:deleteFolder':
      return 1; // High priority for destructive operations
    case 'ide:createFile':
    case 'ide:createFolder':
      return 2; // Medium priority for creation
    case 'ide:updateFileContent':
      return 3; // Lower priority for updates
    case 'ide:renameFile':
    case 'ide:renameFolder':
      return 4; // Lower priority for renames
    case 'ide:updateEditorState':
    case 'ide:updatePresence':
      return 5; // Lowest priority for state updates
    case 'ide:createTab':
    case 'ide:closeTab':
    case 'ide:updateTab':
    case 'ide:moveTab':
      return 6; // Lowest priority for UI operations
    default:
      return 10;
  }
}
