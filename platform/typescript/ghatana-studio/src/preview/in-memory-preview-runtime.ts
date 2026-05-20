/**
 * @fileoverview In-memory preview runtime for React components.
 *
 * Provides a sandboxed preview environment that parses TypeScript/TSX source
 * and renders a static JSX preview without evaluating imported code.
 *
 * @doc.type module
 * @doc.purpose In-memory preview runtime for compiled React components
 * @doc.layer studio
 * @doc.pattern Runtime
 */

import * as ts from 'typescript';
import type {
  PreviewRequest,
  PreviewResult,
  PreviewRuntime,
  RuntimeStatus,
  ConsoleLog,
  ThemeConfig,
} from './preview-protocol.js';

// ============================================================================
// Static Preview Extraction
// ============================================================================

function parsePreviewSource(source: string): ts.SourceFile {
  const sourceFile = ts.createSourceFile('preview.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  if (sourceFile.parseDiagnostics.length > 0) {
    const message = ts.flattenDiagnosticMessageText(sourceFile.parseDiagnostics[0].messageText, '\n');
    throw new Error(`Preview parse failed: ${message}`);
  }
  return sourceFile;
}

function assertPreviewPolicy(sourceFile: ts.SourceFile): void {
  const forbiddenNames = new Set([
    'document',
    'window',
    'globalThis',
    'Function',
    'eval',
    'fetch',
    'XMLHttpRequest',
    'WebSocket',
    'localStorage',
    'sessionStorage',
    'indexedDB',
  ]);
  const findings: string[] = [];

  const allowedImportSpecifiers = new Set(['react', 'react/jsx-runtime', 'react/jsx-dev-runtime']);

  function visit(node: ts.Node): void {
    if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
      const specifier = node.moduleSpecifier.text;
      if (!allowedImportSpecifiers.has(specifier) && !specifier.startsWith('@ghatana/')) {
        findings.push(`module "${specifier}" is not allowed`);
      }
    }
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      node.expression.text === 'require' &&
      node.arguments[0] !== undefined &&
      ts.isStringLiteral(node.arguments[0])
    ) {
      const specifier = node.arguments[0].text;
      if (!allowedImportSpecifiers.has(specifier) && !specifier.startsWith('@ghatana/')) {
        findings.push(`module "${specifier}" is not allowed`);
      }
    }
    if (ts.isIdentifier(node) && forbiddenNames.has(node.text)) {
      findings.push(node.text);
    }
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword) {
      findings.push('dynamic import');
    }
    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);

  if (findings.length > 0) {
    throw new Error(`Preview source violates sandbox policy: ${[...new Set(findings)].join(', ')}`);
  }
}

function hasExportModifier(node: ts.Node): boolean {
  return ts.canHaveModifiers(node) &&
    (ts.getModifiers(node)?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword) ?? false);
}

function isDefaultExport(node: ts.Node): boolean {
  return ts.canHaveModifiers(node) &&
    (ts.getModifiers(node)?.some((modifier) => modifier.kind === ts.SyntaxKind.DefaultKeyword) ?? false);
}

function findReturnedJsxExpression(sourceFile: ts.SourceFile): ts.Expression {
  for (const statement of sourceFile.statements) {
    if (ts.isFunctionDeclaration(statement) && hasExportModifier(statement) && statement.body !== undefined) {
      const returned = findReturnExpression(statement.body);
      if (returned !== null) return returned;
    }

    if (ts.isVariableStatement(statement) && hasExportModifier(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        const initializer = declaration.initializer;
        if (initializer === undefined) continue;
        if (ts.isArrowFunction(initializer)) {
          if (ts.isBlock(initializer.body)) {
            const returned = findReturnExpression(initializer.body);
            if (returned !== null) return returned;
          } else {
            return initializer.body;
          }
        }
        if (ts.isFunctionExpression(initializer) && initializer.body !== undefined) {
          const returned = findReturnExpression(initializer.body);
          if (returned !== null) return returned;
        }
      }
    }

    if (ts.isExportAssignment(statement) && ts.isIdentifier(statement.expression)) {
      const returned = findReturnedJsxForIdentifier(sourceFile, statement.expression.text);
      if (returned !== null) return returned;
    }

    if (ts.isFunctionDeclaration(statement) && isDefaultExport(statement) && statement.body !== undefined) {
      const returned = findReturnExpression(statement.body);
      if (returned !== null) return returned;
    }
  }

  throw new Error('No valid component export found in source');
}

