/**
 * Platform abstraction for File System Access API operations.
 * Provides consistent, typed wrappers for common filesystem operations
 * used across the extension's adapters and components.
 */

import '../types/platform-filesystem.d';
import { STORAGE_KEYS } from '@shared/config/constants';

import { getHandle, setHandle } from './handleStorage';

/**
 * File picker options for the File System Access API
 */
export interface FilePickerOptions {
  description?: string;
  accept?: Record<string, string[]>;
  startIn?: FileSystemWellKnownDirectory | FileSystemDirectoryHandle;
  multiple?: boolean;
}

/**
 * Directory picker options
 */
export interface DirectoryPickerOptions {
  startIn?: FileSystemWellKnownDirectory | FileSystemDirectoryHandle;
}

/**
 * Handle metadata for persistence
 */
export interface HandleMetadata {
  name?: string;
  [key: string]: unknown;
}

/**
 * Ensure permission for a file system handle
 */
export async function ensurePermission(
  handle: FileSystemHandle | undefined,
  mode: 'read' | 'readwrite' = 'read'
): Promise<boolean> {
  if (!handle) return false;
  
  try {
    // Check current permission status
    if ((await handle.queryPermission({ mode })) === 'granted') {
      return true;
    }
    
    // Request permission if not granted
    const result = await handle.requestPermission({ mode });
    return result === 'granted';
  } catch (error) {
    // Permission API not available or failed. Mark `error` as intentionally unused for linters.
    void error;
    return false;
  }
}

/**
 * Pick a directory using the File System Access API
 */
export async function pickDirectory(options: DirectoryPickerOptions = {}): Promise<FileSystemDirectoryHandle> {
  return await globalThis.showDirectoryPicker({
    startIn: options.startIn || 'documents',
  });
}

/**
 * Pick one or more files using the File System Access API
 */
export async function pickFiles(options: FilePickerOptions = {}): Promise<FileSystemFileHandle[]> {
  const pickerOptions: OpenFilePickerOptions = {
    multiple: options.multiple || false,
    startIn: options.startIn || 'documents',
  };
  
  if (options.description || options.accept) {
    pickerOptions.types = [{
      description: options.description || 'Files',
      accept: options.accept || { '*/*': [] },
    }];
  }
  
  return await globalThis.showOpenFilePicker(pickerOptions);
}

/**
 * Pick a single file using the File System Access API
 * @throws {Error} If no file is selected or file access is denied
 */
export async function pickFile(
  options: Omit<FilePickerOptions, 'multiple'> = {}
): Promise<FileSystemFileHandle> {
  const handles = await pickFiles({ ...options, multiple: false });
  const handle = handles[0];
  
  if (!handle) {
    throw new Error('No file was selected');
  }
  
  return handle;
}

/**
 * Get a persisted handle by key, with optional permission check
 */
export async function getPersistedHandle<T extends FileSystemHandle>(
  key: string,
  mode: 'read' | 'readwrite' = 'read'
): Promise<T | null> {
  try {
    const handle = await getHandle(key) as T | null;
    if (handle && await ensurePermission(handle, mode)) {
      return handle;
    }
    return null;
  } catch (error) {
    // Mark `error` as intentionally unused for linters.
    void error;
    return null;
  }
}

/**
 * Persist a handle with metadata
 */
export async function persistHandle(
  key: string,
  handle: FileSystemHandle,
  metadata: HandleMetadata = {}
): Promise<void> {
  try {
    await setHandle(key, handle, metadata);
  } catch (error) {
    // Ignore persistence failures - not critical
    // Mark `error` as intentionally unused for linters.
    void error;
  }
}

/**
 * Get or pick a directory handle, using persisted handle if available
 */
export async function getOrPickDirectory(
  persistKey: string = STORAGE_KEYS.DIR,
  options: DirectoryPickerOptions = {}
): Promise<FileSystemDirectoryHandle> {
  // Try to get persisted handle first
  const persisted = await getPersistedHandle<FileSystemDirectoryHandle>(persistKey, 'readwrite');
  if (persisted) {
    return persisted;
  }
  
  // Pick new directory
  const newHandle = await pickDirectory(options);
  
  // Persist for future use
  await persistHandle(persistKey, newHandle, { name: newHandle.name });
  
  return newHandle;
}

/**
 * Get or pick a file handle, using persisted handle if available
 */
export async function getOrPickFile(
  persistKey: string,
  options: Omit<FilePickerOptions, 'multiple'> = {}
): Promise<FileSystemFileHandle> {
  // Try to get persisted handle first
  const persisted = await getPersistedHandle<FileSystemFileHandle>(persistKey, 'read');
  if (persisted) {
    return persisted;
  }
  
  // Pick new file
  const newHandle = await pickFile(options);
  
  // Persist for future use
  await persistHandle(persistKey, newHandle, { name: newHandle.name });
  
  return newHandle;
}

/**
 * Read lines from a file handle
 */
export async function readLines(handle: FileSystemFileHandle): Promise<string[]> {
  const file = await handle.getFile();
  const text = await file.text();
  return text.split(/\r?\n/).filter(line => line.trim().length > 0);
}

/**
 * Write lines to a file handle, replacing existing content
 */
export async function writeLines(handle: FileSystemFileHandle, lines: string[]): Promise<void> {
  await ensurePermission(handle, 'readwrite');
  const writable = await handle.createWritable({ keepExistingData: false });
  const content = lines.join('\n') + (lines.length > 0 ? '\n' : '');
  await writable.write(content);
  await writable.close();
}

/**
 * Append lines to a file handle
 */
export async function appendLines(handle: FileSystemFileHandle, lines: string[]): Promise<void> {
  await ensurePermission(handle, 'readwrite');
  
  if (lines.length === 0) return;
  
  try {
    // Try positional append first
    const file = await handle.getFile();
    const size = file.size || 0;
    const writable = await handle.createWritable({ keepExistingData: true });
    const content = lines.join('\n') + '\n';
    await writable.write({ type: 'write', position: size, data: content });
    await writable.close();
  } catch (error) {
    // Fallback to read-modify-write if positional append not supported
    // Mark `error` as intentionally unused for linters.
    void error;
    const existingLines = await readLines(handle);
    await writeLines(handle, [...existingLines, ...lines]);
  }
}

/**
 * Append a single line to a file handle
 */
export async function appendLine(handle: FileSystemFileHandle, line: string): Promise<void> {
  await appendLines(handle, [line]);
}

/**
 * Common file picker configurations
 */
export const FILE_PICKER_CONFIGS = {
  JSON: {
    description: 'JSON files',
    accept: { 'application/json': ['.json'] },
  },
  NDJSON: {
    description: 'Newline-delimited JSON files',
    accept: { 
      'text/plain': ['.ndjson', '.jsonl'],
      'application/x-ndjson': ['.ndjson'],
    },
  },
  TEXT: {
    description: 'Text files',
    accept: { 'text/plain': ['.txt', '.log'] },
  },
  ANY: {
    description: 'All files',
    accept: { '*/*': [] },
  },
} as const;
