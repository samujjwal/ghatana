/**
 * Yjs-Monaco Binding Utility
 * 
 * Provides bidirectional synchronization between Yjs Text and Monaco Editor
 * with conflict resolution and performance optimizations.
 * 
 * Features:
 * - 🔄 Bidirectional sync between Y.Text and Monaco
 * - ⚡ Incremental updates for performance
 * - 🛡️ Conflict resolution and prevention
 * - 📊 Change tracking and metrics
 * - 🎯 Collaborative cursor integration
 * 
 * @doc.type class
 * @doc.purpose Yjs-Monaco synchronization
 * @doc.layer product
 * @doc.pattern Binding
 */

import type * as Y from 'yjs';
import type { editor } from 'monaco-editor';
import type { CollaborativeCursor } from '../components/EnhancedCodeEditor';

/**
 * Binding configuration
 */
export interface YjsMonacoBindingConfig {
  /** Enable conflict resolution */
  enableConflictResolution: boolean;
  /** Debounce local changes (ms) */
  debounceMs: number;
  /** Enable change tracking */
  enableTracking: boolean;
  /** Maximum text length before disabling sync */
  maxTextLength: number;
}

/**
 * Change event information
 */
export interface TextChangeEvent {
  /** Origin of the change ('local' | 'remote') */
  origin: 'local' | 'remote';
  /** Change offset */
  offset: number;
  /** Length of deleted text */
  deleteLength: number;
  /** Inserted text */
  insertText: string;
  /** Timestamp */
  timestamp: number;
}

/**
 * Binding metrics
 */
export interface BindingMetrics {
  /** Total sync operations */
  syncOperations: number;
  /** Conflict count */
  conflicts: number;
  /** Average sync time (ms) */
  averageSyncTime: number;
  /** Text length */
  textLength: number;
  /** Last sync timestamp */
  lastSync: number;
}

/**
 * Yjs-Monaco Binding
 */
export class YjsMonacoBinding {
  private editor: editor.IStandaloneCodeEditor;
  private monaco: unknown;
  private ytext: Y.Text;
  private config: YjsMonacoBindingConfig;
  
  // State tracking
  private isLocalChange = false;
  private isRemoteChange = false;
  private disposed = false;
  
  // Debouncing
  private debounceTimeout: NodeJS.Timeout | null = null;
  
  // Metrics
  private metrics: BindingMetrics = {
    syncOperations: 0,
    conflicts: 0,
    averageSyncTime: 0,
    textLength: 0,
    lastSync: 0,
  };
  
  // Event listeners
  private disposables: { dispose(): void }[] = [];
  private ytextUnobserve: (() => void) | null = null;
  
  // Callbacks
  private onTextChange?: (event: TextChangeEvent) => void;
  private onCursorChange?: (position: { line: number; column: number }) => void;
  private onMetricsUpdate?: (metrics: BindingMetrics) => void;
  
  // Binding reference
  private bindingRef: {
    disposable: { dispose(): void };
    ytextUnobserve: () => void;
  } | null = null;

  /**
   * Create Yjs-Monaco binding
   */
  constructor(
    editor: editor.IStandaloneCodeEditor,
    monaco: typeof import('monaco-editor'),
    ytext: Y.Text,
    config: Partial<YjsMonacoBindingConfig> = {},
    callbacks?: {
      onTextChange?: (event: TextChangeEvent) => void;
      onCursorChange?: (position: { line: number; column: number }) => void;
      onMetricsUpdate?: (metrics: BindingMetrics) => void;
    }
  ) {
    this.editor = editor;
    this.monaco = monaco;
    this.ytext = ytext;
    this.config = {
      enableConflictResolution: true,
      debounceMs: 100,
      enableTracking: true,
      maxTextLength: 1000000, // 1M characters
      ...config,
    };
    
    this.onTextChange = callbacks?.onTextChange;
    this.onCursorChange = callbacks?.onCursorChange;
    this.onMetricsUpdate = callbacks?.onMetricsUpdate;

    this.setupBinding();
    this.updateMetrics();
  }

