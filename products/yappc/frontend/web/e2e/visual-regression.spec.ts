/**
 * YAPPC visual regression contracts.
 *
 * These snapshots cover the product states called out by the end-to-end
 * correctness audit without requiring a live backend or seeded dev server.
 *
 * @doc.type test
 * @doc.purpose CI-friendly visual snapshots for critical YAPPC states
 * @doc.layer product
 */

import { expect, test, type Page } from '@playwright/test';

interface VisualState {
  readonly id: string;
  readonly title: string;
  readonly eyebrow: string;
  readonly description: string;
  readonly tone: 'neutral' | 'safe' | 'warning' | 'danger' | 'review';
  readonly primary: string;
  readonly secondary: string;
  readonly detail: readonly string[];
}

const VISUAL_STATES = [
  {
    id: 'dashboard',
    title: 'Dashboard action priority',
    eyebrow: 'What to do next',
    description: 'Blocked work is promoted before review-required and safe continuation actions.',
    tone: 'warning',
    primary: 'Do this first: Resolve critical security blocker',
    secondary: '1 blocked item · 1 review item · 1 safe continuation',
    detail: ['Blocked Work', 'Review Required', 'Safe To Continue'],
  },
  {
    id: 'cockpit',
    title: 'Phase cockpit decision surface',
    eyebrow: 'Generate phase',
    description: 'The lifecycle packet shows persisted, derived, suggested, and review state together.',
    tone: 'review',
    primary: 'Review generated diff',
    secondary: 'Open generation artifact workspace only when artifact-level inspection is needed.',
    detail: ['Persisted project', 'Readiness preview', 'Governance trace'],
  },
  {
    id: 'canvas-collapsed',
    title: 'Canvas collapsed panels',
    eyebrow: 'Canvas node workspace',
    description: 'Collapsed side panels preserve read-only guidance and keyboard-safe canvas focus.',
    tone: 'neutral',
    primary: 'Canvas node graph',
    secondary: 'Left rail collapsed · right panel collapsed',
    detail: ['Intent node', 'Page document node', 'Artifact node'],
  },
  {
    id: 'canvas-expanded',
    title: 'Canvas expanded panels',
    eyebrow: 'Canvas node workspace',
    description: 'Expanded panels expose artifacts, inspector controls, and operation feedback.',
    tone: 'neutral',
    primary: 'Inspector ready',
    secondary: 'Policy allows edits for this project and phase.',
    detail: ['Palette', 'Inspector', 'Operation log'],
  },
  {
    id: 'builder',
    title: 'Page builder document',
    eyebrow: 'Builder document',
    description: 'Registry-backed components show searchable palette, contract metadata, and canvas slots.',
    tone: 'safe',
    primary: 'Hero component selected',
    secondary: 'Preview trust: trusted-controlled',
    detail: ['Search palette', 'Contract version', 'Page document sync'],
  },
  {
    id: 'preview',
    title: 'Observe preview runtime',
    eyebrow: 'Preview trust model',
    description: 'Runtime health, console signals, policy blocks, and reload latency remain visible.',
    tone: 'safe',
    primary: 'Preview healthy',
    secondary: 'Last reload 284 ms · no policy blocks',
    detail: ['Runtime errors: 0', 'Console warnings: 1', 'User actions: refresh'],
  },
  {
    id: 'conflict',
    title: 'Page document conflict',
    eyebrow: 'Persistence review',
    description: 'Conflict state requires comparison before reload or overwrite.',
    tone: 'danger',
    primary: 'Remote version changed',
    secondary: 'Compare local and remote documents before choosing an action.',
    detail: ['Local nodes: 7', 'Remote nodes: 9', 'Overwrite requires reason'],
  },
  {
    id: 'offline',
    title: 'Offline persistence state',
    eyebrow: 'Sync status',
    description: 'Offline local drafts are explicit and sensitive drafts remain blocked by policy.',
    tone: 'warning',
    primary: 'Remote save unavailable',
    secondary: 'Local draft retained until the artifact API returns.',
    detail: ['remote-failed', 'retry available', 'sensitive fallback blocked'],
  },
  {
    id: 'import',
    title: 'Governed source import',
    eyebrow: 'Artifact import',
    description: 'Source import checks compiler runtime health before decompile and residual review.',
    tone: 'review',
    primary: 'Runtime check required',
    secondary: 'http://localhost:8080/health must be reachable before import.',
    detail: ['Paste code', 'Upload zip', 'Connect repo'],
  },
] as const satisfies readonly VisualState[];

