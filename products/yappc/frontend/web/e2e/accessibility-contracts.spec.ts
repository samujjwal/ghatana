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
  | 'partial-data-recovery'
  | 'route-matrix';

interface AccessibilityState {
  readonly id: AccessibilityStateId;
  readonly title: string;
  readonly landmark: string;
  readonly routePath?: string;
  readonly html: string;
  readonly assertions: (page: Page) => Promise<void>;
}

type KeyboardNavigationStateId =
  | 'phase-tabs-actions'
  | 'canvas-keyboard-workspace'
  | 'preview-keyboard-controls'
  | 'admin-keyboard-controls';

interface KeyboardNavigationState {
  readonly id: KeyboardNavigationStateId;
  readonly title: string;
  readonly landmark: string;
  readonly html: string;
  readonly assertions: (page: Page) => Promise<void>;
}

interface AccessibilityRouteMatrixEntry {
  readonly routePath: string;
  readonly title: string;
  readonly landmark: string;
  readonly sectionName: string;
  readonly primaryAction: string;
}

const PHASE_ROUTE_MATRIX = [
  { routePath: '/p/:projectId/intent', title: 'Intent phase route', landmark: 'Intent phase cockpit', sectionName: 'Intent readiness', primaryAction: 'Capture intent notes' },
  { routePath: '/p/:projectId/shape', title: 'Shape phase route', landmark: 'Shape phase cockpit', sectionName: 'Shape readiness', primaryAction: 'Open canvas workspace' },
  { routePath: '/p/:projectId/validate', title: 'Validate phase route', landmark: 'Validate phase cockpit', sectionName: 'Validation gates', primaryAction: 'Review approval gates' },
  { routePath: '/p/:projectId/generate', title: 'Generate phase route', landmark: 'Generate phase cockpit', sectionName: 'Generation assurance', primaryAction: 'Review generated diff' },
  { routePath: '/p/:projectId/run', title: 'Run phase route', landmark: 'Run phase cockpit', sectionName: 'Run controls', primaryAction: 'Retry run' },
  { routePath: '/p/:projectId/observe', title: 'Observe phase route', landmark: 'Observe phase cockpit', sectionName: 'Runtime signals', primaryAction: 'Review recommendations' },
  { routePath: '/p/:projectId/learn', title: 'Learn phase route', landmark: 'Learn phase cockpit', sectionName: 'Learning evidence', primaryAction: 'Inspect learning evidence' },
  { routePath: '/p/:projectId/evolve', title: 'Evolve phase route', landmark: 'Evolve phase cockpit', sectionName: 'Evolution proposal', primaryAction: 'Review impact analysis' },
] as const satisfies readonly AccessibilityRouteMatrixEntry[];

const CONTROL_PLANE_ROUTE_MATRIX = [
  { routePath: '/kernel-health', title: 'Kernel health route', landmark: 'Kernel health control plane', sectionName: 'ProductUnit health', primaryAction: 'Open product health detail' },
  { routePath: '/kernel-health/products/:productUnitId', title: 'Kernel health product detail route', landmark: 'Kernel health product detail', sectionName: 'Lifecycle timeline', primaryAction: 'Inspect gate evidence' },
  { routePath: '/product-family', title: 'Product-family route', landmark: 'Product-family control plane', sectionName: 'Product-family releases', primaryAction: 'Promote product-family asset' },
  { routePath: '/admin/prompt-versions', title: 'Prompt versions admin route', landmark: 'Prompt versions administration', sectionName: 'Prompt lifecycle', primaryAction: 'Rollback prompt version' },
  { routePath: '/admin/ab-testing', title: 'A/B testing admin route', landmark: 'A/B testing administration', sectionName: 'Experiment outcomes', primaryAction: 'Promote experiment winner' },
  { routePath: '/admin/feature-flags', title: 'Feature flag admin route', landmark: 'Feature flag administration', sectionName: 'Tenant feature flags', primaryAction: 'Update tenant flag' },
  { routePath: '/admin/observability', title: 'Admin observability route', landmark: 'Admin observability', sectionName: 'Release gate evidence', primaryAction: 'Refresh release gates' },
] as const satisfies readonly AccessibilityRouteMatrixEntry[];

