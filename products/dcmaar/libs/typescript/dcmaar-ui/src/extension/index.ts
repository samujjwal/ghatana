/**
 * @dcmaar/ui — extension sub-module
 *
 * Consolidated from the former @dcmaar/browser-extension-ui package.
 *
 * @doc.type module
 * @doc.purpose Browser extension UI theme types and defaults for DCMAAR
 * @doc.layer product
 * @doc.pattern Facade
 */

// Theme types and defaults — migrated from @dcmaar/browser-extension-ui
export interface BrowserExtensionTheme {
  primaryColor: string;
  accentColor: string;
  backgroundColor: string;
}

export const defaultBrowserExtensionTheme: BrowserExtensionTheme = {
  primaryColor: '#1E40AF',
  accentColor: '#F97316',
  backgroundColor: '#0F172A',
};

export const getTheme = (): BrowserExtensionTheme => defaultBrowserExtensionTheme;
