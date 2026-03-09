/**
 * Platform filesystem interface for browser-based file system access
 */
declare module '@platform/filesystem' {
  /**
   * File system adapter interface for browser file system operations
   */
  export interface FileSystemAdapter {
    /**
     * Read a file as an ArrayBuffer
     * @param path Path to the file
     */
    readFile(path: string): Promise<ArrayBuffer>;
    
    /**
     * Write data to a file
     * @param path Path to the file
     * @param data Data to write (string, Blob, or ArrayBuffer)
     */
    writeFile(path: string, data: string | Blob | ArrayBuffer): Promise<void>;
    
    /**
     * Check if a file exists
     * @param path Path to check
     */
    exists(path: string): Promise<boolean>;
    
    /**
     * Create a directory
     * @param path Directory path
     * @param options Options including recursive creation
     */
    mkdir(path: string, options?: { recursive?: boolean }): Promise<void>;
    
    /**
     * Read directory contents
     * @param path Directory path
     */
    readdir(path: string): Promise<string[]>;
    
    /**
     * Remove a file
     * @param path Path to the file to remove
     */
    unlink(path: string): Promise<void>;
    
    /**
     * Remove a directory
     * @param path Directory path
     * @param options Options including recursive removal
     */
    rmdir(path: string, options?: { recursive?: boolean }): Promise<void>;
    
    /**
     * Get file information
     * @param path Path to the file
     */
    stat(path: string): Promise<FileSystemFileHandle | FileSystemDirectoryHandle>;
    
    /**
     * Get a file handle
     * @param path Path to the file
     * @param options Options including create flag
     */
    getFileHandle(
      path: string, 
      options?: { create?: boolean }
    ): Promise<FileSystemFileHandle>;
    
    /**
     * Get a directory handle
     * @param path Path to the directory
     * @param options Options including create flag
     */
    getDirectoryHandle(
      path: string, 
      options?: { create?: boolean }
    ): Promise<FileSystemDirectoryHandle>;
  }
  
  /**
   * Default file system adapter instance
   */
  export const fs: FileSystemAdapter;
  
  /**
   * Initialize the file system with a directory handle
   * @param dirHandle Optional directory handle to use
   */
  export function initFileSystem(dirHandle?: FileSystemDirectoryHandle): Promise<FileSystemAdapter>;
  
  /**
   * Request a directory handle from the user
   */
  export function requestDirectoryHandle(): Promise<FileSystemDirectoryHandle>;
  
  /**
   * Check if the File System Access API is supported
   */
  export const isSupported: boolean;
}