function renderRouteMatrixState(entry: AccessibilityRouteMatrixEntry): string {
  const routeId = entry.routePath.replaceAll('/', '-').replaceAll(':', '').replace(/^-/, '');

  return `
    <nav aria-label="YAPPC route matrix">
      <a href="#${routeId}" aria-current="page">${entry.routePath}</a>
    </nav>
    <main id="${routeId}" aria-labelledby="${routeId}-title">
      <section aria-label="${entry.landmark}">
        <p class="eyebrow">Route ${entry.routePath}</p>
        <h1 id="${routeId}-title">${entry.landmark}</h1>
        <p id="${routeId}-summary">Deterministic accessibility fixture for ${entry.title}.</p>
        <section aria-labelledby="${routeId}-section-title">
          <h2 id="${routeId}-section-title">${entry.sectionName}</h2>
          <p>Evidence, ownership, status, and recovery context are available without relying on color alone.</p>
          <ul aria-label="${entry.sectionName} checks">
            <li>Status is represented with text and icon-independent labels.</li>
            <li>Evidence and action controls have accessible names.</li>
            <li>Disabled or blocked controls expose a visible reason.</li>
          </ul>
          <button type="button" aria-describedby="${routeId}-summary">${entry.primaryAction}</button>
        </section>
      </section>
    </main>
  `;
}

