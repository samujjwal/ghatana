/**
 * @ghatana/yappc-ide - IDE State Hook
 * 
 * React hook for managing IDE state with Jotai atoms.
 * 
 * @doc.type hook
 * @doc.purpose IDE state management
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useAtom } from 'jotai';
import {
  ideStateAtom,
  ideFilesAtom,
  ideFoldersAtom,
  ideActiveFileAtom,
  ideSettingsAtom,
} from '../state/atoms';
import type { IDEFile, IDEFolder, IDEWorkspaceSettings } from '../types';

/**
 * IDE state hook return value
 */
export interface UseIDEStateReturn {
  /** Current IDE state */
  state: unknown;
  /** Files in the workspace */
  files: Record<string, IDEFile>;
  /** Folders in the workspace */
  folders: Record<string, IDEFolder>;
  /** Currently active file */
  activeFile: IDEFile | null;
  /** IDE settings */
  settings: IDEWorkspaceSettings;
  /** Update IDE state */
  setState: (state: unknown) => void;
  /** Set active file */
  setActiveFile: (file: IDEFile | null) => void;
  /** Update settings */
  updateSettings: (settings: Partial<IDEWorkspaceSettings>) => void;
}

/**
 * IDE State Hook
 * 
 * @doc.returns IDE state and utilities
 */
export function useIDEState(): UseIDEStateReturn {
  const [state, setState] = useAtom(ideStateAtom);
  const [files] = useAtom(ideFilesAtom);
  const [folders] = useAtom(ideFoldersAtom);
  const [activeFile, setActiveFile] = useAtom(ideActiveFileAtom);
  const [settings, setSettings] = useAtom(ideSettingsAtom);

  const updateSettings = (newSettings: Partial<IDEWorkspaceSettings>) => {
    setSettings(prev => ({ ...prev, ...newSettings }));
  };

  return {
    state,
    files,
    folders,
    activeFile,
    settings,
    setState,
    setActiveFile,
    updateSettings,
  };
}
