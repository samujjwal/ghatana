# Product Toolchain Adapter Specification

This document defines the contract for toolchain adapters.

## Adapter Interface

Toolchain adapters implement a common interface to abstract build tools:

```typescript
interface ToolchainAdapter {
  readonly id: string;
  readonly name: string;
  
  // Phase operations
  dev(surface: Surface, config: SurfaceConfig): Promise<AdapterResult>;
  build(surface: Surface, config: SurfaceConfig): Promise<AdapterResult>;
  test(surface: Surface, config: SurfaceConfig): Promise<AdapterResult>;
  validate(surface: Surface, config: SurfaceConfig): Promise<AdapterResult>;
  
  // Artifact operations
  package(surface: Surface, config: SurfaceConfig, targets: ArtifactTarget[]): Promise<ArtifactManifest>;
}
```

## Adapter Registration

Adapters are registered in the toolchain adapter registry:

```json
{
  "adapters": {
    "gradle-java-service": {
      "package": "@ghatana/kernel-toolchains",
      "module": "./adapters/gradle-java-service"
    },
    "pnpm-vite-react": {
      "package": "@ghatana/kernel-toolchains",
      "module": "./adapters/pnpm-vite-react"
    }
  }
}
```

## Adapter Responsibilities

- Execute tool-specific commands (Gradle, pnpm, etc.)
- Parse tool output into standard AdapterResult format
- Emit artifact manifests during package phase
- Validate tool availability before execution
- Handle tool-specific error conditions

## Adapter Constraints

- Adapters must not introduce product-specific logic
- Adapters must not call Kernel lifecycle APIs (avoid circular dependencies)
- Adapters must validate inputs before invoking tools
- Adapters must surface tool errors with actionable messages

## Built-in Adapters

### gradle-java-service
- Handles Java backend services built with Gradle
- Supports dev, build, test, validate, and package phases
- Emits JAR and container artifacts

### pnpm-vite-react
- Handles React web applications built with pnpm and Vite
- Supports dev, build, test, validate, and package phases
- Emits static-web-bundle artifacts
