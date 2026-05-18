# @ghatana/kernel-toolchains

Kernel toolchain adapters for product lifecycle.

## Purpose

Provides typed, validated adapters for the toolchains (build tools, test runners, package managers, linters, runtimes) invoked during product lifecycle phase execution. Toolchain adapters are the boundary between the kernel lifecycle engine and the underlying build systems of each product.

## Key Concepts

- **ToolchainAdapter** — interface for wrapping a specific toolchain (Gradle, pnpm, Vitest, etc.)
- **ToolchainAdapterRegistry** — registry of available adapters, keyed by toolchain type
- **ToolchainOutputValidator** — validates adapter output against the declared output contract
- **Built-in adapters** — adapters for pnpm, Vitest, Gradle, ESLint, TypeScript, and other common toolchains

## Usage

```ts
import { ToolchainAdapterRegistry } from "@ghatana/kernel-toolchains";

const registry = new ToolchainAdapterRegistry();
const gradleAdapter = registry.get("gradle");
```

## Directory Structure

```
src/
  ToolchainAdapter.ts           # Core adapter interface
  ToolchainAdapterRegistry.ts   # Registry implementation
  ToolchainOutputValidator.ts   # Output validation
  adapters/                     # Built-in toolchain adapters
  artifacts/                    # Toolchain artifact contracts
  execution/                    # Execution coordination helpers
  testing/                      # Testing utilities for custom adapters
```

## Ownership

Platform Kernel Engineering. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
