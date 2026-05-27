# Kernel Handoff

YAPPC hands generated product work to Kernel by producing a `ProductUnitIntent`. The intent is the only handoff payload YAPPC owns; Kernel owns lifecycle execution, runtime truth, gates, registry semantics, and final product-unit state.

## Boundary

| Owner | Owns | Must not own |
| --- | --- | --- |
| YAPPC | Intent capture, Shape/Generate state, `ProductUnitIntent` export, validation, API/CLI handoff, YAPPC audit/evidence refs | Kernel lifecycle execution, Kernel registry mutation, Kernel truth storage |
| Kernel | ProductUnit contract values, lifecycle truth, runtime gates, product-unit acceptance/update flow | YAPPC workspace/project authoring state |
| Data Cloud | Tenant-scoped YAPPC records and Kernel truth records exposed to YAPPC | Local filesystem truth in production runtime |

The backend path is implemented by `KernelProductUnitHandoffService`, which reuses `ProductUnitIntentExporter` and `ProductUnitIntentValidationService`. The scaffold CLI path is implemented by `CreateCommand` for the `kernel-product-unit` target. Both paths validate against `ProductUnitKernelContractRegistry`, whose values are imported from the Kernel public contract resource rather than copied into local constants.

## Contract Values

Canonical values come from `ProductUnitKernelContractRegistry`:

| Field | Default used by YAPPC | Contract behavior |
| --- | --- | --- |
| `targetType` | `kernel-product-unit` | Exporter maps this to Kernel `productUnitKind` and validation rejects unknown target types. |
| `runtimeProvider` | `ghatana-file-registry` | Validation rejects providers outside the imported Kernel provider set. |
| `lifecycleProfile` | `standard-web-api-product` | Validation rejects profiles outside the imported Kernel profile set. |
| `surfaces` | Caller supplied | Validation rejects unknown surfaces before any Kernel handoff is reported as valid. |
| `sourcePhase` | `generate` | API handoff defaults generated intents to the Generate phase unless the caller supplies a phase. |

Do not add provider, profile, or surface constants to YAPPC services. Add or change values in the Kernel public contract, then let the parity tests protect YAPPC drift.

## CLI Flow

Use the scaffold CLI when a local generation run needs a file that can be inspected or passed to a Kernel-owned consumer:

```powershell
yappc create "Digital Marketing" `
  --target kernel-product-unit `
  --workspace-id workspace-001 `
  --project-id digital-marketing `
  --surface web `
  --surface backend-api `
  --runtime-provider ghatana-file-registry `
  --lifecycle-profile standard-web-api-product `
  --intent-output .yappc/product-unit-intent.yaml
```

If `--intent-output` is omitted, `CreateCommand` writes `.yappc/product-unit-intent.yaml` under the target project root. CLI validation fails when project name, workspace ID, project ID, lifecycle profile, or surface values are missing.

Validation evidence:

| Flow | Evidence |
| --- | --- |
| CLI writes Kernel intent YAML | `CreateCommandKernelProductUnitTest` |
| CLI does not create raw GitHub workflows for Kernel targets | `CICommandKernelProductUnitTest` |
| YAML/JSON output remains schema-shaped | `ProductUnitIntentExporterTest` and `golden/product-unit-intent.standard.yaml` |
| Invalid Kernel values fail closed | `ProductUnitIntentValidationServiceTest` |

## API Flow

Use the backend API when the product shell or lifecycle service needs to generate the Kernel payload from saved YAPPC state:

```powershell
$body = @{
  tenantId = "tenant-1"
  workspaceId = "workspace-001"
  projectId = "digital-marketing"
  projectName = "Digital Marketing"
  surfaces = @("web", "backend-api")
  runtimeProvider = "ghatana-file-registry"
  lifecycleProfile = "standard-web-api-product"
  sourcePhase = "generate"
  metadata = @{
    shapeId = "shape-001"
    generationRunId = "generation-run-001"
  }
  correlationId = "corr-001"
} | ConvertTo-Json -Depth 6

Invoke-RestMethod `
  -Method Post `
  -Uri "$env:YAPPC_API_ORIGIN/api/v1/yappc/generate/product-unit-intent" `
  -ContentType "application/json" `
  -Headers @{
    Authorization = "Bearer $env:YAPPC_TOKEN"
    "X-Correlation-ID" = "corr-001"
  } `
  -Body $body
