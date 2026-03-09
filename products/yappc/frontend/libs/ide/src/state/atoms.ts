/**
 * @ghatana/yappc-ide - State Management Atoms
 * 
 * Jotai atoms for IDE state management following YAPPC patterns.
 * 
 * @doc.type module
 * @doc.purpose IDE state management using Jotai
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type {
  IDEState,
  IDEFile,
  IDEFolder,
  IDEEditorState,
  IDELayout,
  IDEWorkspaceSettings,
  IDEPresence,
  IDETab,
  IDEPanelState,
} from '../types';

/**
 * Default IDE workspace settings
 */
const DEFAULT_SETTINGS: IDEWorkspaceSettings = {
  theme: 'dark',
  fontSize: 14,
  tabSize: 2,
  insertSpaces: true,
  autoSave: 'afterDelay',
  autoSaveDelay: 1000,
  formatOnSave: true,
  formatOnPaste: true,
  minimap: true,
  lineNumbers: true,
  wordWrap: true,
};

/**
 * Default IDE layout
 */
const DEFAULT_LAYOUT: IDELayout = {
  sidePanels: [
    {
      type: 'explorer',
      isVisible: true,
      width: 250,
      position: 'left',
    },
  ],
  bottomPanels: [
    {
      type: 'terminal',
      isVisible: false,
      width: 300,
      position: 'bottom',
    },
    {
      type: 'problems',
      isVisible: false,
      width: 300,
      position: 'bottom',
    },
  ],
  editorGroups: [
    {
      id: 'default',
      tabs: [],
      activeTabId: null,
      orientation: 'horizontal',
    },
  ],
};

/**
 * Default IDE editor state
 */
const DEFAULT_EDITOR_STATE: IDEEditorState = {
  activeFileId: null,
  openTabs: [],
  cursorPosition: { line: 0, column: 0 },
  selection: null,
  scrollPosition: { top: 0, left: 0 },
};

/**
 * Root IDE state atom
 */
export const ideStateAtom = atom<IDEState>({
  canvas: {},
  files: {},
  folders: {},
  rootFolderId: null,
  editorState: DEFAULT_EDITOR_STATE,
  layout: DEFAULT_LAYOUT,
  settings: DEFAULT_SETTINGS,
  presence: {},
});

/**
 * Files atom - all files in the workspace
 */
export const ideFilesAtom = atom(
  (get) => get(ideStateAtom).files,
  (get, set, files: Record<string, IDEFile>) => {
    set(ideStateAtom, { ...get(ideStateAtom), files });
  }
);

/**
 * Folders atom - all folders in the workspace
 */
export const ideFoldersAtom = atom(
  (get) => get(ideStateAtom).folders,
  (get, set, folders: Record<string, IDEFolder>) => {
    set(ideStateAtom, { ...get(ideStateAtom), folders });
  }
);

/**
 * Root folder ID atom
 */
export const ideRootFolderIdAtom = atom(
  (get) => get(ideStateAtom).rootFolderId,
  (get, set, rootFolderId: string | null) => {
    set(ideStateAtom, { ...get(ideStateAtom), rootFolderId });
  }
);

/**
 * Editor state atom
 */
export const ideEditorStateAtom = atom(
  (get) => get(ideStateAtom).editorState,
  (get, set, editorState: IDEEditorState) => {
    set(ideStateAtom, { ...get(ideStateAtom), editorState });
  }
);

/**
 * Active file ID atom
 */
export const ideActiveFileIdAtom = atom(
  (get) => get(ideStateAtom).editorState.activeFileId,
  (get, set, activeFileId: string | null) => {
    const editorState = get(ideStateAtom).editorState;
    set(ideStateAtom, {
      ...get(ideStateAtom),
      editorState: { ...editorState, activeFileId },
    });
  }
);

/**
 * Active file atom - derived from active file ID
 */
export const ideActiveFileAtom = atom((get) => {
  const activeFileId = get(ideActiveFileIdAtom);
  if (!activeFileId) return null;
  return get(ideFilesAtom)[activeFileId] || null;
});

/**
 * Open tabs atom
 */
export const ideOpenTabsAtom = atom(
  (get) => get(ideStateAtom).editorState.openTabs,
  (get, set, openTabs: IDETab[]) => {
    const editorState = get(ideStateAtom).editorState;
    set(ideStateAtom, {
      ...get(ideStateAtom),
      editorState: { ...editorState, openTabs },
    });
  }
);

/**
 * Tabs atom (alias for open tabs)
 */
export const ideTabsAtom = ideOpenTabsAtom;

/**
 * Layout atom
 */
export const ideLayoutAtom = atom(
  (get) => get(ideStateAtom).layout,
  (get, set, layout: IDELayout) => {
    set(ideStateAtom, { ...get(ideStateAtom), layout });
  }
);

/**
 * Side panels atom
 */
export const ideSidePanelsAtom = atom(
  (get) => get(ideStateAtom).layout.sidePanels,
  (get, set, sidePanels: IDEPanelState[]) => {
    const layout = get(ideStateAtom).layout;
    set(ideStateAtom, {
      ...get(ideStateAtom),
      layout: { ...layout, sidePanels },
    });
  }
);

