import type { Page } from '@playwright/test';
export interface CapturedConsoleMessage {
    type: string;
    text: string;
    location?: string;
}
/**
 * Captures console messages and page errors while navigating to a URL and
 * returns collected logs and the serialized page HTML.
 */
export declare function captureConsoleAndSnapshot(page: Page, url?: string, waitFor?: string): Promise<{
    logs: CapturedConsoleMessage[];
    html: string;
}>;
//# sourceMappingURL=headlessUtils.d.ts.map