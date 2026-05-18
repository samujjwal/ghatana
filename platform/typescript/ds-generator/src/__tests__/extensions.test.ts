import { describe, expect, it } from "vitest";
import type { ComponentContract } from "@ghatana/ds-schema";
import { createGeneratorManifest } from "../extensions/index.js";

const buttonContract: ComponentContract = {
  name: "Button",
  version: "1.0.0",
  description: "A clickable action control.",
  props: [
    {
      name: "label",
      type: "string",
      required: true,
      builderMetadata: {
        bindable: true,
      },
    },
  ],
  slots: [],
  events: [
    {
      name: "onClick",
      description: "Fired when activated.",
    },
  ],
  metadata: {
    category: "input",
    status: "stable",
    platforms: ["web"],
  },
  builder: {
    bindings: [
      {
        propName: "label",
        bindingTypes: ["data", "state"],
        acceptedValueTypes: ["string"],
        required: true,
      },
    ],
  },
  builderA11y: {
    requiredA11yProps: ["label"],
    trapsFocusRequiresClose: false,
    motionRequiresReductionSupport: false,
    wcagLevel: "AA",
  },
};

describe("generator extension manifest", () => {
  it("produces deterministic artifacts independent of input order", () => {
    const alpha = { ...buttonContract, name: "Alpha" };
    const beta = { ...buttonContract, name: "Beta" };

    const first = createGeneratorManifest({
      presetId: "ghatana-default",
      contracts: [beta, alpha],
      extensionPoints: ["tests", "docs", "builder-bindings"],
    });
    const second = createGeneratorManifest({
      presetId: "ghatana-default",
      contracts: [alpha, beta],
      extensionPoints: ["builder-bindings", "docs", "tests"],
    });

    expect(first).toEqual(second);
    expect(first.artifacts.map((artifact) => artifact.path)).toEqual([
      "builder-bindings/Alpha.json",
      "builder-bindings/Beta.json",
      "docs/components/Alpha.md",
      "docs/components/Beta.md",
      "tests/Alpha.contract.test.json",
      "tests/Beta.contract.test.json",
    ]);
  });

  it("round-trips builder bindings as UI-builder-compatible binding targets", () => {
    const manifest = createGeneratorManifest({
      presetId: "ghatana-default",
      contracts: [buttonContract],
      extensionPoints: ["builder-bindings"],
    });

    const bindingArtifact = manifest.artifacts[0];
    const parsed = JSON.parse(bindingArtifact.content) as {
      readonly component: string;
      readonly bindings: readonly {
        readonly propName: string;
        readonly bindingTypes: readonly string[];
      }[];
    };

    expect(bindingArtifact.path).toBe("builder-bindings/Button.json");
    expect(parsed.component).toBe("Button");
    expect(parsed.bindings).toEqual([
      expect.objectContaining({
        propName: "label",
        bindingTypes: ["data", "state"],
      }),
    ]);
  });
});
