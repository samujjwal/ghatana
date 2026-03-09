/**
 * @ghatana/yappc-ide - Core Type Definitions
 *
 * Type definitions for the Collaborative Polyglot IDE.
 *
 * @doc.type module
 * @doc.purpose Core type definitions for IDE functionality
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

import type { CanvasNode } from '@ghatana/canvas';

/**
 * IDE file metadata with stable UUID identity
 */
export interface IDEFile {
  id: string; // UUID for stable identity
  path: string;
  name: string;
  content: string;
  language: string;
  isDirty: boolean;
  isOpen: boolean;
  lastModified: number;
  createdAt: number;
  size: number;
}

/**
 * IDE folder structure
 */
export interface IDEFolder {
  id: string; // UUID for stable identity
  path: string;
  name: string;
  children: Array<IDEFile | IDEFolder>;
  isExpanded: boolean;
  createdAt: number;
}

/**
 * File tree node type
 */
export type FileTreeNode = IDEFile | IDEFolder;

/**
 * IDE editor tab
 */
export interface IDETab {
  id: string;
  fileId: string;
  title: string;
  isDirty: boolean;
  isActive: boolean;
  isPinned: boolean;
}

/**
 * IDE editor state
 */
export interface IDEEditorState {
  activeFileId: string | null;
  openTabs: IDETab[];
  cursorPosition: { line: number; column: number };
  selection: {
    start: { line: number; column: number };
    end: { line: number; column: number };
  } | null;
  scrollPosition: { top: number; left: number };
}

/**
 * IDE panel types
 */
export type IDEPanelType =
  | 'explorer'
  | 'search'
  | 'git'
  | 'debug'
  | 'extensions'
  | 'terminal'
  | 'output'
  | 'problems';

/**
 * IDE panel state
 */
export interface IDEPanelState {
  type: IDEPanelType;
  isVisible: boolean;
  width: number;
  position: 'left' | 'right' | 'bottom';
}

/**
 * IDE layout configuration
 */
export interface IDELayout {
  sidePanels: IDEPanelState[];
  bottomPanels: IDEPanelState[];
  editorGroups: IDEEditorGroup[];
}

/**
 * IDE editor group for split views
 */
export interface IDEEditorGroup {
  id: string;
  tabs: IDETab[];
  activeTabId: string | null;
  orientation: 'horizontal' | 'vertical';
}

/**
 * IDE workspace settings
 */
export interface IDEWorkspaceSettings {
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
}

/**
 * IDE presence information for collaboration
 */
export interface IDEPresence {
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
  isOnline?: boolean;
}

/**
 * IDE state for CRDT synchronization
 */
export interface IDECRDTState {
  files: Record<string, IDEFile>;
  folders: Record<string, IDEFolder>;
  rootFolderId: string | null;
  editorState: IDEEditorState;
  settings: IDEWorkspaceSettings;
  presence: Record<string, IDEPresence>;
  canvas: Record<string, unknown>;
}

/**
 * Full IDE state including layout
 */
export interface IDEState extends IDECRDTState {
  layout: IDELayout;
}

/**
 * IDE canvas node data for embedding IDE in canvas
 */
export interface IDECanvasNodeData extends Record<string, unknown> {
  type: 'ide';
  ideState: IDEState;
  width: number;
  height: number;
  isExpanded: boolean;
}

/**
 * IDE canvas node
 */
export interface IDECanvasNode extends CanvasNode {
  data: IDECanvasNodeData;
}

/**
 * IDE file operation types
 */
export type IDEFileOperation =
  | { type: 'create'; path: string; content: string; language: string }
  | { type: 'update'; fileId: string; content: string }
  | { type: 'delete'; fileId: string }
  | { type: 'rename'; fileId: string; newPath: string }
  | { type: 'move'; fileId: string; newParentId: string };

/**
 * IDE folder operation types
 */
export type IDEFolderOperation =
  | { type: 'create'; path: string; parentId: string }
  | { type: 'delete'; folderId: string }
  | { type: 'rename'; folderId: string; newPath: string }
  | { type: 'move'; folderId: string; newParentId: string };

/**
 * Language support configuration
 */
export interface LanguageConfig {
  id: string;
  name: string;
  extensions: string[];
  mimeTypes: string[];
  aliases: string[];
  supportsLSP: boolean;
  supportsFormatting: boolean;
  supportsCodeGeneration: boolean;
}

/**
 * LSP diagnostic severity
 */
export enum DiagnosticSeverity {
  Error = 1,
  Warning = 2,
  Information = 3,
  Hint = 4,
}

/**
 * LSP diagnostic
 */
export interface Diagnostic {
  range: {
    start: { line: number; character: number };
    end: { line: number; character: number };
  };
  severity: DiagnosticSeverity;
  message: string;
  source?: string;
  code?: string | number;
}

/**
 * IDE command
 */
export interface IDECommand {
  id: string;
  title: string;
  category?: string;
  keybinding?: string;
  when?: string;
  handler: () => void | Promise<void>;
}

/**
 * IDE context menu item
 */
export interface IDEContextMenuItem {
  id: string;
  label: string;
  icon?: string;
  command: string;
  when?: string;
  group?: string;
  order?: number;
}

/**
 * IDE version
 */
export const IDE_VERSION = '0.1.0';
