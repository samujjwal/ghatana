/**
 * LSP Client Foundation
 * 
 * Language Server Protocol client abstraction for Monaco editor integration.
 * Provides language server management, diagnostics, and code intelligence.
 * 
 * Features:
 * - 🔌 LSP server connection management
 * - 📊 Diagnostics and error highlighting
 * - 🎯 Auto-completion and IntelliSense
 * - 📍 Go-to-definition and references
 * - 🔄 Real-time workspace synchronization
 * - 📈 Performance monitoring
 * 
 * @doc.type module
 * @doc.purpose LSP client foundation
 * @doc.layer product
 * @doc.pattern Service
 */

import * as monaco from 'monaco-editor';
import type {
  InitializeRequest,
  InitializeParams,
  InitializeResult,
  CompletionRequest,
  CompletionParams,
  CompletionItem,
  DefinitionRequest,
  DefinitionParams,
  ReferenceRequest,
  ReferenceParams,
  HoverRequest,
  HoverParams,
  Diagnostic,
  PublishDiagnosticsParams,
} from 'vscode-languageserver-protocol';

// Import types from types.ts and use internally
import type {
  LSPClientConfig,
  LSPDiagnostic,
  LSPClientMetrics,
} from './types';

// Re-export for external consumers
export type {
  LSPClientConfig,
  LSPDiagnostic,
  LSPClientMetrics,
} from './types';

/**
 * LSP server configuration
 */
export interface LSPServerConfig {
  /** Server ID */
  id: string;
  /** Server name */
  name: string;
  /** Language IDs this server handles */
  languages: string[];
  /** Server connection details */
  connection: {
    /** Connection type */
    type: 'stdio' | 'websocket' | 'ipc';
    /** Command for stdio connections */
    command?: string;
    /** Arguments for stdio connections */
    args?: string[];
    /** URL for websocket connections */
    url?: string;
    /** Connection options */
    options?: Record<string, unknown>;
  };
  /** Initialization options */
  initializationOptions?: unknown;
  /** Server capabilities */
  capabilities?: {
    completionProvider?: boolean;
    definitionProvider?: boolean;
    referenceProvider?: boolean;
    hoverProvider?: boolean;
    diagnosticsProvider?: boolean;
  };
}

/**
 * LSP completion item
 */
export interface LSPCompletionItem {
  /** Item label */
  label: string;
  /** Item kind */
  kind: string;
  /** Item detail */
  detail?: string;
  /** Item documentation */
  documentation?: string;
  /** Insert text */
  insertText: string;
  /** Sort text */
  sortText?: string;
  /** Filter text */
  filterText?: string;
}

/**
 * LSP Client Manager
 */
export class LSPClientManager {
  private config: LSPClientConfig;
  private servers: Map<string, LSPServerInstance> = new Map();
  private diagnostics: Map<string, LSPDiagnostic[]> = new Map();
  private metrics: Map<string, LSPClientMetrics> = new Map();
  private disposables: monaco.IDisposable[] = [];

  /**
   * Create LSP client manager
   */
  constructor(config: LSPClientConfig) {
    this.config = config;
    this.setupMonacoIntegration();
  }

  /**
   * Initialize all LSP servers
   */
  async initialize(): Promise<void> {
    const initPromises = this.config.servers.map(serverConfig => 
      this.initializeServer(serverConfig)
    );

    await Promise.allSettled(initPromises);
  }

  /**
   * Initialize a single LSP server
   */
  private async initializeServer(serverConfig: LSPServerConfig): Promise<void> {
    try {
      const server = new LSPServerInstance(serverConfig, this.config.workspaceRoot);
      await server.initialize();

      this.servers.set(serverConfig.id, server);
      this.metrics.set(serverConfig.id, {
        serverId: serverConfig.id,
        requestCount: 0,
        averageResponseTime: 0,
        errorCount: 0,
        diagnosticCount: 0,
        lastActivity: Date.now(),
      });

      // Setup event handlers
      server.onDiagnostics(this.handleDiagnostics.bind(this));
      server.onError(this.handleServerError.bind(this, serverConfig.id));

      console.log(`LSP server '${serverConfig.name}' initialized successfully`);
    } catch (error) {
      console.error(`Failed to initialize LSP server '${serverConfig.name}':`, error);
    }
  }

