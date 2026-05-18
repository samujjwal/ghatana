# @ghatana/kernel-artifacts

Kernel artifact manifest system for product lifecycle.

## Purpose

Manages the creation, registration, validation, and resolution of build artifacts produced during product lifecycle phases. Artifacts represent verifiable build outputs (bundles, containers, JARs, Gradle tasks, etc.) that gate promotion and release.

## Key Concepts

- **Artifact** — a named, content-addressed build output associated with a product phase
- **ArtifactManifest** — a complete manifest of artifacts for a product at a given phase
- **ArtifactFingerprint** — SHA-256 content digest used for verification
- **ArtifactResolver** — resolves artifact references by name and version
- **ArtifactRegistry** — central registry of all known artifacts for a run

## Usage

```ts
import { ArtifactManifest, ArtifactResolver } from "@ghatana/kernel-artifacts";
```

## Directory Structure

```
src/
  domain/         # Core artifact domain types
  fingerprint/    # Content-addressing and verification
  registry/       # Artifact registration
  resolver/       # Artifact lookup and resolution
  storage/        # Artifact persistence adapters
  validator/      # Artifact validation rules
```

## Ownership

Platform Kernel Engineering. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
