import { describe, expect, it } from "vitest";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import {
  bootstrapCompatibleAutoPreset,
  exportCompatibleAutoPreset,
  getAutoPresetNormalizationSummary,
  listCompatibleAutoPresets,
  resolveCompatibleAutoPreset,
} from "./preset-compatibility";
import {
  autoSimulationService,
  getLegacyAutoRuntimeSummary,
  getSimulationPresetById,
  listLegacyRuntimePresetSummaries,
} from "./index";

describe("auto preset compatibility", () => {
  it("lists both curated and legacy-compatible auto presets", () => {
    const results = listCompatibleAutoPresets();

    expect(results.some((preset) => preset.source === "legacy_auto")).toBe(true);
    expect(
      results.some((preset) => preset.source === "curated_starter"),
    ).toBe(true);
  });

  it("supports filtered auto preset search over curated compatibility entries", () => {
    const results = listCompatibleAutoPresets({
      domain: "biology",
      query: "mitosis",
      source: "curated_starter",
      bootstrapOnly: true,
    });

    expect(results).toHaveLength(1);
    expect(results[0]?.starterId).toBe("starter-mitosis-phases");
  });

  it("resolves curated starter aliases before falling back to raw legacy presets", () => {
    const curated = resolveCompatibleAutoPreset("preset-gas-laws");
    const legacy = resolveCompatibleAutoPreset("preset-conservation-energy");

    expect(curated?.source).toBe("curated_starter");
    expect(curated?.starterId).toBe("starter-ideal-gas-law");
    expect(legacy?.source).toBe("legacy_auto");
    expect(legacy?.bootstrapSupported).toBe(true);
  });

  it("returns normalization summary with unresolved legacy ids", () => {
    const summary = getAutoPresetNormalizationSummary();

    expect(summary.legacyPresetCount).toBeGreaterThan(0);
    expect(summary.curatedStarterCount).toBeGreaterThan(0);
    expect(summary.legacyAliasesCovered).toBeGreaterThan(0);
    expect(summary.unresolvedLegacyPresetIds).toContain(
      "preset-conservation-energy",
    );
  });

  it("bootstraps and exports normalized auto preset aliases through starters", () => {
    const manifest = bootstrapCompatibleAutoPreset({
      presetRef: "preset-photosynthesis",
      tenantId: "tenant-1" as TenantId,
      authorId: "user-1" as UserId,
      manifestId: "auto-photosynthesis",
    });
    const exported = exportCompatibleAutoPreset({
      presetRef: "preset-photosynthesis",
      format: "webxr",
      tenantId: "tenant-1" as TenantId,
    });

    expect(manifest?.id).toBe("auto-photosynthesis");
    expect(manifest?.tenantId).toBe("tenant-1");
    expect(exported?.starterId).toBe("starter-photosynthesis-cycle");
    expect((exported?.packageData as { format?: string }).format).toBe("webxr");
  });

  it("bootstraps unresolved legacy presets into governed compatibility manifests", () => {
    const manifest = bootstrapCompatibleAutoPreset({
      presetRef: "preset-conservation-energy",
      tenantId: "tenant-2" as TenantId,
      authorId: "user-2" as UserId,
      manifestId: "legacy-energy",
    });

    expect(manifest?.id).toBe("legacy-energy");
    expect(manifest?.tenantId).toBe("tenant-2");
    expect(manifest?.domain).toBe("PHYSICS");
    expect(manifest?.initialEntities.length).toBeGreaterThan(0);
    expect(manifest?.steps.length).toBeGreaterThan(0);
  });

  it("exports unresolved legacy presets through the same compatibility surface", () => {
    const exported = exportCompatibleAutoPreset({
      presetRef: "preset-conservation-energy",
      format: "unity",
      tenantId: "tenant-2" as TenantId,
    });

    expect(exported?.source).toBe("legacy_auto");
    expect(exported?.presetId).toBe("preset-conservation-energy");
    expect(exported?.starterId).toBeUndefined();
    expect((exported?.packageData as { format?: string }).format).toBe("unity");
  });

  it("reports raw legacy runtime debt by retirement status and domain", () => {
    const summary = getLegacyAutoRuntimeSummary();
    const compatibilityOnly = listLegacyRuntimePresetSummaries({
      status: "legacy_compatibility_only",
      domain: "physics",
    });

    expect(summary.totalPresets).toBeGreaterThan(0);
    expect(summary.governedStarterAvailable).toBeGreaterThan(0);
    expect(summary.compatibilityOnly).toBeGreaterThan(0);
    expect(summary.byDomain.physics?.total).toBeGreaterThan(0);
    expect(
      compatibilityOnly.some((preset) => preset.id === "preset-conservation-energy"),
    ).toBe(true);
  });

  it("keeps canonical raw preset lookups available for compatibility fallbacks", () => {
    const preset = getSimulationPresetById("preset-conservation-energy");

    expect(preset?.name).toBe("Conservation of Energy");
  });

  it("prefers governed starter-backed presets in domain listing and search", () => {
    const byDomain = autoSimulationService.getPresetsByDomain("biology");
    const searched = autoSimulationService.searchPresets("photosynthesis");

    expect(byDomain.some((preset) => preset.id === "preset-photosynthesis")).toBe(true);
    expect(searched[0]?.id).toBe("preset-photosynthesis");
  });

  it("keeps raw fallback discovery limited to legacy-compatibility-only presets", () => {
    const chemistry = autoSimulationService.getPresetsByDomain("chemistry");
    const physics = autoSimulationService.getPresetsByDomain("physics");

    expect(chemistry.some((preset) => preset.id === "preset-gas-laws")).toBe(true);
    expect(chemistry.filter((preset) => preset.id === "preset-gas-laws")).toHaveLength(1);
    expect(
      physics.some((preset) => preset.id === "preset-conservation-energy"),
    ).toBe(true);
  });
});
