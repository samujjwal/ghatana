/**
 * Editor Lifecycle Manager
 * 
 * Manages Monaco editor instances with pooling, lazy loading,
 * and performance optimizations for large-scale IDE usage.
 * 
 * Features:
 * - 🏊 Editor instance pooling for performance
 * - 🔄 Lazy loading and unloading of editors
 * - 💾 Memory management for 100K+ line files
 * - 📊 Performance monitoring and metrics
 * - 🎯 CRDT binding lifecycle management
 * 
 * @doc.type class
 * @doc.purpose Editor lifecycle and performance management
 * @doc.layer product
 * @doc.pattern Manager
 */

import type { editor } from 'monaco-editor';
import type { Monaco } from '@monaco-editor/react';
import * as Y from 'yjs';

import type { EditorInstance, EditorMetrics } from '../components/EnhancedCodeEditor';

/**
 * Editor pool configuration
 */
export interface EditorPoolConfig {
  /** Maximum number of editor instances in pool */
  maxPoolSize: number;
  /** Maximum idle time before unloading (ms) */
  maxIdleTime: number;
  /** Memory threshold for forced cleanup (bytes) */
  memoryThreshold: number;
  /** Enable performance monitoring */
  enableMetrics: boolean;
}

/**
 * Editor factory function type
 */
export type EditorFactory = (
  container: HTMLElement,
  options: editor.IStandaloneEditorConstructionOptions
) => editor.IStandaloneCodeEditor;

/**
 * Editor Lifecycle Manager
 */
export class EditorLifecycleManager {
  private pool: Map<string, EditorInstance> = new Map();
  private factory: EditorFactory;
  private config: EditorPoolConfig;
  private metrics: Map<string, EditorMetrics> = new Map();
  private cleanupInterval: NodeJS.Timeout | null = null;
  private lastCleanup = Date.now();

  /**
   * Create editor lifecycle manager
   */
  constructor(factory: EditorFactory, config: Partial<EditorPoolConfig> = {}) {
    this.factory = factory;
    this.config = {
      maxPoolSize: 10,
      maxIdleTime: 300000, // 5 minutes
      memoryThreshold: 100 * 1024 * 1024, // 100MB
      enableMetrics: true,
      ...config,
    };

    // Start cleanup interval
    this.startCleanupInterval();
  }

  /**
   * Get or create editor instance
   */
  getOrCreateEditor(
    fileId: string,
    container: HTMLElement,
    options: editor.IStandaloneEditorConstructionOptions,
    ydoc: Y.Doc
  ): EditorInstance {
    // Check if editor exists in pool
    let instance = this.pool.get(fileId);
    
    if (instance && instance.isMounted) {
      // Update last activity
      instance.lastActivity = Date.now();
      return instance;
    }

    // Create new instance if pool not at capacity
    if (this.pool.size >= this.config.maxPoolSize) {
      this.cleanupIdleEditors();
    }

    // Create editor instance
    const editor = this.factory(container, options);
    const ymap = ydoc.getMap('files');
    let ytext = ymap.get(fileId) as Y.Text;
    
    if (!ytext) {
      ytext = new Y.Text();
      ymap.set(fileId, ytext);
    }

    instance = {
      id: `editor-${fileId}-${Date.now()}`,
      fileId,
      editor,
      monaco: (editor as unknown as { _monaco?: Monaco })._monaco as Monaco,
      ytext,
      isMounted: true,
      lastActivity: Date.now(),
    };

    // Add to pool
    this.pool.set(fileId, instance);

    // Setup metrics tracking
    if (this.config.enableMetrics) {
      this.setupMetricsTracking(instance);
    }

    return instance;
  }

  /**
   * Release editor instance back to pool
   */
  releaseEditor(fileId: string): void {
    const instance = this.pool.get(fileId);
    if (!instance) return;

    // Mark as unmounted but keep in pool for reuse
    instance.isMounted = false;
    instance.lastActivity = Date.now();

    // Clear decorations and bindings
    this.cleanupEditor(instance);
  }

  /**
   * Remove editor from pool completely
   */
  removeEditor(fileId: string): void {
    const instance = this.pool.get(fileId);
    if (!instance) return;

    // Full cleanup
    this.cleanupEditor(instance);
    instance.editor.dispose();
    
    // Remove from pool and metrics
    this.pool.delete(fileId);
    this.metrics.delete(fileId);
  }

  /**
   * Get editor instance without creating
   */
  getEditor(fileId: string): EditorInstance | undefined {
    const instance = this.pool.get(fileId);
    return instance?.isMounted ? instance : undefined;
  }

  /**
   * Get performance metrics for all editors
   */
  getAllMetrics(): Map<string, EditorMetrics> {
    return new Map(this.metrics);
  }

  /**
   * Get metrics for specific editor
   */
  getMetrics(fileId: string): EditorMetrics | undefined {
    return this.metrics.get(fileId);
  }

  /**
   * Force cleanup of idle editors
   */
  cleanupIdleEditors(): void {
    const now = Date.now();
    const toRemove: string[] = [];

    for (const [fileId, instance] of this.pool) {
      const idleTime = now - instance.lastActivity;
      
      if (!instance.isMounted && idleTime > this.config.maxIdleTime) {
        toRemove.push(fileId);
      }
    }

    // Remove idle editors
    toRemove.forEach(fileId => {
      this.removeEditor(fileId);
    });
    if (toRemove.length > 0) {
      console.log(`Cleaned up ${toRemove.length} idle editors`);
    }
  }

