/**
 * @fileoverview In-memory preview runtime for React components.
 *
 * Provides a sandboxed preview environment that transpiles TypeScript/TSX
 * source and renders it with design-system and theme providers using a
 * lightweight in-memory React renderer.
 *
 * @doc.type module
 * @doc.purpose In-memory preview runtime for compiled React components
 * @doc.layer studio
 * @doc.pattern Runtime
 */

import * as React from 'react';
import * as ReactDOMServer from 'react-dom/server';
import type {
  PreviewRequest,
  PreviewResult,
  PreviewRuntime,
  RuntimeStatus,
  ConsoleLog,
  TranspilationOptions,
  ThemeConfig,
} from './preview-protocol.js';

// ============================================================================
// Transpilation
// ============================================================================

/**
 * Transpile TypeScript/TSX source to executable JavaScript.
 *
 * Uses a simple regex-based transpiler for basic TSX transformation.
 * In production, this would use Babel or SWC for proper transpilation.
 */
function transpileSource(source: string, options: TranspilationOptions = {}): string {
  const { jsx = 'react' } = options;

  let transpiled = source;

  // Simple JSX transformation (for production, use Babel/SWC)
  if (jsx === 'react') {
    // Transform JSX to React.createElement calls
    transpiled = transpiled.replace(
      /<([A-Z][a-zA-Z0-9]*)\s*([^>]*)>/g,
      (_, tagName, attrs) => {
        const props = attrs.trim() 
          ? `{${attrs.replace(/(\w+)=/g, '"$1": ').replace(/'/g, '"')}}` 
          : '{}';
        return `React.createElement('${tagName}', ${props})`;
      }
    );
    
    // Transform closing tags
    transpiled = transpiled.replace(/<\/([A-Z][a-zA-Z0-9]*)>/g, '');
  }

  // Remove TypeScript type annotations (simplified)
  transpiled = transpiled.replace(/:\s*\w+(\[\])?/g, '');
  transpiled = transpiled.replace(/interface\s+\w+\s*{[^}]*}/g, '');
  transpiled = transpiled.replace(/export\s+type\s+\w+[^;]*;/g, '');

  return transpiled;
}

// ============================================================================
// Console Capture
// ============================================================================

/**
 * Capture console output during preview render.
 */
class ConsoleCapture {
  private logs: ConsoleLog[] = [];
  private originalConsole = {
    log: console.log,
    warn: console.warn,
    error: console.error,
    info: console.info,
  };

  start(): void {
    this.logs = [];
    const capture = (level: ConsoleLog['level']) => (...args: unknown[]) => {
      this.logs.push({
        level,
        message: args.map(arg => 
          typeof arg === 'object' ? JSON.stringify(arg) : String(arg)
        ).join(' '),
        timestamp: Date.now(),
      });
    };

    console.log = capture('log');
    console.warn = capture('warn');
    console.error = capture('error');
    console.info = capture('info');
  }

  stop(): ConsoleLog[] {
    console.log = this.originalConsole.log;
    console.warn = this.originalConsole.warn;
    console.error = this.originalConsole.error;
    console.info = this.originalConsole.info;
    return [...this.logs];
  }

  getLogs(): readonly ConsoleLog[] {
    return [...this.logs];
  }
}

// ============================================================================
// In-Memory Preview Runtime
// ============================================================================

/**
 * In-memory preview runtime implementation.
 *
 * Transpiles source code and renders it using React's server-side rendering
 * with injected design-system and theme providers.
 */
export class InMemoryPreviewRuntime implements PreviewRuntime {
  private activeSessions = new Map<string, { startTime: number }>();
  private consoleCapture = new ConsoleCapture();

  async render(request: PreviewRequest): Promise<PreviewResult> {
    const startTime = Date.now();
    this.activeSessions.set(request.sessionId, { startTime });

    this.consoleCapture.start();

    try {
      // Transpile the source
      const transpiled = transpileSource(request.source, {
        jsx: 'react',
        target: 'es2020',
      });

      // Create a sandboxed evaluation context
      const sandbox = this.createSandbox(request);

      // Evaluate the transpiled code
      const Component = this.evaluateComponent(transpiled, sandbox);

      // Render the component
      const html = ReactDOMServer.renderToStaticMarkup(
        React.createElement(Component)
      );

      const logs = this.consoleCapture.stop();
      const duration = Date.now() - startTime;

      return {
        success: true,
        html: this.wrapInDocumentShell(html, request),
        logs,
        duration,
      };
    } catch (err) {
      const logs = this.consoleCapture.stop();
      const duration = Date.now() - startTime;
      const error = err instanceof Error ? err.message : String(err);

      return {
        success: false,
        error,
        logs,
        duration,
      };
    }
  }

  async cleanup(sessionId: string): Promise<void> {
    this.activeSessions.delete(sessionId);
  }

  getStatus(): RuntimeStatus {
    return {
      ready: true,
      activeSessions: this.activeSessions.size,
    };
  }

  /**
   * Create a sandboxed evaluation context with injected dependencies.
   */
  private createSandbox(request: PreviewRequest): Record<string, unknown> {
    const sandbox: Record<string, unknown> = {
      React,
      // Inject design-system components if configured
      ...(request.designSystem?.availableComponents.reduce((acc, compName) => {
        acc[compName] = ({ children }: { children?: React.ReactNode }) => 
          React.createElement('div', { 'data-ds-component': compName }, children);
        return acc;
      }, {} as Record<string, unknown>) ?? {}),
    };

    return sandbox;
  }

  /**
   * Evaluate transpiled component code in the sandbox.
   */
  private evaluateComponent(code: string, sandbox: Record<string, unknown>): React.ComponentType {
    // Create a function from the code with sandbox context
    const sandboxKeys = Object.keys(sandbox);
    const sandboxValues = Object.values(sandbox);
    
    try {
      // Extract the default export or named export
      const factory = new Function(...sandboxKeys, `
        ${code}
        return typeof Component !== 'undefined' ? Component : 
               (typeof exports !== 'undefined' ? exports.default : null);
      `);

      const Component = factory(...sandboxValues);
      
      if (typeof Component !== 'function') {
        throw new Error('No valid component export found in source');
      }

      return Component as React.ComponentType;
    } catch (err) {
      throw new Error(`Failed to evaluate component: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  /**
   * Wrap rendered HTML in a document shell with theme provider.
   */
  private wrapInDocumentShell(html: string, request: PreviewRequest): string {
    const themeMode = request.theme?.mode ?? 'light';
    const themeStyles = this.generateThemeStyles(request.theme);

    return `<!DOCTYPE html>
<html lang="en" data-theme="${themeMode}">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Preview</title>
  <style>
    body { margin: 0; font-family: system-ui, sans-serif; }
    ${themeStyles}
  </style>
</head>
<body>
  <div id="preview-root">${html}</div>
</body>
</html>`;
  }

  /**
   * Generate theme styles based on theme configuration.
   */
  private generateThemeStyles(theme?: ThemeConfig): string {
    if (!theme) return '';

    const mode = theme.mode === 'auto' ? 'light' : theme.mode;
    const baseStyles = `
      :root {
        color-scheme: ${mode};
      }
    `;

    if (theme.tokens) {
      const tokenStyles = Object.entries(theme.tokens)
        .map(([key, value]) => `  --${key}: ${value};`)
        .join('\n');
      return `${baseStyles}\n${tokenStyles}`;
    }

    return baseStyles;
  }
}

/**
 * Default preview runtime instance.
 */
export const defaultPreviewRuntime = new InMemoryPreviewRuntime();
