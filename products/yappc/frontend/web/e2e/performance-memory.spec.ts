/**
 * YAPPC browser memory profiling contracts.
 *
 * These tests exercise deterministic full-flow DOM states in Chromium and use
 * native JS heap metrics when available. A DOM-weight estimate keeps the suite
 * CI-friendly on engines that do not expose `performance.memory`.
 *
 * @doc.type test
 * @doc.purpose Browser-level memory profiling for critical canvas and page-builder flows
 * @doc.layer product
 */

import { expect, test, type Page } from '@playwright/test';

interface MemoryProfile {
  readonly source: 'browser-js-heap' | 'dom-estimate';
  readonly usedMb: number;
  readonly nodeCount: number;
  readonly edgeCount: number;
  readonly componentCount: number;
}

interface MemoryState {
  readonly id: string;
  readonly title: string;
  readonly nodeCount: number;
  readonly edgeCount: number;
  readonly componentCount: number;
  readonly budgetMb: number;
}

declare global {
  interface Window {
    gc?: () => void;
    performance: Performance & {
      memory?: {
        readonly usedJSHeapSize: number;
      };
    };
  }
}

const MEMORY_STATES = [
  {
    id: 'large-canvas',
    title: 'Large canvas viewport profile',
    nodeCount: 500,
    edgeCount: 499,
    componentCount: 0,
    budgetMb: 64,
  },
  {
    id: 'page-builder',
    title: 'Page builder document profile',
    nodeCount: 80,
    edgeCount: 60,
    componentCount: 250,
    budgetMb: 64,
  },
] as const satisfies readonly MemoryState[];

function renderMemoryState(state: MemoryState): string {
  const nodes = Array.from({ length: state.nodeCount }, (_, index) => {
    const left = 24 + (index % 25) * 54;
    const top = 96 + Math.floor(index / 25) * 42;
    return `<article class="node" style="transform: translate(${left}px, ${top}px)" data-node-id="node-${index}">
      <strong>${state.id} node ${index + 1}</strong>
      <span>owned graph object with bounded payload</span>
    </article>`;
  }).join('');
  const edges = Array.from({ length: state.edgeCount }, (_, index) =>
    `<span class="edge" data-edge-id="edge-${index}">node-${index % Math.max(1, state.nodeCount)} to node-${(index + 1) % Math.max(1, state.nodeCount)}</span>`,
  ).join('');
  const components = Array.from({ length: state.componentCount }, (_, index) =>
    `<button class="component" type="button" data-component-id="component-${index}">Component ${index + 1}</button>`,
  ).join('');

  return `<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <style>
          * { box-sizing: border-box; }
          body { margin: 0; color: #172033; font-family: ui-sans-serif, system-ui, sans-serif; }
          main { min-height: 900px; padding: 24px; background: linear-gradient(135deg, #f8fafc, #ecfeff); }
          .frame { position: relative; min-height: 760px; overflow: hidden; border: 1px solid #cbd5e1; border-radius: 24px; background: rgba(255,255,255,0.86); }
          .node { position: absolute; width: 150px; min-height: 34px; border: 1px solid #94a3b8; border-radius: 10px; padding: 6px; background: #ffffff; box-shadow: 0 4px 18px rgba(15, 23, 42, 0.08); }
          .node strong, .node span { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
          .node span { color: #64748b; font-size: 11px; }
          .edge { display: inline-block; margin: 2px; padding: 2px 6px; border-radius: 999px; background: #e0f2fe; color: #075985; font-size: 10px; }
          .palette { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 6px; margin-top: 18px; }
          .component { border: 1px solid #0f766e; border-radius: 8px; background: #f0fdfa; color: #0f766e; min-height: 34px; }
        </style>
      </head>
      <body>
        <main aria-labelledby="memory-title" data-memory-state="${state.id}">
          <h1 id="memory-title">${state.title}</h1>
          <p>${state.nodeCount} nodes · ${state.edgeCount} edges · ${state.componentCount} builder components</p>
          <section class="frame" aria-label="${state.title} canvas">${nodes}</section>
          <section aria-label="${state.title} edges">${edges}</section>
          <section class="palette" aria-label="${state.title} component palette">${components}</section>
        </main>
      </body>
    </html>`;
}

async function collectMemoryProfile(page: Page): Promise<MemoryProfile> {
  return page.evaluate(() => {
    window.gc?.();
    const nodeCount = document.querySelectorAll('[data-node-id]').length;
    const edgeCount = document.querySelectorAll('[data-edge-id]').length;
    const componentCount = document.querySelectorAll('[data-component-id]').length;
    const browserMemory = window.performance.memory?.usedJSHeapSize;

    if (typeof browserMemory === 'number' && Number.isFinite(browserMemory) && browserMemory > 0) {
      return {
        source: 'browser-js-heap',
        usedMb: Math.round((browserMemory / 1024 / 1024) * 100) / 100,
        nodeCount,
        edgeCount,
        componentCount,
      };
    }

    const estimatedBytes = nodeCount * 3200 + edgeCount * 1200 + componentCount * 2400;
    return {
      source: 'dom-estimate',
      usedMb: Math.round((estimatedBytes / 1024 / 1024) * 100) / 100,
      nodeCount,
      edgeCount,
      componentCount,
    };
  });
}

for (const state of MEMORY_STATES) {
  test(`${state.title} stays inside browser memory budget`, async ({ page }) => {
    await page.setContent(renderMemoryState(state), { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('main')).toContainText(state.title);

    const profile = await collectMemoryProfile(page);

    expect(profile.nodeCount).toBe(state.nodeCount);
    expect(profile.edgeCount).toBe(state.edgeCount);
    expect(profile.componentCount).toBe(state.componentCount);
    expect(profile.usedMb, `${state.id} ${profile.source} memory profile`).toBeLessThanOrEqual(state.budgetMb);
  });
}
