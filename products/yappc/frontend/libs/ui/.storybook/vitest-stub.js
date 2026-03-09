// Minimal stub for vitest used only in Storybook preview.
// Exports a tiny subset of the API that other libraries may import
// so they don't try to execute test-runner code in the browser preview.
export const describe = () => {};
export const it = () => {};
export const expect = () => ({
  toBe: () => {},
  toEqual: () => {},
});
export const vi = { fn: () => () => {} };
export default { describe, it, expect, vi };
