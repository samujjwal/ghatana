// Minimal ambient types to satisfy missing @types/minimatch during types-only build
declare module 'minimatch' {
  function minimatch(path: string, pattern: string, options?: unknown): boolean;
  namespace minimatch {
    function filter(pattern: string, options?: unknown): (path: string) => boolean;
  }
  export = minimatch;
}
