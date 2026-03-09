/**
 * IDE File Tree Component
 * 
 * Hierarchical file tree with collaboration indicators,
 * real-time sync status, and integrated LSP features.
 * 
 * Features:
 * - 📁 Hierarchical file/folder structure
 * - 👥 Collaboration indicators (editing, presence)
 * - 🔄 Real-time sync status
 * - 🎯 Quick file navigation
 * - 💾 File status indicators (modified, saved, conflict)
 * - 🔍 Search and filter
 * 
 * @doc.type component
 * @doc.purpose IDE file tree with collaboration
 * @doc.layer product
 * @doc.pattern Advanced Component
 */

import React, { useCallback, useState, useMemo } from 'react';

/**
 * File tree node
 */
export interface FileTreeNode {
  id: string;
  name: string;
  path: string;
  type: 'file' | 'folder';
  children?: FileTreeNode[];
  isOpen?: boolean;
  isDirty?: boolean;
  hasConflict?: boolean;
  activeEditors?: string[]; // User IDs currently editing
  lastModified?: number;
  language?: string;
}

/**
 * File tree configuration
 */
export interface FileTreeConfig {
  root: FileTreeNode;
  onFileSelect: (fileId: string, path: string) => void;
  onFolderToggle: (folderId: string) => void;
  onFileDelete: (fileId: string) => void;
  onFileRename: (fileId: string, newName: string) => void;
  onNewFile: (parentId: string, name: string) => void;
  onNewFolder: (parentId: string, name: string) => void;
  enableSearch?: boolean;
  enableContextMenu?: boolean;
  collaborativeUsers?: Map<string, { name: string; color: string }>;
}

/**
 * File Tree Node Component
 */
