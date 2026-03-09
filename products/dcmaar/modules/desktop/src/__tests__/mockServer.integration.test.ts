// @jest-environment node
import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import fs from 'fs';
import http from 'http';
import https from 'https';
import { describe, test, expect, beforeAll, afterAll, jest } from '@jest/globals';

// Extend ProcessEnv to include custom environment variables
declare global {
  namespace NodeJS {
    interface ProcessEnv {
      USE_EXTERNAL_MOCK?: string;
    }
  }
}

const SAMPLE = path.resolve(
  __dirname,
  '../../../../shared/test-fixtures/config-envelope/samples/sample-config-envelope.json'
);
const MOCK_SCRIPT = path.resolve(__dirname, '../../../../tools/mock-config-server/index.js');

jest.setTimeout(60000);

function startMockServer(
  port: number
): Promise<{ proc: ChildProcess; url: string; port: number; logs: { out: string; err: string } }> {
  return new Promise((resolve, reject) => {
    // Prefer the workspace root node_modules so tools in /tools can resolve.
    // If express isn't present there (fallback installer may have installed only in services/desktop),
    // prefer the services/desktop node_modules. Log the chosen NODE_PATH to help debugging.
    const repoRoot = path.resolve(__dirname, '../../../../');
    const repoNodeModules = path.join(repoRoot, 'node_modules');
    const desktopNodeModules = path.join(repoRoot, 'products', 'desktop', 'node_modules');
    let nodeModulesPath = repoNodeModules;
    try {
      const hasExpressInRepo =
        fs.existsSync(path.join(repoNodeModules, 'express')) ||
        fs.existsSync(path.join(repoNodeModules, 'express', 'package.json'));
      const hasExpressInDesktop =
        fs.existsSync(path.join(desktopNodeModules, 'express')) ||
        fs.existsSync(path.join(desktopNodeModules, 'express', 'package.json'));
      if (!hasExpressInRepo && hasExpressInDesktop) {
        nodeModulesPath = desktopNodeModules;
      }
    } catch {
      // ignore and fall back to repoNodeModules
    }
    const env = Object.assign({}, process.env as any, { PORT: String(port), NODE_PATH: nodeModulesPath });
    const proc = spawn((process as any).execPath, [MOCK_SCRIPT], { env, stdio: ['ignore', 'pipe', 'pipe'] });
    // create temp files for logs so CI/local runs can collect them
    const tmpdir = fs.existsSync('/tmp') ? '/tmp' : (process as any).cwd();
    const outPath = fs.mkdtempSync(path.join(tmpdir, 'mock-out-')) + '.log';
    const errPath = fs.mkdtempSync(path.join(tmpdir, 'mock-err-')) + '.log';
    const outStream = fs.createWriteStream(outPath, { flags: 'a' });
    const errStream = fs.createWriteStream(errPath, { flags: 'a' });
    // buffers intentionally omitted; logs are streamed to temp files

    const onOut = (b: Buffer) => {
      const s = b.toString();
      outStream.write(s);
      const m = s.match(/Mock config server listening on https?:\/\/localhost:(\d+)/);
      if (m) {
        cleanup();
        const actualPort = Number(m[1]);
        // expose where logs landed to help debugging
        // print short note so CI captures it in the test logs
        // (the runner also creates its own log file)
        console.log(`Mock server stdout log: ${outPath}`);
        console.log(`Mock server stderr log: ${errPath}`);
        resolve({
          proc,
          url: `http://localhost:${actualPort}`,
          port: actualPort,
          logs: { out: outPath, err: errPath },
        });
      }
    };

    const onErr = (b: Buffer) => {
      const s = b.toString();
      errStream.write(s);
    };

    const onExit = (code: number | null, signal: string | null) => {
      cleanup();
      // read small tail from logs to include
      let outTail = '';
      let errTail = '';
      try {
        outTail = fs.readFileSync(outPath, 'utf8').slice(-4096);
      } catch {}
      try {
        errTail = fs.readFileSync(errPath, 'utf8').slice(-4096);
      } catch {}
      reject(
        new Error(
          `mock server exited early (code=${code} signal=${signal})\n--- stdout (tail) ---\n${outTail}\n--- stderr (tail) ---\n${errTail}`
        )
      );
    };

    const cleanup = () => {
      if (proc.stdout) {
        proc.stdout.off('data', onOut);
      }
      if (proc.stderr) {
        proc.stderr.off('data', onErr);
      }
      proc.off('exit', onExit);
      clearTimeout(timer);
      try {
        outStream.end();
      } catch {}
      try {
        errStream.end();
      } catch {}
    };

    if (proc.stdout) {
      proc.stdout.on('data', onOut);
    }
    if (proc.stderr) {
      proc.stderr.on('data', onErr);
    }
    proc.on('exit', onExit);

    // fallback: if server hasn't announced itself in time, reject with collected output
    const timer = setTimeout(() => {
      cleanup();
      let outTail = '';
      let errTail = '';
      try {
        outTail = fs.readFileSync(outPath, 'utf8').slice(-4096);
      } catch {}
      try {
        errTail = fs.readFileSync(errPath, 'utf8').slice(-4096);
      } catch {}
      reject(
        new Error(
          `mock server did not announce startup in time\n--- stdout (tail) ---\n${outTail}\n--- stderr (tail) ---\n${errTail}`
        )
      );
    }, 60000);
  });
}

