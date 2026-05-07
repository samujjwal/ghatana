# Artifact Compiler Runtime Dependencies

The YAPPC page-builder import and synthesis flows use two runtime pieces:

| Dependency | Default local endpoint | Health check | Required for |
| --- | --- | --- | --- |
| TypeScript artifact compiler package | bundled in `@ghatana/yappc-web-app` via `yappc-artifact-compiler` | package import during web build/test | Local TSX, route, Storybook, and semantic-model extraction helpers |
| YAPPC artifact analyzer HTTP runtime | `http://localhost:8080/api/v1/yappc/artifact` | `http://localhost:8080/health` | Governed source import, artifact graph analysis, residual island analysis, and synthesis orchestration |

## Local Development

Start the artifact analyzer before using governed source import from the page builder. If your analyzer runs on a different host or port, configure:

```bash
VITE_YAPPC_ARTIFACT_API_BASE_URL=http://localhost:8080/api/v1/yappc/artifact
VITE_YAPPC_ARTIFACT_ANALYZER_HEALTH_URL=http://localhost:8080/health
```

The page builder checks the analyzer health endpoint before governed source import. When the runtime is down, the UI blocks the import and shows the required service and health URL instead of silently falling back to local browser extraction.

## Quick Checks

```bash
curl -fsS http://localhost:8080/health
curl -fsS http://localhost:8080/api/v1/yappc/artifact/health
```

The first check validates the analyzer process. The second check is optional and only applies when the artifact API exposes a route-scoped health endpoint in the active runtime.
