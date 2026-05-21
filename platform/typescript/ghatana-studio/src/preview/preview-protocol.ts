/**
 * @fileoverview Preview runtime protocol and contracts.
 *
 * Defines the protocol for rendering compiled React components in a sandboxed
 * preview environment with proper transpilation, module resolution, and
 * design-system/theme provider injection.
 *
 * @doc.type module
 * @doc.purpose Preview runtime protocol for compiled React components
 * @doc.layer studio
 * @doc.pattern Protocol
 */

// ============================================================================
// Preview Protocol Types
// ============================================================================

/**
 * A preview request from Studio to the preview runtime.
 */
export interface PreviewRequest {
  /** Unique ID for this preview session. */
  readonly sessionId: string;
  /** Compiled TypeScript/TSX source code to preview. */
  readonly source: string;
  /** Relative path of the file (used for module resolution). */
  readonly filePath: string;
  /** Design system configuration to inject. */
  readonly designSystem?: DesignSystemConfig;
  /** Theme configuration to inject. */
  readonly theme?: ThemeConfig;
  /** Security policy for the preview sandbox. */
  readonly securityPolicy?: SecurityPolicy;
  /** Preview execution mode. */
  readonly executionMode?: PreviewExecutionMode;
}

export type PreviewExecutionMode = 'safe-static' | 'isolated-runtime';

/**
 * Design system configuration for preview.
 */
export interface DesignSystemConfig {
  /** Design system package name (e.g., "@ghatana/design-system"). */
  readonly packageName: string;
  /** Design system version. */
  readonly version: string;
  /** Available component names for module resolution. */
  readonly availableComponents: readonly string[];
}

/**
 * Theme configuration for preview.
 */
export interface ThemeConfig {
  /** Theme mode (light, dark, or auto). */
  readonly mode: 'light' | 'dark' | 'auto';
  /** Custom theme tokens to override defaults. */
  readonly tokens?: Record<string, string>;
}

/**
 * Security policy for preview sandbox.
 */
export interface SecurityPolicy {
  /** Whether to allow script execution (always true for React preview). */
  readonly allowScripts: boolean;
  /** Whether to allow same-origin requests. */
  readonly allowSameOrigin: boolean;
  /** Whether to allow popups. */
  readonly allowPopups: boolean;
  /** Whether to allow forms. */
  readonly allowForms: boolean;
  /** Custom CSP directive. */
  readonly contentSecurityPolicy?: string;
}

/**
 * Preview render result from the runtime.
 */
export interface PreviewResult {
  /** Whether the preview rendered successfully. */
  readonly success: boolean;
  /** Rendered HTML (if successful). */
  readonly html?: string;
  /** Error message (if failed). */
  readonly error?: string;
  /** Console logs captured during render. */
  readonly logs: readonly ConsoleLog[];
  /** Render duration in milliseconds. */
  readonly duration: number;
}

/**
 * A console log entry captured during preview.
 */
export interface ConsoleLog {
  /** Log level. */
  readonly level: 'log' | 'warn' | 'error' | 'info';
  /** Log message. */
  readonly message: string;
  /** Timestamp when the log was captured. */
  readonly timestamp: number;
}

// ============================================================================
// Preview Runtime Interface
// ============================================================================

/**
 * Preview runtime interface.
 *
 * Implementations provide the actual rendering environment for previewing
 * compiled React components.
 */
export interface PreviewRuntime {
  /**
   * Render a preview request.
   *
   * @param request - Preview request with source and configuration.
   * @returns Preview result with rendered output or error.
   */
  render(request: PreviewRequest): Promise<PreviewResult>;

  /**
   * Clean up resources for a preview session.
   *
   * @param sessionId - Session ID to clean up.
   */
  cleanup(sessionId: string): Promise<void>;

  /**
   * Get the current status of the runtime.
   */
  getStatus(): RuntimeStatus;
}

/**
 * Runtime status.
 */
export interface RuntimeStatus {
  /** Whether the runtime is ready to render. */
  readonly ready: boolean;
  /** Number of active preview sessions. */
  readonly activeSessions: number;
  /** Memory usage in bytes (if available). */
  readonly memoryUsage?: number;
}

// ============================================================================
// Transpilation Options
// ============================================================================

/**
 * Options for transpiling TypeScript/TSX to JavaScript.
 */
export interface TranspilationOptions {
  /** Target ECMAScript version. */
  readonly target?: 'es5' | 'es2015' | 'es2020';
  /** Whether to generate source maps. */
  readonly sourceMaps?: boolean;
  /** Whether to use JSX transformation. */
  readonly jsx?: 'react' | 'react-native' | 'preserve';
  /** Module format for output. */
  readonly module?: 'commonjs' | 'esm';
}
