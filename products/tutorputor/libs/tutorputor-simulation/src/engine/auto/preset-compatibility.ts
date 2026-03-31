/**
 * Auto Preset Compatibility
 *
 * Provides a compatibility bridge between the older auto preset identifiers
 * and the curated starter catalog without depending on the legacy auto
 * manifest implementation details.
 *
 * @doc.type module
 * @doc.purpose Normalize legacy auto preset identifiers onto curated starters
 * @doc.layer product
 * @doc.pattern Compatibility Layer
 */

import type {
  SimEntity,
  SimEntityId,
  SimStepId,
  SimulationDomain,
  SimulationManifest,
  SimulationStep,
} from "@tutorputor/contracts/v1/simulation";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import {
  getSimulationPresetById,
  type AutoSimulationRequest,
} from "./index";
import type { SimulationStarter } from "../starter-catalog";
import { listSimulationStarters, resolveSimulationStarter } from "../starter-catalog";
import { VRSimulationExporter } from "../export/vr-exporter";
import {
  createSimulationStarterManifest,
  exportSimulationStarterPackage,
} from "../starter-packaging";

export type AutoPresetSource = "legacy_auto" | "curated_starter";

export interface LegacyAutoPresetMetadata {
  id: string;
  name: string;
  description: string;
  domain: AutoSimulationRequest["domain"];
}

export interface CompatibleAutoPreset extends LegacyAutoPresetMetadata {
  source: AutoPresetSource;
  starterId?: string;
  audience?: SimulationStarter["audience"];
  legacyAliases: string[];
  bootstrapSupported: boolean;
  exportFormats: Array<"manifest" | "webxr" | "unity">;
}

export interface AutoPresetNormalizationSummary {
  legacyPresetCount: number;
  curatedStarterCount: number;
  compatiblePresetCount: number;
  legacyAliasesCovered: number;
  unresolvedLegacyPresetIds: string[];
  byDomain: Record<AutoSimulationRequest["domain"], number>;
}

export interface ResolvedCompatibleAutoPresetManifest {
  source: AutoPresetSource;
  presetId: string;
  starterId?: string;
  manifest: SimulationManifest;
}

const LEGACY_AUTO_PRESETS: LegacyAutoPresetMetadata[] = [
  {
    id: "preset-newton-first",
    name: "Newton's First Law (Inertia)",
    description: "Object at rest stays at rest, object in motion stays in motion",
    domain: "physics",
  },
  {
    id: "preset-newton-second",
    name: "Newton's Second Law (F=ma)",
    description: "Relationship between force, mass, and acceleration",
    domain: "physics",
  },
  {
    id: "preset-conservation-energy",
    name: "Conservation of Energy",
    description: "Energy transforms between potential and kinetic forms",
    domain: "physics",
  },
  {
    id: "preset-momentum-conservation",
    name: "Conservation of Momentum",
    description: "Total momentum remains constant in isolated systems",
    domain: "physics",
  },
  {
    id: "preset-wave-interference",
    name: "Wave Interference",
    description: "Constructive and destructive interference patterns",
    domain: "physics",
  },
  {
    id: "preset-atomic-structure",
    name: "Atomic Structure",
    description: "Electrons, orbitals, and atomic composition",
    domain: "chemistry",
  },
  {
    id: "preset-chemical-equilibrium",
    name: "Chemical Equilibrium",
    description: "Dynamic balance between forward and reverse reactions",
    domain: "chemistry",
  },
  {
    id: "preset-gas-laws",
    name: "Gas Laws",
    description: "Pressure, volume, and temperature relationships",
    domain: "chemistry",
  },
  {
    id: "preset-cell-membrane",
    name: "Cell Membrane Transport",
    description: "Passive and active transport across membranes",
    domain: "biology",
  },
  {
    id: "preset-photosynthesis",
    name: "Photosynthesis",
    description: "Light-dependent and Calvin cycle processes",
    domain: "biology",
  },
  {
    id: "preset-dna-replication",
    name: "DNA Replication",
    description: "Replication fork and leading/lagging strand synthesis",
    domain: "biology",
  },
  {
    id: "preset-binary-search",
    name: "Binary Search",
    description: "Divide-and-conquer search over sorted arrays",
    domain: "cs",
  },
  {
    id: "preset-dijkstra",
    name: "Dijkstra's Algorithm",
    description: "Shortest-path relaxation over weighted graphs",
    domain: "cs",
  },
  {
    id: "preset-binary-tree",
    name: "Binary Tree Traversal",
    description: "Traversal and recursion over hierarchical structures",
    domain: "cs",
  },
];