/**
 * Bottom panels atom
 */
export const ideBottomPanelsAtom = atom(
  (get) => get(ideStateAtom).layout.bottomPanels,
  (get, set, bottomPanels: IDEPanelState[]) => {
    const layout = get(ideStateAtom).layout;
    set(ideStateAtom, {
      ...get(ideStateAtom),
      layout: { ...layout, bottomPanels },
    });
  }
);

/**
 * Settings atom with localStorage persistence
 */
export const ideSettingsAtom = atomWithStorage<IDEWorkspaceSettings>(
  'yappc-ide-settings',
  DEFAULT_SETTINGS
);

/**
 * Presence atom - collaborative user presence
 */
export const idePresenceAtom = atom(
  (get) => get(ideStateAtom).presence,
  (get, set, presence: Record<string, IDEPresence>) => {
    set(ideStateAtom, { ...get(ideStateAtom), presence });
  }
);

/**
 * Current user presence atom
 */
export const ideCurrentUserPresenceAtom = atom<IDEPresence | null>(null);

/**
 * Collaboration state for the IDE (active users, conflicts, settings)
 */
export interface IDECollaborationState {
  activeUsers: Record<string, IDEPresence>;
  conflicts: Record<string, {
    id: string;
    fileId: string;
    type: 'concurrent-edit' | 'selection-overlap' | 'file-lock';
    users: string[];
    position: { line: number; column: number };
    timestamp: number;
    resolved: boolean;
  }>;
  settings: {
    showCursors: boolean;
    showSelections: boolean;
    showAvatars: boolean;
    enableTypingIndicators: boolean;
    autoResolveConflicts: boolean;
    conflictResolutionStrategy: 'latest-wins' | 'manual' | 'merge';
  };
}

const DEFAULT_COLLABORATION_STATE: IDECollaborationState = {
  activeUsers: {},
  conflicts: {},
  settings: {
    showCursors: true,
    showSelections: true,
    showAvatars: true,
    enableTypingIndicators: true,
    autoResolveConflicts: false,
    conflictResolutionStrategy: 'manual',
  },
};

export const ideCollaborationAtom = atom<IDECollaborationState>(DEFAULT_COLLABORATION_STATE);

/**
 * Dirty files atom - files with unsaved changes
 */
export const ideDirtyFilesAtom = atom((get) => {
  const files = get(ideFilesAtom);
  return Object.values(files).filter((file) => file.isDirty);
});

/**
 * File tree atom - derived file tree structure
 */
export const ideFileTreeAtom = atom((get) => {
  const rootFolderId = get(ideRootFolderIdAtom);
  const folders = get(ideFoldersAtom);

  if (!rootFolderId || !folders[rootFolderId]) {
    return null;
  }

  return folders[rootFolderId];
});

/**
 * Explorer panel visibility atom
 */
export const ideExplorerVisibleAtom = atom(
  (get) => {
    const sidePanels = get(ideSidePanelsAtom);
    const explorerPanel = sidePanels.find((p) => p.type === 'explorer');
    return explorerPanel?.isVisible ?? false;
  },
  (get, set, isVisible: boolean) => {
    const sidePanels = get(ideSidePanelsAtom);
    const updatedPanels = sidePanels.map((p) =>
      p.type === 'explorer' ? { ...p, isVisible } : p
    );
    set(ideSidePanelsAtom, updatedPanels);
  }
);

/**
 * Terminal panel visibility atom
 */
export const ideTerminalVisibleAtom = atom(
  (get) => {
    const bottomPanels = get(ideBottomPanelsAtom);
    const terminalPanel = bottomPanels.find((p) => p.type === 'terminal');
    return terminalPanel?.isVisible ?? false;
  },
  (get, set, isVisible: boolean) => {
    const bottomPanels = get(ideBottomPanelsAtom);
    const updatedPanels = bottomPanels.map((p) =>
      p.type === 'terminal' ? { ...p, isVisible } : p
    );
    set(ideBottomPanelsAtom, updatedPanels);
  }
);

/**
 * Problems panel visibility atom
 */
export const ideProblemsVisibleAtom = atom(
  (get) => {
    const bottomPanels = get(ideBottomPanelsAtom);
    const problemsPanel = bottomPanels.find((p) => p.type === 'problems');
    return problemsPanel?.isVisible ?? false;
  },
  (get, set, isVisible: boolean) => {
    const bottomPanels = get(ideBottomPanelsAtom);
    const updatedPanels = bottomPanels.map((p) =>
      p.type === 'problems' ? { ...p, isVisible } : p
    );
    set(ideBottomPanelsAtom, updatedPanels);
  }
);

/**
 * Search panel visibility atom
 */
export const ideSearchVisibleAtom = atom<boolean>(false);

/**
 * Source Control panel visibility atom
 */
export const ideSourceControlVisibleAtom = atom<boolean>(false);

/**
 * Run & Debug panel visibility atom
 */
export const ideRunVisibleAtom = atom<boolean>(false);

/**
 * Extensions panel visibility atom
 */
export const ideExtensionsVisibleAtom = atom<boolean>(false);

