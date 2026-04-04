// Minimal ESLint flat config for @ghatana/canvas.
// This package uses TypeScript + Vitest. TypeScript type-checking and strict
// compilation (tsc --noEmit) are the primary quality gates; ESLint rules are
// applied at the workspace level via the root turbo lint pipeline.
/** @type {import('eslint').Linter.Config[]} */
export default [
  {
    ignores: ["dist/**", "node_modules/**"],
  },
];
