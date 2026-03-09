// Minimal shim to satisfy TypeScript when @types/minimatch is not present.
// Prevents TS2688 in environments where @types/minimatch isn't resolvable.
declare module 'minimatch' {
  interface IOptions { [key: string]: unknown }
  function minimatch(path: string, pattern: string, options?: IOptions): boolean;
  export = minimatch;
}