function routeMatrixState(entry: AccessibilityRouteMatrixEntry): AccessibilityState {
  return {
    id: 'route-matrix',
    title: entry.title,
    landmark: entry.landmark,
    routePath: entry.routePath,
    html: renderRouteMatrixState(entry),
    assertions: async (page) => {
      await expect(page.getByRole('navigation', { name: 'YAPPC route matrix' })).toBeVisible();
      await expect(page.getByRole('main', { name: entry.landmark })).toBeVisible();
      await expect(page.getByRole('region', { name: entry.landmark })).toBeVisible();
      await expect(page.getByRole('button', { name: entry.primaryAction })).toBeVisible();
      await expect(page.getByRole('list', { name: `${entry.sectionName} checks` })).toBeVisible();
    },
  };
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
      await expect(page.getByRole('status')).toContainText('Moved hero down');
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

const ACCESSIBILITY_ROUTE_MATRIX = [
  ...PHASE_ROUTE_MATRIX.map(routeMatrixState),
  ...CONTROL_PLANE_ROUTE_MATRIX.map(routeMatrixState),
] as const satisfies readonly AccessibilityState[];

const KEYBOARD_NAVIGATION_STATES = [
  {
    id: 'phase-tabs-actions',
    title: 'Phase tabs and actions keyboard contract',
    landmark: 'Lifecycle keyboard workspace',
    html: `
      <main aria-labelledby="phase-keyboard-title">
        <h1 id="phase-keyboard-title">Lifecycle keyboard workspace</h1>
        <div role="tablist" aria-label="Lifecycle phases" data-phase-tablist>
          <button id="phase-tab-intent" type="button" role="tab" aria-selected="true" aria-controls="phase-panel" data-phase-tab>Intent</button>
          <button id="phase-tab-shape" type="button" role="tab" aria-selected="false" aria-controls="phase-panel" tabindex="-1" data-phase-tab>Shape</button>
          <button id="phase-tab-generate" type="button" role="tab" aria-selected="false" aria-controls="phase-panel" tabindex="-1" data-phase-tab>Generate</button>
        </div>
        <section id="phase-panel" role="tabpanel" aria-labelledby="phase-tab-intent" tabindex="0">
          <h2>Selected phase details</h2>
          <p>Primary phase actions remain reachable without a pointer.</p>
          <button type="button" data-status-message="Opened Generate phase action confirmation.">Open phase action confirmation</button>
        </section>
        <div id="keyboard-status" role="status" aria-live="polite">Intent phase selected.</div>
      </main>
    `,
    assertions: async (page) => {
      const intentTab = page.getByRole('tab', { name: 'Intent' });
      const shapeTab = page.getByRole('tab', { name: 'Shape' });
      const actionButton = page.getByRole('button', { name: 'Open phase action confirmation' });

      await intentTab.focus();
      await expect(intentTab).toBeFocused();
      await page.keyboard.press('ArrowRight');
      await expect(shapeTab).toBeFocused();
      await expect(shapeTab).toHaveAttribute('aria-selected', 'true');
      await expect(page.getByRole('status')).toContainText('Shape phase selected');

      await actionButton.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('status')).toContainText('Opened Generate phase action confirmation');
    },
  },
  {
    id: 'canvas-keyboard-workspace',
    title: 'Canvas keyboard workspace contract',
    landmark: 'Canvas keyboard workspace',
    html: `
      <main aria-labelledby="canvas-keyboard-title">
        <h1 id="canvas-keyboard-title">Canvas keyboard workspace</h1>
        <div role="toolbar" aria-label="Canvas keyboard tools">
          <button type="button" aria-pressed="true">Select node</button>
          <button type="button">Connect selected node</button>
          <button type="button">Fit architecture graph</button>
        </div>
        <section role="region" aria-labelledby="canvas-keyboard-region-title" tabindex="0">
          <h2 id="canvas-keyboard-region-title">Keyboard editable architecture graph</h2>
          <div role="tree" aria-label="Canvas node tree">
            <article
              role="treeitem"
              tabindex="0"
              aria-label="Billing API node. Use Alt plus arrow keys to move this node."
              aria-keyshortcuts="Alt+ArrowUp Alt+ArrowDown Alt+ArrowLeft Alt+ArrowRight"
              data-keyboard-move-target="billing-api"
            >
              <h3>Billing API node</h3>
            </article>
          </div>
        </section>
        <aside aria-label="Keyboard inspector">
          <label for="canvas-node-name">Node name</label>
          <input id="canvas-node-name" value="Billing API" />
        </aside>
        <div id="builder-keyboard-status" role="status" aria-live="polite">Preview trust: trusted controlled.</div>
      </main>
    `,
    assertions: async (page) => {
      const graph = page.getByRole('region', { name: 'Keyboard editable architecture graph' });
      const node = page.getByRole('treeitem', { name: /Billing API node/ });

      await page.getByRole('button', { name: 'Select node' }).focus();
      await expect(page.getByRole('button', { name: 'Select node' })).toBeFocused();
      await graph.focus();
      await expect(graph).toBeFocused();
      await node.focus();
      await page.keyboard.press('Alt+ArrowRight');
      await expect(page.getByRole('status')).toContainText('Moved billing api right');
      await page.keyboard.press('Tab');
      await expect(page.getByRole('textbox', { name: 'Node name' })).toBeFocused();
    },
  },
  {
    id: 'preview-keyboard-controls',
    title: 'Preview keyboard controls contract',
    landmark: 'Preview keyboard workspace',
    html: `
      <main aria-labelledby="preview-keyboard-title">
        <h1 id="preview-keyboard-title">Preview keyboard workspace</h1>
        <div role="tablist" aria-label="Preview verification tabs" data-phase-tablist>
          <button id="preview-tab-runtime" type="button" role="tab" aria-selected="true" aria-controls="preview-panel" data-phase-tab>Runtime</button>
          <button id="preview-tab-security" type="button" role="tab" aria-selected="false" aria-controls="preview-panel" tabindex="-1" data-phase-tab>Security</button>
          <button id="preview-tab-a11y" type="button" role="tab" aria-selected="false" aria-controls="preview-panel" tabindex="-1" data-phase-tab>A11y</button>
        </div>
        <section id="preview-panel" role="tabpanel" aria-labelledby="preview-tab-runtime">
          <h2>Preview frame</h2>
          <p id="preview-trust">Preview token is scoped and expires in ten minutes.</p>
          <button type="button" data-preview-open>Open trusted preview frame</button>
          <iframe title="Trusted application preview" aria-describedby="preview-trust"></iframe>
        </section>
        <div id="keyboard-status" role="status" aria-live="polite">Runtime preview selected.</div>
      </main>
    `,
    assertions: async (page) => {
      const runtimeTab = page.getByRole('tab', { name: 'Runtime' });
      const securityTab = page.getByRole('tab', { name: 'Security' });
      const previewButton = page.getByRole('button', { name: 'Open trusted preview frame' });

      await runtimeTab.focus();
      await page.keyboard.press('ArrowRight');
      await expect(securityTab).toBeFocused();
      await expect(securityTab).toHaveAttribute('aria-selected', 'true');
      await previewButton.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('status')).toContainText('Trusted preview frame opened');
      await page.keyboard.press('Escape');
      await expect(previewButton).toBeFocused();
      await expect(page.getByTitle('Trusted application preview')).toBeVisible();
    },
  },
  {
    id: 'admin-keyboard-controls',
    title: 'Admin keyboard controls contract',
    landmark: 'Admin keyboard workspace',
    html: `
      <main aria-labelledby="admin-keyboard-title">
        <h1 id="admin-keyboard-title">Admin keyboard workspace</h1>
        <section aria-labelledby="flag-admin-title">
          <h2 id="flag-admin-title">Feature flag controls</h2>
          <label for="flag-generate">Generate phase enabled</label>
          <input id="flag-generate" type="checkbox" role="switch" aria-describedby="flag-help" />
          <p id="flag-help">Changing this flag affects backend readiness and action authorization.</p>
          <button type="button" data-status-message="Feature flag update saved with audit evidence.">Save feature flag</button>
        </section>
        <div id="keyboard-status" role="status" aria-live="polite">Feature flag form ready.</div>
      </main>
    `,
    assertions: async (page) => {
      const flagSwitch = page.getByRole('switch', { name: 'Generate phase enabled' });
      const saveButton = page.getByRole('button', { name: 'Save feature flag' });

      await flagSwitch.focus();
      await expect(flagSwitch).toBeFocused();
      await page.keyboard.press('Space');
      await expect(flagSwitch).toBeChecked();
      await page.keyboard.press('Tab');
      await expect(saveButton).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('status')).toContainText('Feature flag update saved with audit evidence');
    },
  },
] as const satisfies readonly KeyboardNavigationState[];

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
          const activateTab = (tabs, index) => {
            tabs.forEach((tab, tabIndex) => {
              const selected = tabIndex === index;
              tab.setAttribute('aria-selected', String(selected));
              tab.tabIndex = selected ? 0 : -1;
              if (selected) {
                tab.focus();
                const label = tab.textContent ? tab.textContent.trim() : 'Selected';
                const status = document.getElementById('keyboard-status');
                if (status) {
                  status.textContent = label + ' phase selected.';
                }
              }
            });
          };
          document.querySelectorAll('[data-phase-tablist]').forEach((tablist) => {
            const tabs = Array.from(tablist.querySelectorAll('[data-phase-tab]'));
            tablist.addEventListener('keydown', (event) => {
              if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
                return;
              }
              const activeIndex = Math.max(0, tabs.indexOf(document.activeElement));
              const nextIndex = event.key === 'Home'
                ? 0
                : event.key === 'End'
                  ? tabs.length - 1
                  : event.key === 'ArrowRight'
                    ? (activeIndex + 1) % tabs.length
                    : (activeIndex - 1 + tabs.length) % tabs.length;
              event.preventDefault();
              activateTab(tabs, nextIndex);
            });
          });
          document.querySelectorAll('[data-status-message]').forEach((control) => {
            control.addEventListener('click', () => {
              const status = document.getElementById('keyboard-status');
              if (status && control instanceof HTMLElement) {
                status.textContent = control.dataset.statusMessage || status.textContent;
              }
            });
          });
          document.querySelectorAll('[data-preview-open]').forEach((control) => {
            control.addEventListener('click', () => {
              const status = document.getElementById('keyboard-status');
              if (status) {
                status.textContent = 'Trusted preview frame opened. Press Escape to return to preview controls.';
              }
            });
          });
          document.addEventListener('keydown', (event) => {
            if (event.key !== 'Escape') {
              return;
            }
            const previewControl = document.querySelector('[data-preview-open]');
            if (previewControl instanceof HTMLElement) {
              previewControl.focus();
            }
          });
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
            const targetName = target.dataset.keyboardMoveTarget
              .split('-')
              .filter(Boolean)
              .join(' ');
            const status = document.getElementById('builder-keyboard-status');
            if (status) {
              status.textContent = 'Preview trust: trusted controlled. Moved ' + targetName + ' ' + direction + '.';
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

function renderKeyboardNavigationState(state: KeyboardNavigationState): string {
  return renderAccessibilityState({
    id: 'route-matrix',
    title: state.title,
    landmark: state.landmark,
    html: state.html,
    assertions: state.assertions,
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

test.describe('YAPPC route accessibility matrix', () => {
  for (const state of ACCESSIBILITY_ROUTE_MATRIX) {
    test(`${state.routePath} has WCAG and landmark contracts`, async ({ page }) => {
      await page.setContent(renderAccessibilityState(state));

      await state.assertions(page);

      const result = await runAxe(page);
      expect(formatViolations(result.violations)).toEqual([]);
    });
  }
});

test.describe('YAPPC keyboard navigation matrix', () => {
  for (const state of KEYBOARD_NAVIGATION_STATES) {
    test(`${state.id} supports keyboard-only operation`, async ({ page }) => {
      await page.setContent(renderKeyboardNavigationState(state));

      await expect(page.getByRole('main', { name: state.landmark })).toBeVisible();
      await state.assertions(page);
    });
  }
});
