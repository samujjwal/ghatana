import { Page, FrameLocator } from '@playwright/test';
/** Resolve the Storybook URL for tests. Prefer STORYBOOK_URL, fallback to STORYBOOK_PORT. */
export declare function getStoryUrl(pathname?: string): string;
/** Check if Storybook is running and accessible */
export declare function checkStorybookAvailable(page: Page): Promise<boolean>;
/** Return a frameLocator for the Storybook preview iframe. */
export declare function getPreviewFrame(page: Page): FrameLocator;
/** Wait for the canvas to be ready inside the preview iframe. */
export declare function waitForCanvasReady(frame: FrameLocator, timeout?: number): Promise<string>;
export declare const OUT_DIR: string;
//# sourceMappingURL=playwright-helpers.d.ts.map