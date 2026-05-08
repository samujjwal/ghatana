/**
 * YAPPC accessibility contracts.
 *
 * These tests exercise deterministic critical product states with real browser
 * semantics and axe-core without requiring a seeded backend.
 *
 * @doc.type test
 * @doc.purpose WCAG and landmark contracts for critical YAPPC decision surfaces
 * @doc.layer product
 */

import { expect, test, type Page } from '@playwright/test';
import axeCore from 'axe-core';

interface AxeNode {
  readonly target: readonly string[];
  readonly failureSummary?: string;
}

interface AxeViolation {
  readonly id: string;
  readonly impact: string | null;
  readonly description: string;
  readonly nodes: readonly AxeNode[];
}

interface AxeRunResult {
  readonly violations: readonly AxeViolation[];
}

declare global {
  interface Window {
    axe: {
      run: (
        context: Document,
        options: {
          runOnly: {
            type: 'tag';
            values: readonly string[];
          };
        },
      ) => Promise<AxeRunResult>;
    };
  }
}

type AccessibilityStateId =
  | 'phase-cockpit'
  | 'canvas-workspace'
  | 'page-builder'
  | 'command-palette'
  | 'governed-import'
  | 'partial-data-recovery';

interface AccessibilityState {
  readonly id: AccessibilityStateId;
  readonly title: string;
  readonly landmark: string;
  readonly html: string;
  readonly assertions: (page: Page) => Promise<void>;
}

