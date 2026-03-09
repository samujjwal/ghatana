/**
 * @ghatana/yappc-ide - File Operations Hook
 * 
 * React hook for IDE file operations with CRDT synchronization.
 * 
 * @doc.type module
 * @doc.purpose File operations hook for IDE
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback } from 'react';
import { useAtom } from 'jotai';

import {
  ideFilesAtom,
  ideFoldersAtom,
  ideActiveFileIdAtom,
  ideOpenTabsAtom,
} from '../state/atoms';
import {
  createFile,
  createFolder,
  detectLanguage,
  normalizePath,
  isValidPath,
} from '../utils/fileSystem';
import { createTab, addTab, removeTab } from '../utils/tabManager';

/**
 * Hook for IDE file operations
 * 
 * @doc.returns File operation functions
 */
export function useIDEFileOperations() {
  const [files, setFiles] = useAtom(ideFilesAtom);
  const [folders, setFolders] = useAtom(ideFoldersAtom);
  const [activeFileId, setActiveFileId] = useAtom(ideActiveFileIdAtom);
  const [openTabs, setOpenTabs] = useAtom(ideOpenTabsAtom);

  /**
   * Create a new file
   */
  const createNewFile = useCallback(
    (path: string, content: string = '', language?: string) => {
      if (!isValidPath(path)) {
        throw new Error(`Invalid file path: ${path}`);
      }

      const normalizedPath = normalizePath(path);
      const detectedLanguage = language || detectLanguage(normalizedPath);
      const file = createFile(normalizedPath, content, detectedLanguage);

      setFiles({
        ...files,
        [file.id]: file,
      });

      return file;
    },
    [setFiles]
  );

  /**
   * Create a new folder
   */
  const createNewFolder = useCallback(
    (path: string) => {
      if (!isValidPath(path)) {
        throw new Error(`Invalid folder path: ${path}`);
      }

      const normalizedPath = normalizePath(path);
      const folder = createFolder(normalizedPath);

      setFolders({
        ...folders,
        [folder.id]: folder,
      });

      return folder;
    },
    [setFolders]
  );

  /**
   * Update file content
   */
  const updateFileContent = useCallback(
    (fileId: string, content: string) => {
      const existing = files[fileId];
      if (!existing) return;

      setFiles({
        ...files,
        [fileId]: {
          ...existing,
          content,
          isDirty: true,
          lastModified: Date.now(),
          size: content.length,
        },
      });
    },
    [setFiles]
  );

  /**
   * Save file (mark as clean)
   */
  const saveFile = useCallback(
    (fileId: string) => {
      const toSave = files[fileId];
      if (!toSave) return;

      setFiles({
        ...files,
        [fileId]: {
          ...toSave,
          isDirty: false,
          lastModified: Date.now(),
        },
      });
    },
    [setFiles]
  );

  /**
   * Delete file
   */
  const deleteFile = useCallback(
    (fileId: string) => {
      const { [fileId]: _, ...rest } = files;
      setFiles(rest);

      // Close tab if open
      setOpenTabs(openTabs.filter((tab) => tab.fileId !== fileId));

      // Clear active file if it was deleted
      if (activeFileId === fileId) setActiveFileId(null);
    },
    [setFiles, setOpenTabs, setActiveFileId]
  );

  /**
   * Rename file
   */
  const renameFile = useCallback(
    (fileId: string, newPath: string) => {
      if (!isValidPath(newPath)) {
        throw new Error(`Invalid file path: ${newPath}`);
      }

      const normalizedPath = normalizePath(newPath);
      const newName = normalizedPath.split('/').pop() || 'untitled';
      const newLanguage = detectLanguage(normalizedPath);

      const fileToRename = files[fileId];
      if (!fileToRename) return;

      setFiles({
        ...files,
        [fileId]: {
          ...fileToRename,
          path: normalizedPath,
          name: newName,
          language: newLanguage,
          lastModified: Date.now(),
        },
      });

      // Update tab title if open
      setOpenTabs(openTabs.map((tab) => (tab.fileId === fileId ? { ...tab, title: newName } : tab)));
    },
    [setFiles, setOpenTabs]
  );

  /**
   * Open file in editor
   */
  const openFile = useCallback(
    (fileId: string) => {
      const file = files[fileId];
      if (!file) return;

      // Mark file as open
      setFiles({
        ...files,
        [fileId]: { ...file, isOpen: true },
      });

      // Add tab
      const tab = createTab(fileId, file.name);
      setOpenTabs(addTab(openTabs, tab));

      // Set as active file
      setActiveFileId(fileId);
    },
    [files, setFiles, setOpenTabs, setActiveFileId]
  );

  /**
   * Close file
   */
  const closeFile = useCallback(
    (fileId: string) => {
      // Find and remove tab
      const tab = openTabs.find((t) => t.fileId === fileId);
      if (!tab) return;

      setOpenTabs(removeTab(openTabs, tab.id));

      // Mark file as closed
      const f = files[fileId];
      if (f) {
        setFiles({
          ...files,
          [fileId]: { ...f, isOpen: false },
        });
      }

      // Clear active file if it was closed
      if (activeFileId === fileId) setActiveFileId(null);
    },
    [openTabs, setOpenTabs, setFiles, setActiveFileId]
  );

  /**
   * Delete folder
   */
  const deleteFolder = useCallback(
    (folderId: string) => {
      const { [folderId]: _, ...restFolders } = folders;
      setFolders(restFolders);
    },
    [setFolders]
  );

  /**
   * Rename folder
   */
  const renameFolder = useCallback(
    (folderId: string, newPath: string) => {
      if (!isValidPath(newPath)) {
        throw new Error(`Invalid folder path: ${newPath}`);
      }

      const normalizedPath = normalizePath(newPath);
      const newName = normalizedPath.split('/').pop() || 'untitled';

      const folderToRename = folders[folderId];
      if (!folderToRename) return;

      setFolders({
        ...folders,
        [folderId]: {
          ...folderToRename,
          path: normalizedPath,
          name: newName,
        },
      });
    },
    [setFolders]
  );

  /**
   * Move file to a new destination path
   */
  const moveFile = useCallback(
    (fileId: string, destination: string) => {
      const file = files[fileId];
      if (!file) return;

      const normalizedDestination = normalizePath(destination);
      const fileName = file.name;
      const newPath = normalizedDestination.endsWith('/')
        ? normalizedDestination + fileName
        : normalizedDestination + '/' + fileName;

      setFiles({
        ...files,
        [fileId]: {
          ...file,
          path: newPath,
          lastModified: Date.now(),
        },
      });
    },
    [files, setFiles]
  );

  /**
   * Toggle folder expanded state
   */
  const toggleFolderExpanded = useCallback(
    (folderId: string) => {
      const folderToToggle = folders[folderId];
      if (!folderToToggle) return;

      setFolders({
        ...folders,
        [folderId]: {
          ...folderToToggle,
          isExpanded: !folderToToggle.isExpanded,
        },
      });
    },
    [setFolders]
  );

  return {
    // File operations
    createNewFile,
    updateFileContent,
    saveFile,
    deleteFile,
    renameFile,
    moveFile,
    openFile,
    closeFile,

    // Folder operations
    createNewFolder,
    deleteFolder,
    renameFolder,
    toggleFolderExpanded,
  };
}
