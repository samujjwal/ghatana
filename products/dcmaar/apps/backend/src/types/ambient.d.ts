/**
 * Ambient module declarations for packages that lack their own type definitions.
 *
 * This file must NOT contain any top-level import/export statements so that
 * TypeScript treats it as a global ambient declaration file.
 *
 * @doc.type declarations
 * @doc.purpose Ambient type shims for untyped third-party packages
 * @doc.layer backend
 * @doc.pattern Type Declaration
 */

// uuid v8 does not ship its own type declarations.
// Provides a minimal shape that covers all usages in src/*.
declare module 'uuid' {
  export function v4(options?: { random?: Uint8Array; rng?: () => Uint8Array }): string;
  export function v1(): string;
  export function validate(uuid: string): boolean;
  export function parse(uuid: string): Uint8Array;
  export function stringify(arr: Uint8Array): string;
}
