/**
 * useLSP Hook
 * 
 * React hook for integrating Language Server Protocol with Monaco editors.
 * Provides automatic LSP server management, diagnostics, and code intelligence.
 * 
 * Features:
 * - 🎣 Easy React integration with hooks
 * - 🔌 Automatic LSP server lifecycle management
 * - 📊 Real-time diagnostics and error highlighting
 * - 🎯 Auto-completion and IntelliSense
 * - 📈 Performance monitoring
 * - 🧼 Automatic cleanup on unmount
 * 
 * @doc.type hook
 * @doc.purpose LSP React hook
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import type * as monaco from 'monaco-editor';
import { 
  LSPClientManager, 
  createLSPClientManager,
} from '../index';
import type {
  LSPClientConfig,
  LSPDiagnostic,
  LSPClientMetrics,
} from '../types';

/**
 * LSP hook configuration
 */
export interface UseLSPConfig {
  /** Workspace root directory */
  workspaceRoot: string;
  /** LSP server configurations */
  servers: Array<{
    id: string;
    name: string;
    languages: string[];
    command?: string;
    args?: string[];
    url?: string;
  }>;
  /** Enable diagnostics */
  enableDiagnostics?: boolean;
  /** Enable auto-completion */
  enableCompletion?: boolean;
  /** Enable performance monitoring */
  enableMetrics?: boolean;
}

/**
 * LSP hook state
 */
export interface LSPState {
  /** Is LSP initialized */
  isInitialized: boolean;
  /** Is LSP ready */
  isReady: boolean;
  /** All diagnostics */
  diagnostics: Map<string, LSPDiagnostic[]>;
  /** Server metrics */
  metrics: Map<string, LSPClientMetrics>;
  /** Active servers */
  activeServers: string[];
  /** Error state */
  error: string | null;
}

/**
 * LSP hook actions
 */
export interface LSPActions {
  /** Get diagnostics for file */
  getDiagnostics: (uri: string) => LSPDiagnostic[];
  /** Get metrics for server */
  getServerMetrics: (serverId: string) => LSPClientMetrics | undefined;
  /** Restart server */
  restartServer: (serverId: string) => Promise<void>;
  /** Get LSP manager instance */
  getManager: () => LSPClientManager | null;
}

/**
 * LSP hook return value
 */
export interface UseLSPReturn extends LSPState, LSPActions {}

/**
 * React hook for LSP integration
 */
export function useLSP(config: UseLSPConfig): UseLSPReturn {
  const {
    workspaceRoot,
    servers,
    enableDiagnostics = true,
    enableCompletion = true,
    enableMetrics = false,
  } = config;

  // State
  const [state, setState] = useState<LSPState>({
    isInitialized: false,
    isReady: false,
    diagnostics: new Map(),
    metrics: new Map(),
    activeServers: [],
    error: null,
  });

  // Refs
  const managerRef = useRef<LSPClientManager | null>(null);
  const metricsIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Initialize LSP manager
  useEffect(() => {
    const initializeLSP = async () => {
      try {
        setState(prev => ({ ...prev, isInitialized: true, error: null }));

        // Convert server config to LSP format
        const lspConfig: LSPClientConfig = {
          workspaceRoot,
          servers: servers.map(server => ({
            id: server.id,
            name: server.name,
            languages: server.languages,
            connection: {
              type: server.url ? 'websocket' : 'stdio',
              command: server.command,
              args: server.args,
              url: server.url,
            },
            capabilities: {
              completionProvider: enableCompletion,
              definitionProvider: true,
              referenceProvider: true,
              hoverProvider: true,
              diagnosticsProvider: enableDiagnostics,
            },
          })),
          enableDiagnostics,
          enableCompletion,
          enableMetrics,
        };

        // Create and initialize manager
        const manager = createLSPClientManager(lspConfig);
        await manager.initialize();

        managerRef.current = manager;

        setState(prev => ({
          ...prev,
          isReady: true,
          activeServers: servers.map(s => s.id),
          diagnostics: manager.getAllDiagnostics(),
          metrics: manager.getMetrics(),
        }));

        // Setup metrics monitoring
        if (enableMetrics) {
          metricsIntervalRef.current = setInterval(() => {
            setState(prev => ({
              ...prev,
              metrics: manager.getMetrics(),
            }));
          }, 5000);
        }

      } catch (error) {
        console.error('Failed to initialize LSP:', error);
        setState(prev => ({
          ...prev,
          error: error instanceof Error ? error.message : 'Unknown error',
        }));
      }
    };

    initializeLSP();

    return () => {
      if (metricsIntervalRef.current) {
        clearInterval(metricsIntervalRef.current);
      }
      if (managerRef.current) {
        managerRef.current.dispose();
      }
    };
  }, [workspaceRoot, servers, enableDiagnostics, enableCompletion, enableMetrics]);

  // Actions
  const getDiagnostics = useCallback((uri: string): LSPDiagnostic[] => {
    if (!managerRef.current) return [];
    return managerRef.current.getDiagnostics(uri);
  }, []);

  const getServerMetrics = useCallback((serverId: string): LSPClientMetrics | undefined => {
    if (!managerRef.current) return undefined;
    return managerRef.current.getServerMetrics(serverId);
  }, []);

  const restartServer = useCallback(async (serverId: string): Promise<void> => {
    if (!managerRef.current) return;
    
    try {
      await managerRef.current.restartServer(serverId);
      setState(prev => ({
        ...prev,
        metrics: managerRef.current!.getMetrics(),
      }));
    } catch (error) {
      console.error(`Failed to restart server ${serverId}:`, error);
    }
  }, []);

  const getManager = useCallback((): LSPClientManager | null => {
    return managerRef.current;
  }, []);

  return {
    ...state,
    getDiagnostics,
    getServerMetrics,
    restartServer,
    getManager,
  };
}