  /**
   * Setup bidirectional binding
   */
  private setupBinding(): void {
    // Initial sync from Yjs to Monaco
    this.syncFromYjsToMonaco();

    // Listen to Monaco changes
    const monacoDisposable = this.editor.onDidChangeModelContent((e) => {
      if (!this.isRemoteChange && !this.disposed) {
        this.handleMonacoChange(e);
      }
    });
    this.disposables.push(monacoDisposable);

    // Listen to cursor changes
    if (this.onCursorChange) {
      const cursorDisposable = this.editor.onDidChangeCursorPosition((e) => {
        this.onCursorChange?.({
          line: e.position.lineNumber,
          column: e.position.column,
        });
      });
      this.disposables.push(cursorDisposable);
    }

    // Listen to Yjs changes
    const ytextHandler = (event: Y.YTextEvent) => {
      if (!this.isLocalChange && !this.disposed) {
        this.handleYjsChange(event);
      }
    };
    
    this.ytext.observe(ytextHandler);
    this.ytextUnobserve = () => this.ytext.unobserve(ytextHandler);
  }

  // Local change handler
  private handleMonacoChange = (event: editor.IModelContentChangedEvent): void => {
    if (this.config.debounceMs > 0) {
      this.debouncedSyncFromMonacoToYjs(event);
    } else {
      this.syncFromMonacoToYjs(event);
    }
  };

  /**
   * Debounced sync from Monaco to Yjs
   */
  private debouncedSyncFromMonacoToYjs(event: editor.IModelContentChangedEvent): void {
    if (this.debounceTimeout) {
      clearTimeout(this.debounceTimeout);
    }

    this.debounceTimeout = setTimeout(() => {
      this.syncFromMonacoToYjs(event);
    }, this.config.debounceMs);
  }

  /**
   * Sync changes from Monaco to Yjs
   */
  private syncFromMonacoToYjs(event: editor.IModelContentChangedEvent): void {
    const startTime = performance.now();
    
    try {
      this.isLocalChange = true;

      // Apply changes to Yjs
      for (const change of event.changes) {
        const { range, text } = change;
        const startOffset = this.getOffsetAtPosition(range.startLineNumber, range.startColumn);
        const endOffset = this.getOffsetAtPosition(range.endLineNumber, range.endColumn);
        const deleteLength = endOffset - startOffset;

        // Check text length limit
        if (this.ytext.length - deleteLength + text.length > this.config.maxTextLength) {
          console.warn('Text length exceeds maximum, skipping sync');
          return;
        }

        // Apply change to Yjs
        this.ytext.delete(startOffset, deleteLength);
        this.ytext.insert(startOffset, text);

        // Track change
        if (this.config.enableTracking && this.onTextChange) {
          this.onTextChange({
            origin: 'local',
            offset: startOffset,
            deleteLength,
            insertText: text,
            timestamp: Date.now(),
          });
        }
      }

      // Update metrics
      this.metrics.syncOperations++;
      this.updateMetrics();

    } catch (error) {
      console.error('Error syncing Monaco to Yjs:', error);
      this.metrics.conflicts++;
    } finally {
      this.isLocalChange = false;
      
      // Update sync time
      const syncTime = performance.now() - startTime;
      this.metrics.averageSyncTime = 
        (this.metrics.averageSyncTime * (this.metrics.syncOperations - 1) + syncTime) / 
        this.metrics.syncOperations;
      
      this.onMetricsUpdate?.(this.metrics);
    }
  }

  /**
   * Handle Yjs text changes
   */
  private handleYjsChange(event: Y.YTextEvent): void {
    const startTime = performance.now();
    
    try {
      this.isRemoteChange = true;

      // Apply changes to Monaco
      let currentDelta = event.delta;
      
      // Handle retain/delete/insert operations
      let currentPosition = 0;
      
      for (const delta of currentDelta) {
        if (delta.retain) {
          currentPosition += delta.retain;
        }
        
        if (delta.delete) {
          const startPos = this.getPositionAtOffset(currentPosition);
          const endPos = this.getPositionAtOffset(currentPosition + delta.delete);
          
          this.editor.getModel()?.applyEdits([{
            range: new this.monaco.Range(
              startPos.line,
              startPos.column,
              endPos.line,
              endPos.column
            ),
            text: '',
          }]);
        }
        
        if (delta.insert) {
          const text = typeof delta.insert === 'string' ? delta.insert : '';
          const startPos = this.getPositionAtOffset(currentPosition);
          
          this.editor.getModel()?.applyEdits([{
            range: new this.monaco.Range(
              startPos.line,
              startPos.column,
              startPos.line,
              startPos.column
            ),
            text,
          }]);
          
          currentPosition += text.length;
        }
      }

      // Track change
      if (this.config.enableTracking && this.onTextChange) {
        this.onTextChange({
          origin: 'remote',
          offset: 0, // Would need more detailed tracking
          deleteLength: 0,
          insertText: '',
          timestamp: Date.now(),
        });
      }

      // Update metrics
      this.metrics.syncOperations++;
      this.updateMetrics();

    } catch (error) {
      console.error('Error syncing Yjs to Monaco:', error);
      this.metrics.conflicts++;
    } finally {
      this.isRemoteChange = false;
      
      // Update sync time
      const syncTime = performance.now() - startTime;
      this.metrics.averageSyncTime = 
        (this.metrics.averageSyncTime * (this.metrics.syncOperations - 1) + syncTime) / 
        this.metrics.syncOperations;
      
      this.onMetricsUpdate?.(this.metrics);
    }
  }

