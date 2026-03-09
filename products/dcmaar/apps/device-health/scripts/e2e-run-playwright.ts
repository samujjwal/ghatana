import { chromium, Route, Request, BrowserContext, ConsoleMessage } from 'playwright';
import os from 'os';
import path from 'path';
import fs from 'fs';

const projectRoot = process.cwd();

type Opts = { headless: boolean; timeout: number; out?: string };
type RunnerArgs = Opts & {
  host?: string;
  port?: number;
  debug?: boolean;
  report?: string;
  extensionDir?: string;
};

function parseArgs(argv: string[]): RunnerArgs {
  const opts: RunnerArgs = {
    headless: true,
    timeout: 10000,
    host: '127.0.0.1',
    port: 3000,
    debug: false,
    extensionDir: undefined,
  };
  argv.forEach((a) => {
    if (a === '--headed') opts.headless = false;
    if (a === '--debug') opts.debug = true;
    if (a.startsWith('--timeout=')) opts.timeout = Number(a.split('=')[1]) || opts.timeout;
    if (a.startsWith('--out=')) opts.out = a.split('=')[1];
    if (a.startsWith('--host=')) opts.host = a.split('=')[1] || opts.host;
    if (a.startsWith('--port=')) opts.port = Number(a.split('=')[1]) || opts.port;
    if (a.startsWith('--report=')) opts.report = a.split('=')[1];
    if (a.startsWith('--extension-dir=')) opts.extensionDir = a.split('=')[1];
    if (a.startsWith('--browser=')) {
      const candidate = a.split('=')[1];
      if (candidate) {
        opts.extensionDir = path.resolve(projectRoot, 'dist', candidate);
      }
    }
  });
  return opts;
}

