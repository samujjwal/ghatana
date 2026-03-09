/**
 * LSP Types
 * 
 * Core type definitions for Language Server Protocol client.
 * Extracted to prevent circular dependencies.
 * 
 * @doc.type types
 * @doc.purpose LSP type definitions
 * @doc.layer code-editor/lsp
 */

/**
 * LSP client configuration
 */
export interface LSPClientConfig {
  workspaceRoot: string;
  servers: Array<{
    id: string;
    name: string;
    languages: string[];
    command?: string;
    args?: string[];
    url?: string;
  }>;
  enableDiagnostics?: boolean;
  enableCompletion?: boolean;
  debug?: boolean;
}

/**
 * LSP diagnostic information
 */
export interface LSPDiagnostic {
  message: string;
  severity: 'error' | 'warning' | 'info' | 'hint';
  range: {
    start: { line: number; character: number };
    end: { line: number; character: number };
  };
  source?: string;
  code?: string | number;
}

/**
 * LSP client metrics
 */
export interface LSPClientMetrics {
  requestCount: number;
  errorCount: number;
  avgResponseTime: number;
  lastError?: string;
  uptime: number;
}
