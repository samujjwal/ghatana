/**
 * @ghatana/yappc-ide - Status Bar Component
 * 
 * IDE status bar showing file info, errors, and collaboration status.
 * 
 * @doc.type component
 * @doc.purpose Status bar for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { useAtom } from 'jotai';
import {
  ideActiveFileAtom,
  ideDirtyFilesAtom,
  idePresenceAtom,
  ideSettingsAtom,
} from '../state/atoms';

/**
 * Status Bar Props
 */
export interface StatusBarProps {
  className?: string;
  showCollaborators?: boolean;
  showFileInfo?: boolean;
}

/**
 * Status Bar Component
 * 
 * @doc.param props - Component props
 * @doc.returns Status bar component
 */
export const StatusBar: React.FC<StatusBarProps> = ({
  className = '',
  showCollaborators = true,
  showFileInfo = true,
}) => {
  const [activeFile] = useAtom(ideActiveFileAtom);
  const [dirtyFiles] = useAtom(ideDirtyFilesAtom);
  const [presence] = useAtom(idePresenceAtom);
  const [settings] = useAtom(ideSettingsAtom);

  const collaboratorCount = Object.keys(presence).length;
  const unsavedCount = dirtyFiles.length;

  return (
    <div className={`flex items-center justify-between px-4 py-1 bg-blue-600 text-white text-xs ${className}`}>
      {/* Left Section */}
      <div className="flex items-center gap-4">
        {/* Branch/Git Info */}
        <div className="flex items-center gap-1">
          <span>🔀</span>
          <span>main</span>
        </div>

        {/* Errors/Warnings */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1">
            <span>❌</span>
            <span>0</span>
          </div>
          <div className="flex items-center gap-1">
            <span>⚠️</span>
            <span>0</span>
          </div>
        </div>

        {/* Unsaved Files */}
        {unsavedCount > 0 && (
          <div className="flex items-center gap-1">
            <span>●</span>
            <span>{unsavedCount} unsaved</span>
          </div>
        )}
      </div>

      {/* Center Section */}
      {showFileInfo && activeFile && (
        <div className="flex items-center gap-4">
          <span>{activeFile.language}</span>
          <span>UTF-8</span>
          <span>LF</span>
        </div>
      )}

      {/* Right Section */}
      <div className="flex items-center gap-4">
        {/* Collaborators */}
        {showCollaborators && collaboratorCount > 0 && (
          <div className="flex items-center gap-1">
            <span>👥</span>
            <span>{collaboratorCount} online</span>
          </div>
        )}

        {/* Theme */}
        <div className="flex items-center gap-1">
          <span>{settings.theme === 'dark' ? '🌙' : '☀️'}</span>
        </div>

        {/* Notifications */}
        <button className="hover:bg-blue-700 px-2 py-0.5 rounded">
          🔔
        </button>

        {/* Settings */}
        <button className="hover:bg-blue-700 px-2 py-0.5 rounded">
          ⚙️
        </button>
      </div>
    </div>
  );
};

export default StatusBar;
