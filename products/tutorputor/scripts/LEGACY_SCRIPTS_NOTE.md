# Legacy Scripts

The following scripts have been moved to `legacy/` as they are superseded by the new `ttr` CLI:

## Replaced by `ttr` Commands

| Legacy Script | Replacement |
|--------------|-------------|
| `run-dev.sh` | `ttr dev` |
| `run-dev-no-seed.sh` | `ttr dev --no-seed` |
| `run-seed.sh` | `ttr seed` |

## New CLI (`bin/`)

All development, testing, and deployment operations are now handled by the `ttr` command:

```bash
# Add bin/ to PATH or use directly
./bin/ttr dev
./bin/ttr test
./bin/ttr doctor
```

See [bin/README.md](../bin/README.md) for full documentation.

## Remaining Active Scripts

| Script | Purpose |
|--------|---------|
| `analyze-bundle.sh` | Bundle analysis |
| `audit-any-types.ts` | TypeScript audit |
| `build-ai-agents-docker.sh` | Docker build |
| `check-no-legacy-references.sh` | Legacy check |
| `ci-check-contract-drift.sh` | CI checks |
| `ci-check-fastify.sh` | CI checks |
| `cleanup-empty-dirs.sh` | Cleanup utility |
| `deploy-production.sh` | Production deploy |

These will be migrated to `ttr` subcommands in future releases.
