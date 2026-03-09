// Minimal shim to satisfy TypeScript when @types/minimatch is not present.
// See notes in browser-extension shim. This file prevents TS2688 in this package.
declare module 'minimatch' {
  interface IOptions { [key: string]: unknown }
  function minimatch(path: string, pattern: string, options?: IOptions): boolean;
  export = minimatch;
}
