import type { Page, ConsoleMessage } from '@playwright/test';

export interface CapturedConsoleMessage {
  type: string;
  text: string;
  location?: string;
}

/**
 * Captures console messages and page errors while navigating to a URL and
 * returns collected logs and the serialized page HTML.
 */
export async function captureConsoleAndSnapshot(page: Page, url = '/', waitFor = 'networkidle') {
  const messages: CapturedConsoleMessage[] = [];

  const onConsole = (msg: ConsoleMessage) => {
    const location = msg.location();
    messages.push({
      type: msg.type(),
      text: msg.text(),
      location: location?.url ? `${location.url}:${location.lineNumber}:${location.columnNumber}` : undefined,
    });
  };

  const onPageError = (err: Error) => {
    messages.push({ type: 'pageerror', text: String(err?.stack ?? err?.message ?? err) });
  };

  page.on('console', onConsole);
  page.on('pageerror', onPageError as unknown);

  try {
    await page.goto(url, { waitUntil: waitFor as unknown });

    // Give some time for any background client work to settle (mocked services, websockets, etc.)
    await page.waitForTimeout(250);

    const html = await page.content();

    return { logs: messages, html };
  } finally {
    page.off('console', onConsole);
    page.off('pageerror', onPageError as unknown);
  }
}
