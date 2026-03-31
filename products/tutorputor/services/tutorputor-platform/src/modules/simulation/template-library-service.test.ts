import { describe, expect, it, vi, beforeEach } from "vitest";
import { SimulationTemplateLibraryService } from "./template-library-service";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

vi.mock("@tutorputor/simulation/engine", () => ({
  createSimulationStarterManifest: vi.fn(({ title, description, tenantId, authorId }) =>
    makeManifestPayload({
      ...(title ? { title } : {}),
      ...(description ? { description } : {}),
      ...(tenantId ? { tenantId } : {}),
      ...(authorId ? { authorId } : {}),
    }),
  ),
  resolveSimulationStarterReference: vi.fn((starterId: string) =>
    starterId === "starter-second"
      ? {
          matchedBy: "starter_id",
          requestedRef: starterId,
          starter: {
            id: "starter-second",
            name: "Second Starter",
            summary: "Second summary",
            domain: "CHEMISTRY",
            difficulty: "intermediate",
            tags: ["chemistry"],
            estimatedMinutes: 10,
            audience: "undergraduate",
            legacyPresetIds: ["preset-gas-laws"],
            manifest: makeManifestPayload({ domain: "CHEMISTRY" }),
          },
        }
      : {
          matchedBy: "starter_id",
          requestedRef: starterId,
          starter: {
            id: "starter-newton-cart",
            name: "Newton Cart Push",
            summary: "Starter summary",
            domain: "PHYSICS",
            difficulty: "beginner",
            tags: ["forces"],
            estimatedMinutes: 8,
            audience: "k12",
            legacyPresetIds: ["preset-newton-first"],
            manifest: makeManifestPayload(),
          },
        },
  ),
  listSimulationStarters: vi.fn(() => [
    {
      id: "starter-newton-cart",
      name: "Newton Cart Push",
      summary: "Starter summary",
      domain: "PHYSICS",
      difficulty: "beginner",
      tags: ["forces"],
      estimatedMinutes: 8,
      audience: "k12",
      legacyPresetIds: ["preset-newton-first"],
      manifest: makeManifestPayload(),
    },
    {
      id: "starter-second",
      name: "Second Starter",
      summary: "Second summary",
      domain: "CHEMISTRY",
      difficulty: "intermediate",
      tags: ["chemistry"],
      estimatedMinutes: 10,
      audience: "undergraduate",
      legacyPresetIds: ["preset-gas-laws"],
      manifest: makeManifestPayload({ domain: "CHEMISTRY" }),
    },
  ]),
  listCompatibleAutoPresets: vi.fn((input?: { source?: string; domain?: string }) =>
    [
      {
        id: "preset-photosynthesis",
        name: "Photosynthesis",
        description: "Auto preset description",
        domain: "biology",
        source: "curated_starter",
        starterId: "starter-photosynthesis-cycle",
        audience: "undergraduate",
        legacyAliases: ["preset-photosynthesis"],
        bootstrapSupported: true,
        exportFormats: ["manifest", "webxr", "unity"],
      },
      {
        id: "preset-gas-laws",
        name: "Gas Laws",
        description: "Chemistry auto preset",
        domain: "chemistry",
        source: "curated_starter",
        starterId: "starter-second",
        audience: "undergraduate",
        legacyAliases: ["preset-gas-laws"],
        bootstrapSupported: true,
        exportFormats: ["manifest", "webxr", "unity"],
      },
      {
        id: "preset-conservation-energy",
        name: "Conservation of Energy",
        description: "Legacy physics preset",
        domain: "physics",
        source: "legacy_auto",
        audience: "undergraduate",
        legacyAliases: [],
        bootstrapSupported: true,
        exportFormats: ["manifest", "webxr", "unity"],
      },
    ].filter((preset) => (input?.source ? preset.source === input.source : true))
      .filter((preset) => (input?.domain ? preset.domain === input.domain : true)),
  ),
  validateManifest: vi.fn((manifest: Record<string, unknown>) => ({
    valid: manifest.title !== "Broken Template",
    errors:
      manifest.title === "Broken Template"
        ? [{ path: "title", message: "Broken", severity: "error" as const }]
        : [],
    warnings: [],
  })),
  resolveCompatibleAutoPreset: vi.fn((presetId: string) =>
    presetId === "preset-conservation-energy"
      ? {
          id: presetId,
          name: "Conservation of Energy",
          description: "Legacy physics preset",
          domain: "physics",
          source: "legacy_auto",
          audience: "undergraduate",
          legacyAliases: [],
          bootstrapSupported: true,
          exportFormats: ["manifest", "webxr", "unity"],
        }
      : {
          id: presetId,
          name: "Photosynthesis",
          description: "Auto preset description",
          domain: "biology",
          source: "curated_starter",
          starterId: "starter-photosynthesis-cycle",
          audience: "undergraduate",
          legacyAliases: ["preset-photosynthesis"],
          bootstrapSupported: true,
          exportFormats: ["manifest", "webxr", "unity"],
        },
  ),
  bootstrapCompatibleAutoPreset: vi.fn(({ tenantId, authorId, title, description, presetRef }) =>
    makeManifestPayload({
      id: presetRef === "preset-conservation-energy" ? "legacy-manifest-1" : "auto-manifest-1",
      domain: presetRef === "preset-conservation-energy" ? "PHYSICS" : "BIOLOGY",
      ...(tenantId ? { tenantId } : {}),
      ...(authorId ? { authorId } : {}),
      ...(title ? { title } : {}),
      ...(description ? { description } : {}),
    }),
  ),
  exportSimulationStarterPackage: vi.fn(({ starterRef, format }) => ({
    starterId: starterRef,
    exportFormat: format ?? "manifest",
    manifest: makeManifestPayload(),
    packageData: { format: format ?? "manifest" },
  })),
  exportCompatibleAutoPreset: vi.fn(({ presetRef, format }) => ({
    presetId: presetRef,
    source: presetRef === "preset-conservation-energy" ? "legacy_auto" : "curated_starter",
    ...(presetRef === "preset-conservation-energy" ? {} : { starterId: presetRef }),
    exportFormat: format ?? "manifest",
    manifest: makeManifestPayload({ domain: "BIOLOGY" }),
    packageData: { format: format ?? "manifest" },
  })),
}));