  /**
   * Setup Monaco editor integration
   */
  private setupMonacoIntegration(): void {
    // Register language providers
    this.config.servers.forEach(serverConfig => {
      serverConfig.languages.forEach(languageId => {
        this.registerLanguageProvider(languageId, serverConfig);
      });
    });
  }

  /**
   * Register language provider for Monaco
   */
  private registerLanguageProvider(languageId: string, serverConfig: LSPServerConfig): void {
    const server = this.servers.get(serverConfig.id);
    if (!server) return;

    // Completion provider
    if (serverConfig.capabilities?.completionProvider && this.config.enableCompletion) {
      const completionProvider = monaco.languages.registerCompletionItemProvider(languageId, {
        provideCompletionItems: async (model, position) => {
          try {
            const items = await server.provideCompletionItems(model, position);
            return {
              suggestions: items.map(item => this.convertCompletionItem(item)),
            };
          } catch (error) {
            console.error('Completion error:', error);
            return { suggestions: [] };
          }
        },
      });

      this.disposables.push(completionProvider);
    }

    // Definition provider
    if (serverConfig.capabilities?.definitionProvider) {
      const definitionProvider = monaco.languages.registerDefinitionProvider(languageId, {
        provideDefinition: async (model, position) => {
          try {
            const locations = await server.provideDefinition(model, position);
            return locations.map(loc => this.convertLocation(loc));
          } catch (error) {
            console.error('Definition error:', error);
            return [];
          }
        },
      });

      this.disposables.push(definitionProvider);
    }

    // Hover provider
    if (serverConfig.capabilities?.hoverProvider) {
      const hoverProvider = monaco.languages.registerHoverProvider(languageId, {
        provideHover: async (model, position) => {
          try {
            const hover = await server.provideHover(model, position);
            if (!hover) return null;

            return {
              range: this.convertRange(hover.range),
              contents: [
                { value: '**' + (hover.contents as string) + '**' }
              ],
            };
          } catch (error) {
            console.error('Hover error:', error);
            return null;
          }
        },
      });

      this.disposables.push(hoverProvider);
    }
  }

  /**
   * Convert LSP completion item to Monaco format
   */
  private convertCompletionItem(item: LSPCompletionItem): monaco.languages.CompletionItem {
    return {
      label: item.label,
      kind: this.convertCompletionKind(item.kind),
      detail: item.detail,
      documentation: item.documentation,
      insertText: item.insertText,
      sortText: item.sortText,
      filterText: item.filterText,
    };
  }

  /**
   * Convert LSP completion kind to Monaco format
   */
  private convertCompletionKind(kind: string): monaco.languages.CompletionItemKind {
    const kindMap: Record<string, monaco.languages.CompletionItemKind> = {
      'text': monaco.languages.CompletionItemKind.Text,
      'method': monaco.languages.CompletionItemKind.Method,
      'function': monaco.languages.CompletionItemKind.Function,
      'constructor': monaco.languages.CompletionItemKind.Constructor,
      'field': monaco.languages.CompletionItemKind.Field,
      'variable': monaco.languages.CompletionItemKind.Variable,
      'class': monaco.languages.CompletionItemKind.Class,
      'interface': monaco.languages.CompletionItemKind.Interface,
      'module': monaco.languages.CompletionItemKind.Module,
      'property': monaco.languages.CompletionItemKind.Property,
      'unit': monaco.languages.CompletionItemKind.Unit,
      'value': monaco.languages.CompletionItemKind.Value,
      'enum': monaco.languages.CompletionItemKind.Enum,
      'keyword': monaco.languages.CompletionItemKind.Keyword,
      'snippet': monaco.languages.CompletionItemKind.Snippet,
      'color': monaco.languages.CompletionItemKind.Color,
      'file': monaco.languages.CompletionItemKind.File,
      'reference': monaco.languages.CompletionItemKind.Reference,
    };

    return kindMap[kind] || monaco.languages.CompletionItemKind.Text;
  }