  /**
   * Check memory usage and cleanup if necessary
   */
  checkMemoryUsage(): void {
    if (!this.config.enableMetrics) return;

    const memoryUsage = (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory?.usedJSHeapSize || 0;
    
    if (memoryUsage > this.config.memoryThreshold) {
      console.warn(`Memory usage (${memoryUsage} bytes) exceeds threshold, forcing cleanup`);
      
      // Force cleanup of all unmounted editors
      const toRemove: string[] = [];
      for (const [fileId, instance] of this.pool) {
        if (!instance.isMounted) {
          toRemove.push(fileId);
        }
      }
      
      toRemove.forEach(fileId => this.removeEditor(fileId));
      
      // If still over threshold, remove oldest mounted editors
      const currentMemory = (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory?.usedJSHeapSize ?? 0;
      if (currentMemory > this.config.memoryThreshold) {
        const mountedEditors = Array.from(this.pool.entries())
          .filter(([, instance]) => instance.isMounted)
          .sort(([, a], [, b]) => a.lastActivity - b.lastActivity);
        
        // Remove oldest 25% of mounted editors
        const toRemoveCount = Math.ceil(mountedEditors.length * 0.25);
        for (let i = 0; i < toRemoveCount; i++) {
          const [fileId] = mountedEditors[i];
          this.removeEditor(fileId);
        }
      }
    }
  }

  /**
   * Setup performance metrics tracking for editor
   */
  private setupMetricsTracking(instance: EditorInstance): void {
    const updateMetrics = () => {
      const model = instance.editor.getModel();
      const lineCount = model?.getLineCount() || 0;
      const isLargeFile = lineCount > 10000;

      const metrics: EditorMetrics = {
        renderTime: performance.now(),
        lineCount,
        memoryUsage: (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory?.usedJSHeapSize || 0,
        cursorCount: 0, // Would be populated by collaborative cursors
        isLargeFile,
      };

      this.metrics.set(instance.fileId, metrics);
    };

    // Update metrics on content change
    const disposable = instance.editor.onDidChangeModelContent(updateMetrics);

    // Initial metrics
    updateMetrics();

    // Store disposable for cleanup
    (instance as unknown as { metricsDisposable?: { dispose(): void } }).metricsDisposable = disposable;
  }

  /**
   * Cleanup editor instance
   */
  private cleanupEditor(instance: EditorInstance): void {
    // Clear decorations
    instance.editor.deltaDecorations([], []);
    
    // Dispose metrics tracking
    if ((instance as unknown as { metricsDisposable?: { dispose(): void } }).metricsDisposable) {
      (instance as unknown as { metricsDisposable: { dispose(): void } }).metricsDisposable.dispose();
    }
    
    // Clear Yjs bindings if they exist
    if ((instance as unknown as { yjsBinding?: { dispose(): void } }).yjsBinding) {
      (instance as unknown as { yjsBinding: { dispose(): void } }).yjsBinding.dispose();
    }
  }

  /**
   * Start cleanup interval
   */
  private startCleanupInterval(): void {
    this.cleanupInterval = setInterval(() => {
      const now = Date.now();
      
      // Run cleanup every 5 minutes
      if (now - this.lastCleanup > 300000) {
        this.cleanupIdleEditors();
        this.checkMemoryUsage();
        this.lastCleanup = now;
      }
    }, 60000); // Check every minute
  }

  /**
   * Stop cleanup interval
   */
  stopCleanupInterval(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
  }

  /**
   * Dispose all editors and cleanup manager
   */
  dispose(): void {
    // Stop cleanup interval
    this.stopCleanupInterval();
    
    // Dispose all editors
    for (const [, instance] of this.pool) {
      this.cleanupEditor(instance);
      instance.editor.dispose();
    }
    
    // Clear pools and metrics
    this.pool.clear();
    this.metrics.clear();
  }

  /**
   * Get pool statistics
   */
  getPoolStats(): {
    totalEditors: number;
    mountedEditors: number;
    idleEditors: number;
    memoryUsage: number;
    largeFileEditors: number;
  } {
    let mounted = 0;
    let idle = 0;
    let largeFiles = 0;
    
    for (const instance of this.pool.values()) {
      if (instance.isMounted) {
        mounted++;
      } else {
        idle++;
      }
      
      const metrics = this.metrics.get(instance.fileId);
      if (metrics?.isLargeFile) {
        largeFiles++;
      }
    }

    return {
      totalEditors: this.pool.size,
      mountedEditors: mounted,
      idleEditors: idle,
      memoryUsage: (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory?.usedJSHeapSize || 0,
      largeFileEditors: largeFiles,
    };
  }
}

/**
 * Global editor lifecycle manager instance
 */
let globalEditorManager: EditorLifecycleManager | null = null;

/**
 * Get or create global editor lifecycle manager
 */
export function getEditorLifecycleManager(
  factory?: EditorFactory,
  config?: Partial<EditorPoolConfig>
): EditorLifecycleManager {
  if (!globalEditorManager) {
    const defaultFactory: EditorFactory = () => {
      // This would be replaced with actual Monaco editor creation
      throw new Error('Editor factory not provided');
    };
    
    globalEditorManager = new EditorLifecycleManager(
      factory || defaultFactory,
      config
    );
  }
  
  return globalEditorManager;
}

/**
 * Dispose global editor manager
 */
export function disposeEditorManager(): void {
  if (globalEditorManager) {
    globalEditorManager.dispose();
    globalEditorManager = null;
  }
}
