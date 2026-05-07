import { describe, expect, it } from "vitest";
import {
  getSimulationStarterByLegacyPresetId,
  getSimulationStarterCatalogSummary,
  getSimulationStarterById,
  listSimulationStarters,
  resolveSimulationStarter,
} from "./starter-catalog";

describe("starter catalog", () => {
  it("filters starters by domain and query", () => {
    const results = listSimulationStarters({
      domain: "MEDICINE",
      query: "dose",
    });

    expect(results).toHaveLength(1);
    expect(results[0]?.id).toBe("starter-dose-response");
  });

  it("filters starters by tag", () => {
    const results = listSimulationStarters({ tag: "algorithms" });

    expect(results.map((result) => result.id)).toContain(
      "starter-binary-search",
    );
  });

  it("returns deep-cloned starter manifests", () => {
    const starter = getSimulationStarterById("starter-newton-cart");
    expect(starter).not.toBeNull();

    starter!.manifest.title = "Changed title";

    const fetchedAgain = getSimulationStarterById("starter-newton-cart");
    expect(fetchedAgain?.manifest.title).toBe("Newton Cart Push");
  });

  it("adds full biology and chemistry starter coverage", () => {
    const biology = listSimulationStarters({ domain: "BIOLOGY" });
    const chemistry = listSimulationStarters({ domain: "CHEMISTRY" });

    expect(biology.map((starter) => starter.id)).toEqual(
      expect.arrayContaining([
        "starter-mitosis-phases",
        "starter-photosynthesis-cycle",
        "starter-dna-replication",
        "starter-natural-selection",
        "starter-ecosystem-dynamics",
      ]),
    );
    expect(chemistry.map((starter) => starter.id)).toEqual(
      expect.arrayContaining([
        "starter-ideal-gas-law",
        "starter-reaction-kinetics",
        "starter-electrochemical-cell",
        "starter-molecular-geometry",
        "starter-buffer-titration",
      ]),
    );
  });

  it("adds full medicine and economics starter coverage", () => {
    const medicine = listSimulationStarters({ domain: "MEDICINE" });
    const economics = listSimulationStarters({ domain: "ECONOMICS" });

    expect(medicine.map((starter) => starter.id)).toEqual(
      expect.arrayContaining([
        "starter-cardiac-cycle",
        "starter-action-potential",
        "starter-lung-mechanics",
        "starter-pharmacokinetics",
        "starter-epidemiology-sir",
      ]),
    );
    expect(economics.map((starter) => starter.id)).toEqual(
      expect.arrayContaining([
        "starter-supply-demand-dynamics",
        "starter-market-structures",
        "starter-keynesian-cross",
        "starter-monetary-policy",
        "starter-game-theory",
      ]),
    );
  });

  it("supports new domain-specific search queries", () => {
    expect(
      listSimulationStarters({ domain: "BIOLOGY", query: "mitosis" })[0]?.id,
    ).toBe("starter-mitosis-phases");
    expect(
      listSimulationStarters({ domain: "ECONOMICS", query: "nash" })[0]?.id,
    ).toBe("starter-game-theory");
  });

  it("supports audience filtering and reports catalog summary", () => {
    const professional = listSimulationStarters({ audience: "professional" });
    const summary = getSimulationStarterCatalogSummary();

    expect(professional.map((starter) => starter.id)).toEqual(
      expect.arrayContaining([
        "starter-cardiac-cycle",
        "starter-action-potential",
      ]),
    );
    expect(summary.total).toBeGreaterThan(20);
    expect(summary.byDomain.BIOLOGY).toBeGreaterThan(0);
    expect(summary.legacyPresetCoverage).toBeGreaterThan(10);
  });

  it("resolves legacy preset ids onto curated starters", () => {
    const legacy = getSimulationStarterByLegacyPresetId("preset-gas-laws");
    const resolved = resolveSimulationStarter("preset-newton-first");

    expect(legacy?.id).toBe("starter-ideal-gas-law");
    expect(resolved?.starter.id).toBe("starter-newton-cart");
    expect(resolved?.matchedBy).toBe("legacy_preset");
  });

  it("requires every starter to conform to the canonical simulation manifest contract", () => {
    const starters = listSimulationStarters();

    for (const starter of starters) {
      const { accessibility, canonical, ecd } = starter.manifest;

      expect(accessibility?.altText, starter.id).toBeTruthy();
      expect(accessibility?.screenReaderNarration, starter.id).toBe(true);
      expect(accessibility?.highContrast, starter.id).toBe(true);

      expect(canonical?.seed, starter.id).toEqual(expect.any(Number));
      expect(canonical?.parameterBounds.length, starter.id).toBeGreaterThan(0);
      expect(canonical?.outputs.length, starter.id).toBeGreaterThan(0);
      expect(canonical?.failureStates.length, starter.id).toBeGreaterThan(0);
      expect(canonical?.telemetryEvents.map((event) => event.eventType)).toEqual(
        expect.arrayContaining([
          "sim.start",
          "sim.control.change",
          "sim.snapshot",
          "sim.capture",
          "sim.complete",
        ]),
      );
      expect(canonical?.claimLinks.length, starter.id).toBeGreaterThan(0);

      const claimIds = new Set(ecd?.claims.map((claim) => claim.id) ?? []);
      const evidenceIds = new Set(ecd?.evidence.map((item) => item.id) ?? []);
      const taskIds = new Set(ecd?.tasks.map((task) => task.id) ?? []);

      for (const link of canonical?.claimLinks ?? []) {
        expect(claimIds.has(link.claimId), starter.id).toBe(true);
        expect(link.evidenceIds.every((id) => evidenceIds.has(id)), starter.id).toBe(true);
        expect(link.taskIds.every((id) => taskIds.has(id)), starter.id).toBe(true);
      }
    }
  });
});