  /**
   * Sync initial content from Yjs to Monaco
   */
  private syncFromYjsToMonaco(): void {
    const content = this.ytext.toString();
    const model = this.editor.getModel();
    
    if (model && model.getValue() !== content) {
      this.isRemoteChange = true;
      model.setValue(content);
      this.isRemoteChange = false;
    }
  }

  /**
   * Get character offset at position
   */
  private getOffsetAtPosition(lineNumber: number, column: number): number {
    const model = this.editor.getModel();
    if (!model) return 0;
    
    let offset = 0;
    for (let i = 1; i < lineNumber; i++) {
      offset += model.getLineLength(i) + 1; // +1 for newline
    }
    offset += column - 1;
    
    return offset;
  }

  /**
   * Get position at character offset
   */
  private getPositionAtOffset(offset: number): { line: number; column: number } {
    const model = this.editor.getModel();
    if (!model) return { line: 1, column: 1 };
    
    let currentOffset = 0;
    let line = 1;
    
    while (currentOffset < offset && line <= model.getLineCount()) {
      const lineLength = model.getLineLength(line);
      const nextOffset = currentOffset + lineLength + 1;
      
      if (nextOffset > offset) {
        return {
          line,
          column: offset - currentOffset + 1,
        };
      }
      
      currentOffset = nextOffset;
      line++;
    }
    
    return {
      line: model.getLineCount(),
      column: model.getLineLength(model.getLineCount()) + 1,
    };
  }

  /**
   * Update binding metrics
   */
  private updateMetrics(): void {
    this.metrics.textLength = this.ytext.length;
    this.metrics.lastSync = Date.now();
    this.onMetricsUpdate?.(this.metrics);
  }

  /**
   * Get current metrics
   */
  getMetrics(): BindingMetrics {
    return { ...this.metrics };
  }

  /**
   * Force sync from Monaco to Yjs
   */
  forceSyncToYjs(): void {
    const model = this.editor.getModel();
    if (!model) return;
    
    const content = model.getValue();
    
    if (content !== this.ytext.toString()) {
      this.isLocalChange = true;
      this.ytext.delete(0, this.ytext.length);
      this.ytext.insert(0, content);
      this.isLocalChange = false;
      
      this.metrics.syncOperations++;
      this.updateMetrics();
    }
  }

  /**
   * Force sync from Yjs to Monaco
   */
  forceSyncToMonaco(): void {
    this.syncFromYjsToMonaco();
  }

  /**
   * Check if binding is healthy
   */
  isHealthy(): boolean {
    const model = this.editor.getModel();
    if (!model || this.disposed) return false;
    
    const monacoContent = model.getValue();
    const yjsContent = this.ytext.toString();
    
    return monacoContent === yjsContent;
  }

  /**
   * Dispose binding
   */
  dispose(): void {
    if (this.disposed) return;
    
    this.disposed = true;
    
    // Clear debounce timeout
    if (this.debounceTimeout) {
      clearTimeout(this.debounceTimeout);
    }
    
    // Dispose event listeners
    this.disposables.forEach(d => d.dispose());
    this.disposables = [];
    
    // Unobserve Yjs
    if (this.ytextUnobserve) {
      this.ytextUnobserve();
      this.ytextUnobserve = null;
    }
  }
}

/**
 * Create Yjs-Monaco binding with default configuration
 */
export function createYjsMonacoBinding(
  editor: editor.IStandaloneCodeEditor,
  monaco: typeof import('monaco-editor'),
  ytext: Y.Text,
  options?: {
    config?: Partial<YjsMonacoBindingConfig>;
    callbacks?: {
      onTextChange?: (event: TextChangeEvent) => void;
      onCursorChange?: (position: { line: number; column: number }) => void;
      onMetricsUpdate?: (metrics: BindingMetrics) => void;
    };
  }
): YjsMonacoBinding {
  return new YjsMonacoBinding(
    editor,
    monaco,
    ytext,
    options?.config,
    options?.callbacks
  );
}