function toAutoDomainFromStarter(
  starter: SimulationStarter,
): AutoSimulationRequest["domain"] {
  switch (starter.domain) {
    case "PHYSICS":
      return "physics";
    case "CHEMISTRY":
      return "chemistry";
    case "BIOLOGY":
      return "biology";
    case "MEDICINE":
      return "medicine";
    case "CS_DISCRETE":
      return "cs";
    default:
      return "math";
  }
}

function toCompatibleCuratedPreset(starter: SimulationStarter): CompatibleAutoPreset {
  return {
    id: starter.legacyPresetIds[0] ?? starter.id,
    name: starter.name,
    description: starter.summary,
    domain: toAutoDomainFromStarter(starter),
    source: "curated_starter",
    starterId: starter.id,
    audience: starter.audience,
    legacyAliases: [...starter.legacyPresetIds],
    bootstrapSupported: true,
    exportFormats: ["manifest", "webxr", "unity"],
  };
}

function toCompatibleLegacyPreset(
  preset: LegacyAutoPresetMetadata,
): CompatibleAutoPreset {
  return {
    ...preset,
    source: "legacy_auto",
    legacyAliases: [],
    bootstrapSupported: true,
    exportFormats: ["manifest", "webxr", "unity"],
  };
}

function toSimulationDomain(
  domain: AutoSimulationRequest["domain"],
): SimulationDomain {
  switch (domain) {
    case "physics":
      return "PHYSICS";
    case "chemistry":
      return "CHEMISTRY";
    case "biology":
      return "BIOLOGY";
    case "medicine":
      return "MEDICINE";
    case "cs":
      return "CS_DISCRETE";
    default:
      return "MATHEMATICS";
  }
}

function makeLegacyCompatibilityEntity(
  id: string,
  label: string,
  x: number,
  y: number,
  type: SimEntity["type"] = "annotation",
): SimEntity {
  return {
    id: id as SimEntityId,
    type,
    x,
    y,
    label,
    color: "#2563eb",
    strokeColor: "#1e293b",
    opacity: 0.9,
  };
}

function makeLegacyCompatibilityStep(
  id: string,
  index: number,
  title: string,
  narration: string,
  annotation: string,
  durationMs: number,
): SimulationStep {
  return {
    id: id as SimStepId,
    orderIndex: index,
    title,
    narration,
    description: annotation,
    duration: durationMs,
    actions: [
      {
        action: "ANNOTATE",
        text: annotation,
      },
    ],
  };
}

