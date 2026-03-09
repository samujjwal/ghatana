/**
 * @ghatana/yappc-ide - File System Utilities
 * 
 * Utilities for file and folder operations with UUID-based stable identity.
 * 
 * @doc.type module
 * @doc.purpose File system utilities for IDE
 * @doc.layer product
 * @doc.pattern Utility Functions
 */

import { v4 as uuidv4 } from 'uuid';
import type { IDEFile, IDEFolder, FileTreeNode } from '../types';

/**
 * Create a new IDE file
 * 
 * @doc.param path - File path
 * @doc.param content - File content
 * @doc.param language - Programming language
 * @doc.returns New IDE file with UUID
 */
export function createFile(
  path: string,
  content: string = '',
  language: string = 'plaintext'
): IDEFile {
  const name = path.split('/').pop() || 'untitled';
  const now = Date.now();
  
  return {
    id: uuidv4(),
    path,
    name,
    content,
    language,
    isDirty: false,
    isOpen: false,
    lastModified: now,
    createdAt: now,
    size: content.length,
  };
}

/**
 * Create a new IDE folder
 * 
 * @doc.param path - Folder path
 * @doc.returns New IDE folder with UUID
 */
export function createFolder(path: string): IDEFolder {
  const name = path.split('/').pop() || 'untitled';
  const now = Date.now();
  
  return {
    id: uuidv4(),
    path,
    name,
    children: [],
    isExpanded: false,
    createdAt: now,
  };
}

/**
 * Detect language from file extension
 * 
 * @doc.param filename - File name with extension
 * @doc.returns Language identifier
 */
export function detectLanguage(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase();
  
  const languageMap: Record<string, string> = {
    ts: 'typescript',
    tsx: 'typescript',
    js: 'javascript',
    jsx: 'javascript',
    json: 'json',
    html: 'html',
    css: 'css',
    scss: 'scss',
    sass: 'sass',
    less: 'less',
    md: 'markdown',
    py: 'python',
    java: 'java',
    go: 'go',
    rs: 'rust',
    c: 'c',
    cpp: 'cpp',
    h: 'c',
    hpp: 'cpp',
    cs: 'csharp',
    rb: 'ruby',
    php: 'php',
    swift: 'swift',
    kt: 'kotlin',
    sql: 'sql',
    sh: 'shell',
    bash: 'shell',
    yaml: 'yaml',
    yml: 'yaml',
    xml: 'xml',
    toml: 'toml',
    ini: 'ini',
    dockerfile: 'dockerfile',
  };
  
  return ext ? languageMap[ext] || 'plaintext' : 'plaintext';
}

/**
 * Check if a node is a file
 * 
 * @doc.param node - File tree node
 * @doc.returns True if node is a file
 */
export function isFile(node: FileTreeNode): node is IDEFile {
  return 'content' in node;
}

/**
 * Check if a node is a folder
 * 
 * @doc.param node - File tree node
 * @doc.returns True if node is a folder
 */
export function isFolder(node: FileTreeNode): node is IDEFolder {
  return 'children' in node;
}

/**
 * Get file extension
 * 
 * @doc.param filename - File name
 * @doc.returns File extension without dot
 */
export function getFileExtension(filename: string): string {
  return filename.split('.').pop()?.toLowerCase() || '';
}

/**
 * Get file name without extension
 * 
 * @doc.param filename - File name
 * @doc.returns File name without extension
 */
export function getFileNameWithoutExtension(filename: string): string {
  const parts = filename.split('.');
  if (parts.length === 1) return filename;
  return parts.slice(0, -1).join('.');
}

/**
 * Get parent path from a file path
 * 
 * @doc.param path - File or folder path
 * @doc.returns Parent path or null if root
 */
export function getParentPath(path: string): string | null {
  const parts = path.split('/').filter(Boolean);
  if (parts.length <= 1) return null;
  return '/' + parts.slice(0, -1).join('/');
}

/**
 * Join path segments
 * 
 * @doc.param segments - Path segments
 * @doc.returns Joined path
 */
export function joinPath(...segments: string[]): string {
  return '/' + segments
    .join('/')
    .split('/')
    .filter(Boolean)
    .join('/');
}

/**
 * Normalize path
 * 
 * @doc.param path - Path to normalize
 * @doc.returns Normalized path
 */
export function normalizePath(path: string): string {
  return '/' + path.split('/').filter(Boolean).join('/');
}

/**
 * Check if path is valid
 * 
 * @doc.param path - Path to validate
 * @doc.returns True if path is valid
 */
export function isValidPath(path: string): boolean {
  if (!path || path.length === 0) return false;
  if (path.includes('..')) return false; // No parent directory traversal
  if (path.includes('//')) return false; // No double slashes
  return true;
}

/**
 * Sort file tree nodes (folders first, then alphabetically)
 * 
 * @doc.param nodes - Array of file tree nodes
 * @doc.returns Sorted array
 */
export function sortFileTreeNodes(nodes: FileTreeNode[]): FileTreeNode[] {
  return [...nodes].sort((a, b) => {
    const aIsFolder = isFolder(a);
    const bIsFolder = isFolder(b);
    
    // Folders first
    if (aIsFolder && !bIsFolder) return -1;
    if (!aIsFolder && bIsFolder) return 1;
    
    // Alphabetically
    return a.name.localeCompare(b.name);
  });
}

/**
 * Find file by path in file tree
 * 
 * @doc.param folder - Root folder
 * @doc.param path - File path to find
 * @doc.returns File if found, null otherwise
 */
export function findFileByPath(folder: IDEFolder, path: string): IDEFile | null {
  for (const child of folder.children) {
    if (isFile(child) && child.path === path) {
      return child;
    }
    if (isFolder(child)) {
      const found = findFileByPath(child, path);
      if (found) return found;
    }
  }
  return null;
}

/**
 * Find folder by path in file tree
 * 
 * @doc.param folder - Root folder
 * @doc.param path - Folder path to find
 * @doc.returns Folder if found, null otherwise
 */
export function findFolderByPath(folder: IDEFolder, path: string): IDEFolder | null {
  if (folder.path === path) return folder;
  
  for (const child of folder.children) {
    if (isFolder(child)) {
      const found = findFolderByPath(child, path);
      if (found) return found;
    }
  }
  return null;
}

/**
 * Get all files in a folder recursively
 * 
 * @doc.param folder - Root folder
 * @doc.returns Array of all files
 */
export function getAllFiles(folder: IDEFolder): IDEFile[] {
  const files: IDEFile[] = [];
  
  for (const child of folder.children) {
    if (isFile(child)) {
      files.push(child);
    } else if (isFolder(child)) {
      files.push(...getAllFiles(child));
    }
  }
  
  return files;
}

/**
 * Calculate folder size (total size of all files)
 * 
 * @doc.param folder - Folder to calculate size for
 * @doc.returns Total size in bytes
 */
export function calculateFolderSize(folder: IDEFolder): number {
  return getAllFiles(folder).reduce((total, file) => total + file.size, 0);
}

/**
 * Format file size for display
 * 
 * @doc.param bytes - Size in bytes
 * @doc.returns Formatted size string
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${units[i]}`;
}
