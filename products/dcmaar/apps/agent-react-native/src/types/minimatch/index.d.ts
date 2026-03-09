// Minimal package-style type definition shim for minimatch
declare module 'minimatch' {
  interface IOptions { [key: string]: unknown }
  function minimatch(path: string, pattern: string, options?: IOptions): boolean;
  export = minimatch;
}
