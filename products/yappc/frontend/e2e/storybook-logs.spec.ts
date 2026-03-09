import { test } from '@playwright/test';
import fs from 'fs';
import path from 'path';

// Curated list of story ids for the diagram stories (kebab-cased story names)
// Source files:
// - libs/diagram/src/components/stories/Diagram.stories.tsx (title: 'Diagram/Components/Diagram')
// - libs/diagram/src/components/__stories__/Diagram.stories.tsx (title: 'Diagram/Diagram')
const STORY_IDS = [
  // Diagram/Components/Diagram
  'diagram-components-diagram--basic',
  'diagram-components-diagram--read-only',
  'diagram-components-diagram--no-controls',
  'diagram-components-diagram--no-mini-map',
  'diagram-components-diagram--no-background',
  'diagram-components-diagram--from-store',
  'diagram-components-diagram--dark-theme',
  // Diagram/Diagram
  'diagram-diagram--empty',
  'diagram-diagram--simple-flow',
  'diagram-diagram--branching-flow',
  'diagram-diagram--hierarchical',
  'diagram-diagram--circular-reference',
  'diagram-diagram--with-interactions',
  'diagram-diagram--no-controls',
];

test('capture storybook preview console for multiple Diagram stories', async ({ page }) => {
  const port = process.env.STORYBOOK_PORT || '6006';
  const base = `http://localhost:${port}`;

  for (const id of STORY_IDS) {
    const logs: string[] = [];
    const onConsole = (msg: unknown) => {
      try {
        logs.push(`${msg.type()}: ${msg.text()}`);
      } catch (err) {
        logs.push('console: <failed to read message>');
      }
    };

    page.on('console', onConsole);

    const url = `${base}/iframe.html?id=${id}`;
    try {
      await page.goto(url, { waitUntil: 'networkidle' });
      await page.waitForTimeout(2000);
    } catch (err) {
      console.error(`Failed to load story ${id}:`, err);
    }

    console.log(`PLAYWRIGHT_CONSOLE_LOGS_START ${id}`);
    for (const l of logs) console.log(l);
    console.log(`PLAYWRIGHT_CONSOLE_LOGS_END ${id}`);

    page.removeListener('console', onConsole);
    // Persist logs as JSON for CI/artifacts inspection
    try {
      const artifactsDir = path.resolve(__dirname, 'artifacts', 'storybook-console-logs');
      fs.mkdirSync(artifactsDir, { recursive: true });
      const outPath = path.join(artifactsDir, `${id}.json`);
      fs.writeFileSync(outPath, JSON.stringify({ storyId: id, url, logs }, null, 2));
      console.log(`WROTE_ARTIFACT ${outPath}`);
    } catch (writeErr) {
      console.error('Failed to write artifact for', id, writeErr);
    }
  }
});
