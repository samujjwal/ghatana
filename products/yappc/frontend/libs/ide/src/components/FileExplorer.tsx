/**
 * @ghatana/yappc-ide - File Explorer Component
 * 
 * File tree navigation component with collaborative features.
 * 
 * @doc.type component
 * @doc.purpose File tree navigation for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useAtom } from 'jotai';
import {
  ideFileTreeAtom,
  ideFilesAtom,
  ideActiveFileIdAtom,
} from '../state/atoms';
import { useIDEFileOperations } from '../hooks/useIDEFileOperations';
import { useAdvancedFileOperations } from '../hooks/useAdvancedFileOperations';
import { useVirtualScroll, flattenTree } from '../utils/VirtualScroll';
import { SearchBar } from './SearchBar';
import { BulkOperationsToolbar } from './BulkOperationsToolbar';
import { AdvancedSearchPanel } from './AdvancedSearchPanel';
import { InteractiveButton } from './MicroInteractions';
import {
  isFile,
  isFolder,
  sortFileTreeNodes,
  formatFileSize,
} from '../utils/fileSystem';
import type { FileTreeNode, IDEFile, IDEFolder } from '../types';

/**
 * Get file icon based on language/extension
 */
function getFileIcon(language: string): string {
  const iconMap: Record<string, string> = {
    javascript: '🟨',
    typescript: '🔷',
    react: '⚛️',
    vue: '💚',
    angular: '🔴',
    html: '🌐',
    css: '🎨',
    json: '📋',
    markdown: '📝',
    python: '🐍',
    java: '☕',
    cpp: '⚙️',
    c: '⚙️',
    go: '🐹',
    rust: '🦀',
    php: '🐘',
    ruby: '💎',
    sql: '🗃️',
    docker: '🐳',
    gitignore: '📄',
    env: '🔧',
    config: '⚙️',
    xml: '📄',
    yaml: '📄',
    yml: '📄',
  };

  return iconMap[language.toLowerCase()] || '📄';
}

/**
 * Enhanced File Explorer Props
 */
export interface FileExplorerProps {
  className?: string;
  onFileSelect?: (fileId: string) => void;
  showFileSize?: boolean;
  showFileIcons?: boolean;
  enableMultiSelect?: boolean;
  enableSearch?: boolean;
  enableAdvancedSearch?: boolean;
  enableBulkOperations?: boolean;
  enableVirtualScroll?: boolean;
  itemHeight?: number;
  maxHeight?: number;
}

/**
 * Enhanced File Tree Item Component
 */
interface FileTreeItemProps {
  node: FileTreeNode;
  level: number;
  onFileClick: (fileId: string, event: React.MouseEvent) => void;
  onFolderToggle: (folderId: string) => void;
  activeFileId: string | null;
  showFileSize: boolean;
  isSelected?: boolean;
  enableMultiSelect?: boolean;
  showFileIcons?: boolean;
}

const FileTreeItem: React.FC<FileTreeItemProps> = ({
  node,
  level,
  onFileClick,
  onFolderToggle,
  activeFileId,
  showFileSize,
  isSelected = false,
  enableMultiSelect = false,
  showFileIcons = true,
}) => {
  const paddingLeft = level * 16 + 8;

  const handleClick = useCallback((event: React.MouseEvent) => {
    if (isFile(node)) {
      onFileClick((node as IDEFile).id, event);
    }
  }, [node, onFileClick]);

  if (isFile(node)) {
    const file = node as IDEFile;
    const isActive = file.id === activeFileId;

    return (
      <div
        className={`
          flex items-center gap-2 px-2 py-1 cursor-pointer group
          hover:bg-gray-100 dark:hover:bg-gray-800
          ${isActive ? 'bg-blue-50 dark:bg-blue-900 border-l-2 border-blue-500' : ''}
          ${isSelected ? 'bg-blue-100 dark:bg-blue-800/50 border-l-2 border-blue-400' : ''}
          ${file.isDirty ? 'font-semibold' : ''}
        `}
        style={{ paddingLeft }}
        onClick={handleClick}
        role="button"
        tabIndex={0}
        aria-label={`File: ${file.name}${isSelected ? ', selected' : ''}`}
        aria-selected={isSelected || isActive}
      >
        <span className="flex-shrink-0 w-4 h-4 text-gray-500 group-hover:text-gray-700 dark:group-hover:text-gray-300">
          {showFileIcons ? getFileIcon(file.language) : '📄'}
        </span>
        <span className="flex-1 truncate text-sm">
          {file.name}
          {file.isDirty && <span className="ml-1 text-orange-500" title="Unsaved changes">●</span>}
        </span>
        {showFileSize && (
          <span className="text-xs text-gray-400 group-hover:text-gray-600 dark:group-hover:text-gray-300">
            {formatFileSize(file.size)}
          </span>
        )}
        {enableMultiSelect && (
          <div className="opacity-0 group-hover:opacity-100 transition-opacity">
            <input
              type="checkbox"
              checked={isSelected}
              onChange={() => { }} // Handled by parent click
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              onClick={(e) => e.stopPropagation()}
            />
          </div>
        )}
      </div>
    );
  }

  if (isFolder(node)) {
    const folder = node as IDEFolder;
    const sortedChildren = sortFileTreeNodes(folder.children);

    return (
      <div>
        <div
          className="flex items-center gap-2 px-2 py-1 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800"
          style={{ paddingLeft }}
          onClick={() => onFolderToggle(folder.id)}
          role="button"
          tabIndex={0}
          aria-label={`Folder: ${folder.name}`}
          aria-expanded={folder.isExpanded}
        >
          <span className="flex-shrink-0 w-4 h-4 text-gray-500">
            {folder.isExpanded ? '📂' : '📁'}
          </span>
          <span className="flex-1 truncate text-sm font-medium">
            {folder.name}
          </span>
          <span className="text-xs text-gray-400">
            {folder.children.length}
          </span>
        </div>
        {folder.isExpanded && (
          <div>
            {sortedChildren.map((child) => (
              <FileTreeItem
                key={child.id}
                node={child}
                level={level + 1}
                onFileClick={onFileClick}
                onFolderToggle={onFolderToggle}
                activeFileId={activeFileId}
                showFileSize={showFileSize}
              />
            ))}
          </div>
        )}
      </div>
    );
  }

  return null;
};

