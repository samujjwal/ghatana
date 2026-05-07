// @ts-nocheck
/**
 * @doc.type service
 * @doc.purpose VR Lab management service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import { randomUUID } from "crypto";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type {
  VRLabService,
  TenantId,
  UserId,
  PaginatedResult,
  PaginationArgs,
} from "@tutorputor/contracts/v1";
import type {
  VRLab,
  VRLabId,
  VRScene,
  VRSceneId,
  VRInteractable,
  VRLabObjective,
  CreateVRLabRequest,
  UpdateVRLabRequest,
  CreateVRSceneRequest,
  VRLabListParams,
} from "@tutorputor/contracts/v1";

export class VRLabServiceImpl implements VRLabService {
  constructor(private prisma: TutorPrismaClient) {}

  async createLab(args: {
    tenantId: TenantId;
    userId: UserId;
    data: CreateVRLabRequest;
  }): Promise<VRLab> {
    const { tenantId, userId, data } = args;

    const slug = this.generateSlug(data.title);

    const lab = await this.prisma.vRLab.create({
      data: {
        id: randomUUID(),
        tenantId,
        slug,
        title: data.title,
        description: data.description,
        category: data.category,
        difficulty: data.difficulty,
        thumbnailUrl: data.thumbnailUrl,
        estimatedDuration: data.estimatedDuration,
        tags: data.tags || [],
        requiredDevices: ["quest_2", "quest_3", "desktop"],
        minRequirements: {
          minGpuMemory: 4096,
          minRam: 8192,
          minRefreshRate: 72,
          requiresControllers: true,
          requiresHandTracking: false,
          requiresPassthrough: false,
        },
        isPublished: false,
        createdBy: userId,
        memberCount: 0,
        completionRate: 0,
        averageRating: 0,
        totalSessions: 0,
      },
      include: {
        scenes: true,
        objectives: true,
      },
    });

    return this.mapToVRLab(lab);
  }

  async getLabById(args: {
    tenantId: TenantId;
    labId: VRLabId;
  }): Promise<VRLab | null> {
    const { tenantId, labId } = args;

    const lab = await this.prisma.vRLab.findFirst({
      where: {
        id: labId,
        tenantId,
      },
      include: {
        scenes: {
          include: {
            interactables: true,
          },
          orderBy: { order: "asc" },
        },
        objectives: {
          orderBy: { order: "asc" },
        },
      },
    });

    return lab ? this.mapToVRLab(lab) : null;
  }

  async listLabs(args: {
    tenantId: TenantId;
    params: VRLabListParams;
  }): Promise<PaginatedResult<VRLab>> {
    const { tenantId, params } = args;
    const {
      category,
      difficulty,
      isPublished,
      search,
      page = 1,
      limit = 20,
    } = params;

    const where: Record<string, unknown> = { tenantId };

    if (category) where.category = category;
    if (difficulty) where.difficulty = difficulty;
    if (isPublished !== undefined) where.isPublished = isPublished;
    if (search) {
      where.OR = [
        { title: { contains: search, mode: "insensitive" } },
        { description: { contains: search, mode: "insensitive" } },
        { tags: { hasSome: [search] } },
      ];
    }

    const [labs, total] = await Promise.all([
      this.prisma.vRLab.findMany({
        where,
        include: {
          scenes: true,
          objectives: true,
        },
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: "desc" },
      }),
      this.prisma.vRLab.count({ where }),
    ]);

    return {
      items: labs.map((lab) => this.mapToVRLab(lab)),
      total,
      page,
      limit,
      hasMore: page * limit < total,
    };
  }

  async updateLab(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
    data: UpdateVRLabRequest;
  }): Promise<VRLab> {
    const { tenantId, labId, data } = args;

    const lab = await this.prisma.vRLab.update({
      where: {
        id: labId,
        tenantId,
      },
      data: {
        ...(data.title && {
          title: data.title,
          slug: this.generateSlug(data.title),
        }),
        ...(data.description && { description: data.description }),
        ...(data.category && { category: data.category }),
        ...(data.difficulty && { difficulty: data.difficulty }),
        ...(data.thumbnailUrl && { thumbnailUrl: data.thumbnailUrl }),
        ...(data.estimatedDuration && {
          estimatedDuration: data.estimatedDuration,
        }),
        ...(data.tags && { tags: data.tags }),
        ...(data.isPublished !== undefined && {
          isPublished: data.isPublished,
        }),
        updatedAt: new Date(),
      },
      include: {
        scenes: true,
        objectives: true,
      },
    });

    return this.mapToVRLab(lab);
  }

  async deleteLab(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
  }): Promise<void> {
    const { tenantId, labId } = args;

    // Delete in order: interactables -> scenes -> objectives -> lab
    await this.prisma.$transaction(async (tx) => {
      // Get all scenes
      const scenes = await tx.vRScene.findMany({
        where: { labId },
        select: { id: true },
      });

      // Delete interactables
      await tx.vRInteractable.deleteMany({
        where: {
          sceneId: { in: scenes.map((s) => s.id) },
        },
      });

      // Delete scenes
      await tx.vRScene.deleteMany({
        where: { labId },
      });

      // Delete objectives
      await tx.vRLabObjective.deleteMany({
        where: { labId },
      });

      // Delete lab
      await tx.vRLab.delete({
        where: {
          id: labId,
          tenantId,
        },
      });
    });
  }

  async publishLab(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
  }): Promise<VRLab> {
    const { tenantId, labId } = args;

    // Validate lab has at least one scene
    const lab = await this.prisma.vRLab.findFirst({
      where: { id: labId, tenantId },
      include: { scenes: true },
    });

    if (!lab) {
      throw new Error("Lab not found");
    }

    if (lab.scenes.length === 0) {
      throw new Error("Cannot publish lab without scenes");
    }

    const updated = await this.prisma.vRLab.update({
      where: { id: labId },
      data: {
        isPublished: true,
        updatedAt: new Date(),
      },
      include: {
        scenes: true,
        objectives: true,
      },
    });

    return this.mapToVRLab(updated);
  }

  async addScene(args: {
    tenantId: TenantId;
    userId: UserId;
    data: CreateVRSceneRequest;
  }): Promise<VRScene> {
    const { data } = args;

    const scene = await this.prisma.vRScene.create({
      data: {
        id: randomUUID(),
        labId: data.labId,
        name: data.name,
        description: data.description,
        order: data.order,
        environmentUrl: data.environmentUrl,
        skyboxUrl: data.skyboxUrl,
        lightingPreset: data.lightingPreset,
        estimatedDuration: 10,
        spawnPoints: [{ x: 0, y: 0, z: 0 }],
      },
      include: {
        interactables: true,
      },
    });

    return this.mapToVRScene(scene);
  }

  async updateScene(args: {
    tenantId: TenantId;
    sceneId: VRSceneId;
    userId: UserId;
    data: Partial<VRScene>;
  }): Promise<VRScene> {
    const { sceneId, data } = args;

    const scene = await this.prisma.vRScene.update({
      where: { id: sceneId },
      data: {
        ...(data.name && { name: data.name }),
        ...(data.description && { description: data.description }),
        ...(data.order !== undefined && { order: data.order }),
        ...(data.environmentUrl && { environmentUrl: data.environmentUrl }),
        ...(data.skyboxUrl && { skyboxUrl: data.skyboxUrl }),
        ...(data.lightingPreset && { lightingPreset: data.lightingPreset }),
        ...(data.estimatedDuration && {
          estimatedDuration: data.estimatedDuration,
        }),
      },
      include: {
        interactables: true,
      },
    });

    return this.mapToVRScene(scene);
  }

  async deleteScene(args: {
    tenantId: TenantId;
    sceneId: VRSceneId;
    userId: UserId;
  }): Promise<void> {
    const { sceneId } = args;

    await this.prisma.$transaction(async (tx) => {
      await tx.vRInteractable.deleteMany({
        where: { sceneId },
      });
      await tx.vRScene.delete({
        where: { id: sceneId },
      });
    });
  }

  async addInteractable(args: {
    tenantId: TenantId;
    sceneId: VRSceneId;
    userId: UserId;
    data: Omit<VRInteractable, "id" | "sceneId">;
  }): Promise<VRInteractable> {
    const { sceneId, data } = args;

    const interactable = await this.prisma.vRInteractable.create({
      data: {
        id: randomUUID(),
        sceneId,
        name: data.name,
        type: data.type,
        position: data.position as Record<string, number>,
        rotation: data.rotation as Record<string, number>,
        scale: data.scale as Record<string, number>,
        modelUrl: data.modelUrl,
        materialOverrides: data.materialOverrides as Record<string, unknown>,
        allowedInteractions: data.allowedInteractions,
        interactionRange: data.interactionRange,
        behavior: data.behavior as Record<string, unknown>,
        tooltip: data.tooltip,
        audioFeedbackUrl: data.audioFeedbackUrl,
      },
    });

    return this.mapToVRInteractable(interactable);
  }

  async updateInteractable(args: {
    tenantId: TenantId;
    interactableId: string;
    userId: UserId;
    data: Partial<VRInteractable>;
  }): Promise<VRInteractable> {
    const { interactableId, data } = args;

    const interactable = await this.prisma.vRInteractable.update({
      where: { id: interactableId },
      data: {
        ...(data.name && { name: data.name }),
        ...(data.type && { type: data.type }),
        ...(data.position && {
          position: data.position as Record<string, number>,
        }),
        ...(data.rotation && {
          rotation: data.rotation as Record<string, number>,
        }),
        ...(data.scale && { scale: data.scale as Record<string, number> }),
        ...(data.modelUrl && { modelUrl: data.modelUrl }),
        ...(data.materialOverrides && {
          materialOverrides: data.materialOverrides as Record<string, unknown>,
        }),
        ...(data.allowedInteractions && {
          allowedInteractions: data.allowedInteractions,
        }),
        ...(data.interactionRange && {
          interactionRange: data.interactionRange,
        }),
        ...(data.behavior && {
          behavior: data.behavior as Record<string, unknown>,
        }),
        ...(data.tooltip && { tooltip: data.tooltip }),
        ...(data.audioFeedbackUrl && {
          audioFeedbackUrl: data.audioFeedbackUrl,
        }),
      },
    });

    return this.mapToVRInteractable(interactable);
  }

  async deleteInteractable(args: {
    tenantId: TenantId;
    interactableId: string;
    userId: UserId;
  }): Promise<void> {
    const { interactableId } = args;

    await this.prisma.vRInteractable.delete({
      where: { id: interactableId },
    });
  }

  async addObjective(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
    data: Omit<VRLabObjective, "id" | "labId">;
  }): Promise<VRLabObjective> {
    const { labId, data } = args;

    const objective = await this.prisma.vRLabObjective.create({
      data: {
        id: randomUUID(),
        labId,
        title: data.title,
        description: data.description,
        order: data.order,
        type: data.type,
        criteria: data.criteria as Record<string, unknown>,
        hints: data.hints,
        points: data.points,
        isOptional: data.isOptional,
      },
    });

    return this.mapToVRLabObjective(objective);
  }

  async updateObjective(args: {
    tenantId: TenantId;
    objectiveId: string;
    userId: UserId;
    data: Partial<VRLabObjective>;
  }): Promise<VRLabObjective> {
    const { objectiveId, data } = args;

    const objective = await this.prisma.vRLabObjective.update({
      where: { id: objectiveId },
      data: {
        ...(data.title && { title: data.title }),
        ...(data.description && { description: data.description }),
        ...(data.order !== undefined && { order: data.order }),
        ...(data.type && { type: data.type }),
        ...(data.criteria && {
          criteria: data.criteria as Record<string, unknown>,
        }),
        ...(data.hints && { hints: data.hints }),
        ...(data.points !== undefined && { points: data.points }),
        ...(data.isOptional !== undefined && { isOptional: data.isOptional }),
      },
    });

    return this.mapToVRLabObjective(objective);
  }

  async deleteObjective(args: {
    tenantId: TenantId;
    objectiveId: string;
    userId: UserId;
  }): Promise<void> {
    const { objectiveId } = args;

    await this.prisma.vRLabObjective.delete({
      where: { id: objectiveId },
    });
  }

  // ============================================
  // Private helper methods
  // ============================================

  private generateSlug(title: string): string {
    return (
      title
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-|-$/g, "") +
      "-" +
      randomUUID().slice(0, 8)
    );
  }

  private mapToVRLab(lab: Record<string, unknown>): VRLab {
    const scenes =
      (lab.scenes as Array<Record<string, unknown>> | undefined) ?? [];
    const objectives =
      (lab.objectives as Array<Record<string, unknown>> | undefined) ?? [];
    return {
      id: lab.id as string,
      slug: lab.slug as string,
      title: lab.title as string,
      description: lab.description as string,
      category: lab.category as string,
      difficulty: lab.difficulty as string,
      scenes: scenes.map((s) => this.mapToVRScene(s)),
      objectives: objectives.map((o) => this.mapToVRLabObjective(o)),
      thumbnailUrl: lab.thumbnailUrl as string | undefined,
      previewVideoUrl: lab.previewVideoUrl as string | undefined,
      estimatedDuration: lab.estimatedDuration as number,
      requiredDevices: lab.requiredDevices as string[],
      minRequirements: lab.minRequirements as Record<string, unknown>,
      completionRate: lab.completionRate as number,
      averageRating: lab.averageRating as number,
      totalSessions: lab.totalSessions as number,
      isPublished: lab.isPublished as boolean,
      createdAt: (lab.createdAt as Date).toISOString(),
      updatedAt: (lab.updatedAt as Date).toISOString(),
      createdBy: lab.createdBy as string,
      tags: lab.tags as string[],
      prerequisites: lab.prerequisites as string[],
    };
  }

  private mapToVRScene(scene: Record<string, unknown>): VRScene {
    const interactables =
      (scene.interactables as Array<Record<string, unknown>> | undefined) ?? [];
    return {
      id: scene.id as string,
      labId: scene.labId as string,
      name: scene.name as string,
      description: scene.description as string,
      order: scene.order as number,
      environmentUrl: scene.environmentUrl as string,
      skyboxUrl: scene.skyboxUrl as string,
      lightingPreset: scene.lightingPreset as string,
      interactables: interactables.map((i) => this.mapToVRInteractable(i)),
      spawnPoints: scene.spawnPoints as Array<Record<string, unknown>>,
      ambientSoundUrl: scene.ambientSoundUrl as string | undefined,
      narrationUrl: scene.narrationUrl as string | undefined,
      estimatedDuration: scene.estimatedDuration as number,
    };
  }

  private mapToVRInteractable(
    interactable: Record<string, unknown>,
  ): VRInteractable {
    return {
      id: interactable.id as string,
      sceneId: interactable.sceneId as string,
      name: interactable.name as string,
      type: interactable.type as string,
      position: interactable.position as Record<string, number>,
      rotation: interactable.rotation as Record<string, number>,
      scale: interactable.scale as Record<string, number>,
      modelUrl: interactable.modelUrl as string,
      materialOverrides: interactable.materialOverrides as Record<
        string,
        unknown
      >,
      allowedInteractions: interactable.allowedInteractions as string[],
      interactionRange: interactable.interactionRange as number,
      behavior: interactable.behavior as Record<string, unknown>,
      tooltip: interactable.tooltip as string | undefined,
      audioFeedbackUrl: interactable.audioFeedbackUrl as string | undefined,
    };
  }

  private mapToVRLabObjective(
    objective: Record<string, unknown>,
  ): VRLabObjective {
    return {
      id: objective.id as string,
      labId: objective.labId as string,
      title: objective.title as string,
      description: objective.description as string | undefined,
      order: objective.order as number,
      type: objective.type as string,
      criteria: objective.criteria as Record<string, unknown>,
      hints: objective.hints as string[] | undefined,
      points: objective.points as number,
      isOptional: objective.isOptional as boolean,
    };
  }
}