function createLegacyAutoPresetManifest(input: {
  preset: CompatibleAutoPreset;
  manifestId?: string;
  tenantId?: TenantId;
  authorId?: UserId;
  title?: string;
  description?: string;
}): SimulationManifest {
  type LegacyPresetManifestShape = {
    entities?: Array<{
      id: string;
      type?: string;
      x: number;
      y: number;
    }>;
    steps?: Array<{
      id: string;
      title?: string;
      description?: string;
      duration?: number;
    }>;
  };
  const timestamp = new Date().toISOString();
  const presetDefinition = getSimulationPresetById(input.preset.id);
  const legacyManifest = (presetDefinition?.manifest ?? {}) as LegacyPresetManifestShape;
  const legacyEntities = legacyManifest.entities ?? [];
  const legacySteps = legacyManifest.steps ?? [];

  return {
    id: (input.manifestId ?? `compat-${input.preset.id}`) as SimulationManifest["id"],
    version: "1.0.0",
    title: input.title ?? input.preset.name,
    description: input.description ?? input.preset.description,
    domain: toSimulationDomain(input.preset.domain),
    authorId: (input.authorId ?? "compatibility-layer") as UserId,
    tenantId: (input.tenantId ?? "system") as TenantId,
    canvas: {
      width: 1280,
      height: 720,
      backgroundColor: "#f8fafc",
    },
    playback: {
      defaultSpeed: 1,
      loop: false,
      autoPlay: false,
    },
    initialEntities:
      legacyEntities.length > 0
        ? legacyEntities.map((entity, index) => ({
            id: entity.id as SimEntityId,
            type:
              entity.type === "dynamic-body"
                ? "rigidBody"
                : entity.type === "boundary"
                  ? "boundary"
                  : entity.type === "sensor"
                    ? "annotation"
                    : entity.type === "force-field"
                      ? "vector"
                      : "annotation",
            x: entity.x,
            y: entity.y,
            label:
              entity.id
                .split("-")
                .map((part: string) => `${part[0]?.toUpperCase() ?? ""}${part.slice(1)}`)
                .join(" ") || `Entity ${index + 1}`,
            color: "#2563eb",
            strokeColor: "#1e293b",
          }))
        : [
            makeLegacyCompatibilityEntity(
              `${input.preset.id}-focus`,
              input.preset.name,
              360,
              320,
            ),
            makeLegacyCompatibilityEntity(
              `${input.preset.id}-reference`,
              "Reference",
              860,
              320,
            ),
          ],
    steps:
      legacySteps.length > 0
        ? legacySteps.map((step, index) => ({
            id: step.id as SimStepId,
            orderIndex: index,
            title: step.title,
            narration: step.description,
            description: step.description,
            duration: step.duration ?? 1500,
            actions: [
              {
                action: "ANNOTATE",
                text: step.description,
              },
            ],
          })) as SimulationStep[]
        : [
            makeLegacyCompatibilityStep(
              `${input.preset.id}-observe`,
              0,
              "Observe",
              `Introduce ${input.preset.name}.`,
              input.preset.description,
              1600,
            ),
            makeLegacyCompatibilityStep(
              `${input.preset.id}-analyze`,
              1,
              "Analyze",
              "Trace the relationships and state changes in the model.",
              "Inspect the core variables and how they interact.",
              2200,
            ),
          ],
    accessibility: {
      screenReaderNarration: true,
      reducedMotion: false,
      highContrast: true,
    },
    createdAt: timestamp,
    updatedAt: timestamp,
    schemaVersion: "1.0.0",
  };
}

export function listCompatibleAutoPresets(input: {
  domain?: AutoSimulationRequest["domain"];
  query?: string;
  source?: AutoPresetSource;
  bootstrapOnly?: boolean;
} = {}): CompatibleAutoPreset[] {
  const curated = listSimulationStarters().map(toCompatibleCuratedPreset);
  const legacy = LEGACY_AUTO_PRESETS.map(toCompatibleLegacyPreset);

  return [...curated, ...legacy]
    .filter((preset) => (input.source ? preset.source === input.source : true))
    .filter((preset) => (input.domain ? preset.domain === input.domain : true))
    .filter((preset) => (input.bootstrapOnly ? preset.bootstrapSupported : true))
    .filter((preset) => {
      const query = input.query?.trim().toLowerCase();
      if (!query) {
        return true;
      }

      return [
        preset.id,
        preset.name,
        preset.description,
        preset.domain,
        ...preset.legacyAliases,
      ]
        .join(" ")
        .toLowerCase()
        .includes(query);
    })
    .map((preset) => structuredClone(preset));
}

export function resolveCompatibleAutoPreset(
  presetRef: string,
): CompatibleAutoPreset | null {
  const curated = resolveSimulationStarter(presetRef);
  if (curated) {
    return toCompatibleCuratedPreset(curated.starter);
  }

  const normalizedRef = presetRef.trim().toLowerCase();
  const legacy = LEGACY_AUTO_PRESETS.find(
    (preset) => preset.id.toLowerCase() === normalizedRef,
  );
  return legacy ? toCompatibleLegacyPreset(legacy) : null;
}

