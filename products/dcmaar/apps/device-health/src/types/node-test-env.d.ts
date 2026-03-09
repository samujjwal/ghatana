/**
 * Type definitions for Node.js filesystem operations in test environments
 * These types bridge the gap between Node.js fs API and browser File System Access API
 */

declare global {
  // Node.js filesystem mock for browser tests
  interface NodeFileSystemHandle {
    kind: 'file' | 'directory';
    name: string;
    createWritable(): Promise<NodeWritableFileStream>;
    queryPermission(): Promise<PermissionState>;
    requestPermission(): Promise<PermissionState>;
    getFile(): Promise<File>;
  }

  interface NodeWritableFileStream {
    write(data: string | BufferSource | Blob): Promise<void>;
    close(): Promise<void>;
  }

  // Mock implementations for test environments
  interface MockFileSystemAdapter {
    showDirectoryPicker?: (options?: DirectoryPickerOptions) => Promise<FileSystemDirectoryHandle>;
    showOpenFilePicker?: (options?: OpenFilePickerOptions) => Promise<FileSystemFileHandle[]>;
  }

  // Test environment globals
  interface Window {
    mockFileSystem?: MockFileSystemAdapter;
    __fs_test_handles?: Map<string, NodeFileSystemHandle>;
  }
}

// Node.js module types for test imports
declare module 'fs/promises' {
  export function readFile(path: string, encoding?: BufferEncoding): Promise<string>;
  export function writeFile(path: string, data: string | Buffer, options?: { encoding?: BufferEncoding }): Promise<void>;
  export function mkdir(path: string, options?: { recursive?: boolean }): Promise<void>;
  export function access(path: string, mode?: number): Promise<void>;
  export function unlink(path: string): Promise<void>;
  export function stat(path: string): Promise<{
    isFile(): boolean;
    isDirectory(): boolean;
    size: number;
    mtime: Date;
  }>;
}

declare module 'path' {
  export function join(...paths: string[]): string;
  export function resolve(...paths: string[]): string;
  export function dirname(path: string): string;
  export function basename(path: string): string;
  export function extname(path: string): string;
}

declare module 'crypto' {
  export function randomBytes(size: number): Buffer;
  export function createHash(algorithm: string): {
    update(data: string | Buffer): void;
    digest(encoding?: string): string | Buffer;
  };
}

export {};