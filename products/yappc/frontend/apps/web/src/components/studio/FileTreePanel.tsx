import React, { useState } from 'react';
import { cn } from '@/lib/utils';

/**
 * File Tree Panel component.
 *
 * Displays project file structure with status indicators.
 * Part of Studio Mode layout.
 *
 * @doc.type component
 * @doc.purpose File tree navigation for Studio Mode
 * @doc.layer ui
 */

export interface FileNode {
  id: string;
  name: string;
  type: 'file' | 'directory';
  path: string;
  children?: FileNode[];
  status?: 'modified' | 'added' | 'deleted' | 'clean';
  isOpen?: boolean;
}

export interface FileTreePanelProps {
  files: FileNode[];
  selectedFile?: string;
  onFileSelect?: (file: FileNode) => void;
  onFileCreate?: (parentPath: string, type: 'file' | 'directory') => void;
  onFileDelete?: (file: FileNode) => void;
  className?: string;
}

function FileIcon({
  type,
  status,
}: {
  type: 'file' | 'directory';
  status?: string;
}) {
  const statusColors = {
    modified: 'text-yellow-500',
    added: 'text-green-500',
    deleted: 'text-red-500',
    clean: 'text-gray-500',
  };

  const icon = type === 'directory' ? '📁' : '📄';
  const color = status
    ? statusColors[status as keyof typeof statusColors]
    : 'text-gray-500';

  return <span className={cn('text-sm', color)}>{icon}</span>;
}

function FileTreeNode({
  node,
  level = 0,
  selectedFile,
  onFileSelect,
  onToggle,
}: {
  node: FileNode;
  level?: number;
  selectedFile?: string;
  onFileSelect?: (file: FileNode) => void;
  onToggle?: (nodeId: string) => void;
}) {
  const isSelected = selectedFile === node.id;
  const hasChildren = node.children && node.children.length > 0;

  return (
    <div>
      <div
        className={cn(
          'flex items-center gap-2 px-2 py-1 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 rounded',
          isSelected && 'bg-blue-100 dark:bg-blue-900/30'
        )}
        style={{ paddingLeft: `${level * 16 + 8}px` }}
        onClick={() => {
          if (node.type === 'directory') {
            onToggle?.(node.id);
          } else {
            onFileSelect?.(node);
          }
        }}
      >
        {hasChildren && (
          <span className="text-xs text-gray-500">
            {node.isOpen ? '▼' : '▶'}
          </span>
        )}
        <FileIcon type={node.type} status={node.status} />
        <span className="text-sm text-gray-900 dark:text-gray-100 flex-1">
          {node.name}
        </span>
        {node.status === 'modified' && (
          <span className="text-xs text-yellow-600 dark:text-yellow-400">
            ●
          </span>
        )}
      </div>

      {node.isOpen && hasChildren && (
        <div>
          {node.children!.map((child) => (
            <FileTreeNode
              key={child.id}
              node={child}
              level={level + 1}
              selectedFile={selectedFile}
              onFileSelect={onFileSelect}
              onToggle={onToggle}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export function FileTreePanel({
  files,
  selectedFile,
  onFileSelect,
  onFileCreate,
  className,
}: FileTreePanelProps) {
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());

  const handleToggle = (nodeId: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  };

  const filesWithOpenState = React.useMemo(() => {
    const addOpenState = (nodes: FileNode[]): FileNode[] => {
      return nodes.map((node) => ({
        ...node,
        isOpen: expandedNodes.has(node.id),
        children: node.children ? addOpenState(node.children) : undefined,
      }));
    };
    return addOpenState(files);
  }, [files, expandedNodes]);

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="p-3 border-b border-gray-200 dark:border-gray-800">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
            Files
          </h3>
          <div className="flex items-center gap-1">
            <button
              onClick={() => onFileCreate?.('/', 'file')}
              className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 rounded"
              title="New File"
            >
              <span className="text-xs">📄+</span>
            </button>
            <button
              onClick={() => onFileCreate?.('/', 'directory')}
              className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 rounded"
              title="New Folder"
            >
              <span className="text-xs">📁+</span>
            </button>
          </div>
        </div>
      </div>

      {/* File Tree */}
      <div className="flex-1 overflow-auto p-2">
        {filesWithOpenState.map((node) => (
          <FileTreeNode
            key={node.id}
            node={node}
            selectedFile={selectedFile}
            onFileSelect={onFileSelect}
            onToggle={handleToggle}
          />
        ))}
      </div>
    </div>
  );
}

/**
 * Hook for managing file tree state.
 *
 * @doc.type hook
 * @doc.purpose File tree state management
 */
export function useFileTree(initialFiles: FileNode[]) {
  const [files, setFiles] = useState<FileNode[]>(initialFiles);
  const [selectedFile, setSelectedFile] = useState<string | undefined>();

  const handleFileSelect = (file: FileNode) => {
    setSelectedFile(file.id);
  };

  const handleFileCreate = (parentPath: string, type: 'file' | 'directory') => {
    const name = type === 'directory' ? 'New Folder' : 'new-file.ts';
    const newFile: FileNode = {
      id: `${parentPath}/${name}-${Date.now()}`,
      name,
      type,
      path: `${parentPath}/${name}`,
      status: 'added',
      ...(type === 'directory' ? { children: [] } : {}),
    };
    setFiles(prev => addNodeAtPath(prev, parentPath, newFile));
  };

  const handleFileDelete = (file: FileNode) => {
    setFiles(prev => removeNodeById(prev, file.id));
  };

  return {
    files,
    setFiles,
    selectedFile,
    handleFileSelect,
    handleFileCreate,
    handleFileDelete,
  };
}

function addNodeAtPath(nodes: FileNode[], parentPath: string, newNode: FileNode): FileNode[] {
  return nodes.map(node => {
    if (node.path === parentPath && node.type === 'directory') {
      return { ...node, children: [...(node.children ?? []), newNode] };
    }
    if (node.children) {
      return { ...node, children: addNodeAtPath(node.children, parentPath, newNode) };
    }
    return node;
  });
}

function removeNodeById(nodes: FileNode[], id: string): FileNode[] {
  return nodes
    .filter(node => node.id !== id)
    .map(node => ({
      ...node,
      children: node.children ? removeNodeById(node.children, id) : undefined,
    }));
}