export function getAutoPresetNormalizationSummary(): AutoPresetNormalizationSummary {
  const curated = listSimulationStarters().map(toCompatibleCuratedPreset);
  const legacyAliasesCovered = curated.reduce(
    (count, preset) => count + preset.legacyAliases.length,
    0,
  );
  const unresolvedLegacyPresetIds = LEGACY_AUTO_PRESETS.map((preset) => preset.id).filter(
    (legacyId) => !curated.some((preset) => preset.legacyAliases.includes(legacyId)),
  );
  const byDomain = {
    physics: 0,
    chemistry: 0,
    biology: 0,
    medicine: 0,
    cs: 0,
    math: 0,
  } satisfies Record<AutoSimulationRequest["domain"], number>;

  for (const preset of curated) {
    byDomain[preset.domain]++;
  }
  for (const preset of LEGACY_AUTO_PRESETS) {
    byDomain[preset.domain]++;
  }

  return {
    legacyPresetCount: LEGACY_AUTO_PRESETS.length,
    curatedStarterCount: curated.length,
    compatiblePresetCount: curated.length + LEGACY_AUTO_PRESETS.length,
    legacyAliasesCovered,
    unresolvedLegacyPresetIds,
    byDomain,
  };
}

export function resolveCompatibleAutoPresetManifest(input: {
  presetRef: string;
  manifestId?: string;
  tenantId?: TenantId;
  authorId?: UserId;
  title?: string;
  description?: string;
}): ResolvedCompatibleAutoPresetManifest | null {
  const resolvedStarter = resolveSimulationStarter(input.presetRef);
  if (resolvedStarter) {
    const manifest = createSimulationStarterManifest({
      starterRef: resolvedStarter.starter.id,
      ...(input.manifestId ? { manifestId: input.manifestId } : {}),
      ...(input.tenantId ? { tenantId: input.tenantId } : {}),
      ...(input.authorId ? { authorId: input.authorId } : {}),
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    });
    if (!manifest) {
      return null;
    }

    return {
      source: "curated_starter",
      presetId: input.presetRef,
      starterId: resolvedStarter.starter.id,
      manifest,
    };
  }

  const preset = resolveCompatibleAutoPreset(input.presetRef);
  if (!preset || preset.source !== "legacy_auto") {
    return null;
  }

  return {
    source: preset.source,
    presetId: preset.id,
    manifest: createLegacyAutoPresetManifest({
      preset,
      ...(input.manifestId ? { manifestId: input.manifestId } : {}),
      ...(input.tenantId ? { tenantId: input.tenantId } : {}),
      ...(input.authorId ? { authorId: input.authorId } : {}),
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    }),
  };
}

export function bootstrapCompatibleAutoPreset(input: {
  presetRef: string;
  manifestId?: string;
  tenantId?: TenantId;
  authorId?: UserId;
  title?: string;
  description?: string;
}): SimulationManifest | null {
  return (
    resolveCompatibleAutoPresetManifest(input)?.manifest ??
    null
  );
}

export function exportCompatibleAutoPreset(input: {
  presetRef: string;
  format?: "manifest" | "webxr" | "unity";
  manifestId?: string;
  tenantId?: TenantId;
  authorId?: UserId;
  title?: string;
  description?: string;
}) {
  const resolved = resolveCompatibleAutoPresetManifest(input);
  if (!resolved) {
    return null;
  }

  if (resolved.source === "curated_starter" && resolved.starterId) {
    return exportSimulationStarterPackage({
      starterRef: resolved.starterId,
      ...(input.format ? { format: input.format } : {}),
      ...(input.manifestId ? { manifestId: input.manifestId } : {}),
      ...(input.tenantId ? { tenantId: input.tenantId } : {}),
      ...(input.authorId ? { authorId: input.authorId } : {}),
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    });
  }

  const exporter = new VRSimulationExporter();
  const format = input.format ?? "manifest";
  const packageData =
    format === "webxr"
      ? exporter.exportToWebXR(resolved.manifest)
      : format === "unity"
        ? exporter.exportToUnity(resolved.manifest)
        : resolved.manifest;

  return {
    presetId: resolved.presetId,
    source: resolved.source,
    exportFormat: format,
    manifest: resolved.manifest,
    ...(resolved.starterId ? { starterId: resolved.starterId } : {}),
    packageData,
  };
}
