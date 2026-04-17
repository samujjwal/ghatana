# YAPPC Custom ESLint Config

Workspace-local ESLint configuration package for YAPPC frontend packages and applications.

## Scope
- Shared lint presets and rule composition.
- Product-local conventions that extend base workspace linting.
- Reusable configuration for apps, libraries, and tooling packages.

## Key Areas
- Package exports for ESLint configuration.
- Shared rule composition and overrides.
- Integration with frontend package build and check flows.

## Audit Notes
- Keep config focused on enforceable team conventions.
- Avoid overlapping or conflicting presets when composing downstream configs.