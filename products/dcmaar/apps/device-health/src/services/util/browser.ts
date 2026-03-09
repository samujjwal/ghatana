// Utility helpers for checking availability of browser storage APIs

export function hasBrowserStorage(): boolean {
  try {
    const g = globalThis as unknown as { chrome?: any; browser?: any };
    const br = g.browser || g.chrome;
    return !!(br && br.storage && br.storage.local);
  } catch {
    return false;
  }
}
