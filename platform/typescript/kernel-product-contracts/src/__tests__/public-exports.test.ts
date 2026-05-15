import { describe, expect, it } from "vitest";
import {
  isKernelPluginLifecycleHook,
  isProductUnit,
  isProductUnitIntent,
  isKernelLifecycleEvent,
  isKernelProviderMode,
  isSemanticArtifactReference,
  type KernelLifecycleEvent,
  type KernelLifecycleProviderContext,
  type KernelPlugin,
  type ProductUnit,
  type ProductUnitHealthSnapshot,
  type ProductUnitIntent,
  type RegistryProvider,
  type SemanticArtifactReference,
} from "../index";

describe("public package exports", () => {
  it("exports stable ProductUnit, provider, event, health, plugin, and artifact intelligence contracts", () => {
    const productUnit: ProductUnit = {
      schemaVersion: "1.0.0",
      id: "sample",
      name: "Sample",
      kind: "business-product",
      registryProviderRef: { providerId: "ghatana-file-registry" },
      sourceProviderRef: { providerId: "github" },
      surfaces: [
        {
          id: "sample-web",
          type: "web",
          implementationStatus: "planned",
        },
      ],
    };

    const intent: ProductUnitIntent = {
      schemaVersion: "1.0.0",
      intentId: "intent-sample",
      intentType: "create",
      scope: {
        tenantId: "tenant-sample",
        workspaceId: "workspace-sample",
        projectId: "project-sample",
      },
      producer: {
        id: "yappc",
        type: "yappc",
        correlationId: "corr-sample",
      },
      target: {
        registryProvider: "ghatana-file-registry",
        sourceProvider: "github",
      },
      productUnit: {
        id: productUnit.id,
        name: productUnit.name,
        kind: productUnit.kind,
        surfaces: productUnit.surfaces,
      },
    };

    const provider: RegistryProvider = {
      providerId: "sample-registry",
      version: "1.0.0",
      capabilities: ["registry-read"],
      getProductUnit: async () => productUnit,
      listProductUnits: async () => [productUnit],
      listProductUnitsByKind: async () => [productUnit],
      validateProductUnit: async () => ({ valid: true, errors: [] }),
    };

    const event: KernelLifecycleEvent = {
      metadata: {
        eventId: "event-1",
        schemaVersion: "1.0.0",
        eventType: "lifecycle.manifest.written",
        productUnitId: productUnit.id,
        runId: "run-1",
        phase: "build",
        timestamp: "2026-01-01T00:00:00.000Z",
        source: "test",
        correlationId: "corr-sample",
      },
      payload: {
        manifestType: "lifecycle-plan",
        path: ".kernel/out/run-1/lifecycle-plan.json",
        required: true,
        status: "written",
      },
    };

    const lifecycleContext: KernelLifecycleProviderContext = {
      mode: "bootstrap",
    };

    const health: ProductUnitHealthSnapshot = {
      productUnitId: productUnit.id,
      status: "unknown",
      surfaces: [],
      lifecycleStatus: "unknown",
      snapshotAt: "2026-01-01T00:00:00.000Z",
    };

    const plugin: KernelPlugin = {
      pluginId: "kernel.sample",
      kind: "platform-plugin",
      capabilities: [],
      lifecycleHooks: [],
      execute: async () => ({
        success: true,
        message: "ok",
        durationMs: 0,
      }),
    };

    const semanticArtifact: SemanticArtifactReference = {
      schemaVersion: "1.0.0",
      evidenceId: "evidence-semantic-1",
      evidenceType: "semantic-artifact-reference",
      tenantId: "tenant-sample",
      workspaceId: "workspace-sample",
      projectId: "project-sample",
      confidence: 0.9,
      provenanceRefs: ["prov:scan-1"],
      createdAt: "2026-01-01T00:00:00.000Z",
      createdBy: "yappc-artifact-intelligence",
      correlationId: "corr-sample",
      productUnitId: productUnit.id,
      privacyClassification: "internal",
      retention: {
        expiresAt: "2026-02-01T00:00:00.000Z",
      },
      artifactId: "artifact:sample-web",
      artifactKind: "ui-route",
      displayName: "Sample web route",
      semanticTags: ["studio", "sample"],
    };

    expect(isProductUnit(productUnit)).toBe(true);
    expect(isProductUnitIntent(intent)).toBe(true);
    expect(provider.providerId).toBe("sample-registry");
    expect(event.metadata.eventType).toBe("lifecycle.manifest.written");
    expect(isKernelLifecycleEvent(event)).toBe(true);
    expect(isKernelProviderMode(lifecycleContext.mode)).toBe(true);
    expect(health.status).toBe("unknown");
    expect(plugin.kind).toBe("platform-plugin");
    expect(isKernelPluginLifecycleHook("onProductValidated")).toBe(true);
    expect(isSemanticArtifactReference(semanticArtifact)).toBe(true);
  });
});
