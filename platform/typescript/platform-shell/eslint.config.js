// Minimal ESLint flat config for @ghatana/platform-shell.
// This package uses TypeScript + Jotai atoms. TypeScript type-checking and
// strict compilation (tsc --noEmit) are the primary quality gates.
/** @type {import('eslint').Linter.Config[]} */
export default [
  {
    ignores: ["dist/**", "node_modules/**"],
  },
];
