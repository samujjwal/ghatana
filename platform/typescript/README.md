# platform/typescript

Shared TypeScript packages for the Ghatana platform.

## Dependency direction

Package dependencies should flow from lower-level primitives to higher-level UI packages.

Preferred direction:

1. `@ghatana/platform-utils`
2. `@ghatana/tokens`
3. `@ghatana/theme`
4. `@ghatana/design-system`
5. higher-level feature packages such as `@ghatana/charts`, `@ghatana/ui-integration`, `@ghatana/platform-shell`

## Rules

- lower layers must not depend on higher layers
- `tokens` stays framework-agnostic
- `theme` may depend on `tokens`, but not on `design-system`
- `design-system` may depend on `platform-utils` and peer on `theme` and `tokens`
- integration packages may compose `design-system`, `theme`, `tokens`, and `platform-utils`

## Documentation policy

If a package includes `README.md` in its `files` list, the package directory must contain a real README. That file is part of the published package contract.

## Packaging guidance

- keep exports explicit and tree-shakeable
- prefer peer dependencies for host UI frameworks when the package should not bundle them
- document expected dependency direction in the package README when the package sits high in the graph
