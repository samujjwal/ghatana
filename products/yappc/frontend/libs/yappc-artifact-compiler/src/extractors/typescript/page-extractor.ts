/**
 * @fileoverview Page and Route Extractor for Next.js / React Router.
 *
 * Extracts page models, route metadata, layout hierarchy, auth guards,
 * and data dependencies from Next.js app/pages directory files.
 */

import * as ts from 'typescript';
import type { ArtifactRecord } from '../../inventory/types';
import type { GraphNode, GraphEdge, GraphNodeKind } from '../../graph/types';
import type { PageModel, LayoutModel } from '../../model/types';
import type { ExtractionResult, ExtractionContext } from '../types';

export const EXTRACTOR_ID = 'typescript-page';
export const EXTRACTOR_VERSION = '0.1.0';

// ============================================================================
// Page Extraction
// ============================================================================

export interface ExtractedPage {
  readonly routePath: string;
  readonly isLayout: boolean;
  readonly isLoading: boolean;
  readonly isError: boolean;
  readonly componentsRendered: readonly string[];
  readonly dataFetches: readonly string[];
  readonly authChecks: readonly string[];
  readonly exports: readonly string[];
  readonly sourceLocation: {
    readonly filePath: string;
    readonly startLine: number;
    readonly startColumn: number;
    readonly endLine: number;
    readonly endColumn: number;
  };
}

export function extractPageFromSource(content: string, filePath: string): ExtractedPage | null {
  const sourceFile = ts.createSourceFile(
    filePath,
    content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );

  const isAppDir = filePath.includes('/app/');
  const isPagesDir = filePath.includes('/pages/');

  if (!isAppDir && !isPagesDir) {
    return null;
  }

  const name = filePath.split('/').pop()?.toLowerCase() ?? '';
  const isLayout = name.startsWith('layout.');
  const isLoading = name.startsWith('loading.');
  const isError = name.startsWith('error.');

  // Derive route path from file path
  let routePath = '/';
  if (isAppDir) {
    const appIndex = filePath.indexOf('/app/');
    const afterApp = filePath.slice(appIndex + 5);
    const segments = afterApp.split('/').filter(s => s && !s.startsWith('(') && !s.startsWith('layout') && !s.startsWith('page') && !s.startsWith('loading') && !s.startsWith('error'));
    routePath = '/' + segments.join('/');
    if (routePath.endsWith('/page') || routePath.endsWith('/layout') || routePath.endsWith('/loading') || routePath.endsWith('/error')) {
      routePath = routePath.replace(/\/(page|layout|loading|error)$/, '');
    }
    if (routePath === '') routePath = '/';
  } else if (isPagesDir) {
    const pagesIndex = filePath.indexOf('/pages/');
    let afterPages = filePath.slice(pagesIndex + 7);
    afterPages = afterPages.replace(/\.(tsx|jsx|ts|js)$/, '');
    if (afterPages === 'index') {
      routePath = '/';
    } else {
      routePath = '/' + afterPages.replace(/\/index$/, '');
    }
  }

  const componentsRendered: string[] = [];
  const dataFetches: string[] = [];
  const authChecks: string[] = [];
  const exports: string[] = [];

  ts.forEachChild(sourceFile, (node) => {
    // Find exports
    if (ts.isExportAssignment(node)) {
      if (node.isExportEquals) {
        exports.push('export=');
      } else {
        exports.push('default');
      }
    }
    if (ts.isVariableStatement(node) && node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword)) {
      for (const decl of node.declarationList.declarations) {
        if (ts.isIdentifier(decl.name)) {
          exports.push(decl.name.text);
        }
      }
    }
    if (ts.isFunctionDeclaration(node) && node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword) && node.name) {
      exports.push(node.name.text);
    }

    // Find JSX component usage
    function visitJsx(n: ts.Node) {
      if (ts.isJsxOpeningLikeElement(n) && ts.isIdentifier(n.tagName)) {
        const tagName = n.tagName.text;
        if (tagName.length > 0 && tagName.charAt(0) === tagName.charAt(0).toUpperCase()) {
          componentsRendered.push(tagName);
        }
      }
      ts.forEachChild(n, visitJsx);
    }
    visitJsx(node);

    // Find data fetching patterns
    function visitDataFetch(n: ts.Node) {
      if (ts.isCallExpression(n) && ts.isIdentifier(n.expression)) {
        const callName = n.expression.text;
        if (callName === 'fetch' || callName.startsWith('useSWR') || callName.startsWith('useQuery') ||
            callName === 'getServerSideProps' || callName === 'getStaticProps' || callName === 'getData') {
          dataFetches.push(callName);
        }
      }
      ts.forEachChild(n, visitDataFetch);
    }
    visitDataFetch(node);

    // Find auth patterns
    if (content.includes('useAuth') || content.includes('withAuth') || content.includes('requireAuth') ||
        content.includes('isAuthenticated') || content.includes('checkPermission') ||
        content.includes('redirect') && content.includes('login')) {
      if (!authChecks.includes('auth-guard')) {
        authChecks.push('auth-guard');
      }
    }
  });

  return {
    routePath,
    isLayout,
    isLoading,
    isError,
    componentsRendered: [...new Set(componentsRendered)],
    dataFetches: [...new Set(dataFetches)],
    authChecks: [...new Set(authChecks)],
    exports: [...new Set(exports)],
    sourceLocation: {
      filePath,
      startLine: 1,
      startColumn: 1,
      endLine: sourceFile.getLineAndCharacterOfPosition(sourceFile.getEnd()).line + 1,
      endColumn: 1,
    },
  };
}

