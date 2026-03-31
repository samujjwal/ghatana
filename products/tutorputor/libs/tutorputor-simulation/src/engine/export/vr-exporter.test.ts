import { describe, expect, it } from "vitest";
import { VRSimulationExporter } from "./vr-exporter";
import type { SimulationManifest } from "@tutorputor/contracts/v1/simulation";

function makeManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: "manifest-1" as SimulationManifest["id"],
    version: "1.0.0",
    title: "Cardiac Cycle",
    domain: "MEDICINE",
    authorId: "user-1" as SimulationManifest["authorId"],
    tenantId: "tenant-1" as SimulationManifest["tenantId"],
    canvas: { width: 1280, height: 720 },
    playback: { defaultSpeed: 1 },
    initialEntities: [
      {
        id: "heart" as SimulationManifest["initialEntities"][number]["id"],
        type: "rigidBody",
        x: 200,
        y: 300,
        width: 120,
        height: 80,
        label: "Heart",
        mass: 2,
      },
    ],
    steps: [
      {
        id: "step-1" as SimulationManifest["steps"][number]["id"],
        orderIndex: 0,
        title: "Systole",
        description: "The ventricle contracts.",
        actions: [
          {
            action: "ANNOTATE",
            text: "Contract",
          },
        ],
      },
    ],
    createdAt: new Date("2026-03-30T12:00:00Z").toISOString(),
    updatedAt: new Date("2026-03-30T12:00:00Z").toISOString(),
    schemaVersion: "1.0.0",
    ...overrides,
  };
}

describe("VRSimulationExporter", () => {
  it("exports manifests to WebXR packages", () => {
    const exporter = new VRSimulationExporter();
    const result = exporter.exportToWebXR(makeManifest());

    expect(result.format).toBe("webxr");
    expect(result.scene.environment).toBe("lab");
    expect(result.scene.nodes[0].interactions).toContain("grab");
  });

  it("exports manifests to Unity packages", () => {
    const exporter = new VRSimulationExporter();
    const result = exporter.exportToUnity(makeManifest());

    expect(result.format).toBe("unity");
    expect(result.prefabs[0].components).toContain("XRGrabInteractable");
    expect(result.scene.sceneName).toContain("CardiacCycle");
  });
});
