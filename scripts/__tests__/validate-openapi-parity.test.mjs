#!/usr/bin/env node

import { describe, expect, it } from 'vitest';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const scriptPath = path.join(repoRoot, 'scripts/validate-openapi-parity.js');

function withFixture(files, callback) {
  const dir = mkdtempSync(path.join(tmpdir(), 'openapi-parity-'));
  try {
    const paths = {};
    for (const [name, contents] of Object.entries(files)) {
      const filePath = path.join(dir, name);
      writeFileSync(filePath, contents);
      paths[name] = filePath;
    }
    return callback(paths);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

describe('validate-openapi-parity', () => {
  it('accepts DMOS route manifests that declare lifecycle without a legacy boundary field', () => {
    withFixture(
      {
        'openapi.yaml': `
openapi: 3.0.3
paths:
  /v1/workspaces/{workspaceId}/campaigns:
    get:
      responses:
        "200":
          description: Success
`,
        'manifest.yaml': `
version: "1.0"
product: DMOS
routes:
  - path: /v1/workspaces/:workspaceId/campaigns
    method: GET
    capability: dmos.campaigns
    actions:
      - view-campaigns
    minimumRole: brand-manager
    lifecycle: stable
    servlet: DmosCampaignServlet
`,
      },
      files => {
        const output = execFileSync('node', [scriptPath, files['openapi.yaml'], files['manifest.yaml'], '--stable-only'], {
          encoding: 'utf8',
        });

        expect(output).toContain('All checks passed');
      }
    );
  });

  it('fails when a manifest route omits lifecycle', () => {
    withFixture(
      {
        'openapi.yaml': `
openapi: 3.0.3
paths:
  /v1/workspaces/{workspaceId}/campaigns:
    get:
      responses:
        "200":
          description: Success
`,
        'manifest.yaml': `
version: "1.0"
product: DMOS
routes:
  - path: /v1/workspaces/:workspaceId/campaigns
    method: GET
    capability: dmos.campaigns
    actions:
      - view-campaigns
    minimumRole: brand-manager
    servlet: DmosCampaignServlet
`,
      },
      files => {
        expect(() =>
          execFileSync('node', [scriptPath, files['openapi.yaml'], files['manifest.yaml']], {
            encoding: 'utf8',
            stdio: 'pipe',
          })
        ).toThrow(/missing lifecycle field/);
      }
    );
  });
});
