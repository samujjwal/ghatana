/**
 * @ghatana/yappc-ide - IDE CRDT Schema
 * 
 * CRDT schema definition for IDE state that extends the existing YAPPC canvas schema.
 * 
 * @doc.type module
 * @doc.purpose IDE CRDT schema definition
 * @doc.layer product
 * @doc.pattern CRDT Schema
 */

import * as Y from 'yjs';
import type { CRDTState } from '@ghatana/yappc-crdt/core';
import type { IDEFile, IDEFolder, IDEPresence } from '../types';

/**
 * IDE CRDT schema
 * 
 * Extends the existing YAPPC canvas CRDT state with IDE-specific data.
 * Uses Yjs Map for collaborative text editing and Map for structured data.
 */
export interface IDECRDTState {
  /** Existing canvas state */
  canvas: Record<string, unknown>; // Will be populated with existing canvas CRDT state

  /** IDE files with collaborative text editing */
  files: Y.Map<{
    id: string;
    path: string;
    content: unknown; // Yjs Text for collaborative editing
    language: string;
    metadata: {
      createdAt: number;
      modifiedAt: number;
      size: number;
      createdBy: string;
      modifiedBy: string;
    };
  }>;

  /** IDE folders */
  folders: Y.Map<{
    id: string;
    path: string;
    children: string[];
    metadata: {
      createdAt: number;
      createdBy: string;
    };
  }>;

  /** Root folder reference */
  rootFolderId: string | null;

  /** Editor state per user */
  editorState: Y.Map<{
    activeFileId: string | null;
    cursorPosition: { line: number; column: number };
    selection: {
      start: { line: number; column: number };
      end: { line: number; column: number };
    } | null;
    openTabs: string[];
    scrollPosition: { top: number; left: number };
    lastActivity: number;
  }>;

  /** Presence information */
  presence: Y.Map<{
    userId: string;
    userName: string;
    userColor: string;
    activeFileId: string | null;
    cursorPosition: { line: number; column: number } | null;
    selection: {
      start: { line: number; column: number };
      end: { line: number; column: number };
    } | null;
    lastActivity: number;
    isOnline: boolean;
  }>;

  /** IDE settings */
  settings: Y.Map<{
    theme: 'light' | 'dark' | 'high-contrast';
    fontSize: number;
    tabSize: number;
    insertSpaces: boolean;
    autoSave: 'off' | 'afterDelay' | 'onFocusChange' | 'onWindowChange';
    autoSaveDelay: number;
    formatOnSave: boolean;
    formatOnPaste: boolean;
    minimap: boolean;
    lineNumbers: boolean;
    wordWrap: boolean;
  }>;
}

// Type aliases for YMap value types
type IDEFileCRDT = {
  id: string;
  path: string;
  content: unknown;
  language: string;
  metadata: {
    createdAt: number;
    modifiedAt: number;
    size: number;
    createdBy: string;
    modifiedBy: string;
  };
};

type IDEFolderCRDT = {
  id: string;
  path: string;
  children: string[];
  metadata: {
    createdAt: number;
    createdBy: string;
  };
};

type IDEPresenceCRDT = {
  userId: string;
  userName: string;
  userColor: string;
  activeFileId: string | null;
  cursorPosition: { line: number; column: number } | null;
  selection: {
    start: { line: number; column: number };
    end: { line: number; column: number };
  } | null;
  lastActivity: number;
  isOnline: boolean;
};

/**
 * IDE CRDT state wrapper for CRDT core integration
 */
export interface IDECRDTStateWrapper extends CRDTState {
  state: IDECRDTState;
}

/**
 * Create initial IDE CRDT state
 * 
 * @doc.returns Initial IDE CRDT state
 */
export function createInitialIDEState(): IDECRDTState {
  return {
    canvas: {}, // Will be populated with existing canvas state
    // Use plain JS Maps for test-friendly CRDT-like state in unit tests
    files: new Map() as unknown as Y.Map<unknown>,
    folders: new Map() as unknown as Y.Map<unknown>,
    rootFolderId: null,
    editorState: new Map() as unknown as Y.Map<unknown>,
    presence: new Map() as unknown as Y.Map<unknown>,
    settings: new Map() as unknown as Y.Map<unknown>,
  };
}

/**
 * Convert IDE file to CRDT format
 * 
 * @doc.param file - IDE file to convert
 * @doc.returns CRDT file data
 */
export function fileToCRDT(file: IDEFile): [string, IDEFileCRDT] {
  return [
    file.id,
    {
      id: file.id,
      path: file.path,
      content: new Map(), // Will be populated with Yjs Text
      language: file.language,
      metadata: {
        createdAt: file.createdAt,
        modifiedAt: file.lastModified,
        size: file.size,
        createdBy: 'system',
        modifiedBy: 'system',
      },
    },
  ];
}

/**
 * Convert CRDT file to IDE file
 * 
 * @doc.param id - File ID
 * @doc.param crdtFile - CRDT file data
 * @doc.returns IDE file
 */
export function crdtToFile(id: string, crdtFile: IDEFileCRDT): IDEFile {
  return {
    id,
    path: crdtFile.path,
    name: crdtFile.path.split('/').pop() || 'untitled',
    content: '', // Will be populated from Yjs Text
    language: crdtFile.language,
    isDirty: false,
    isOpen: false,
    lastModified: crdtFile.metadata.modifiedAt,
    createdAt: crdtFile.metadata.createdAt,
    size: crdtFile.metadata.size,
  };
}