  /**
   * Convert LSP location to Monaco format
   */
  private convertLocation(location: unknown): monaco.languages.Location {
    return {
      uri: monaco.Uri.parse(location.uri),
      range: this.convertRange(location.range),
    };
  }

  /**
   * Convert LSP range to Monaco format
   */
  private convertRange(range: unknown): monaco.Range {
    return new monaco.Range(
      range.start.line + 1,
      range.start.character + 1,
      range.end.line + 1,
      range.end.character + 1
    );
  }

  /**
   * Handle diagnostics from server
   */
  private handleDiagnostics(params: PublishDiagnosticsParams): void {
    if (!this.config.enableDiagnostics) return;

    const uri = params.uri;
    const diagnostics = params.diagnostics.map(diag => ({
      uri,
      severity: this.convertSeverity(diag.severity),
      message: diag.message,
      start: {
        line: diag.range.start.line + 1,
        column: diag.range.start.character + 1,
      },
      end: {
        line: diag.range.end.line + 1,
        column: diag.range.end.character + 1,
      },
      source: diag.source,
      code: diag.code,
    }));

    this.diagnostics.set(uri, diagnostics);

    // Update metrics
    const serverId = this.getServerIdForUri(uri);
    if (serverId) {
      const metrics = this.metrics.get(serverId);
      if (metrics) {
        metrics.diagnosticCount = diagnostics.length;
        metrics.lastActivity = Date.now();
      }
    }

    // Apply diagnostics to Monaco model
    this.applyDiagnosticsToModel(uri, diagnostics);
  }

  /**
   * Convert LSP severity to Monaco format
   */
  private convertSeverity(severity?: number): 'error' | 'warning' | 'info' | 'hint' {
    switch (severity) {
      case 1: return 'error';
      case 2: return 'warning';
      case 3: return 'info';
      case 4: return 'hint';
      default: return 'error';
    }
  }

  /**
   * Apply diagnostics to Monaco model
   */
  private applyDiagnosticsToModel(uri: string, diagnostics: LSPDiagnostic[]): void {
    try {
      const model = monaco.editor.getModel(monaco.Uri.parse(uri));
      if (!model) return;

      const monacoDiagnostics = diagnostics.map(diag => {
        const severity = diag.severity === 'error' 
          ? monaco.MarkerSeverity.Error 
          : diag.severity === 'warning'
          ? monaco.MarkerSeverity.Warning
          : monaco.MarkerSeverity.Info;

        return {
          severity,
          message: diag.message,
          startLineNumber: diag.start.line,
          startColumn: diag.start.column,
          endLineNumber: diag.end.line,
          endColumn: diag.end.column,
          source: diag.source,
        };
      });

      monaco.editor.setModelMarkers(model, 'lsp', monacoDiagnostics);
    } catch (error) {
      console.error('Failed to apply diagnostics:', error);
    }
  }

  /**
   * Handle server error
   */
  private handleServerError(serverId: string, error: Error): void {
    console.error(`LSP server ${serverId} error:`, error);
    
    const metrics = this.metrics.get(serverId);
    if (metrics) {
      metrics.errorCount++;
    }
  }

  /**
   * Get server ID for URI
   */
  private getServerIdForUri(uri: string): string | null {
    const language = this.getLanguageFromUri(uri);
    
    for (const [serverId, serverConfig] of this.servers.entries()) {
      if (serverConfig.languages.includes(language)) {
        return serverId;
      }
    }
    
    return null;
  }