function makeManifestPayload(overrides: Record<string, unknown> = {}) {
  return {
    id: "manifest-1",
    version: "1.0.0",
    title: "Starter Manifest",
    description: "Starter description",
    domain: "PHYSICS",
    tenantId: "tenant-1",
    authorId: "user-1",
    canvas: { width: 1280, height: 720 },
    playback: { defaultSpeed: 1, loop: false, autoPlay: false },
    initialEntities: [],
    steps: [],
    createdAt: new Date("2026-03-30T12:00:00Z").toISOString(),
    updatedAt: new Date("2026-03-30T12:00:00Z").toISOString(),
    schemaVersion: "1.0.0",
    ...overrides,
  };
}

function makePrisma() {
  const tx = {
    simulationManifest: {
      create: vi.fn().mockResolvedValue({
        id: "manifest-1",
      }),
      update: vi.fn().mockResolvedValue({
        id: "manifest-1",
      }),
    },
    simulationManifestVersion: {
      create: vi.fn().mockResolvedValue({
        id: "manifest-version-1",
      }),
    },
    simulationTemplate: {
      create: vi.fn().mockResolvedValue({
        id: "template-1",
        title: "Newton Cart Push",
      }),
      update: vi.fn().mockResolvedValue({
        id: "template-1",
        title: "Updated Template",
        status: "DRAFT",
        publishedAt: null,
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      }),
    },
  };

  return {
    simulationTemplate: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
      groupBy: vi.fn().mockResolvedValue([]),
      create: tx.simulationTemplate.create,
      update: tx.simulationTemplate.update,
    },
    simulationManifest: {
      create: tx.simulationManifest.create,
      update: tx.simulationManifest.update,
    },
    simulationManifestVersion: {
      create: tx.simulationManifestVersion.create,
    },
    $transaction: vi.fn(async (arg: any) => {
      if (typeof arg === "function") {
        return arg(tx as never);
      }
      return arg;
    }),
  };
}

