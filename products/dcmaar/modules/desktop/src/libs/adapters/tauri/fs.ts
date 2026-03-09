/**
 * Tauri file system bridge for adapter operations.
 * Provides secure file I/O with proper error handling.
 */

export interface TauriFS {
  readTextFile(path: string): Promise<string>;
  writeTextFile(path: string, content: string): Promise<void>;
  appendTextFile(path: string, content: string): Promise<void>;
  exists(path: string): Promise<boolean>;
  createDir(path: string, options?: { recursive?: boolean }): Promise<void>;
  readDir(path: string): Promise<FileEntry[]>;
  removeFile(path: string): Promise<void>;
  metadata(path: string): Promise<FileMetadata>;
}

export interface FileEntry {
  name: string;
  path: string;
  isDirectory: boolean;
  isFile: boolean;
}

export interface FileMetadata {
  size: number;
  modifiedAt: number;
  createdAt: number;
  isDirectory: boolean;
  isFile: boolean;
}

class TauriFSBridge implements TauriFS {
  private async invoke<T>(command: string, args?: Record<string, unknown>): Promise<T> {
    if (typeof window === 'undefined' || !('__TAURI__' in window)) {
      throw new Error('Tauri runtime not available');
    }

    const tauri = (window as any).__TAURI__;
    return await tauri.invoke(command, args);
  }

  async readTextFile(path: string): Promise<string> {
    try {
      return await this.invoke<string>('plugin:fs|read_text_file', { path });
    } catch (error) {
      throw new Error(`Failed to read file ${path}: ${(error as Error).message}`);
    }
  }

  async writeTextFile(path: string, content: string): Promise<void> {
    try {
      await this.invoke('plugin:fs|write_text_file', { path, content });
    } catch (error) {
      throw new Error(`Failed to write file ${path}: ${(error as Error).message}`);
    }
  }

  async appendTextFile(path: string, content: string): Promise<void> {
    try {
      await this.invoke('plugin:fs|append_text_file', { path, content });
    } catch (error) {
      throw new Error(`Failed to append to file ${path}: ${(error as Error).message}`);
    }
  }

  async exists(path: string): Promise<boolean> {
    try {
      return await this.invoke<boolean>('plugin:fs|exists', { path });
    } catch {
      return false;
    }
  }

  async createDir(path: string, options?: { recursive?: boolean }): Promise<void> {
    try {
      await this.invoke('plugin:fs|create_dir', {
        path,
        recursive: options?.recursive ?? false,
      });
    } catch (error) {
      throw new Error(`Failed to create directory ${path}: ${(error as Error).message}`);
    }
  }

  async readDir(path: string): Promise<FileEntry[]> {
    try {
      return await this.invoke<FileEntry[]>('plugin:fs|read_dir', { path });
    } catch (error) {
      throw new Error(`Failed to read directory ${path}: ${(error as Error).message}`);
    }
  }

  async removeFile(path: string): Promise<void> {
    try {
      await this.invoke('plugin:fs|remove_file', { path });
    } catch (error) {
      throw new Error(`Failed to remove file ${path}: ${(error as Error).message}`);
    }
  }

  async metadata(path: string): Promise<FileMetadata> {
    try {
      return await this.invoke<FileMetadata>('plugin:fs|metadata', { path });
    } catch (error) {
      throw new Error(`Failed to get metadata for ${path}: ${(error as Error).message}`);
    }
  }
}

// Mock implementation for non-Tauri environments
class MockFSBridge implements TauriFS {
  private storage = new Map<string, string>();

  async readTextFile(path: string): Promise<string> {
    const content = this.storage.get(path);
    if (!content) {
      throw new Error(`File not found: ${path}`);
    }
    return content;
  }

  async writeTextFile(path: string, content: string): Promise<void> {
    this.storage.set(path, content);
  }

  async appendTextFile(path: string, content: string): Promise<void> {
    const existing = this.storage.get(path) ?? '';
    this.storage.set(path, existing + content);
  }

  async exists(path: string): Promise<boolean> {
    return this.storage.has(path);
  }

  async createDir(_path: string): Promise<void> {
    // No-op in mock
  }

  async readDir(_path: string): Promise<FileEntry[]> {
    return [];
  }

  async removeFile(path: string): Promise<void> {
    this.storage.delete(path);
  }

  async metadata(path: string): Promise<FileMetadata> {
    const content = this.storage.get(path);
    if (!content) {
      throw new Error(`File not found: ${path}`);
    }

    return {
      size: content.length,
      modifiedAt: Date.now(),
      createdAt: Date.now(),
      isDirectory: false,
      isFile: true,
    };
  }
}

export const createTauriFS = (): TauriFS => {
  if (typeof window !== 'undefined' && '__TAURI__' in window) {
    return new TauriFSBridge();
  }
  return new MockFSBridge();
};

export const tauriFS = createTauriFS();