  /**
   * Get language from URI
   */
  private getLanguageFromUri(uri: string): string {
    const extension = uri.split('.').pop()?.toLowerCase();
    
    const extensionMap: Record<string, string> = {
      'ts': 'typescript',
      'tsx': 'typescript',
      'js': 'javascript',
      'jsx': 'javascript',
      'py': 'python',
      'java': 'java',
      'cpp': 'cpp',
      'c': 'c',
      'cs': 'csharp',
      'php': 'php',
      'rb': 'ruby',
      'go': 'go',
      'rs': 'rust',
      'sql': 'sql',
      'json': 'json',
      'xml': 'xml',
      'html': 'html',
      'css': 'css',
      'scss': 'scss',
      'less': 'less',
    };
    
    return extensionMap[extension || ''] || 'plaintext';
  }

  /**
   * Get diagnostics for file
   */
  getDiagnostics(uri: string): LSPDiagnostic[] {
    return this.diagnostics.get(uri) || [];
  }

  /**
   * Get all diagnostics
   */
  getAllDiagnostics(): Map<string, LSPDiagnostic[]> {
    return new Map(this.diagnostics);
  }

  /**
   * Get metrics for all servers
   */
  getMetrics(): Map<string, LSPClientMetrics> {
    return new Map(this.metrics);
  }

  /**
   * Get metrics for specific server
   */
  getServerMetrics(serverId: string): LSPClientMetrics | undefined {
    return this.metrics.get(serverId);
  }

  /**
   * Restart server
   */
  async restartServer(serverId: string): Promise<void> {
    const serverConfig = this.config.servers.find(s => s.id === serverId);
    if (!serverConfig) return;

    // Dispose existing server
    const existingServer = this.servers.get(serverId);
    if (existingServer) {
      existingServer.dispose();
      this.servers.delete(serverId);
    }

    // Reinitialize
    await this.initializeServer(serverConfig);
  }

  /**
   * Dispose LSP client manager
   */
  dispose(): void {
    // Dispose all language providers
    this.disposables.forEach(d => d.dispose());
    this.disposables = [];

    // Dispose all servers
    this.servers.forEach(server => server.dispose());
    this.servers.clear();

    // Clear diagnostics
    this.diagnostics.clear();
    this.metrics.clear();
  }
}

/**
 * LSP Server Instance (simplified implementation)
 */
class LSPServerInstance {
  private config: LSPServerConfig;
  private workspaceRoot: string;
  private initialized = false;

  constructor(config: LSPServerConfig, workspaceRoot: string) {
    this.config = config;
    this.workspaceRoot = workspaceRoot;
  }

  async initialize(): Promise<void> {
    // Simplified initialization - in real implementation would connect to actual LSP server
    this.initialized = true;
  }

  async provideCompletionItems(model: monaco.editor.ITextModel, position: monaco.Position): Promise<LSPCompletionItem[]> {
    // Simplified completion - in real implementation would call LSP server
    return [];
  }

  async provideDefinition(model: monaco.editor.ITextModel, position: monaco.Position): Promise<unknown[]> {
    // Simplified definition - in real implementation would call LSP server
    return [];
  }

  async provideHover(model: monaco.editor.ITextModel, position: monaco.Position): Promise<unknown> {
    // Simplified hover - in real implementation would call LSP server
    return null;
  }

  onDiagnostics(callback: (params: PublishDiagnosticsParams) => void): void {
    // Register diagnostics callback
  }

  onError(callback: (error: Error) => void): void {
    // Register error callback
  }

  dispose(): void {
    // Cleanup resources
  }
}

/**
 * Create LSP client manager
 */
export function createLSPClientManager(config: LSPClientConfig): LSPClientManager {
  return new LSPClientManager(config);
}

// Note: Hooks are available from './hooks/useLSP' to avoid circular dependencies
// Do not re-export hooks from this barrel file