function findReturnedJsxForIdentifier(sourceFile: ts.SourceFile, identifier: string): ts.Expression | null {
  for (const statement of sourceFile.statements) {
    if (ts.isFunctionDeclaration(statement) && statement.name?.text === identifier && statement.body !== undefined) {
      return findReturnExpression(statement.body);
    }
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        if (!ts.isIdentifier(declaration.name) || declaration.name.text !== identifier) continue;
        const initializer = declaration.initializer;
        if (initializer === undefined) continue;
        if (ts.isArrowFunction(initializer)) {
          return ts.isBlock(initializer.body) ? findReturnExpression(initializer.body) : initializer.body;
        }
        if (ts.isFunctionExpression(initializer) && initializer.body !== undefined) {
          return findReturnExpression(initializer.body);
        }
      }
    }
  }
  return null;
}

function findReturnExpression(body: ts.Block): ts.Expression | null {
  for (const statement of body.statements) {
    if (ts.isReturnStatement(statement) && statement.expression !== undefined) {
      return statement.expression;
    }
  }
  return null;
}

function collectStaticConsoleLogs(sourceFile: ts.SourceFile): ConsoleLog[] {
  const logs: ConsoleLog[] = [];
  const consoleLevels = new Set<ConsoleLog['level']>(['log', 'warn', 'error', 'info']);

  function visit(node: ts.Node): void {
    if (
      ts.isCallExpression(node) &&
      ts.isPropertyAccessExpression(node.expression) &&
      ts.isIdentifier(node.expression.expression) &&
      node.expression.expression.text === 'console' &&
      consoleLevels.has(node.expression.name.text as ConsoleLog['level'])
    ) {
      logs.push({
        level: node.expression.name.text as ConsoleLog['level'],
        message: node.arguments.map(argumentToText).join(' '),
        timestamp: Date.now(),
      });
    }
    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);
  return logs;
}

function argumentToText(node: ts.Node): string {
  if (ts.isStringLiteralLike(node)) return node.text;
  if (ts.isNumericLiteral(node)) return node.text;
  if (node.kind === ts.SyntaxKind.TrueKeyword) return 'true';
  if (node.kind === ts.SyntaxKind.FalseKeyword) return 'false';
  return node.getText();
}

function renderStaticPreviewHtml(sourceFile: ts.SourceFile, request: PreviewRequest): string {
  const returned = findReturnedJsxExpression(sourceFile);
  return renderExpression(returned, request);
}

function renderExpression(expression: ts.Expression, request: PreviewRequest): string {
  if (ts.isParenthesizedExpression(expression)) {
    return renderExpression(expression.expression, request);
  }
  if (ts.isJsxElement(expression)) {
    return renderJsxElement(expression, request);
  }
  if (ts.isJsxSelfClosingElement(expression)) {
    return renderJsxSelfClosingElement(expression, request);
  }
  if (ts.isJsxFragment(expression)) {
    return expression.children.map((child) => renderJsxChild(child, request)).join('');
  }
  if (ts.isStringLiteralLike(expression) || ts.isNumericLiteral(expression)) {
    return escapeHtml(expression.text);
  }
  if (
    ts.isIdentifier(expression) ||
    expression.kind === ts.SyntaxKind.NullKeyword ||
    expression.kind === ts.SyntaxKind.FalseKeyword ||
    expression.kind === ts.SyntaxKind.UndefinedKeyword
  ) {
    return '';
  }
  throw new Error('Preview component must return static JSX');
}

function renderJsxElement(element: ts.JsxElement, request: PreviewRequest): string {
  const tagName = element.openingElement.tagName.getText();
  const tag = resolveTagName(tagName, request);
  const attrs = renderAttributes(element.openingElement.attributes, request, tagName);
  const children = element.children.map((child) => renderJsxChild(child, request)).join('');
  return `<${tag}${attrs}>${children}</${tag}>`;
}

function renderJsxSelfClosingElement(element: ts.JsxSelfClosingElement, request: PreviewRequest): string {
  const tagName = element.tagName.getText();
  const tag = resolveTagName(tagName, request);
  const attrs = renderAttributes(element.attributes, request, tagName);
  return `<${tag}${attrs}></${tag}>`;
}

function resolveTagName(tagName: string, request: PreviewRequest): string {
  if (/^[a-z]/.test(tagName)) return tagName;
  const available = request.designSystem?.availableComponents ?? [];
  if (available.includes(tagName)) return 'div';
  return 'div';
}