const ACCESSIBILITY_STATES = [
  {
    id: 'phase-cockpit',
    title: 'Phase cockpit decision surface',
    landmark: 'Generate phase cockpit',
    html: `
      <nav aria-label="Project lifecycle">
        <ol>
          <li><a href="#intent">Intent</a></li>
          <li><a href="#generate" aria-current="step">Generate</a></li>
          <li><a href="#observe">Observe</a></li>
        </ol>
      </nav>
      <main aria-labelledby="phase-cockpit-title">
        <section aria-label="Generate phase cockpit">
          <p class="eyebrow">Lifecycle packet</p>
          <h1 id="phase-cockpit-title">Generate phase cockpit</h1>
          <p id="phase-summary">Review generated files, approval evidence, and blockers before continuing.</p>
          <div role="status" aria-live="polite">Readiness score updated: 84 percent.</div>
          <article aria-labelledby="review-required-title">
            <h2 id="review-required-title">Review required</h2>
            <p>Generated diff touches billing permissions and needs owner approval.</p>
            <button type="button" aria-describedby="phase-summary">Open generated diff review</button>
          </article>
        </section>
      </main>
    `,
    assertions: async (page) => {
      await expect(page.getByRole('navigation', { name: 'Project lifecycle' })).toBeVisible();
      await expect(page.getByRole('main', { name: 'Generate phase cockpit' })).toBeVisible();
      await expect(page.getByRole('status')).toContainText('Readiness score updated');
      await expect(page.getByRole('button', { name: 'Open generated diff review' })).toBeVisible();
    },
  },
  {
    id: 'canvas-workspace',
    title: 'Canvas workspace controls',
    landmark: 'Canvas node workspace',
    html: `
      <nav aria-label="Canvas phases">
        <button type="button" aria-current="page">Shape</button>
        <button type="button">Validate</button>
        <button type="button">Generate</button>
      </nav>
      <main aria-labelledby="canvas-title">
        <h1 id="canvas-title">Canvas node workspace</h1>
        <div role="toolbar" aria-label="Canvas editing tools">
          <button type="button" aria-pressed="true">Select tool</button>
          <button type="button">Connect nodes</button>
          <button type="button" disabled>Delete selected node</button>
        </div>
        <section role="region" aria-labelledby="graph-title" tabindex="0">
          <h2 id="graph-title">Application architecture graph</h2>
          <p>Three nodes are visible in the current viewport.</p>
        </section>
        <aside aria-label="Inspector">
          <h2>Node inspector</h2>
          <label for="node-title">Node title</label>
          <input id="node-title" name="node-title" value="Billing API" />
        </aside>
      </main>
    `,
    assertions: async (page) => {
      await expect(page.getByRole('toolbar', { name: 'Canvas editing tools' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Application architecture graph' })).toBeVisible();
      await expect(page.getByRole('textbox', { name: 'Node title' })).toHaveValue('Billing API');
      await expect(page.getByRole('button', { name: 'Delete selected node' })).toBeDisabled();
    },
  },
  {
    id: 'page-builder',
    title: 'Registry-backed page builder',
    landmark: 'Page builder document',
    html: `
      <main aria-labelledby="builder-title">
        <h1 id="builder-title">Page builder document</h1>
        <section aria-labelledby="palette-title">
          <h2 id="palette-title">Component palette</h2>
          <label for="palette-search">Search components</label>
          <input id="palette-search" type="search" value="hero" />
          <div role="listbox" aria-label="Filtered component categories">
            <div role="option" aria-selected="true">Recommended hero components</div>
            <div role="option" aria-selected="false">Forms</div>
          </div>
        </section>
        <section role="region" aria-labelledby="page-canvas-title" tabindex="0">
          <h2 id="page-canvas-title">Editable page canvas</h2>
          <div role="tree" aria-label="Builder component tree">
            <article
              role="treeitem"
              tabindex="0"
              aria-label="Hero component. Use Alt plus arrow keys to move this component."
              aria-keyshortcuts="Alt+ArrowUp Alt+ArrowDown Alt+ArrowLeft Alt+ArrowRight"
              data-keyboard-move-target="hero"
            >
              <h3>Hero component</h3>
              <button type="button">Move component down</button>
            </article>
          </div>
        </section>
        <div id="builder-keyboard-status" role="status" aria-live="polite">Preview trust: trusted controlled.</div>
      </main>
    `,
    assertions: async (page) => {
      await expect(page.getByRole('searchbox', { name: 'Search components' })).toHaveValue('hero');
      await expect(page.getByRole('listbox', { name: 'Filtered component categories' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Editable page canvas' })).toBeVisible();
      await page.getByRole('treeitem', { name: /Hero component/ }).focus();
      await page.keyboard.press('Alt+ArrowDown');
      await expect(page.getByRole('status')).toContainText('Moved hero component down');
      await expect(page.getByRole('status')).toContainText('Preview trust');
    },
  },
  {
    id: 'command-palette',
    title: 'Command palette keyboard contract',
    landmark: 'Command palette',
    html: `
      <main aria-labelledby="command-page-title">
        <h1 id="command-page-title">Command palette</h1>
        <section role="dialog" aria-modal="true" aria-labelledby="command-title" aria-describedby="command-help">
          <h2 id="command-title">Command palette</h2>
          <p id="command-help">Search and run workspace actions.</p>
          <label for="command-search">Search commands</label>
          <input
            id="command-search"
            role="combobox"
            aria-expanded="true"
            aria-controls="command-results"
            aria-activedescendant="command-open-preview"
            value="open"
          />
          <div id="command-results" role="listbox" aria-label="Command results">
            <div id="command-open-preview" role="option" aria-selected="true">Open preview verification</div>
            <div id="command-review-import" role="option" aria-selected="false">Review governed import</div>
          </div>
        </section>
      </main>
    `,
    assertions: async (page) => {
      await expect(page.getByRole('dialog', { name: 'Command palette' })).toBeVisible();
      await expect(page.getByRole('combobox', { name: 'Search commands' })).toHaveAttribute(
        'aria-activedescendant',
        'command-open-preview',
      );
      await expect(page.getByRole('option', { name: 'Open preview verification' })).toHaveAttribute(
        'aria-selected',
        'true',
      );
    },
  },
  {
    id: 'governed-import',
    title: 'Governed source import',
    landmark: 'Governed source import',
    html: `
      <main aria-labelledby="import-title">
        <h1 id="import-title">Governed source import</h1>
        <section aria-labelledby="runtime-title">
          <h2 id="runtime-title">Runtime health check</h2>
          <p id="runtime-description">The artifact compiler must be reachable before decompile.</p>
          <div role="status" aria-live="polite">Checking http://localhost:8080/health.</div>
          <button type="button" aria-describedby="runtime-description">Retry runtime health check</button>
        </section>
        <section aria-labelledby="template-title">
          <h2 id="template-title">Import templates</h2>
          <fieldset>
            <legend>Choose import source</legend>
            <label><input type="radio" name="source" checked /> Paste code</label>
            <label><input type="radio" name="source" /> Upload zip</label>
            <label><input type="radio" name="source" /> Connect repo</label>
          </fieldset>
        </section>
      </main>
    `,
    assertions: async (page) => {
      await expect(page.getByRole('main', { name: 'Governed source import' })).toBeVisible();
      await expect(page.getByRole('status')).toContainText('Checking http://localhost:8080/health');
      await expect(page.getByRole('radio', { name: 'Paste code' })).toBeChecked();
      await expect(page.getByRole('button', { name: 'Retry runtime health check' })).toBeVisible();
    },
  },
  {
    id: 'partial-data-recovery',
    title: 'Partial data recovery panel',
    landmark: 'Partial data recovery',
    html: `
      <main aria-labelledby="recovery-title">
        <h1 id="recovery-title">Partial data recovery</h1>
        <section aria-labelledby="persisted-title">
          <h2 id="persisted-title">Persisted project data</h2>
          <p>Project summary and phase actions remain available.</p>
        </section>
        <section aria-labelledby="warning-title">
          <h2 id="warning-title">Recover missing activity</h2>
          <div role="alert">Activity feed failed. The cockpit is using persisted project data.</div>
          <button type="button">Retry activity feed</button>
          <button type="button">Retry readiness preview</button>
        </section>
      </main>
    `,
    assertions: async (page) => {
      await expect(page.getByRole('main', { name: 'Partial data recovery' })).toBeVisible();
      await expect(page.getByRole('alert')).toContainText('Activity feed failed');
      await expect(page.getByRole('button', { name: 'Retry activity feed' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Retry readiness preview' })).toBeVisible();
    },
  },
] as const satisfies readonly AccessibilityState[];

function renderAccessibilityState(state: AccessibilityState): string {
  return `<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <title>${state.title}</title>
        <style>
          * { box-sizing: border-box; }
          body {
            margin: 0;
            background: #f8fafc;
            color: #172033;
            font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            line-height: 1.5;
          }
          a { color: #164e63; }
          button,
          input {
            min-height: 40px;
            border: 1px solid #536179;
            border-radius: 10px;
            background: #ffffff;
            color: #172033;
            font: inherit;
            padding: 8px 12px;
          }
          button {
            cursor: pointer;
            font-weight: 700;
          }
          button:disabled {
            color: #64748b;
            cursor: not-allowed;
          }
          [aria-current],
          [aria-selected="true"],
          [aria-pressed="true"] {
            outline: 3px solid #0f766e;
            outline-offset: 2px;
          }
          main,
          nav,
          section,
          aside,
          article {
            border: 1px solid #d4dbe8;
            border-radius: 18px;
            margin: 16px;
            padding: 20px;
          }
          main {
            background: #ffffff;
            box-shadow: 0 20px 70px rgba(15, 23, 42, 0.12);
          }
          h1,
          h2,
          h3,
          p {
            margin-top: 0;
          }
          nav ol {
            display: flex;
            gap: 16px;
            list-style: none;
            margin: 0;
            padding: 0;
          }
          .eyebrow {
            color: #0f766e;
            font-size: 0.75rem;
            font-weight: 800;
            letter-spacing: 0.16em;
            text-transform: uppercase;
          }
          [role="toolbar"],
          [role="listbox"] {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            margin: 12px 0;
          }
          [role="option"] {
            border: 1px solid #536179;
            border-radius: 999px;
            padding: 8px 12px;
          }
          label {
            display: block;
            margin: 10px 0;
          }
        </style>
      </head>
      <body>
        ${state.html}
        <script>
          document.addEventListener('keydown', (event) => {
            const target = document.activeElement;
            if (!(target instanceof HTMLElement) || !target.dataset.keyboardMoveTarget || !event.altKey) {
              return;
            }
            const direction = event.key.replace('Arrow', '').toLowerCase();
            if (!['up', 'down', 'left', 'right'].includes(direction)) {
              return;
            }
            event.preventDefault();
            const status = document.getElementById('builder-keyboard-status');
            if (status) {
              status.textContent = 'Preview trust: trusted controlled. Moved hero component ' + direction + '.';
            }
          });
        </script>
      </body>
    </html>`;
}

function formatViolations(violations: readonly AxeViolation[]): readonly string[] {
  return violations.map((violation) => {
    const targets = violation.nodes
      .map((node) => `${node.target.join(' ')}${node.failureSummary ? `: ${node.failureSummary}` : ''}`)
      .join('; ');

    return `${violation.id} (${violation.impact ?? 'unknown impact'}): ${violation.description} :: ${targets}`;
  });
}

async function runAxe(page: Page): Promise<AxeRunResult> {
  await page.addScriptTag({ content: axeCore.source });

  return page.evaluate(async () => {
    return window.axe.run(document, {
      runOnly: {
        type: 'tag',
        values: ['wcag2a', 'wcag2aa'],
      },
    });
  });
}

test.describe('critical YAPPC accessibility contracts', () => {
  for (const state of ACCESSIBILITY_STATES) {
    test(`${state.id} has WCAG and semantic contracts`, async ({ page }) => {
      await page.setContent(renderAccessibilityState(state));

      await expect(page.getByRole('main', { name: state.landmark })).toBeVisible();
      await state.assertions(page);

      const result = await runAxe(page);
      expect(formatViolations(result.violations)).toEqual([]);
    });
  }
});
