import { describe, expect, it } from "vitest";
import { ModalityConversionService } from "./service.js";

function makeReadService() {
  return {
    async getAssetDetail() {
      return {
        asset: {
          id: "asset-1",
          tenantId: "tenant-1",
          title: "Projectile Motion",
          domain: "physics",
          assetType: "lesson",
          authorId: "author-1",
        },
        blocks: [
          {
            id: "block-1",
            blockRef: "intro",
            title: "Overview",
            blockType: "text_explainer",
            payload: {
              text: "A projectile moves under gravity. Horizontal velocity stays constant. Vertical motion accelerates downward.",
            },
          },
        ],
        manifests: [],
      };
    },
  };
}

describe("ModalityConversionService", () => {
  it("lists supported conversions for a canonical asset", async () => {
    const service = new ModalityConversionService(makeReadService() as never);

    const conversions = await service.listAvailableConversions(
      "tenant-1",
      "asset-1",
    );

    expect(conversions).toHaveLength(4);
    expect(conversions.every((conversion) => conversion.supported)).toBe(true);
  });

  it("derives a storyboard-style visual conversion from text blocks", async () => {
    const service = new ModalityConversionService(makeReadService() as never);

    const result = await service.convertAsset("tenant-1", "asset-1", "visual");

    expect(result.targetModality).toBe("visual");
    expect(result.blocks[0].cues?.[0]).toContain("panel-1");
  });

  it("derives a simulation scaffold when no simulation manifest exists", async () => {
    const service = new ModalityConversionService(makeReadService() as never);

    const result = await service.convertAsset(
      "tenant-1",
      "asset-1",
      "simulation",
    );

    expect(result.simulation?.manifest.domain).toBe("PHYSICS");
    expect(result.simulation?.skillNames.length).toBeGreaterThan(0);
    expect(result.blocks[0].content).toContain("projectile");
  });
});
