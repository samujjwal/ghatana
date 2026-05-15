# Artifacts

## Current Contract

Lifecycle artifacts are recorded through `LifecycleManifestWriter` in `@ghatana/kernel-lifecycle`. The writer produces lifecycle result, gate, artifact, deployment, verify health, health snapshot, and event manifests under the run output directory.

Artifact manifests are lifecycle truth. Required artifact manifest writes must fail when a required artifact cannot be fingerprinted or persisted through a required provider.

## Manifest Fields

Artifact manifests include:

- `schemaVersion`
- `productUnitId`
- `runId`
- `correlationId`
- `providerMode`
- artifact entries with type, surface, path or image ref, expected/found state, produced-by metadata, and fingerprint evidence

Studio and API clients should read manifest refs from lifecycle results first. Latest pointer files are a bootstrap/local convenience and are not the platform source of truth.

## Fingerprints

File artifacts use real SHA-256 fingerprints from file bytes. Directory artifacts use a deterministic directory digest:

1. Walk files recursively.
2. Sort relative paths.
3. Hash each file.
4. Hash the ordered tuple of relative path, size, and file hash into one aggregate digest.

Container image refs preserve an existing digest when available. Required container artifacts fail when a required digest cannot be resolved.

## Deployment Linkage

Deployment manifests link deploy and verify evidence back to the same product unit, run ID, correlation ID, environment, target, services, health URLs when safe to display, evidence refs, and rollback readiness. Verify health reports include:

- `schemaVersion`
- `productUnitId`
- `runId`
- `correlationId`
- `environment`
- `status`
- `checkedAt`
- `checks`
- `evidenceRefs`

This keeps Studio, CLI, gateway, and future Data Cloud provider reads aligned on the same lifecycle run truth.

## Privacy And Retention

Provider write options can carry privacy classification and retention metadata. Artifact, health, runtime truth, provenance, and memory providers should preserve this metadata when present and return safe relative refs to UI/API surfaces.

## Validation

```bash
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm check:kernel-lifecycle-truth
pnpm check:digital-marketing-lifecycle-pilot --smoke
```