function renderAttributes(attributes: ts.JsxAttributes, request: PreviewRequest, tagName: string): string {
  const rendered: string[] = [];
  if (/^[A-Z]/.test(tagName)) {
    const available = request.designSystem?.availableComponents ?? [];
    rendered.push(`${available.includes(tagName) ? 'data-ds-component' : 'data-preview-component'}="${escapeHtml(tagName)}"`);
  }

  for (const prop of attributes.properties) {
    if (!ts.isJsxAttribute(prop)) continue;
    const name = prop.name.text;
    if (name.startsWith('on') || name === 'ref' || name === 'dangerouslySetInnerHTML') continue;
    if (prop.initializer === undefined) {
      rendered.push(`${name}="true"`);
      continue;
    }
    if (ts.isStringLiteral(prop.initializer)) {
      rendered.push(`${name}="${escapeHtml(prop.initializer.text)}"`);
      continue;
    }
    if (ts.isJsxExpression(prop.initializer) && prop.initializer.expression !== undefined) {
      const value = staticExpressionValue(prop.initializer.expression);
      if (value !== null) rendered.push(`${name}="${escapeHtml(value)}"`);
    }
  }

  return rendered.length > 0 ? ` ${rendered.join(' ')}` : '';
}

function staticExpressionValue(expression: ts.Expression): string | null {
  if (ts.isStringLiteralLike(expression)) return expression.text;
  if (ts.isNumericLiteral(expression)) return expression.text;
  if (expression.kind === ts.SyntaxKind.TrueKeyword) return 'true';
  if (expression.kind === ts.SyntaxKind.FalseKeyword) return 'false';
  return null;
}

function renderJsxChild(child: ts.JsxChild, request: PreviewRequest): string {
  if (ts.isJsxText(child)) {
    return escapeHtml(child.text.replace(/\s+/g, ' '));
  }
  if (ts.isJsxExpression(child)) {
    if (child.expression === undefined) return '';
    return renderExpression(child.expression, request);
  }
  if (ts.isJsxElement(child)) return renderJsxElement(child, request);
  if (ts.isJsxSelfClosingElement(child)) return renderJsxSelfClosingElement(child, request);
  if (ts.isJsxFragment(child)) return child.children.map((nested) => renderJsxChild(nested, request)).join('');
  return '';
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
      const sourceFile = parsePreviewSource(request.source);
      assertPreviewPolicy(sourceFile);

      const html = renderStaticPreviewHtml(sourceFile, request);
      this.consoleCapture.stop();
      const logs = collectStaticConsoleLogs(sourceFile);
      const duration = Math.max(1, Date.now() - startTime);

      return {
        success: true,
        html: this.wrapInDocumentShell(html, request),
        logs,
        duration,
      };
    } catch (err) {
      const logs = this.consoleCapture.stop();
      const duration = Math.max(1, Date.now() - startTime);
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
   * Wrap rendered HTML in a document shell with theme provider.
   */
  private wrapInDocumentShell(html: string, request: PreviewRequest): string {
    const themeMode = request.theme?.mode ?? 'light';
    const themeStyles = this.generateThemeStyles(request.theme);

    const sandbox = this.generateSandboxAttribute(request);
    const csp = request.securityPolicy?.contentSecurityPolicy;

    return `<!DOCTYPE html>
<html lang="en" data-theme="${themeMode}">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  ${csp ? `<meta http-equiv="Content-Security-Policy" content="${escapeHtml(csp)}" />` : ''}
  <title>Preview</title>
  <style>
    body { margin: 0; font-family: system-ui, sans-serif; }
    ${themeStyles}
  </style>
</head>
<body>
  <iframe title="Preview sandbox" sandbox="${sandbox}" srcdoc="${escapeHtml(`<div id="preview-root">${html}</div>`)}"></iframe>
</body>
</html>`;
  }

  private generateSandboxAttribute(request: PreviewRequest): string {
    const policy = request.securityPolicy;
    const tokens: string[] = [];
    if (policy?.allowSameOrigin === true) tokens.push('allow-same-origin');
    if (policy?.allowScripts === true) tokens.push('allow-scripts');
    if (policy?.allowPopups === true) tokens.push('allow-popups');
    if (policy?.allowForms === true) tokens.push('allow-forms');
    return tokens.join(' ');
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

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

/**
 * Default preview runtime instance.
 */
export const defaultPreviewRuntime = new InMemoryPreviewRuntime();