// ============================================================================
// Artifact Extractor Implementation
// ============================================================================

export async function extractPageArtifact(
  record: ArtifactRecord,
  context: ExtractionContext,
): Promise<ExtractionResult> {
  const startTime = Date.now();

  try {
    const content = await context.readFile(record.relativePath);
    const page = extractPageFromSource(content, record.relativePath);

    if (!page) {
      return {
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        artifact: record,
        nodes: [],
        edges: [],
        modelElements: [],
        residualIslands: [],
        errors: [{ message: 'File does not appear to be a Next.js/React page file', recoverable: true }],
        warnings: [],
        durationMs: Date.now() - startTime,
      };
    }

    const nodes: GraphNode[] = [];
    const edges: GraphEdge[] = [];
    const modelElements: Array<PageModel | LayoutModel> = [];
    const warnings: Array<ExtractionResult['warnings'][number]> = [];
    const errors: ExtractionResult['errors'] = [];
    const now = new Date().toISOString();

    const pageId = crypto.randomUUID();

    const nodeKind: GraphNodeKind = page.isLayout ? 'layout' : 'page';

    nodes.push({
      id: pageId,
      type: nodeKind, // P0: Canonical field name 'type', not legacy 'kind'
      label: page.routePath,
      sourceLocation: page.sourceLocation,
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      confidence: 0.85,
      provenance: 'exact',
      privacySecurityFlags: page.authChecks.length > 0 ? ['has-auth-logic'] : [],
      residualFragmentIds: [],
      metadata: {
        routePath: page.routePath,
        isLayout: page.isLayout,
        componentsRendered: page.componentsRendered,
        dataFetches: page.dataFetches,
      },
    });

    // Edges for rendered components
    for (const comp of page.componentsRendered) {
      edges.push({
        id: crypto.randomUUID(),
        sourceId: pageId,
        targetId: comp,
        relationshipType: 'RENDERS', // P0: Canonical field name 'relationshipType', not legacy 'kind'
        confidence: 0.8,
        bidirectional: false,
        metadata: { componentName: comp },
      });
    }

    // Model element
    if (page.isLayout) {
      modelElements.push({
        id: pageId,
        kind: 'layout',
        name: page.routePath === '/' ? 'RootLayout' : `Layout_${page.routePath.replace(/\//g, '_')}`,
        description: `Layout for ${page.routePath}`,
        confidence: 0.85,
        provenance: {
          extractorId: EXTRACTOR_ID,
          extractorVersion: EXTRACTOR_VERSION,
          sourcePaths: [record.relativePath],
          kind: 'exact',
          extractedAt: now,
        },
        securityFlags: [],
        privacyFlags: [],
        tags: [],
        templateType: 'default',
        slotRegions: [],
        appliedToPageIds: [],
      });
    } else {
      modelElements.push({
        id: pageId,
        kind: 'page',
        name: page.routePath === '/' ? 'HomePage' : `Page_${page.routePath.replace(/\//g, '_')}`,
        description: `Page at route ${page.routePath}`,
        confidence: 0.85,
        provenance: {
          extractorId: EXTRACTOR_ID,
          extractorVersion: EXTRACTOR_VERSION,
          sourcePaths: [record.relativePath],
          kind: 'exact',
          extractedAt: now,
        },
        securityFlags: [],
        privacyFlags: [],
        tags: [],
        routePath: page.routePath,
        layoutId: undefined,
        componentIds: [],
        dataDependencies: [],
        authGuard: page.authChecks.length > 0
          ? { required: true, roles: [], redirectPath: undefined }
          : undefined,
        seoMetadata: undefined,
        visibility: 'public',
      });
    }

    return {
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      artifact: record,
      nodes,
      edges,
      modelElements,
      residualIslands: [],
      errors,
      warnings,
      durationMs: Date.now() - startTime,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return {
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      artifact: record,
      nodes: [],
      edges: [],
      modelElements: [],
      residualIslands: [],
      errors: [{ message, recoverable: false }],
      warnings: [],
      durationMs: Date.now() - startTime,
    };
  }
}
