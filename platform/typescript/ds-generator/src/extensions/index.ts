/**
 * @fileoverview Stable extension manifest for design-system generation.
 *
 * The generator owns product-neutral artifacts only: docs, examples, tests,
 * and builder bindings derived from design-system component contracts.
 */

import { z } from "zod";
import type { ComponentContract } from "@ghatana/ds-schema";

export const GeneratorExtensionPointSchema = z.enum([
  "docs",
  "examples",
  "tests",
  "builder-bindings",
]);

export type GeneratorExtensionPoint = z.infer<
  typeof GeneratorExtensionPointSchema
>;

export const GeneratorOutputArtifactSchema = z
  .object({
    path: z.string().min(1),
    extensionPoint: GeneratorExtensionPointSchema,
    content: z.string(),
  })
  .strict();

export type GeneratorOutputArtifact = z.infer<
  typeof GeneratorOutputArtifactSchema
>;

export const DesignSystemGeneratorManifestSchema = z
  .object({
    schemaVersion: z.literal("ds-generator.manifest.v1"),
    presetId: z.string().min(1),
    artifacts: z.array(GeneratorOutputArtifactSchema),
  })
  .strict();

export type DesignSystemGeneratorManifest = z.infer<
  typeof DesignSystemGeneratorManifestSchema
>;

export interface CreateGeneratorManifestOptions {
  readonly presetId: string;
  readonly contracts?: readonly ComponentContract[];
  readonly extensionPoints?: readonly GeneratorExtensionPoint[];
}

function normalizeExtensionPoints(
  extensionPoints: readonly GeneratorExtensionPoint[] | undefined,
): readonly GeneratorExtensionPoint[] {
  const points = extensionPoints ?? [
    "docs",
    "examples",
    "tests",
    "builder-bindings",
  ];
  return [...new Set(points)].sort((a, b) => a.localeCompare(b));
}

function renderDocsArtifact(
  contract: ComponentContract,
): GeneratorOutputArtifact {
  return {
    path: `docs/components/${contract.name}.md`,
    extensionPoint: "docs",
    content: [
      `# ${contract.name}`,
      "",
      contract.description ?? `${contract.name} component.`,
      "",
      `Status: ${contract.metadata.status}`,
      `Category: ${contract.metadata.category}`,
    ].join("\n"),
  };
}

function renderExamplesArtifact(
  contract: ComponentContract,
): GeneratorOutputArtifact {
  const examples = contract.examples ?? [];
  return {
    path: `examples/${contract.name}.json`,
    extensionPoint: "examples",
    content: `${JSON.stringify({ component: contract.name, examples }, null, 2)}\n`,
  };
}

function renderTestsArtifact(
  contract: ComponentContract,
): GeneratorOutputArtifact {
  return {
    path: `tests/${contract.name}.contract.test.json`,
    extensionPoint: "tests",
    content: `${JSON.stringify(
      {
        component: contract.name,
        requiredProps: contract.props
          .filter((prop) => prop.required)
          .map((prop) => prop.name),
        slots: contract.slots.map((slot) => slot.name),
        a11y: contract.builderA11y ?? contract.metadata.a11y ?? null,
      },
      null,
      2,
    )}\n`,
  };
}

function renderBuilderBindingsArtifact(
  contract: ComponentContract,
): GeneratorOutputArtifact {
  return {
    path: `builder-bindings/${contract.name}.json`,
    extensionPoint: "builder-bindings",
    content: `${JSON.stringify(
      {
        component: contract.name,
        bindings: contract.builder?.bindings ?? [],
        slots: contract.slots.map((slot) => ({
          name: slot.name,
          required: slot.builderMetadata?.required ?? false,
          allowsMultiple: slot.isSingleChild !== true,
        })),
      },
      null,
      2,
    )}\n`,
  };
}

function renderArtifactsForContract(
  contract: ComponentContract,
  extensionPoints: readonly GeneratorExtensionPoint[],
): readonly GeneratorOutputArtifact[] {
  const artifacts: GeneratorOutputArtifact[] = [];
  if (extensionPoints.includes("docs"))
    artifacts.push(renderDocsArtifact(contract));
  if (extensionPoints.includes("examples"))
    artifacts.push(renderExamplesArtifact(contract));
  if (extensionPoints.includes("tests"))
    artifacts.push(renderTestsArtifact(contract));
  if (extensionPoints.includes("builder-bindings"))
    artifacts.push(renderBuilderBindingsArtifact(contract));
  return artifacts;
}

export function createGeneratorManifest(
  options: CreateGeneratorManifestOptions,
): DesignSystemGeneratorManifest {
  const extensionPoints = normalizeExtensionPoints(options.extensionPoints);
  const contracts = [...(options.contracts ?? [])].sort((a, b) =>
    a.name.localeCompare(b.name),
  );
  const artifacts = contracts
    .flatMap((contract) =>
      renderArtifactsForContract(contract, extensionPoints),
    )
    .sort((a, b) => a.path.localeCompare(b.path));

  return DesignSystemGeneratorManifestSchema.parse({
    schemaVersion: "ds-generator.manifest.v1",
    presetId: options.presetId,
    artifacts,
  });
}