describe("SimulationTemplateLibraryService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: SimulationTemplateLibraryService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new SimulationTemplateLibraryService(prisma as never);
  });

  it("creates a template from a curated starter", async () => {
    const result = await service.createTemplateFromStarter(
      "tenant-1",
      "user-1",
      "starter-newton-cart",
    );

    expect(prisma.simulationManifest.create).toHaveBeenCalled();
    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: "Newton Cart Push",
          status: "DRAFT",
        }),
      }),
    );
    expect(result.id).toBe("template-1");
  });

  it("creates a template from an AI-generated manifest", async () => {
    const result = await service.createTemplateFromGeneratedManifest(
      "tenant-1",
      "user-1",
      makeManifestPayload({
        id: "generated-manifest-1",
        title: "Generated Simulation",
        description: "Generated description",
        domain: "CHEMISTRY",
      }) as never,
      {
        tags: ["ai-generated", "chemistry"],
      },
    );

    expect(prisma.simulationManifest.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          id: "generated-manifest-1",
          title: "Generated Simulation",
        }),
      }),
    );
    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: "Generated Simulation",
          status: "DRAFT",
        }),
      }),
    );
    expect(result.id).toBe("template-1");
  });

  it("creates a template from a legacy auto preset through compatibility bootstrap", async () => {
    const result = await service.createTemplateFromAutoPreset(
      "tenant-1",
      "user-1",
      "preset-conservation-energy",
    );

    expect(prisma.simulationManifest.create).toHaveBeenCalled();
    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: "Conservation of Energy",
          status: "DRAFT",
        }),
      }),
    );
    expect(result.id).toBe("template-1");
  });

  it("creates a template from a normalized auto preset", async () => {
    await service.createTemplateFromAutoPreset(
      "tenant-1",
      "user-1",
      "preset-photosynthesis",
    );

    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          status: "DRAFT",
        }),
      }),
    );
  });

  it("previews template creation from starters, normalized auto presets, and legacy auto presets", async () => {
    const starterPreview = await service.previewTemplateFromStarter(
      "tenant-1",
      "user-1",
      "starter-newton-cart",
    );
    const autoPreview = await service.previewTemplateFromAutoPreset(
      "tenant-1",
      "user-1",
      "preset-photosynthesis",
    );
    const legacyAutoPreview = await service.previewTemplateFromAutoPreset(
      "tenant-1",
      "user-1",
      "preset-conservation-energy",
    );

    expect(starterPreview.source).toBe("starter");
    expect(starterPreview.governance.reviewStatus).toBe("draft");
    expect(autoPreview.source).toBe("auto_preset");
    expect(autoPreview.autoPresetId).toBe("preset-photosynthesis");
    expect(legacyAutoPreview.autoPresetId).toBe("preset-conservation-energy");
    expect(legacyAutoPreview.starterId).toBeUndefined();
  });

  it("clones an existing template into a new draft version", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      tenantId: "tenant-1",
      title: "Original Template",
      description: "Original description",
      domain: "PHYSICS",
      difficulty: "BEGINNER",
      tags: "[]",
      thumbnailUrl: null,
      license: "FREE",
      isPremium: false,
      isVerified: true,
      version: "1.0.0",
      authorName: null,
      authorAvatarUrl: null,
      organization: null,
      status: "PUBLISHED",
      moduleId: null,
      conceptId: null,
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload(),
      },
    });

    await service.cloneTemplate("tenant-1", "user-2", "template-1");

    expect(prisma.simulationManifest.create).toHaveBeenCalled();
    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: "Original Template Copy",
          version: "1.0.1",
          isVerified: false,
        }),
      }),
    );
  });

  it("revises an existing template from a refined manifest and records a manifest version snapshot", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      tenantId: "tenant-1",
      title: "Original Template",
      description: "Original description",
      domain: "PHYSICS",
      difficulty: "BEGINNER",
      tags: "[]",
      thumbnailUrl: null,
      license: "FREE",
      isPremium: false,
      isVerified: true,
      version: "1.0.0",
      status: "PUBLISHED",
      manifestId: "manifest-1",
      publishedAt: new Date("2026-03-30T12:00:00Z"),
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload({
          templateGovernance: {
            reviewStatus: "approved",
            source: "generated",
          },
        }),
      },
    });

    await service.reviseTemplateFromManifest(
      "tenant-1",
      "user-2",
      "template-1",
      makeManifestPayload({
        id: "ignored-new-id",
        title: "Refined Template",
        description: "Refined description",
      }) as never,
      {
        changeNote: "Apply refinement",
      },
    );

    expect(prisma.simulationManifestVersion.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          manifestId: "manifest-1",
          version: "1.0.0",
          createdBy: "user-2",
        }),
      }),
    );
    expect(prisma.simulationManifest.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "manifest-1" },
        data: expect.objectContaining({
          version: "1.0.1",
        }),
      }),
    );
    expect(prisma.simulationTemplate.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "template-1" },
        data: expect.objectContaining({
          version: "1.0.1",
          status: "DRAFT",
          isVerified: false,
        }),
      }),
    );
  });

  it("submits templates for review by updating manifest governance", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      tenantId: "tenant-1",
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload(),
      },
    });

    await service.submitTemplateForReview(
      "tenant-1",
      "template-1",
      "reviewer-1",
      "Ready for review",
    );

    expect(prisma.simulationManifest.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "manifest-1" },
      }),
    );
  });

  it("returns governance summary counts and current review history", async () => {
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: { reviewStatus: "submitted", source: "starter" },
            }),
          },
        },
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: { reviewStatus: "approved", source: "starter" },
            }),
          },
        },
      ])
      .mockResolvedValueOnce([]);
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      tenantId: "tenant-1",
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload({
          templateGovernance: {
            reviewStatus: "approved",
            source: "starter",
            reviewedBy: "reviewer-1",
          },
        }),
      },
    });

    const summary = await service.getGovernanceSummary("tenant-1");
    const history = await service.getTemplateReviewHistory("tenant-1", "template-1");

    expect(summary.submitted).toBe(1);
    expect(summary.approved).toBe(1);
    expect(history[0]?.status).toBe("approved");
  });

  it("reports starter and auto preset coverage gaps", async () => {
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "starter",
                starterId: "starter-newton-cart",
              },
            }),
          },
        },
      ])
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "auto_preset",
                autoPresetId: "preset-photosynthesis",
              },
            }),
          },
        },
      ]);

    const starterCoverage = await service.getStarterCoverage("tenant-1");
    const autoCoverage = await service.getAutoPresetCoverage("tenant-1");

    expect(starterCoverage.coveredStarters).toBe(1);
    expect(starterCoverage.uncoveredStarterIds).toContain("starter-second");
    expect(autoCoverage.coveredAutoPresets).toBe(1);
    expect(autoCoverage.uncoveredAutoPresetIds).toContain("preset-gas-laws");
    expect(autoCoverage.uncoveredAutoPresetIds).toContain("preset-conservation-energy");
  });

  it("builds domain and source coverage summaries", async () => {
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "starter",
                starterId: "starter-newton-cart",
              },
            }),
          },
        },
      ])
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "auto_preset",
                autoPresetId: "preset-photosynthesis",
              },
            }),
          },
        },
      ]);

    const summary = await service.getCoverageSummary("tenant-1");

    expect(summary.starters.byDomain.PHYSICS?.covered).toBe(1);
    expect(summary.starters.byAudience.k12?.covered).toBe(1);
    expect(summary.autoPresets.bySource.curated_starter?.covered).toBe(1);
    expect(summary.autoPresets.bySource.legacy_auto?.uncovered).toBe(1);
  });

  it("builds a prioritized coverage action plan for uncovered starters and legacy auto presets", async () => {
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([]);

    const plan = await service.getCoverageActionPlan("tenant-1", {
      audience: "k12",
      limit: 1,
    });

    expect(plan.starters).toHaveLength(1);
    expect(plan.starters[0]?.starterId).toBe("starter-newton-cart");
    expect(plan.legacyAutoPresets[0]?.presetId).toBe("preset-conservation-energy");
  });

  it("executes the prioritized coverage action plan", async () => {
    prisma.simulationTemplate.findFirst
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce(null);
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([]);

    const result = await service.executeCoverageActionPlan("tenant-1", "user-1", {
      audience: "k12",
      limit: 1,
    });

    expect(result.seededStarterTemplates).toBe(1);
    expect(result.submittedStarterTemplates).toBe(1);
    expect(result.createdLegacyTemplates).toBe(1);
    expect(result.templateIds).toHaveLength(2);
  });

  it("builds a retirement plan for legacy auto presets", async () => {
    prisma.simulationTemplate.findMany.mockResolvedValueOnce([
      {
        id: "template-ready",
        status: "PUBLISHED",
        manifest: {
          manifest: makeManifestPayload({
            templateGovernance: {
              reviewStatus: "approved",
              source: "auto_preset",
              autoPresetId: "preset-conservation-energy",
            },
          }),
        },
      },
    ]);

    const plan = await service.getLegacyAutoRetirementPlan("tenant-1");

    expect(plan.readyToRetire).toBe(1);
    expect(plan.awaitingPublish).toBe(0);
    expect(plan.needsTemplate).toBe(0);
    expect(plan.items[0]?.status).toBe("ready_to_retire");
  });

  it("validates single templates and bulk validation batches", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      title: "Broken Template",
      description: "Too short",
      domain: "PHYSICS",
      tenantId: "tenant-1",
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload({ title: "Broken Template" }),
      },
    });
    prisma.simulationTemplate.findFirst
      .mockResolvedValueOnce({
        id: "template-1",
        title: "Valid Template",
        description: "This description is long enough for validation.",
        domain: "PHYSICS",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload({
            entities: [{ id: "e1", label: "Mass", value: 1 }],
            steps: [
              {
                id: "step-1",
                orderIndex: 1,
                title: "Initial State",
                narration: "Set up the simulation with a known mass.",
                entityStates: [{ entityId: "e1", value: 1 }],
              },
            ],
          }),
        },
      })
      .mockResolvedValueOnce({
        id: "template-2",
        title: "Broken Template",
        description: "Too short",
        domain: "PHYSICS",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-2",
          manifest: makeManifestPayload({ title: "Broken Template" }),
        },
      });
    prisma.simulationTemplate.findMany.mockResolvedValueOnce([
      {
        id: "template-1",
        manifest: {
          manifest: makeManifestPayload(),
        },
      },
      {
        id: "template-2",
        manifest: {
          manifest: makeManifestPayload({ title: "Broken Template" }),
        },
      },
    ]);

    const single = await service.validateTemplate("tenant-1", "template-1");
    const bulk = await service.validateTemplatesBulk("tenant-1");

    expect(single.valid).toBe(false);
    expect(bulk.processed).toBe(2);
    expect(bulk.items[1]?.validation.valid).toBe(false);
  });

  it("returns template lineage and exports via starter-backed packaging", async () => {
    const templateRecord = {
      id: "template-1",
      tenantId: "tenant-1",
      title: "Starter Template",
      description: "Desc",
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload({
          templateGovernance: {
            reviewStatus: "approved",
            source: "starter",
            starterId: "starter-newton-cart",
          },
        }),
      },
    };
    prisma.simulationTemplate.findFirst
      .mockResolvedValueOnce(templateRecord)
      .mockResolvedValueOnce(templateRecord);
    prisma.simulationTemplate.findMany.mockResolvedValueOnce([
      {
        id: "template-child",
        title: "Child Template",
        status: "DRAFT",
        manifest: {
          manifest: makeManifestPayload({
            templateGovernance: {
              reviewStatus: "draft",
              source: "clone",
              parentTemplateId: "template-1",
            },
          }),
        },
      },
    ]);

    const lineage = await service.getTemplateLineage("tenant-1", "template-1");
    const exported = await service.exportTemplate("tenant-1", "template-1", "manifest");

    expect(lineage.parentTemplateId).toBeNull();
    expect(lineage.children[0]?.id).toBe("template-child");
    expect(exported).toEqual(
      expect.objectContaining({
        starterId: "starter-newton-cart",
      }),
    );
  });

  it("bulk creates starter-backed and auto-preset-backed templates", async () => {
    const starterItems = await service.bulkCreateTemplatesFromStarters(
      "tenant-1",
      "user-1",
      {
        starterIds: ["starter-newton-cart", "starter-newton-cart"],
      },
    );
    const autoItems = await service.bulkCreateTemplatesFromAutoPresets(
      "tenant-1",
      "user-1",
      {
        presetIds: ["preset-photosynthesis", "preset-photosynthesis"],
      },
    );

    expect(starterItems).toHaveLength(2);
    expect(autoItems).toHaveLength(2);
  });

  it("seeds missing starter and auto-preset templates from coverage gaps", async () => {
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "starter",
                starterId: "starter-newton-cart",
              },
            }),
          },
        },
      ])
      .mockResolvedValueOnce([
        {
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "auto_preset",
                autoPresetId: "preset-photosynthesis",
              },
            }),
          },
        },
      ]);

    const starterItems = await service.seedMissingStarterTemplates(
      "tenant-1",
      "user-1",
      { limit: 1 },
    );
    const autoItems = await service.seedMissingAutoPresetTemplates(
      "tenant-1",
      "user-1",
      { limit: 1 },
    );

    expect(starterItems).toHaveLength(1);
    expect(autoItems).toHaveLength(1);
    expect(prisma.simulationTemplate.create).toHaveBeenCalledTimes(2);
  });

  it("filters uncovered auto-preset seeding by domain and source", async () => {
    prisma.simulationTemplate.findMany.mockResolvedValueOnce([]);

    const items = await service.seedMissingAutoPresetTemplates("tenant-1", "user-1", {
      domain: "physics",
      source: "legacy_auto",
      limit: 1,
    });

    expect(items).toHaveLength(1);
    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: "Conservation of Energy",
        }),
      }),
    );
  });

  it("filters uncovered starter seeding by audience", async () => {
    prisma.simulationTemplate.findMany.mockResolvedValueOnce([]);

    const items = await service.seedMissingStarterTemplates("tenant-1", "user-1", {
      audience: "undergraduate",
      limit: 1,
    });

    expect(items).toHaveLength(1);
    expect(prisma.simulationTemplate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: "Second Starter",
        }),
      }),
    );
  });

  it("seeds the broader coverage backlog without duplicating curated auto aliases", async () => {
    prisma.simulationTemplate.findFirst
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      });
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([]);

    const result = await service.seedCoverageBacklog("tenant-1", "user-1", {
      autoSubmitForReview: true,
    });

    expect(result.createdStarterTemplates).toBe(2);
    expect(result.createdLegacyAutoTemplates).toBe(1);
    expect(result.skippedCuratedAutoAliases).toBe(2);
    expect(result.submittedForReview).toBe(3);
    expect(result.templateIds).toHaveLength(3);
  });

  it("executes the legacy auto retirement plan by creating and publishing missing templates", async () => {
    prisma.simulationTemplate.findFirst
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        status: "DRAFT",
        publishedAt: null,
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      });
    prisma.simulationTemplate.findMany
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        {
          id: "template-ready",
          status: "PUBLISHED",
          manifest: {
            manifest: makeManifestPayload({
              templateGovernance: {
                reviewStatus: "approved",
                source: "auto_preset",
                autoPresetId: "preset-conservation-energy",
              },
            }),
          },
        },
      ]);

    const result = await service.executeLegacyAutoRetirement("tenant-1", "user-1", {
      autoSubmitForReview: true,
      autoPublish: true,
    });

    expect(result.createdTemplates).toBe(1);
    expect(result.submittedForReview).toBe(1);
    expect(result.published).toBeGreaterThanOrEqual(0);
    expect(Array.isArray(result.failed)).toBe(true);
    expect(result.remainingPlan.readyToRetire).toBe(1);
  });

  it("bulk submits, reviews, deprecates, and publishes templates", async () => {
    prisma.simulationTemplate.findFirst
      .mockResolvedValueOnce({
        id: "template-1",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-1",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-2",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-2",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-3",
        tenantId: "tenant-1",
        status: "DRAFT",
        publishedAt: null,
        manifest: {
          id: "manifest-3",
          manifest: makeManifestPayload({
            templateGovernance: { reviewStatus: "submitted", source: "starter" },
          }),
        },
      })
      .mockResolvedValueOnce({
        id: "template-4",
        tenantId: "tenant-1",
        status: "DRAFT",
        publishedAt: null,
        manifest: {
          id: "manifest-4",
          manifest: makeManifestPayload({
            templateGovernance: { reviewStatus: "submitted", source: "starter" },
          }),
        },
      })
      .mockResolvedValueOnce({
        id: "template-5",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-5",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-6",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-6",
          manifest: makeManifestPayload(),
        },
      })
      .mockResolvedValueOnce({
        id: "template-7",
        title: "Template Seven",
        description: "A sufficiently detailed description for template seven.",
        domain: "PHYSICS",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-7",
          manifest: makeManifestPayload({
            entities: [{ id: "e1", label: "Mass", value: 1 }],
            steps: [
              {
                id: "step-1",
                orderIndex: 1,
                title: "Step One",
                narration: "Explain the first step in detail.",
                entityStates: [{ entityId: "e1", value: 1 }],
              },
            ],
            templateGovernance: { reviewStatus: "approved", source: "starter" },
          }),
        },
      })
      .mockResolvedValueOnce({
        id: "template-8",
        title: "Template Eight",
        description: "A sufficiently detailed description for template eight.",
        domain: "PHYSICS",
        tenantId: "tenant-1",
        manifest: {
          id: "manifest-8",
          manifest: makeManifestPayload({
            entities: [{ id: "e2", label: "Velocity", value: 2 }],
            steps: [
              {
                id: "step-1",
                orderIndex: 1,
                title: "Step One",
                narration: "Explain the first step in detail.",
                entityStates: [{ entityId: "e2", value: 2 }],
              },
            ],
            templateGovernance: { reviewStatus: "approved", source: "starter" },
          }),
        },
      });

    const submitted = await service.bulkSubmitTemplatesForReview("tenant-1", "user-1", {
      templateIds: ["template-1", "template-2"],
      notes: "Review all",
    });
    const reviewed = await service.bulkReviewTemplates("tenant-1", "reviewer-1", {
      templateIds: ["template-3", "template-4"],
      action: "approve",
      publish: true,
    });
    const deprecated = await service.bulkDeprecateTemplates("tenant-1", {
      templateIds: ["template-5", "template-6"],
      reason: "Superseded",
    });
    const published = await service.bulkPublishTemplates("tenant-1", {
      templateIds: ["template-7", "template-8"],
    });

    expect(submitted.processed).toBe(2);
    expect(reviewed.processed).toBe(2);
    expect(deprecated.processed).toBe(2);
    expect(published.processed).toBe(2);
  });

  it("approves and optionally publishes reviewed templates", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      tenantId: "tenant-1",
      status: "DRAFT",
      publishedAt: null,
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload({
          templateGovernance: { reviewStatus: "submitted", source: "starter" },
        }),
      },
    });

    await service.reviewTemplate(
      "tenant-1",
      "template-1",
      "reviewer-1",
      "approve",
      { publish: true },
    );

    expect(prisma.simulationTemplate.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          isVerified: true,
          status: "PUBLISHED",
        }),
      }),
    );
  });

  it("deprecates templates by archiving them", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      tenantId: "tenant-1",
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload(),
      },
    });

    await service.deprecateTemplate("tenant-1", "template-1", "Superseded");

    expect(prisma.simulationTemplate.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "template-1" },
        data: expect.objectContaining({ status: "ARCHIVED" }),
      }),
    );
  });

  it("publishes validated templates directly", async () => {
    prisma.simulationTemplate.findFirst.mockResolvedValueOnce({
      id: "template-1",
      title: "Template One",
      description: "A sufficiently detailed description for publish validation.",
      domain: "PHYSICS",
      tenantId: "tenant-1",
      manifest: {
        id: "manifest-1",
        manifest: makeManifestPayload({
          entities: [{ id: "e1", label: "Mass", value: 1 }],
          steps: [
            {
              id: "step-1",
              orderIndex: 1,
              title: "Step One",
              narration: "Explain the first step in detail.",
              entityStates: [{ entityId: "e1", value: 1 }],
            },
          ],
          templateGovernance: { reviewStatus: "approved", source: "starter" },
        }),
      },
    });

    await service.publishTemplate("tenant-1", "template-1");

    expect(prisma.simulationTemplate.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "template-1" },
        data: expect.objectContaining({
          status: "PUBLISHED",
          isVerified: true,
        }),
      }),
    );
  });

  it("builds a phased coverage campaign plan", async () => {
    vi.spyOn(service, "getCoverageActionPlan").mockResolvedValueOnce({
      starters: [
        {
          starterId: "starter-newton-cart",
          name: "Newton Cart Push",
          domain: "PHYSICS",
          audience: "k12",
          priority: 90,
          recommendedAction: "seed_and_submit",
        },
        {
          starterId: "starter-second",
          name: "Second Starter",
          domain: "CHEMISTRY",
          audience: "undergraduate",
          priority: 70,
          recommendedAction: "seed_template",
        },
      ],
      legacyAutoPresets: [
        {
          presetId: "preset-conservation-energy",
          name: "Conservation of Energy",
          domain: "physics",
          priority: 95,
          recommendedAction: "create_and_publish",
        },
      ],
    });

    const result = await service.getCoverageCampaignPlan("tenant-1");

    expect(result.phases).toHaveLength(3);
    expect(result.phases[0]?.phase).toBe("starter_foundation");
    expect(result.phases[0]?.actions).toBe(1);
    expect(result.phases[1]?.phase).toBe("review_ready_starters");
    expect(result.phases[1]?.actions).toBe(1);
    expect(result.phases[2]?.phase).toBe("legacy_retirement");
    expect(result.phases[2]?.actions).toBe(1);
  });

  it("executes phased coverage campaigns without duplicating governance flows", async () => {
    vi.spyOn(service, "getCoverageActionPlan").mockResolvedValueOnce({
      starters: [
        {
          starterId: "starter-newton-cart",
          name: "Newton Cart Push",
          domain: "PHYSICS",
          audience: "k12",
          priority: 90,
          recommendedAction: "seed_and_submit",
        },
        {
          starterId: "starter-second",
          name: "Second Starter",
          domain: "CHEMISTRY",
          audience: "undergraduate",
          priority: 70,
          recommendedAction: "seed_template",
        },
      ],
      legacyAutoPresets: [
        {
          presetId: "preset-conservation-energy",
          name: "Conservation of Energy",
          domain: "physics",
          priority: 95,
          recommendedAction: "create_and_publish",
        },
      ],
    });
    vi.spyOn(service, "createTemplateFromStarter")
      .mockResolvedValueOnce({ id: "template-starter-1" } as never)
      .mockResolvedValueOnce({ id: "template-starter-2" } as never);
    vi.spyOn(service, "createTemplateFromAutoPreset").mockResolvedValueOnce({
      id: "template-legacy-1",
    } as never);
    vi.spyOn(service, "submitTemplateForReview").mockResolvedValue({} as never);
    vi.spyOn(service, "publishTemplate").mockResolvedValue({} as never);

    const result = await service.executeCoverageCampaign("tenant-1", "user-1");

    expect(result.processedPhases).toEqual([
      "starter_foundation",
      "review_ready_starters",
      "legacy_retirement",
    ]);
    expect(result.seededStarterTemplates).toBe(2);
    expect(result.submittedStarterTemplates).toBe(1);
    expect(result.createdLegacyTemplates).toBe(1);
    expect(result.publishedLegacyTemplates).toBe(1);
    expect(result.templateIds).toEqual([
      "template-starter-1",
      "template-starter-2",
      "template-legacy-1",
    ]);
  });

  it("builds a domain catalog backlog across uncovered starters and legacy runtime debt", async () => {
    vi.spyOn(service, "getStarterCoverage").mockResolvedValueOnce({
      totalStarters: 2,
      coveredStarters: 0,
      uncoveredStarterIds: ["starter-newton-cart", "starter-second"],
      uncoveredStarters: [
        {
          id: "starter-newton-cart",
          name: "Newton Cart Push",
          domain: "PHYSICS",
          difficulty: "beginner",
          audience: "k12",
        },
        {
          id: "starter-second",
          name: "Second Starter",
          domain: "CHEMISTRY",
          difficulty: "intermediate",
          audience: "undergraduate",
        },
      ],
    } as never);
    vi.spyOn(service, "getLegacyAutoRetirementPlan").mockResolvedValueOnce({
      totalLegacyAutoPresets: 2,
      readyToRetire: 0,
      awaitingPublish: 1,
      needsTemplate: 1,
      items: [
        {
          presetId: "preset-conservation-energy",
          name: "Conservation of Energy",
          domain: "physics",
          status: "needs_template",
        },
        {
          presetId: "preset-gas-laws",
          name: "Gas Laws",
          domain: "chemistry",
          status: "awaiting_publish",
          templateId: "template-gas-laws",
        },
      ],
    } as never);

    const result = await service.getDomainCatalogBacklog("tenant-1", {
      domain: "PHYSICS",
    });

    expect(result.domain).toBe("PHYSICS");
    expect(result.uncoveredStarters).toHaveLength(1);
    expect(result.uncoveredStarters[0]?.starterId).toBe("starter-newton-cart");
    expect(result.legacyRuntimePresetsNeedingTemplates).toHaveLength(1);
    expect(result.legacyRuntimePresetsNeedingTemplates[0]?.presetId).toBe(
      "preset-conservation-energy",
    );
    expect(result.totalActions).toBe(2);
  });

  it("seeds a domain catalog slice across starters and legacy runtime presets", async () => {
    vi.spyOn(service, "getDomainCatalogBacklog").mockResolvedValueOnce({
      domain: "PHYSICS",
      uncoveredStarters: [
        {
          starterId: "starter-newton-cart",
          name: "Newton Cart Push",
          audience: "k12",
        },
      ],
      legacyRuntimePresetsNeedingTemplates: [
        {
          presetId: "preset-conservation-energy",
          name: "Conservation of Energy",
          status: "awaiting_publish",
        },
      ],
      totalActions: 2,
    } as never);
    vi.spyOn(service, "createTemplateFromStarter").mockResolvedValueOnce({
      id: "template-starter-1",
    } as never);
    vi.spyOn(service, "createTemplateFromAutoPreset").mockResolvedValueOnce({
      id: "template-legacy-1",
    } as never);
    vi.spyOn(service, "submitTemplateForReview").mockResolvedValue({} as never);
    vi.spyOn(service, "publishTemplate").mockResolvedValue({} as never);

    const result = await service.seedDomainCatalogCoverage("tenant-1", "user-1", {
      domain: "PHYSICS",
      autoSubmitStarters: true,
      autoSubmitLegacy: true,
    });

    expect(result.seededStarterTemplates).toBe(1);
    expect(result.submittedStarterTemplates).toBe(1);
    expect(result.createdLegacyTemplates).toBe(1);
    expect(result.submittedLegacyTemplates).toBe(1);
    expect(result.publishedLegacyTemplates).toBe(1);
    expect(result.templateIds).toEqual(["template-starter-1", "template-legacy-1"]);
  });

  it("builds a catalog progress matrix across domains and audiences", async () => {
    vi.spyOn(service, "getDomainCatalogBacklog")
      .mockResolvedValueOnce({
        domain: "PHYSICS",
        audience: "k12",
        uncoveredStarters: [{ starterId: "starter-newton-cart", name: "Newton", audience: "k12" }],
        legacyRuntimePresetsNeedingTemplates: [{ presetId: "preset-conservation-energy", name: "Energy", status: "needs_template" }],
        totalActions: 2,
      } as never)
      .mockResolvedValueOnce({
        domain: "CHEMISTRY",
        audience: "undergraduate",
        uncoveredStarters: [{ starterId: "starter-second", name: "Second", audience: "undergraduate" }],
        legacyRuntimePresetsNeedingTemplates: [],
        totalActions: 1,
      } as never);

    const result = await service.getCatalogProgressMatrix("tenant-1", {
      domains: ["PHYSICS", "CHEMISTRY"],
      audiences: ["k12", "undergraduate"],
    });

    expect(result.domains).toHaveLength(4);
    expect(result.totals.totalActions).toBeGreaterThanOrEqual(3);
    expect(result.totals.uncoveredStarters).toBeGreaterThanOrEqual(2);
  });

  it("seeds multiple domain catalog slices through one governed matrix execution", async () => {
    vi.spyOn(service, "seedDomainCatalogCoverage")
      .mockResolvedValueOnce({
        domain: "PHYSICS",
        seededStarterTemplates: 1,
        submittedStarterTemplates: 1,
        createdLegacyTemplates: 1,
        submittedLegacyTemplates: 0,
        publishedLegacyTemplates: 1,
        templateIds: ["template-physics-1", "template-physics-2"],
      } as never)
      .mockResolvedValueOnce({
        domain: "CHEMISTRY",
        seededStarterTemplates: 1,
        submittedStarterTemplates: 0,
        createdLegacyTemplates: 0,
        submittedLegacyTemplates: 0,
        publishedLegacyTemplates: 0,
        templateIds: ["template-chemistry-1"],
      } as never);

    const result = await service.seedCatalogProgressMatrix("tenant-1", "user-1", {
      domains: ["PHYSICS", "CHEMISTRY"],
    });

    expect(result.processedDomains).toEqual(["PHYSICS", "CHEMISTRY"]);
    expect(result.totalSeededStarterTemplates).toBe(2);
    expect(result.totalCreatedLegacyTemplates).toBe(1);
    expect(result.totalPublishedLegacyTemplates).toBe(1);
    expect(result.templateIds).toEqual([
      "template-physics-1",
      "template-physics-2",
      "template-chemistry-1",
    ]);
  });
});
