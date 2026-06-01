import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

const SCRIPT_PATH = join(process.cwd(), 'scripts', 'check-phr-correlation-id-usage.mjs');

function runCheck(routes) {
  const dir = mkdtempSync(join(tmpdir(), 'phr-corr-check-'));
  try {
    for (const [fileName, content] of Object.entries(routes)) {
      writeFileSync(join(dir, fileName), content);
    }
    const output = execFileSync('node', [SCRIPT_PATH], {
      cwd: process.cwd(),
      env: { ...process.env, PHR_ROUTE_DIR: dir },
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    return { ok: true, output };
  } catch (error) {
    return {
      ok: false,
      output: `${error.stdout ?? ''}${error.stderr ?? ''}`,
      status: error.status,
    };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('passes when nested response bodies include the correlation overload', () => {
  const result = runCheck({
    'NestedRoutes.java': `
      final class NestedRoutes {
        void ok() {
          return PhrRouteSupport.jsonResponse(200, Map.of("items", List.of(Map.of("id", "1"))), correlationId);
          return PhrRouteSupport.errorResponse(400, "INVALID", ex.getMessage(), correlationId);
          return PhrRouteSupport.textResponse(200, csv, "text/csv", correlationId);
        }
      }
    `,
  });

  assert.equal(result.ok, true, result.output);
  assert.match(result.output, /PASS/);
});

test('fails when a route uses a correlation-free helper overload', () => {
  const result = runCheck({
    'UnsafeRoutes.java': `
      final class UnsafeRoutes {
        void unsafe() {
          return PhrRouteSupport.errorResponse(400, "INVALID", ex.getMessage());
        }
      }
    `,
  });

  assert.equal(result.ok, false);
  assert.equal(result.status, 1);
  assert.match(result.output, /UnsafeRoutes\.java/);
  assert.match(result.output, /errorResponse without correlationId/);
});