/**
 * Enhanced LSP hook with additional features
 */
export function useEnhancedLSP(
  config: UseLSPConfig & {
    /** Enable auto-restart on error */
    enableAutoRestart?: boolean;
    /** Auto-restart delay (ms) */
    autoRestartDelay?: number;
    /** Enable diagnostics panel */
    enableDiagnosticsPanel?: boolean;
  }
): UseLSPReturn & {
  /** Restart failed servers automatically */
  autoRestartEnabled: boolean;
  /** Clear all diagnostics */
  clearDiagnostics: () => void;
  /** Get diagnostic summary */
  getDiagnosticSummary: () => {
    errors: number;
    warnings: number;
    info: number;
    hints: number;
  };
} {
  const {
    enableAutoRestart = false,
    autoRestartDelay = 5000,
    enableDiagnosticsPanel = false,
    ...baseConfig
  } = config;

  const baseHook = useLSP(baseConfig);
  const autoRestartTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Auto-restart failed servers
  useEffect(() => {
    if (!enableAutoRestart || !baseHook.error) return;

    autoRestartTimeoutRef.current = setTimeout(() => {
      console.log('Auto-restarting LSP servers due to error');
      baseHook.restartServer(baseHook.activeServers[0]);
    }, autoRestartDelay);

    return () => {
      if (autoRestartTimeoutRef.current) {
        clearTimeout(autoRestartTimeoutRef.current);
      }
    };
  }, [enableAutoRestart, autoRestartDelay, baseHook.error, baseHook.activeServers, baseHook.restartServer]);

  // Additional actions
  const clearDiagnostics = useCallback(() => {
    const manager = baseHook.getManager();
    if (manager) {
      // Clear all diagnostics by setting empty map
      baseHook.setState?.(prev => ({ ...prev, diagnostics: new Map() }));
    }
  }, [baseHook.getManager]);

  const getDiagnosticSummary = useCallback(() => {
    let errors = 0;
    let warnings = 0;
    let info = 0;
    let hints = 0;

    baseHook.diagnostics.forEach(diagnostics => {
      diagnostics.forEach(diag => {
        switch (diag.severity) {
          case 'error': errors++; break;
          case 'warning': warnings++; break;
          case 'info': info++; break;
          case 'hint': hints++; break;
        }
      });
    });

    return { errors, warnings, info, hints };
  }, [baseHook.diagnostics]);

  return {
    ...baseHook,
    autoRestartEnabled: enableAutoRestart,
    clearDiagnostics,
    getDiagnosticSummary,
  };
}

/**
 * LSP diagnostics panel hook
 */
export function useLSPDiagnosticsPanel(lspHook: UseLSPReturn) {
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [filter, setFilter] = useState<'all' | 'errors' | 'warnings' | 'info' | 'hints'>('all');

  // Filter diagnostics based on selected file and filter type
  const filteredDiagnostics = React.useMemo(() => {
    let diagnostics: Array<{ uri: string; diagnostics: LSPDiagnostic[] }> = [];

    lspHook.diagnostics.forEach((diags, uri) => {
      const filteredDiags = diags.filter(diag => {
        if (selectedFile && uri !== selectedFile) return false;
        if (filter !== 'all' && diag.severity !== filter) return false;
        return true;
      });

      if (filteredDiags.length > 0) {
        diagnostics.push({ uri, diagnostics: filteredDiags });
      }
    });

    return diagnostics;
  }, [lspHook.diagnostics, selectedFile, filter]);

  // Get file list from diagnostics
  const fileList = React.useMemo(() => {
    const files = new Set<string>();
    lspHook.diagnostics.forEach((_, uri) => files.add(uri));
    return Array.from(files);
  }, [lspHook.diagnostics]);

  return {
    selectedFile,
    setSelectedFile,
    filter,
    setFilter,
    filteredDiagnostics,
    fileList,
    diagnosticCount: filteredDiagnostics.reduce((sum, { diagnostics }) => sum + diagnostics.length, 0),
  };
}
