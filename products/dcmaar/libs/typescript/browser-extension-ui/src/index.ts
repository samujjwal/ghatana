// Placeholder implementation for browser extension UI components.
// This ensures the package compiles successfully until real components are added.

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
