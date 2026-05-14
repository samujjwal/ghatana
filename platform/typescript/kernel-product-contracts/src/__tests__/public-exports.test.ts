import { describe, expect, it } from "vitest";
import {
  isKernelPluginLifecycleHook,
  isProductUnit,
  isProductUnitIntent,
  type KernelLifecycleEvent,
  type KernelPlugin,
  type ProductUnit,
  type ProductUnitHealthSnapshot,
  type ProductUnitIntent,
  type RegistryProvider,
} from "../index";

describe("public package exports", () => {
  it("exports stable ProductUnit, provider, event, health, and plugin contracts", () => {
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
        eventType: "lifecycle.plan.created",
        productUnitId: productUnit.id,
        runId: "run-1",
        phase: "build",
        timestamp: "2026-01-01T00:00:00.000Z",
        source: "test",
        correlationId: "corr-sample",
      },
      payload: {
        phase: "build",
        status: "planned",
        timestamp: "2026-01-01T00:00:00.000Z",
      },
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

    expect(isProductUnit(productUnit)).toBe(true);
    expect(isProductUnitIntent(intent)).toBe(true);
    expect(provider.providerId).toBe("sample-registry");
    expect(event.metadata.eventType).toBe("lifecycle.plan.created");
    expect(health.status).toBe("unknown");
    expect(plugin.kind).toBe("platform-plugin");
    expect(isKernelPluginLifecycleHook("onProductValidated")).toBe(true);
  });
});
