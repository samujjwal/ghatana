// Type declarations for test environment
declare global {
  // Mock File System Access API types
  interface MockFileSystemHandle {
    kind: 'file' | 'directory';
    name: string;
    isSameEntry: (other: MockFileSystemHandle) => boolean;
  }

  interface MockFileHandle extends FileSystemFileHandle, MockFileSystemHandle {
    kind: 'file';
    _content: string;
    _getContent: () => string;
    createWritable: (options?: { keepExistingData?: boolean }) => Promise<{
      write: (content: string | BufferSource | Blob) => Promise<void>;
      close: () => Promise<void>;
    }>;
    getFile: () => Promise<File>;
  }

  interface MockDirectoryHandle extends FileSystemDirectoryHandle, MockFileSystemHandle {
    kind: 'directory';
    _handles: Map<string, MockFileSystemHandle>;
    getFileHandle: (name: string, options?: { create?: boolean }) => Promise<MockFileHandle>;
    getDirectoryHandle: (name: string, options?: { create?: boolean }) => Promise<MockDirectoryHandle>;
    removeEntry: (name: string, options?: { recursive?: boolean }) => Promise<void>;
    keys: () => AsyncIterableIterator<string>;
    values: () => AsyncIterableIterator<MockFileSystemHandle>;
    entries: () => AsyncIterableIterator<[string, MockFileSystemHandle]>;
    [Symbol.asyncIterator]: () => AsyncIterableIterator<[string, MockFileSystemHandle]>;
  }

  // Test utility functions
  function createInMemoryFileHandle(initial?: string): MockFileHandle;
  function createInMemoryDirHandle(initialFiles?: Record<string, string>): MockDirectoryHandle;

  // Extend Window interface for test globals
  interface Window {
    showDirectoryPicker?: () => Promise<MockDirectoryHandle>;
    showOpenFilePicker?: () => Promise<MockFileHandle[]>;
  }

  // Global test context
  const ctx: {
    logger: {
      warn: (...args: any[]) => void;
      error: (...args: any[]) => void;
      info: (...args: any[]) => void;
    };
  };
}

export {}; // This file is a module
