/**
 * @ghatana/yappc-ide - Advanced File Operations Hook
 * 
 * Enhanced file operations with bulk actions, search, and optimization.
 * Extends existing file operations with advanced capabilities.
 * 
 * @doc.type module
 * @doc.purpose Advanced file operations for IDE
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback, useState, useMemo } from 'react';
import { useAtom } from 'jotai';
import {
  ideFilesAtom,
  ideFoldersAtom,
} from '../state/atoms';
import { useIDEFileOperations } from './useIDEFileOperations';
import {
  normalizePath,
  isFile,
  isFolder,
} from '../utils/fileSystem';
import type { IDEFile, IDEFolder } from '../types';

/**
 * Bulk file operation types
 */
export type BulkOperationType = 'rename' | 'move' | 'delete' | 'copy';

/**
 * Bulk operation configuration
 */
export interface BulkOperation {
  type: BulkOperationType;
  fileIds: string[];
  destination?: string;
  options?: {
    overwrite?: boolean;
    preserveStructure?: boolean;
    pattern?: string;
  };
}

/**
 * Advanced search query
 */
export interface FileSearchQuery {
  pattern: string;
  fileTypes?: string[];
  dateRange?: { start: Date; end: Date };
  content?: string;
  caseSensitive?: boolean;
  regex?: boolean;
  minSize?: number;
  maxSize?: number;
}

/**
 * Search result with highlighting
 */
export interface SearchResult {
  file: IDEFile;
  matches: Array<{
    line: number;
    column: number;
    text: string;
    context: string;
  }>;
  score: number;
}

/**
 * File selection state
 */
export interface FileSelection {
  selectedIds: Set<string>;
  lastSelectedId: string | null;
  rangeStartId: string | null;
}

/**
 * Hook for advanced file operations
 */