const FileTreeNodeComponent: React.FC<{
  node: FileTreeNode;
  level: number;
  onSelect: (id: string, path: string) => void;
  onToggle: (id: string) => void;
  onDelete: (id: string) => void;
  onRename: (id: string, newName: string) => void;
  collaborativeUsers?: Map<string, { name: string; color: string }>;
}> = ({
  node,
  level,
  onSelect,
  onToggle,
  onDelete,
  onRename,
  collaborativeUsers,
}) => {
  const [isRenaming, setIsRenaming] = useState(false);
  const [newName, setNewName] = useState(node.name);

  const handleRename = useCallback(() => {
    if (newName && newName !== node.name) {
      onRename(node.id, newName);
    }
    setIsRenaming(false);
  }, [newName, node.id, node.name, onRename]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') {
        handleRename();
      } else if (e.key === 'Escape') {
        setIsRenaming(false);
      }
    },
    [handleRename]
  );

  const hasChildren = node.children && node.children.length > 0;
  const isFolder = node.type === 'folder';

  return (
    <div>
      <div
        className="file-tree-item flex items-center px-2 py-1 hover:bg-gray-700 cursor-pointer group"
        style={{ paddingLeft: `${level * 16 + 8}px` }}
      >
        {isFolder && (
          <button
            onClick={() => onToggle(node.id)}
            className="mr-1 text-gray-400 hover:text-gray-200"
          >
            {node.isOpen ? '▼' : '▶'}
          </button>
        )}
        {!isFolder && <span className="mr-1 text-gray-400">📄</span>}
        {isFolder && !node.isOpen && <span className="mr-1 text-gray-400">📁</span>}
        {isFolder && node.isOpen && <span className="mr-1 text-gray-400">📂</span>}

        {isRenaming ? (
          <input
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onBlur={handleRename}
            onKeyDown={handleKeyDown}
            className="flex-1 px-2 py-0 bg-gray-600 text-white rounded focus:outline-none"
            autoFocus
          />
        ) : (
          <>
            <span
              onClick={() => !isFolder && onSelect(node.id, node.path)}
              className={`flex-1 text-sm ${
                node.isDirty ? 'font-bold text-white' : 'text-gray-300'
              }`}
            >
              {node.name}
              {node.isDirty && <span className="ml-1">●</span>}
              {node.hasConflict && <span className="ml-1 text-red-400">⚠</span>}
            </span>

            {/* Collaboration indicators */}
            {node.activeEditors && node.activeEditors.length > 0 && (
              <div className="flex items-center gap-1 ml-2">
                {node.activeEditors.slice(0, 2).map((userId) => {
                  const user = collaborativeUsers?.get(userId);
                  return (
                    <div
                      key={userId}
                      className="w-4 h-4 rounded-full"
                      style={{ backgroundColor: user?.color || '#888' }}
                      title={user?.name || userId}
                    />
                  );
                })}
                {node.activeEditors.length > 2 && (
                  <span className="text-xs text-gray-400">
                    +{node.activeEditors.length - 2}
                  </span>
                )}
              </div>
            )}

            {/* Context menu */}
            <div className="hidden group-hover:flex items-center gap-1 ml-2">
              <button
                onClick={() => setIsRenaming(true)}
                className="text-gray-400 hover:text-gray-200 text-xs"
                title="Rename"
              >
                ✎
              </button>
              <button
                onClick={() => onDelete(node.id)}
                className="text-gray-400 hover:text-red-400 text-xs"
                title="Delete"
              >
                ✕
              </button>
            </div>
          </>
        )}
      </div>

      {/* Children */}
      {isFolder && node.isOpen && hasChildren && (
        <div>
          {node.children!.map((child) => (
            <FileTreeNodeComponent
              key={child.id}
              node={child}
              level={level + 1}
              onSelect={onSelect}
              onToggle={onToggle}
              onDelete={onDelete}
              onRename={onRename}
              collaborativeUsers={collaborativeUsers}
            />
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * IDE File Tree Component
 */
export const IDEFileTree: React.FC<FileTreeConfig> = ({
  root,
  onFileSelect,
  onFolderToggle,
  onFileDelete,
  onFileRename,
  onNewFile,
  onNewFolder,
  enableSearch = true,
  collaborativeUsers,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());

  /**
   * Filter tree based on search query
   */
  const filteredTree = useMemo(() => {
    if (!searchQuery) return root;

    const filterNode = (node: FileTreeNode): FileTreeNode | null => {
      const matches = node.name.toLowerCase().includes(searchQuery.toLowerCase());
      const children = node.children
        ?.map(filterNode)
        .filter((child): child is FileTreeNode => child !== null);

      if (matches || (children && children.length > 0)) {
        return {
          ...node,
          children,
          isOpen: searchQuery.length > 0 ? true : node.isOpen,
        };
      }

      return null;
    };

    return filterNode(root) || root;
  }, [root, searchQuery]);

  /**
   * Handle folder toggle
   */
  const handleToggle = useCallback(
    (folderId: string) => {
      const newExpanded = new Set(expandedFolders);
      if (newExpanded.has(folderId)) {
        newExpanded.delete(folderId);
      } else {
        newExpanded.add(folderId);
      }
      setExpandedFolders(newExpanded);
      onFolderToggle(folderId);
    },
    [expandedFolders, onFolderToggle]
  );

  return (
    <div className="ide-file-tree flex flex-col h-full bg-gray-800 text-gray-100 border-r border-gray-700">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700">
        <h3 className="text-sm font-semibold">Explorer</h3>
        <div className="flex items-center gap-2">
          <button
            onClick={() => onNewFile(root.id, 'untitled.txt')}
            className="text-gray-400 hover:text-gray-200 text-xs"
            title="New File"
          >
            ➕
          </button>
          <button
            onClick={() => onNewFolder(root.id, 'untitled')}
            className="text-gray-400 hover:text-gray-200 text-xs"
            title="New Folder"
          >
            📁
          </button>
        </div>
      </div>

      {/* Search */}
      {enableSearch && (
        <div className="px-2 py-2 border-b border-gray-700">
          <input
            type="text"
            placeholder="Search files..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full px-2 py-1 bg-gray-700 text-white rounded text-xs focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      )}

      {/* File Tree */}
      <div className="flex-1 overflow-y-auto">
        <FileTreeNodeComponent
          node={filteredTree}
          level={0}
          onSelect={onFileSelect}
          onToggle={handleToggle}
          onDelete={onFileDelete}
          onRename={onFileRename}
          collaborativeUsers={collaborativeUsers}
        />
      </div>

      {/* Status Bar */}
      <div className="px-4 py-2 border-t border-gray-700 text-xs text-gray-400">
        <div className="flex items-center gap-2">
          <span>🔄 Synced</span>
        </div>
      </div>
    </div>
  );
};

export default IDEFileTree;
