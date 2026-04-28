# core/cli-tools

Command-line tooling module for YAPPC knowledge-graph operations.

## Current Status

- Module is active and included in `products/yappc/settings.gradle.kts` as `:core:cli-tools`.
- Current executable entrypoint: `com.ghatana.yappc.cli.KgCli`.
- Placeholder subcommands were intentionally removed during re-audit to avoid test-theatre/false capability claims.

## Scope

- Provide a stable CLI shell for knowledge-graph operations.
- Delegate real KG behavior to `:products:yappc:core:knowledge-graph` services.

## Build

- Uses plugin: `java-module`
- Declared in: `build.gradle.kts`

## Operator Note

Running the CLI currently prints guidance and help output. Real command behavior should be added only when backed by implemented domain services and test coverage.