```

The route is declared as `/api/v1/yappc/generate/product-unit-intent` with operation ID `generateProductUnitIntent` in `api/route-manifest.yaml` and `api/openapi.yaml`. The response contains:

| Field | Meaning |
| --- | --- |
| `intentId` | Stable exported ProductUnitIntent identifier. |
| `productUnitIntent` | Kernel-compatible ProductUnitIntent object. |
| `valid` | `true` only after `ProductUnitIntentValidationService` accepts the payload. |
| `validationErrors` | Contract failures when validation rejects the payload. |
| `correlationId` | Caller correlation ID propagated into handoff metadata. |

Validation evidence:

| Flow | Evidence |
| --- | --- |
| Backend service creates validated handoff payloads | `KernelProductUnitHandoffServiceTest` |
| HTTP route returns a validated payload and rejects invalid surfaces | `GenerationApiControllerTest` |
| Manifest/OpenAPI/backend route parity | `RouteManifestParityTest` |
| Generated frontend clients include the operation | frontend generated-client parity tests |

## Validation

Every handoff path must validate before reporting success:

1. `ProductUnitIntentExporter` builds the typed ProductUnitIntent document and serializes YAML/JSON only after required fields are present.
2. `ProductUnitIntentValidationService` validates the generated document against imported Kernel contract values from `ProductUnitKernelContractRegistry`.
3. `KernelProductUnitHandoffService` throws an export exception when validation fails, causing the API to return an error envelope instead of a fake valid intent.
4. CLI commands return a non-zero exit code for missing required provenance or unsupported contract values.

Focused checks:

```powershell
./gradlew :products:yappc:core:scaffold:api:test `
  --tests "com.ghatana.yappc.cli.CreateCommandKernelProductUnitTest" `
  --tests "com.ghatana.yappc.kernel.ProductUnitIntentExporterTest" `
  --tests "com.ghatana.yappc.kernel.ProductUnitIntentValidationServiceTest" `
  --no-daemon

./gradlew :products:yappc:core:yappc-services:test `
  --tests "com.ghatana.yappc.services.kernel.KernelProductUnitHandoffServiceTest" `
  --tests "com.ghatana.yappc.api.GenerationApiControllerTest" `
  --no-daemon
```

## Kernel Consumption

After generation, pass the ProductUnitIntent file or API response body to the Kernel-owned product-unit create/update entry point. YAPPC does not mutate Kernel registries and does not generate raw Kernel workflows for `kernel-product-unit` targets.

Operationally, the handoff is considered ready to send to Kernel only when:

| Requirement | Evidence |
| --- | --- |
| `valid` is `true` or the CLI command exited `0` | `KernelProductUnitHandoffServiceTest`, `CreateCommandKernelProductUnitTest` |
| Provider/profile/surface values came from the imported Kernel contract | `ProductUnitKernelContractRegistryTest` |
| The payload has a tenant, workspace, project, source phase, surfaces, and correlation metadata where available | `ProductUnitIntentExporterTest`, `GenerationApiControllerTest` |
| Runtime truth after Kernel execution is read from Data Cloud Kernel truth, not the local filesystem provider in production | `DataCloudKernelLifecycleTruthSourceTest`, production truth-source checker |

If Kernel rejects a payload that YAPPC validated, treat that as a contract drift incident. Update the Kernel public contract or the YAPPC importer, then run the contract parity and exporter golden tests before retrying the handoff.

## Operator Checks

| Check | Command |
| --- | --- |
| Handoff docs stay linked to code evidence | `node products/yappc/scripts/check-kernel-handoff-docs.mjs` |
| ProductUnitIntent CLI/export/validation | `./gradlew :products:yappc:core:scaffold:api:test --tests "com.ghatana.yappc.cli.CreateCommandKernelProductUnitTest" --tests "com.ghatana.yappc.kernel.ProductUnitIntentExporterTest" --tests "com.ghatana.yappc.kernel.ProductUnitIntentValidationServiceTest" --no-daemon` |
| Backend API handoff | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.kernel.KernelProductUnitHandoffServiceTest" --tests "com.ghatana.yappc.api.GenerationApiControllerTest" --no-daemon` |
| Route/API parity | `python products/yappc/scripts/generate-api-reference.py --check` |
| Doc evidence links | `node products/yappc/scripts/check-doc-evidence-links.mjs products/yappc/docs` |

## Change Rules

1. Reuse `ProductUnitIntentExporter`, `ProductUnitIntentValidationService`, and `ProductUnitKernelContractRegistry` for all new handoff surfaces.
2. Add new Kernel contract values in Kernel first, then update/import the public contract resource and parity tests.
3. Keep CLI and API examples aligned with route manifest, OpenAPI, and backend handler registration.
4. Preserve tenant/workspace/project/correlation provenance in every handoff.
5. Fail closed on validation or Kernel contract drift; do not emit a handoff as valid when any required contract value is unknown.
