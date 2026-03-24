/**
 * File Explorer - IDE to Canvas Bridge
 * 
 * Maps IDE FileExplorer to CanvasFileExplorer
 * 
 * @deprecated Use CanvasFileExplorer or FileTree from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect } from 'react';

export interface FileExplorerProps {
  /** Root directory path */
  rootPath?: string;
  /** File tree data */
  files?: FileTreeItem[];
  /** File selection handler */
  onFileSelect?: (path: string) => void;
  /** File open handler */
  onFileOpen?: (path: string) => void;
  /** Context menu handler */
  onContextMenu?: (path: string, event: React.MouseEvent) => void;
  /** Currently selected file */
  selectedFile?: string;
  /** Show hidden files */
  showHiddenFiles?: boolean;
  /** Additional CSS classes */
  className?: string;
}

export interface FileTreeItem {
  name: string;
  path: string;
  type: 'file' | 'directory';
  children?: FileTreeItem[];
  isExpanded?: boolean;
  icon?: string;
}

/**
 * FileExplorer - Bridge to Canvas File Explorer
 */
export const FileExplorer: React.FC<FileExplorerProps> = ({
  rootPath = '/',
  files = [],
  onFileSelect,
  onFileOpen,
  onContextMenu,
  selectedFile,
  showHiddenFiles = false,
  className,
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] FileExplorer from @ghatana/yappc-ide is deprecated. ' +
      'Use CanvasFileExplorer or FileTree from @ghatana/yappc-canvas.'
    );
  }, []);

  return (
    <div 
      className={`file-explorer-bridge ${className || ''}`}
      data-root={rootPath}
    >
      <div className="file-tree-container">
        {files.length === 0 ? (
          <div className="empty-state">No files to display</div>
        ) : (
          <FileTree 
            items={files}
            onSelect={onFileSelect}
            onOpen={onFileOpen}
            onContextMenu={onContextMenu}
            selectedPath={selectedFile}
            showHidden={showHiddenFiles}
          />
        )}
      </div>
    </div>
  );
};

export interface FileTreeProps {
  items: FileTreeItem[];
  onSelect?: (path: string) => void;
  onOpen?: (path: string) => void;
  onContextMenu?: (path: string, event: React.MouseEvent) => void;
  selectedPath?: string;
  showHidden?: boolean;
  level?: number;
}

/**
 * FileTree - Recursive File Tree Component
 */
export const FileTree: React.FC<FileTreeProps> = ({
  items,
  onSelect,
  onOpen,
  onContextMenu,
  selectedPath,
  showHidden = false,
  level = 0,
}) => {
  return (
    <ul className="file-tree" style={{ paddingLeft: level * 16 }}>
      {items
        .filter(item => showHidden || !item.name.startsWith('.'))
        .map(item => (
          <li 
            key={item.path}
            className={`file-tree-item ${item.type} ${selectedPath === item.path ? 'selected' : ''}`}
            onClick={() => onSelect?.(item.path)}
            onDoubleClick={() => item.type === 'file' && onOpen?.(item.path)}
            onContextMenu={(e) => onContextMenu?.(item.path, e)}
          >
            <span className="file-icon">{item.icon || (item.type === 'directory' ? '📁' : '📄')}</span>
            <span className="file-name">{item.name}</span>
            {item.type === 'directory' && item.children && item.isExpanded && (
              <FileTree 
                items={item.children}
                onSelect={onSelect}
                onOpen={onOpen}
                onContextMenu={onContextMenu}
                selectedPath={selectedPath}
                showHidden={showHidden}
                level={level + 1}
              />
            )}
          </li>
        ))}
    </ul>
  );
};

// Re-export types
export { FileExplorer as CanvasFileExplorer };
export { FileTree as CanvasFileTree };
