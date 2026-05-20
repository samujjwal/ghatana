/**
 * @fileoverview Tests for the new JSX tree extraction, route graph detection,
 * and component usage extraction functions added to decompile/tsx.ts.
 *
 * All tests import and invoke the real production functions.
 */

import { describe, it, expect } from 'vitest';
import * as ts from 'typescript';
import {
  extractJsxTree,
  extractRouteGraph,
  extractComponentUsages,
} from '../decompile/tsx.js';

// ============================================================================
// Helper
// ============================================================================

function makeSourceFile(content: string, filename = 'Test.tsx'): ts.SourceFile {
  return ts.createSourceFile(
    filename,
    content.trim(),
    ts.ScriptTarget.Latest,
    /* setParentNodes */ true,
    ts.ScriptKind.TSX,
  );
}

// ============================================================================
// extractJsxTree
// ============================================================================

describe('extractJsxTree', () => {
  it('extracts a simple single-element JSX tree', () => {
    const sf = makeSourceFile(`
      export function App() {
        return <div className="app"><span>Hello</span></div>;
      }
    `);
    const trees = extractJsxTree(sf);
    expect(trees).toHaveLength(1);
    expect(trees[0]?.tagName).toBe('div');
    expect(trees[0]?.isIntrinsic).toBe(true);
    expect(trees[0]?.children).toHaveLength(1);
    expect(trees[0]?.children[0]?.tagName).toBe('span');
  });

  it('detects custom component tags as non-intrinsic', () => {
    const sf = makeSourceFile(`
      export function Page() {
        return <Layout><Header /></Layout>;
      }
    `);
    const trees = extractJsxTree(sf);
    expect(trees).toHaveLength(1);
    expect(trees[0]?.isIntrinsic).toBe(false);
    expect(trees[0]?.tagName).toBe('Layout');
    expect(trees[0]?.children[0]?.tagName).toBe('Header');
  });

  it('extracts a self-closing element', () => {
    const sf = makeSourceFile(`
      export function Avatar() {
        return <img src="/avatar.png" alt="User" />;
      }
    `);
    const trees = extractJsxTree(sf);
    expect(trees).toHaveLength(1);
    expect(trees[0]?.tagName).toBe('img');
    expect(trees[0]?.children).toHaveLength(0);
  });

  it('returns empty array for a file with no JSX', () => {
    const sf = ts.createSourceFile(
      'util.ts',
      'export function add(a: number, b: number): number { return a + b; }',
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TS,
    );
    const trees = extractJsxTree(sf);
    expect(trees).toHaveLength(0);
  });

  it('records correct start and end line numbers (1-based)', () => {
    const content = `
export function Button() {
  return <button type="button">Click</button>;
}`.trim();
    const sf = makeSourceFile(content);
    const trees = extractJsxTree(sf);
    expect(trees).toHaveLength(1);
    // Lines should be 1-based and valid positive numbers
    expect(trees[0]?.startLine).toBeGreaterThanOrEqual(1);
    expect(trees[0]?.endLine).toBeGreaterThanOrEqual(trees[0]!.startLine);
  });
});

// ============================================================================
// extractRouteGraph
// ============================================================================

describe('extractRouteGraph', () => {
  it('detects a simple Route element with a path prop', () => {
    const sf = makeSourceFile(`
      import { Route, Routes } from 'react-router-dom';
      import { Dashboard } from './Dashboard';

      export function AppRoutes() {
        return (
          <Routes>
            <Route path="/dashboard" element={<Dashboard />} />
          </Routes>
        );
      }
    `);
    const routes = extractRouteGraph(sf);
    expect(routes.length).toBeGreaterThanOrEqual(1);
    const dashRoute = routes.find((r) => r.path === '/dashboard');
    expect(dashRoute).toBeDefined();
    expect(dashRoute?.componentName).toBe('Dashboard');
  });

  it('detects multiple routes', () => {
    const sf = makeSourceFile(`
      import { Route, Routes } from 'react-router-dom';
      import { Home } from './Home';
      import { About } from './About';

      export function App() {
        return (
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/about" element={<About />} />
          </Routes>
        );
      }
    `);
    const routes = extractRouteGraph(sf);
    const paths = routes.map((r) => r.path);
    expect(paths).toContain('/');
    expect(paths).toContain('/about');
  });

  it('returns empty array for a file with no Route elements', () => {
    const sf = makeSourceFile(`
      export function Banner() {
        return <div>No routes here</div>;
      }
    `);
    expect(extractRouteGraph(sf)).toHaveLength(0);
  });

  it('records 1-based source line for detected routes', () => {
    const sf = makeSourceFile(`
      import { Route, Routes } from 'react-router-dom';
      import { Foo } from './Foo';
      export function R() {
        return <Routes><Route path="/foo" element={<Foo />} /></Routes>;
      }
    `);
    const routes = extractRouteGraph(sf);
    expect(routes.length).toBeGreaterThanOrEqual(1);
    expect(routes[0]?.sourceLine).toBeGreaterThanOrEqual(1);
  });
});

// ============================================================================
// extractComponentUsages
// ============================================================================

describe('extractComponentUsages', () => {
  it('extracts custom component usages (capital first letter)', () => {
    const sf = makeSourceFile(`
      import { Button } from './Button';
      import { Badge } from '@ghatana/design-system';

      export function Page() {
        return <div><Button label="Go" /><Badge>New</Badge></div>;
      }
    `);
    const specMap = new Map<string, string>([
      ['Button', './Button'],
      ['Badge', '@ghatana/design-system'],
    ]);
    const usages = extractComponentUsages(sf, new Set(['Badge']), specMap);

    const names = usages.map((u) => u.tagName);
    expect(names).toContain('Button');
    expect(names).toContain('Badge');
  });

  it('does not include HTML intrinsic elements (lower-case)', () => {
    const sf = makeSourceFile(`
      export function Card() {
        return <div><h2>Title</h2><p>Body</p></div>;
      }
    `);
    const usages = extractComponentUsages(sf, new Set(), new Map());
    expect(usages).toHaveLength(0);
  });

  it('marks design-system components with isDesignSystem=true', () => {
    const sf = makeSourceFile(`
      import { Button } from '@ghatana/design-system';
      export function Form() {
        return <Button label="Submit" />;
      }
    `);
    const usages = extractComponentUsages(sf, new Set(['Button']), new Map([['Button', '@ghatana/design-system']]));
    const btn = usages.find((u) => u.tagName === 'Button');
    expect(btn?.isDesignSystem).toBe(true);
    expect(btn?.importedFrom).toBe('@ghatana/design-system');
  });

  it('sets importedFrom to null for locally-defined components not in import map', () => {
    const sf = makeSourceFile(`
      function Inner() { return <div />; }
      export function Outer() { return <Inner />; }
    `);
    const usages = extractComponentUsages(sf, new Set(), new Map());
    const inner = usages.find((u) => u.tagName === 'Inner');
    expect(inner).toBeDefined();
    expect(inner?.importedFrom).toBeNull();
  });
});