export function useAdvancedFileOperations() {
  const [files, setFiles] = useAtom(ideFilesAtom);
  const [folders, setFolders] = useAtom(ideFoldersAtom);

  // File selection state
  const [selection, setSelection] = useState<FileSelection>({
    selectedIds: new Set(),
    lastSelectedId: null,
    rangeStartId: null,
  });

  // Existing file operations hook
  const {
    openFile,
    createNewFile: baseCreateFile,
    createNewFolder: baseCreateFolder,
    deleteFile: baseDeleteFile,
    deleteFolder: baseDeleteFolder,
    renameFile: baseRenameFile,
    moveFile: baseMoveFile
  } = useIDEFileOperations();
  const [bulkOperation, setBulkOperation] = useState<BulkOperation | null>(null);
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  /**
   * Enhanced file selection with multi-select support
   */
  const selectFile = useCallback((fileId: string, event: React.MouseEvent | KeyboardEvent) => {
    setSelection(prev => {
      const newSelection = { ...prev };

      if (event.ctrlKey || event.metaKey) {
        // Toggle selection
        if (newSelection.selectedIds.has(fileId)) {
          newSelection.selectedIds.delete(fileId);
        } else {
          newSelection.selectedIds.add(fileId);
        }
        newSelection.lastSelectedId = fileId;
      } else if (event.shiftKey && prev.lastSelectedId) {
        // Range selection
        const allIds = Object.keys(files);
        const lastIndex = allIds.indexOf(prev.lastSelectedId);
        const currentIndex = allIds.indexOf(fileId);

        if (lastIndex !== -1 && currentIndex !== -1) {
          const start = Math.min(lastIndex, currentIndex);
          const end = Math.max(lastIndex, currentIndex);

          newSelection.selectedIds = new Set(allIds.slice(start, end + 1));
        }
        newSelection.lastSelectedId = fileId;
      } else {
        // Single selection
        newSelection.selectedIds = new Set([fileId]);
        newSelection.lastSelectedId = fileId;
        newSelection.rangeStartId = fileId;
      }

      return newSelection;
    });
  }, [files]);

  /**
   * Select all files in current directory
   */
  const selectAll = useCallback(() => {
    const allFileIds = Object.keys(files);
    setSelection({
      selectedIds: new Set(allFileIds),
      lastSelectedId: allFileIds[allFileIds.length - 1] || null,
      rangeStartId: allFileIds[0] || null,
    });
  }, [files]);

  /**
   * Clear selection
   */
  const clearSelection = useCallback(() => {
    setSelection({
      selectedIds: new Set(),
      lastSelectedId: null,
      rangeStartId: null,
    });
  }, []);

  /**
   * Bulk rename files with pattern support
   */
  const bulkRename = useCallback(async (pattern: string) => {
    if (!pattern || selection.selectedIds.size === 0) return;

    const operation: BulkOperation = {
      type: 'rename',
      fileIds: Array.from(selection.selectedIds),
      options: { pattern },
    };

    setBulkOperation(operation);

    try {
      const updatedFiles = { ...files };
      let counter = 1;

      for (const fileId of selection.selectedIds) {
        const file = updatedFiles[fileId];
        if (!file) continue;

        const extension = file.name.includes('.') ? file.name.split('.').pop() : '';
        const newName = pattern
          .replace('{name}', file.name.replace(/\.[^/.]+$/, ''))
          .replace('{ext}', extension || '')
          .replace('{n}', counter.toString().padStart(3, '0'))
          .replace('{uuid}', file.id.substring(0, 8));

        const newPath = file.path.replace(file.name, newName);

        updatedFiles[fileId] = {
          ...file,
          name: newName,
          path: newPath,
          lastModified: Date.now(),
        };
        counter++;
      }

      setFiles(updatedFiles);
      clearSelection();
    } catch (error) {
      console.error('Bulk rename failed:', error);
    } finally {
      setBulkOperation(null);
    }
  }, [files, selection.selectedIds, setFiles, clearSelection]);

  /**
   * Bulk move files to destination
   */
  const bulkMove = useCallback(async (destination: string) => {
    if (!destination || selection.selectedIds.size === 0) return;

    const operation: BulkOperation = {
      type: 'move',
      fileIds: Array.from(selection.selectedIds),
      destination,
      options: { preserveStructure: true },
    };

    setBulkOperation(operation);

    try {
      const updatedFiles = { ...files };

      for (const fileId of selection.selectedIds) {
        const file = updatedFiles[fileId];
        if (!file) continue;

        const fileName = file.path.split('/').pop() || '';
        const newPath = normalizePath(`${destination}/${fileName}`);

        updatedFiles[fileId] = {
          ...file,
          path: newPath,
          lastModified: Date.now(),
        };
      }

      setFiles(updatedFiles);
      clearSelection();
    } catch (error) {
      console.error('Bulk move failed:', error);
    } finally {
      setBulkOperation(null);
    }
  }, [files, selection.selectedIds, setFiles, clearSelection]);

  /**
   * Bulk delete files with confirmation
   */
  const bulkDelete = useCallback(async () => {
    if (selection.selectedIds.size === 0) return;

    const operation: BulkOperation = {
      type: 'delete',
      fileIds: Array.from(selection.selectedIds),
    };

    setBulkOperation(operation);

    try {
      const updatedFiles = { ...files };
      const updatedFolders = { ...folders };

      for (const fileId of selection.selectedIds) {
        const item = updatedFiles[fileId] || updatedFolders[fileId];
        if (!item) continue;

        if (isFile(item)) {
          delete updatedFiles[fileId];
        } else if (isFolder(item)) {
          delete updatedFolders[fileId];
        }
      }

      setFiles(updatedFiles);
      setFolders(updatedFolders);
      clearSelection();
    } catch (error) {
      console.error('Bulk delete failed:', error);
    } finally {
      setBulkOperation(null);
    }
  }, [files, folders, selection.selectedIds, setFiles, setFolders, clearSelection]);

  /**
   * Advanced file search with indexing
   */
  const searchFiles = useCallback(async (query: FileSearchQuery) => {
    setIsSearching(true);
    setSearchResults([]);

    try {
      const results: SearchResult[] = [];
      const allFiles = Object.values(files);

      // Normalize search pattern
      const pattern = query.caseSensitive ? query.pattern : query.pattern.toLowerCase();
      const contentPattern = query.content
        ? (query.caseSensitive ? query.content : query.content.toLowerCase())
        : null;

      for (const file of allFiles) {
        let score = 0;
        const matches: SearchResult['matches'] = [];

        // File name matching
        const fileName = query.caseSensitive ? file.name : file.name.toLowerCase();
        if (fileName.includes(pattern)) {
          score += fileName === pattern ? 100 : 50;
        }

        // File extension matching
        if (query.fileTypes && query.fileTypes.length > 0) {
          const extension = file.name.split('.').pop()?.toLowerCase();
          if (extension && query.fileTypes.includes(extension)) {
            score += 25;
          }
        }

        // Date range matching
        if (query.dateRange) {
          const fileDate = new Date(file.lastModified);
          if (fileDate >= query.dateRange.start && fileDate <= query.dateRange.end) {
            score += 20;
          }
        }

        // Size matching
        if (query.minSize !== undefined && file.size < query.minSize) {
          continue;
        }
        if (query.maxSize !== undefined && file.size > query.maxSize) {
          continue;
        }

        // Content matching
        if (contentPattern && file.content) {
          const content = query.caseSensitive ? file.content : file.content.toLowerCase();
          const lines = content.split('\n');

          lines.forEach((line, index) => {
            if (line.includes(contentPattern)) {
              const column = line.indexOf(contentPattern);
              matches.push({
                line: index + 1,
                column,
                text: line.trim(),
                context: line.substring(Math.max(0, column - 50), column + 50),
              });
              score += 10;
            }
          });
        }

        if (score > 0) {
          results.push({ file, matches, score });
        }
      }

      // Sort by score (descending)
      results.sort((a, b) => b.score - a.score);
      setSearchResults(results);
    } catch (error) {
      console.error('Search failed:', error);
    } finally {
      setIsSearching(false);
    }
  }, [files]);

  /**
   * Clear search results
   */
  const clearSearch = useCallback(() => {
    setSearchResults([]);
  }, []);

  /**
   * Get selected files
   */
  const selectedFiles = useMemo(() => {
    return Array.from(selection.selectedIds)
      .map(id => (files[id as string] || folders[id as string]) as IDEFile | IDEFolder)
      .filter(Boolean);
  }, [selection.selectedIds, files, folders]);

  /**
   * Check if file is selected
   */
  const isFileSelected = useCallback((fileId: string) => {
    return selection.selectedIds.has(fileId);
  }, [selection.selectedIds]);

  /**
   * Get selection count
   */
  const selectionCount = selection.selectedIds.size;

  return {
    // Selection management
    selectFile,
    selectAll,
    clearSelection,
    selectedFiles,
    isFileSelected,
    selectionCount,

    // Bulk operations
    bulkRename,
    bulkMove,
    bulkDelete,
    bulkOperation,

    // Search functionality
    searchFiles,
    clearSearch,
    searchResults,
    isSearching,

    // Base operations (re-exported)
    createFile: baseCreateFile,
    createFolder: baseCreateFolder,
    deleteFile: baseDeleteFile,
    deleteFolder: baseDeleteFolder,
    renameFile: baseRenameFile,
    moveFile: baseMoveFile,
    openFile,
  };
}