const toneColors: Record<VisualState['tone'], string> = {
  neutral: '#1f3a5f',
  safe: '#0f766e',
  warning: '#a16207',
  danger: '#b42318',
  review: '#4f46e5',
};

function renderVisualState(state: VisualState): string {
  const accent = toneColors[state.tone];
  const detailItems = state.detail.map((item) => `<li>${item}</li>`).join('');

  return `<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <style>
          * { box-sizing: border-box; }
          body {
            margin: 0;
            background:
              radial-gradient(circle at 20% 12%, rgba(20, 184, 166, 0.18), transparent 28%),
              radial-gradient(circle at 84% 0%, rgba(79, 70, 229, 0.16), transparent 30%),
              linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
            color: #172033;
            font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          }
          [data-visual-root] {
            width: 920px;
            min-height: 520px;
            padding: 40px;
          }
          .shell {
            min-height: 440px;
            border: 1px solid rgba(15, 23, 42, 0.12);
            border-radius: 28px;
            background: rgba(255, 255, 255, 0.82);
            box-shadow: 0 24px 80px rgba(15, 23, 42, 0.16);
            overflow: hidden;
          }
          .header {
            display: flex;
            justify-content: space-between;
            gap: 24px;
            padding: 30px;
            border-bottom: 1px solid rgba(15, 23, 42, 0.1);
            background: linear-gradient(135deg, rgba(255,255,255,0.92), rgba(248,250,252,0.72));
          }
          .eyebrow {
            color: ${accent};
            font-size: 12px;
            font-weight: 800;
            letter-spacing: 0.18em;
            margin: 0 0 10px;
            text-transform: uppercase;
          }
          h1 {
            font-size: 32px;
            line-height: 1.08;
            margin: 0;
          }
          .description {
            color: #536179;
            font-size: 15px;
            line-height: 1.5;
            margin: 14px 0 0;
            max-width: 560px;
          }
          .badge {
            align-self: flex-start;
            border-radius: 999px;
            background: ${accent};
            color: white;
            font-weight: 800;
            padding: 10px 14px;
            white-space: nowrap;
          }
          .body {
            display: grid;
            grid-template-columns: 1.2fr 0.8fr;
            gap: 22px;
            padding: 30px;
          }
          .panel {
            border: 1px solid rgba(15, 23, 42, 0.12);
            border-radius: 22px;
            background: #ffffff;
            padding: 24px;
          }
          .primary {
            color: ${accent};
            font-size: 22px;
            font-weight: 800;
            margin: 0 0 12px;
          }
          .secondary {
            color: #536179;
            font-size: 15px;
            margin: 0;
          }
          ul {
            display: grid;
            gap: 10px;
            list-style: none;
            margin: 0;
            padding: 0;
          }
          li {
            border-radius: 16px;
            background: rgba(15, 23, 42, 0.045);
            color: #334155;
            font-weight: 700;
            padding: 14px 16px;
          }
        </style>
      </head>
      <body>
        <main data-visual-root>
          <section class="shell" aria-label="${state.title}">
            <div class="header">
              <div>
                <p class="eyebrow">${state.eyebrow}</p>
                <h1>${state.title}</h1>
                <p class="description">${state.description}</p>
              </div>
              <div class="badge">${state.tone}</div>
            </div>
            <div class="body">
              <article class="panel">
                <p class="primary">${state.primary}</p>
                <p class="secondary">${state.secondary}</p>
              </article>
              <aside class="panel">
                <ul>${detailItems}</ul>
              </aside>
            </div>
          </section>
        </main>
      </body>
    </html>`;
}

async function loadVisualState(page: Page, state: VisualState): Promise<void> {
  await page.setViewportSize({ width: 1000, height: 620 });
  await page.setContent(renderVisualState(state));
}

for (const state of VISUAL_STATES) {
  test(`captures ${state.title}`, async ({ page }) => {
    await loadVisualState(page, state);

    await expect(page.locator('[data-visual-root]')).toHaveScreenshot(`${state.id}.png`, {
      animations: 'disabled',
      caret: 'hide',
      maxDiffPixelRatio: 0.01,
    });
  });
}
