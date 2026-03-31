import { describe, expect, it } from "vitest";
import { ContentVariationService } from "./service.js";

function makeReadService() {
  return {
    async getAssetDetail() {
      return {
        asset: { id: "asset-1", title: "Newton's Laws" },
        blocks: [
          {
            blockRef: "intro",
            blockType: "text_explainer",
            payload: {
              text: "Force changes motion. Objects resist changes in motion. Net force determines acceleration.",
            },
          },
        ],
        manifests: [],
      };
    },
  };
}

describe("ContentVariationService", () => {
  it("creates easier and expert difficulty variants", async () => {
    const service = new ContentVariationService(makeReadService() as never);

    const variants = await service.generateDifficultyVariants("tenant-1", "asset-1");

    expect(variants.easy.summary).toContain("Simplified");
    expect(variants.easy.blocks[0].text).toContain("Key idea");
    expect(variants.expert.blocks[0].text).toContain("Expert extension");
  });

  it("creates scaffolded explanation variants with numbered steps", async () => {
    const service = new ContentVariationService(makeReadService() as never);

    const variants = await service.generateExplanationVariants("tenant-1", "asset-1");

    expect(variants.scaffolded.blocks[0].text).toContain("1.");
    expect(variants.minimal.blocks[0].text.length).toBeLessThan(
      variants.detailed.blocks[0].text.length,
    );
  });
});
