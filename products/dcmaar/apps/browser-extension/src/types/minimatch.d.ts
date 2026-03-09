// Minimal shim to satisfy TypeScript when @types/minimatch is not present.
// This avoids TS2688: "Cannot find type definition file for 'minimatch'"
// The real minimatch package provides its own types. This shim is temporary
// and safe: it only provides a module declaration so builds succeed.
declare module 'minimatch' {
  interface IOptions {
    flipNegate?: boolean;
    dot?: boolean;
    nobrace?: boolean;
    noglobstar?: boolean;
    noext?: boolean;
    nocase?: boolean;
    nonegate?: boolean;
    nocomment?: boolean;
    matchBase?: boolean;
  }

  function Minimatch(pattern: string, options?: IOptions): unknown;
  function minimatch(path: string, pattern: string, options?: IOptions): boolean;

  export = minimatch;
}
