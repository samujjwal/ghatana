// Type definitions for native-file-system-adapter
declare module 'native-file-system-adapter' {
  export interface FileSystemHandle {
    readonly kind: 'file' | 'directory';
    readonly name: string;
    isSameEntry: (other: FileSystemHandle) => Promise<boolean>;
  }

  export interface FileSystemFileHandle extends FileSystemHandle {
    readonly kind: 'file';
    getFile: () => Promise<File>;
    createWritable: (options?: { keepExistingData?: boolean }) => Promise<FileSystemWritableFileStream>;
  }

  export interface FileSystemDirectoryHandle extends FileSystemHandle {
    readonly kind: 'directory';
    getFileHandle: (name: string, options?: { create?: boolean }) => Promise<FileSystemFileHandle>;
    getDirectoryHandle: (name: string, options?: { create?: boolean }) => Promise<FileSystemDirectoryHandle>;
    removeEntry: (name: string, options?: { recursive?: boolean }) => Promise<void>;
    resolve: (possibleDescendant: FileSystemHandle) => Promise<string[] | null>;
    keys: () => AsyncIterableIterator<string>;
    values: () => AsyncIterableIterator<FileSystemHandle>;
    entries: () => AsyncIterableIterator<[string, FileSystemHandle]>;
    [Symbol.asyncIterator]: () => AsyncIterableIterator<[string, FileSystemHandle]>;
  }

  export interface FileSystemWritableFileStream extends WritableStream {
    write: (data: string | BufferSource | Blob | { type: 'write' | 'seek' | 'truncate'; size?: number; position?: number; data?: string | BufferSource | Blob }) => Promise<void>;
    seek: (position: number) => Promise<void>;
    truncate: (size: number) => Promise<void>;
    close: () => Promise<void>;
  }

  export interface FileSystemAccessHandle {
    createWritable: (options?: { keepExistingData?: boolean }) => Promise<FileSystemWritableFileStream>;
    getFile: () => Promise<File>;
    close: () => Promise<void>;
  }

  export interface FileSystemDirectoryAccessHandle {
    getFileHandle: (name: string, options?: { create?: boolean }) => Promise<FileSystemAccessHandle>;
    getDirectoryHandle: (name: string, options?: { create?: boolean }) => Promise<FileSystemDirectoryAccessHandle>;
    removeEntry: (name: string, options?: { recursive?: boolean }) => Promise<void>;
    resolve: (possibleDescendant: string) => Promise<string[] | null>;
    keys: () => AsyncIterableIterator<string>;
    values: () => AsyncIterableIterator<FileSystemAccessHandle | FileSystemDirectoryAccessHandle>;
    entries: () => AsyncIterableIterator<[string, FileSystemAccessHandle | FileSystemDirectoryAccessHandle]>;
    [Symbol.asyncIterator]: () => AsyncIterableIterator<[string, FileSystemAccessHandle | FileSystemDirectoryAccessHandle]>;
  }

  export interface FileSystemAccess {
    requestFileHandle: (options?: { type?: 'open-file' | 'save-file' | 'open-directory'; multiple?: boolean; suggestedName?: string; accepts?: { description?: string; mimeTypes?: string[]; extensions?: string[] }[] }) => Promise<FileSystemFileHandle | FileSystemDirectoryHandle | null>;
    requestDirectoryAccess: () => Promise<FileSystemDirectoryHandle | null>;
    requestFileAccess: () => Promise<FileSystemFileHandle | null>;
    requestSaveAccess: (suggestedName?: string, accepts?: { description?: string; mimeTypes?: string[]; extensions?: string[] }[]) => Promise<FileSystemFileHandle | null>;
    getOriginPrivateDirectory: () => Promise<FileSystemDirectoryHandle>;
    getFileHandle: (name: string, options?: { create?: boolean }) => Promise<FileSystemFileHandle>;
    getDirectoryHandle: (name: string, options?: { create?: boolean }) => Promise<FileSystemDirectoryHandle>;
    removeEntry: (name: string, options?: { recursive?: boolean }) => Promise<void>;
    resolve: (possibleDescendant: string) => Promise<string[] | null>;
    keys: () => AsyncIterableIterator<string>;
    values: () => AsyncIterableIterator<FileSystemHandle>;
    entries: () => AsyncIterableIterator<[string, FileSystemHandle]>;
    [Symbol.asyncIterator]: () => AsyncIterableIterator<[string, FileSystemHandle]>;
  }

  const fileSystemAccess: FileSystemAccess;
  export default fileSystemAccess;
}