async function runPlaywright(opts: RunnerArgs): Promise<{ code: number; captured?: unknown }> {
  const extensionPath = opts.extensionDir
    ? path.resolve(projectRoot, opts.extensionDir)
    : path.resolve(projectRoot, 'dist', 'chrome');
  if (!fs.existsSync(extensionPath)) {
    console.error('Extension build not found at', extensionPath);
    return { code: 2 };
  }

  const userDataDir = path.resolve(projectRoot, 'tmp', `pw-user-${Date.now()}`);
  const browserArgs = [`--disable-extensions-except=${extensionPath}`, `--load-extension=${extensionPath}`];

  const context: BrowserContext = await chromium.launchPersistentContext(userDataDir, {
    headless: opts.headless,
    args: browserArgs,
  });

  const page = await context.newPage();
  let captured: unknown | null = null;
  let intercepted = false;
  let attempts = 0;
  const errors: string[] = [];
  const startedAt = Date.now();
  const debugLog = (msg: string, ...rest: unknown[]) => opts.debug && console.log('[e2e-run-playwright]', msg, ...rest);

  const consoleSnapshot: Array<{ ts: number; type: string; text: string }> = [];
  const pushConsole = (type: string, text: string) => {
    consoleSnapshot.push({ ts: Date.now(), type, text });
    if (consoleSnapshot.length > 100) consoleSnapshot.shift();
  };

  await context.route('**/*', async (route: Route, request: Request) => {
    try {
      const url = request.url();
      if (request.method() === 'POST' && url.includes('/ingest')) {
        intercepted = true;
        const post = request.postData();
        try {
          captured = post ? JSON.parse(post) : null;
        } catch (_) {
          captured = post;
        }
        await route.fulfill({ status: 200, body: 'ok' });
        return;
      }
    } catch (e) {
      // swallow
    }
    await route.continue();
  });

  page.on('console', (m: ConsoleMessage) => {
    const text = m.text();
    const type = m.type();
    pushConsole(type, text);
    console.log('[PAGE]', type, text);
  });

  const baseUrl = `http://${opts.host}:${opts.port}/index.html`;
  try {
    debugLog('navigating to', baseUrl);
    await page.goto(baseUrl, { waitUntil: 'load', timeout: Math.max(5000, Math.min(opts.timeout, 30000)) });
    // Probe the page for content script markers (attribute, DOM mirror, window array)
    try {
      const probe = await page.evaluate(() => {
        try {
          const attr = document.documentElement?.getAttribute('data-dcmaar-extension') || null;
          const dom = !!document.getElementById('__dcmaar_events_container');
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          const w = (window as any).__dcmaarEvents ? true : false;
          return { attr, dom, windowEvents: w };
        } catch (e) {
          return { error: String(e) };
        }
      });
      debugLog('page probe result', probe);
    } catch (e) {
      debugLog('page probe (pre-click) failed', e instanceof Error ? e.message : String(e));
    }
  } catch (e) {
    console.error('Failed to open fixture page', e);
  }

  // attempt to click the trigger up to a few times, waiting briefly between attempts
  const maxClicks = 5;
  let clicked = false;
  for (let i = 0; i < maxClicks && !intercepted; i += 1) {
    try {
      const el = await page.$('#trigger-metric');
      if (!el) {
        debugLog('trigger element not found on page');
        break;
      }
      await el.click();
      attempts += 1;
      clicked = true;
      debugLog('clicked trigger, attempt', i + 1);
      // small wait for any immediate requests to fire
       
      await page.waitForTimeout(250);
      if (intercepted) break;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      errors.push(msg);
      debugLog('trigger click failed', msg);
       
      await page.waitForTimeout(200);
    }
  }

  if (!clicked) debugLog('never clicked trigger element');

  // wait for intercepted or timeout using Promise.race
  const interceptedPromise = new Promise<boolean>((resolve) => {
    const iv = setInterval(() => {
      if (intercepted) {
        clearInterval(iv);
        resolve(true);
      }
    }, 150);
  });

  const timeoutPromise = new Promise<boolean>((resolve) => setTimeout(() => resolve(false), opts.timeout));
  const ok = await Promise.race([interceptedPromise, timeoutPromise]);
  if (!ok) debugLog('timed out waiting for ingest POST');

  // If no network POST was observed, try to read the content-script DOM mirror
  // or the page-visible events array as a fallback. This helps tests where the
  // extension persists to storage or mirrors events instead of issuing a network
  // POST that the runner can intercept.
  if (!intercepted) {
    try {
      debugLog('probing page for DOM mirror or window events as fallback');
      const pageCaptured = await page.evaluate(() => {
        try {
          // Check window-visible events array first
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          const w = (window as any).__dcmaarEvents as unknown[] | undefined;
          if (Array.isArray(w) && w.length > 0) return { type: 'windowEvents', events: w };
          // Then check DOM mirror container
          const el = document.getElementById('__dcmaar_events_container');
          if (el && el.children && el.children.length > 0) {
            const last = el.children[el.children.length - 1];
            try { return { type: 'domMirror', data: JSON.parse(last.textContent || 'null') }; } catch { return { type: 'domMirror', data: last.textContent || null }; }
          }
        } catch (e) {
          return null;
        }
        return null;
      });
      if (pageCaptured) {
        intercepted = true;
        captured = pageCaptured;
        debugLog('fallback captured via page mirror', pageCaptured);
      }
    } catch (e) {
      debugLog('page probe failed', e instanceof Error ? e.message : String(e));
    }
  }

  const finishedAt = Date.now();
  const durationMs = finishedAt - startedAt;

  // collect extra metadata
  let browserVersion: string | undefined;
  let userAgent: string | undefined;
  try {
    const b = context.browser();
    // @ts-expect-error - playwright types may not expose version() directly on the browser instance
    browserVersion = (await b.version()) || undefined;
    userAgent = await page.evaluate(() => navigator.userAgent).catch(() => undefined);
  } catch {
    // ignore
  }
  const nodeVersion = process.version;
  const platform = process.platform;
  const osType = os.type();
  const osRelease = os.release();
  const envSnapshot: Record<string, string | undefined> = {
    CI: process.env.CI,
    GITHUB_ACTIONS: process.env.GITHUB_ACTIONS,
    HOST: opts.host,
    PORT: String(opts.port),
  };

  // write the captured payload (legacy behavior)
  if (opts.out && typeof opts.out === 'string') {
    try {
      fs.mkdirSync(path.dirname(opts.out), { recursive: true });
      fs.writeFileSync(opts.out, JSON.stringify(captured, null, 2));
      console.log('Wrote captured payload to', opts.out);
    } catch (e) {
      console.error('Failed to write output file', e);
    }
  }

  // write a richer machine-readable report if requested
  const report: Record<string, unknown> = {
    startedAt,
    finishedAt,
    durationMs,
    host: opts.host,
    port: opts.port,
    intercepted,
    attempts,
    errors,
    captured,
  };
  // extend report with environment and browser metadata
  Object.assign(report, {
    browserVersion,
    userAgent,
    nodeVersion,
    platform,
    userDataDir,
    extensionPath,
    env: envSnapshot,
  });

  // append extra metadata
  Object.assign(report, {
    osType,
    osRelease,
    consoleSnapshot,
    browserArgs: browserArgs,
  });

  // if we did not intercept, capture a screenshot (both file and base64 in report)
  let screenshotBase64: string | undefined;
  let screenshotPath: string | undefined;
  if (!intercepted) {
    try {
      const buf = await page.screenshot({ fullPage: true }) as Buffer;
      screenshotPath = path.resolve(projectRoot, 'tmp', `e2e-screenshot-${Date.now()}.png`);
      fs.mkdirSync(path.dirname(screenshotPath), { recursive: true });
      fs.writeFileSync(screenshotPath, buf);
      screenshotBase64 = buf.toString('base64');
      console.log('Wrote screenshot to', screenshotPath);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      errors.push(`screenshot:${msg}`);
      debugLog('screenshot capture failed', msg);
    }
  }

  if (screenshotBase64) report.screenshotBase64 = screenshotBase64;
  if (screenshotPath) report.screenshotPath = screenshotPath;
  if (screenshotBase64) report.screenshotMime = 'image/png';
  if (opts.report && typeof opts.report === 'string') {
    try {
      fs.mkdirSync(path.dirname(opts.report), { recursive: true });
      fs.writeFileSync(opts.report, JSON.stringify(report, null, 2));
      console.log('Wrote runner report to', opts.report);
    } catch (e) {
      console.error('Failed to write report file', e);
    }
  }

  await context.close();
  return { code: intercepted ? 0 : 3, captured };
}

async function main(): Promise<number> {
  const opts = parseArgs(process.argv.slice(2));
  const result = await runPlaywright(opts);
  if (result.captured) console.log('Captured payload:', result.captured);
  return result.code;
}

void (async () => {
  const code = await main();
  process.exit(code);
})();
 