/**
 * Enhanced File Explorer Component
 * 
 * @doc.param props - Component props
 * @doc.returns Enhanced file explorer component
 */
export const FileExplorer: React.FC<FileExplorerProps> = ({
  className = '',
  onFileSelect,
  showFileSize = false,
  showFileIcons = true,
  enableMultiSelect = false,
  enableSearch = true,
  enableAdvancedSearch = true,
  enableBulkOperations = true,
  enableVirtualScroll = false,
  itemHeight = 28,
  maxHeight = 400,
}) => {
  const [fileTree] = useAtom(ideFileTreeAtom);
  const [files] = useAtom(ideFilesAtom);
  const [activeFileId] = useAtom(ideActiveFileIdAtom);
  const [showAdvancedSearch, setShowAdvancedSearch] = useState(false);

  // Enhanced hooks
  const { openFile, toggleFolderExpanded } = useIDEFileOperations();
  const {
    selectFile,
    isFileSelected,
    selectionCount,
    clearSelection,
    bulkRename,
    bulkDelete,
    bulkOperation,
  } = useAdvancedFileOperations();

  // Virtual scrolling
  // Guard against missing or uninitialized fileTree (e.g., during initial load)
  const flattenedTree = useMemo(() => {
    if (!fileTree) return [] as unknown[];
    return flattenTree(fileTree.children as unknown[] || [], new Set()) as unknown[];
  }, [fileTree]);

  const virtualScroll = useVirtualScroll(
    {
      itemHeight,
      containerHeight: maxHeight,
      overscan: 5,
    },
    enableVirtualScroll ? flattenedTree.length : 0
  );

  const handleFileClick = useCallback(
    (fileId: string, event: React.MouseEvent) => {
      if (enableMultiSelect) {
        selectFile(fileId, event);
      }

      openFile(fileId);
      onFileSelect?.(fileId);
    },
    [openFile, onFileSelect, enableMultiSelect, selectFile]
  );

  const handleFolderToggle = useCallback(
    (folderId: string) => {
      toggleFolderExpanded(folderId);
    },
    [toggleFolderExpanded]
  );

  const handleSearchResultSelect = useCallback((result: unknown) => {
    handleFileClick(result.file.id, {} as React.MouseEvent);
  }, [handleFileClick]);

  // Handle keyboard shortcuts
  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    if (enableMultiSelect) {
      switch (event.key) {
        case 'a':
        case 'A':
          if (event.ctrlKey || event.metaKey) {
            event.preventDefault();
            // Select all would be implemented here
          }
          break;
        case 'Delete':
        case 'Backspace':
          if (selectionCount > 0) {
            event.preventDefault();
            bulkDelete();
          }
          break;
      }
    }
  }, [enableMultiSelect, selectionCount, bulkDelete]);

  // Render tree items (virtual or regular)
  const renderTreeItems = () => {
    const itemsToRender = enableVirtualScroll
      ? virtualScroll.items.map(item => flattenedTree[item.index])
      : flattenedTree;

    return itemsToRender.map((node, _index) => {
      const isSelected = enableMultiSelect ? isFileSelected(node.id) : false;

      return (
        <FileTreeItem
          key={node.id}
          node={node}
          level={node.depth || 0}
          onFileClick={handleFileClick}
          onFolderToggle={handleFolderToggle}
          activeFileId={activeFileId}
          showFileSize={showFileSize}
          isSelected={isSelected}
          enableMultiSelect={enableMultiSelect}
          showFileIcons={showFileIcons}
        />
      );
    });
  };

  return (
    <>
      <div
        className={`flex flex-col h-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg ${className}`}
        onKeyDown={handleKeyDown}
        tabIndex={0}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-3 py-2 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
            📁 Explorer
            {enableMultiSelect && selectionCount > 0 && (
              <span className="ml-2 text-xs text-blue-600 dark:text-blue-400">
                ({selectionCount} selected)
              </span>
            )}
          </h2>
          <div className="flex items-center gap-1">
            {selectionCount > 0 && (
              <>
                <button
                  onClick={clearSelection}
                  className="p-1 text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                  title="Clear selection"
                >
                  Clear
                </button>
                <button
                  onClick={() => bulkRename('{name}_{n}')}
                  disabled={bulkOperation !== null}
                  className="p-1 text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 disabled:opacity-50"
                  title="Bulk rename"
                >
                  Rename
                </button>
                <button
                  onClick={bulkDelete}
                  disabled={bulkOperation !== null}
                  className="p-1 text-xs text-red-500 hover:text-red-700 dark:hover:text-red-400 disabled:opacity-50"
                  title="Bulk delete"
                >
                  Delete
                </button>
              </>
            )}
            <button
              className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
              aria-label="New file"
            >
              ➕
            </button>
          </div>
        </div>

        {/* Bulk Operations Toolbar */}
        {enableBulkOperations && selectionCount > 0 && (
          <BulkOperationsToolbar
            onOperationComplete={(operation) => {
              console.log('Bulk operation completed:', operation);
            }}
            onOperationError={(error) => {
              console.error('Bulk operation failed:', error);
            }}
          />
        )}

        {/* Search */}
        {enableSearch && (
          <div className="px-2 py-2 flex gap-2">
            <SearchBar
              onResultSelect={handleSearchResultSelect}
              placeholder="Search files..."
              autoFocus={false}
              className="flex-1"
            />
            {enableAdvancedSearch && (
              <InteractiveButton
                variant="secondary"
                size="sm"
                onClick={() => setShowAdvancedSearch(true)}
                title="Advanced Search"
              >
                🔍
              </InteractiveButton>
            )}
          </div>
        )}

        {/* Bulk Operation Progress */}
        {bulkOperation && (
          <div className="px-2 py-1 bg-blue-50 dark:bg-blue-900/20 border-b border-blue-200 dark:border-blue-800">
            <div className="flex items-center gap-2 text-xs text-blue-700 dark:text-blue-300">
              <div className="animate-spin h-3 w-3 border border-blue-500 border-t-transparent rounded-full" />
              <span>
                Performing bulk {bulkOperation.type} on {bulkOperation.fileIds.length} items...
              </span>
            </div>
          </div>
        )}

        {/* File Tree */}
        <div
          className={`flex-1 overflow-auto ${enableVirtualScroll ? 'relative' : ''}`}
          style={{ maxHeight: enableVirtualScroll ? maxHeight : undefined }}
          onScroll={enableVirtualScroll ? virtualScroll.handleScroll : undefined}
        >
          {/* If the file tree is not yet available (e.g., backend loading), show a placeholder */}
          {!fileTree ? (
            <div className="p-4 text-sm text-gray-500">No workspace loaded — the file tree will appear here when available.</div>
          ) : enableVirtualScroll ? (
            <>
              {/* Spacer for items above viewport */}
              <div style={{ height: virtualScroll.offsetY }} />

              {/* Visible items */}
              {renderTreeItems()}

              {/* Spacer for items below viewport */}
              <div style={{
                height: virtualScroll.totalHeight - virtualScroll.offsetY - (virtualScroll.items.length * itemHeight)
              }} />
            </>
          ) : (
            renderTreeItems()
          )}
        </div>

        {/* Status Bar */}
        <div className="px-3 py-1 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-400">
          {Object.keys(files).length} files • {Object.keys(files).filter(f => files[f].isDirty).length} dirty
          {enableVirtualScroll && ` • Virtual scrolling enabled`}
        </div>
      </div>

      {/* Advanced Search Panel */}
      {enableAdvancedSearch && (
        <AdvancedSearchPanel
          isVisible={showAdvancedSearch}
          onResultSelect={handleSearchResultSelect}
          onClose={() => setShowAdvancedSearch(false)}
        />
      )}
    </>
  );
};

export default FileExplorer;
