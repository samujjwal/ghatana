// Type definitions for test utilities
declare module '@communication/__tests__/test-utils' {
  export interface InMemoryFileHandle {
    _content: string;
    _getContent: () => string;
    _setContent: (content: string) => void;
    createWritable: () => Promise<{
      write: (data: string | Blob | ArrayBuffer) => Promise<void>;
      close: () => Promise<void>;
    }>;
    getFile: () => Promise<File>;
  }

  export interface InMemoryDirectoryHandle {
    _handles: Record<string, InMemoryFileHandle | InMemoryDirectoryHandle>;
    getFileHandle: (name: string, options?: { create?: boolean }) => Promise<InMemoryFileHandle>;
    getDirectoryHandle: (name: string, options?: { create?: boolean }) => Promise<InMemoryDirectoryHandle>;
    values: () => IterableIterator<InMemoryFileHandle | InMemoryDirectoryHandle>;
    keys: () => IterableIterator<string>;
    entries: () => IterableIterator<[string, InMemoryFileHandle | InMemoryDirectoryHandle]>;
    [Symbol.iterator]: () => IterableIterator<[string, InMemoryFileHandle | InMemoryDirectoryHandle]>;
  }

  export function createInMemoryFileHandle(initial?: string): InMemoryFileHandle;
  export function createInMemoryDirHandle(fileMap?: Record<string, string>): InMemoryDirectoryHandle;
}