/**
 * Convert IDE folder to CRDT format
 * 
 * @doc.param folder - IDE folder to convert
 * @doc.returns CRDT folder data
 */
export function folderToCRDT(folder: IDEFolder): [string, IDEFolderCRDT] {
  return [
    folder.id,
    {
      id: folder.id,
      path: folder.path,
      children: folder.children.map(child => child.id),
      metadata: {
        createdAt: folder.createdAt,
        createdBy: 'system',
      },
    },
  ];
}

/**
 * Convert CRDT folder to IDE folder
 * 
 * @doc.param id - Folder ID
 * @doc.param crdtFolder - CRDT folder data
 * @doc.returns IDE folder
 */
export function crdtToFolder(id: string, crdtFolder: IDEFolderCRDT): IDEFolder {
  return {
    id,
    path: crdtFolder.path,
    name: crdtFolder.path.split('/').pop() || 'untitled',
    children: crdtFolder.children.map((childId: string) => ({
      id: childId,
      path: '',
      name: '',
      children: [],
      isExpanded: false,
      createdAt: 0
    })),
    isExpanded: false,
    createdAt: crdtFolder.metadata.createdAt,
  };
}

/**
 * Convert IDE presence to CRDT format
 * 
 * @doc.param presence - IDE presence to convert
 * @doc.returns CRDT presence data
 */
export function presenceToCRDT(presence: IDEPresence): [string, IDEPresenceCRDT] {
  return [
    presence.userId,
    {
      userId: presence.userId,
      userName: presence.userName,
      userColor: presence.userColor,
      activeFileId: presence.activeFileId,
      cursorPosition: presence.cursorPosition,
      selection: presence.selection,
      lastActivity: presence.lastActivity,
      isOnline: true,
    },
  ];
}

/**
 * Convert CRDT presence to IDE presence
 * 
 * @doc.param id - User ID
 * @doc.param crdtPresence - CRDT presence data
 * @doc.returns IDE presence
 */
export function crdtToPresence(id: string, crdtPresence: IDEPresenceCRDT): IDEPresence {
  return {
    userId: id,
    userName: crdtPresence.userName,
    userColor: crdtPresence.userColor,
    activeFileId: crdtPresence.activeFileId,
    cursorPosition: crdtPresence.cursorPosition,
    selection: crdtPresence.selection,
    lastActivity: crdtPresence.lastActivity,
  };
}

/**
 * Validate IDE CRDT state
 * 
 * @doc.param state - IDE CRDT state to validate
 * @doc.returns Validation result
 */
export function validateIDEState(state: IDECRDTState): {
  valid: boolean;
  errors: string[];
  warnings: string[];
} {
  const errors: string[] = [];
  const warnings: string[] = [];

  // Validate files
  for (const [fileId, file] of state.files) {
    if (!file.id || file.id !== fileId) {
      errors.push(`File ${fileId} has invalid ID`);
    }
    if (!file.path) {
      errors.push(`File ${fileId} has no path`);
    }
    if (!file.language) {
      warnings.push(`File ${fileId} has no language`);
    }
    if (!file.metadata) {
      errors.push(`File ${fileId} has no metadata`);
    }
  }

  // Validate folders
  for (const [folderId, folder] of state.folders) {
    if (!folder.id || folder.id !== folderId) {
      errors.push(`Folder ${folderId} has invalid ID`);
    }
    if (!folder.path) {
      errors.push(`Folder ${folderId} has no path`);
    }
    if (!Array.isArray(folder.children)) {
      errors.push(`Folder ${folderId} has invalid children`);
    }
  }

  // Validate root folder
  if (state.rootFolderId && !state.folders.has(state.rootFolderId)) {
    errors.push(`Root folder ${state.rootFolderId} does not exist`);
  }

  // Validate presence
  for (const [userId, presence] of state.presence) {
    if (!userId || userId !== presence.userId) {
      errors.push(`Presence ${userId} has invalid user ID`);
    }
    if (!presence.userName) {
      errors.push(`Presence ${userId} has no user name`);
    }
    if (!presence.userColor) {
      warnings.push(`Presence ${userId} has no user color`);
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

/**
 * Get IDE state statistics
 * 
 * @doc.param state - IDE CRDT state
 * @doc.returns State statistics
 */
export function getIDEStateStats(state: IDECRDTState): {
  files: number;
  folders: number;
  activeUsers: number;
  totalUsers: number;
  dirtyFiles: number;
  totalSize: number;
} {
  return {
    files: state.files.size,
    folders: state.folders.size,
    activeUsers: Array.from(state.presence.values()).filter((p: unknown) =>
      (p as IDEPresence & { isOnline?: boolean }).isOnline || false
    ).length,
    totalUsers: state.presence.size,
    dirtyFiles: Array.from(state.files.values()).filter((f: unknown) => {
      const fileData = f as { content?: { toString(): string } };
      return fileData.content?.toString().length !== undefined && fileData.content.toString().length > 0;
    }).length,
    totalSize: Array.from(state.files.values()).reduce((total: number, file: unknown) => {
      const fileData = file as { metadata?: { size?: number } };
      return total + (fileData.metadata?.size || 0);
    }, 0),
  };
}
