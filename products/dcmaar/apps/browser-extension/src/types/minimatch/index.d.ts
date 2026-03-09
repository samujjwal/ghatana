// Minimal package-style type definition so TS can resolve the 'minimatch' type library
// when a dependency references it via a triple-slash or "types" entry.
declare module 'minimatch' {
  interface IOptions { [key: string]: unknown }
  function minimatch(path: string, pattern: string, options?: IOptions): boolean;
  export = minimatch;
}
