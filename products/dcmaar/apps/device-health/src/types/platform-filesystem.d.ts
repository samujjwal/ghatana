/**
 * Type definitions for File System Access API
 * These supplement the built-in types to provide better coverage
 */

declare global {
  // File System Access API types
  interface FileSystemHandle {
    queryPermission(descriptor?: FileSystemPermissionDescriptor): Promise<PermissionState>;
    requestPermission(descriptor?: FileSystemPermissionDescriptor): Promise<PermissionState>;
  }

  interface FileSystemPermissionDescriptor {
    mode?: 'read' | 'readwrite';
  }

  type FileSystemWellKnownDirectory = 
    | 'desktop'
    | 'documents'
    | 'downloads'
    | 'music'
    | 'pictures'
    | 'videos';

  interface DirectoryPickerOptions {
    startIn?: FileSystemWellKnownDirectory | FileSystemDirectoryHandle;
  }

  interface OpenFilePickerOptions {
    multiple?: boolean;
    startIn?: FileSystemWellKnownDirectory | FileSystemDirectoryHandle;
    types?: Array<{
      description?: string;
      accept: Record<string, string | string[]>;
    }>;
  }

  interface FileSystemWritableFileStreamWriteOptions {
    type: 'write';
    position?: number;
    data: string | BufferSource | Blob;
  }

  interface FileSystemCreateWritableOptions {
    keepExistingData?: boolean;
  }

  interface FileSystemFileHandle {
    createWritable(options?: FileSystemCreateWritableOptions): Promise<FileSystemWritableFileStream>;
  }

  interface FileSystemWritableFileStream {
    write(data: string | BufferSource | Blob | FileSystemWritableFileStreamWriteOptions): Promise<void>;
    close(): Promise<void>;
  }

  // Global functions
  function showDirectoryPicker(options?: DirectoryPickerOptions): Promise<FileSystemDirectoryHandle>;
  function showOpenFilePicker(options?: OpenFilePickerOptions): Promise<FileSystemFileHandle[]>;
}

export {};