async function waitFor(url: string, timeout = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    try {
      await nodeGet(url + '/config', { timeout: 1000 });
      return;
    } catch {
      await new Promise(r => setTimeout(r, 200));
    }
  }
  throw new Error('timeout waiting for server');
}

describe('mock-config-server integration', () => {
  let proc: ChildProcess | null = null;
  let url = '';

  beforeAll(async () => {
    const port = 9001;
    if ((process.env as any).USE_EXTERNAL_MOCK === '1') {
      url = `http://localhost:${port}`;
      // assume external server is already running
      await waitFor(url);
      return;
    }
    const started = await startMockServer(port);
    proc = started.proc;
    url = started.url;
    await waitFor(url);
  });

  afterAll(() => {
    if (proc && !proc.killed) {
      proc.kill();
    }
  });

  test('validate, apply, and observe config and audit', async () => {
    const sample = JSON.parse(fs.readFileSync(SAMPLE, 'utf8'));
    const v = await nodePost(url + '/validate', sample);
    expect(v.valid).toBe(true);

    const a = await nodePost(url + '/apply', sample);
    expect(a.ok).toBe(true);

    const cfg = await nodeGet(url + '/config');
    expect(cfg).toBeDefined();
    expect(cfg.profileId).toBe(sample.profileId);

    const audits = await nodeGet(url + '/audit');
    expect(Array.isArray(audits)).toBe(true);
    expect(audits.some((x: any) => x.action === 'apply')).toBe(true);
  });
});

function nodeGet(urlStr: string, opts: { timeout?: number } = {}): Promise<any> {
  return new Promise((resolve, reject) => {
    const u = new URL(urlStr);
    const h = u.protocol === 'https:' ? https : http;
    const req = h.get(u, { timeout: opts.timeout ?? 5000 }, res => {
      let data = '';
      res.setEncoding('utf8');
      res.on('data', chunk => (data += chunk));
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch {
          resolve(data);
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy(new Error('timeout'));
    });
  });
}

function nodePost(urlStr: string, body: any, opts: { timeout?: number } = {}): Promise<any> {
  return new Promise((resolve, reject) => {
    const u = new URL(urlStr);
    const h = u.protocol === 'https:' ? https : http;
    const payload = JSON.stringify(body);
    const req = h.request(
      u,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
        },
        timeout: opts.timeout ?? 5000,
      },
      res => {
        let data = '';
        res.setEncoding('utf8');
        res.on('data', chunk => (data += chunk));
        res.on('end', () => {
          try {
            resolve(JSON.parse(data));
          } catch {
            resolve(data);
          }
        });
      }
    );
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.write(payload);
    req.end();
  });
}
