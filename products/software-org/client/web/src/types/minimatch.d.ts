// Local shim to ensure the minimatch module is available to TypeScript in this package
// When `@types/minimatch` is present in devDependencies, this is redundant.
// This exists to help CI or other environments that don't have the @types package.

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

    function Minimatch(pattern: string, options?: IOptions): any;
    function minimatch(path: string, pattern: string, options?: IOptions): boolean;

    export = minimatch;
}
