/**
 * @doc.type service
 * @doc.purpose VR Lab management service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import { v4 as uuidv4 } from 'uuid';
import type {
  VRLabService,
  TenantId,
  UserId,
  PaginatedResult,
  PaginationArgs,
} from '@ghatana/tutorputor-contracts/v1';
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
} from '@ghatana/tutorputor-contracts/v1';

export class VRLabServiceImpl implements VRLabService {
  constructor(private prisma: PrismaClient) {}

  async createLab(args: {
    tenantId: TenantId;
    userId: UserId;
    data: CreateVRLabRequest;
  }): Promise<VRLab> {
    const { tenantId, userId, data } = args;

    const slug = this.generateSlug(data.title);

    const lab = await this.prisma.vRLab.create({
      data: {
        id: uuidv4(),
        tenantId,
        slug,
        title: data.title,
        description: data.description,
        category: data.category,
        difficulty: data.difficulty,
        thumbnailUrl: data.thumbnailUrl,
        estimatedDuration: data.estimatedDuration,
        tags: data.tags || [],
        requiredDevices: ['quest_2', 'quest_3', 'desktop'],
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
          orderBy: { order: 'asc' },
        },
        objectives: {
          orderBy: { order: 'asc' },
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
    const { category, difficulty, isPublished, search, page = 1, limit = 20 } = params;

    const where: any = { tenantId };

    if (category) where.category = category;
    if (difficulty) where.difficulty = difficulty;
    if (isPublished !== undefined) where.isPublished = isPublished;
    if (search) {
      where.OR = [
        { title: { contains: search, mode: 'insensitive' } },
        { description: { contains: search, mode: 'insensitive' } },
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
        orderBy: { createdAt: 'desc' },
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
        ...(data.title && { title: data.title, slug: this.generateSlug(data.title) }),
        ...(data.description && { description: data.description }),
        ...(data.category && { category: data.category }),
        ...(data.difficulty && { difficulty: data.difficulty }),
        ...(data.thumbnailUrl && { thumbnailUrl: data.thumbnailUrl }),
        ...(data.estimatedDuration && { estimatedDuration: data.estimatedDuration }),
        ...(data.tags && { tags: data.tags }),
        ...(data.isPublished !== undefined && { isPublished: data.isPublished }),
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
      throw new Error('Lab not found');
    }

    if (lab.scenes.length === 0) {
      throw new Error('Cannot publish lab without scenes');
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
        id: uuidv4(),
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
        ...(data.estimatedDuration && { estimatedDuration: data.estimatedDuration }),
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
    data: Omit<VRInteractable, 'id' | 'sceneId'>;
  }): Promise<VRInteractable> {
    const { sceneId, data } = args;

    const interactable = await this.prisma.vRInteractable.create({
      data: {
        id: uuidv4(),
        sceneId,
        name: data.name,
        type: data.type,
        position: data.position as any,
        rotation: data.rotation as any,
        scale: data.scale as any,
        modelUrl: data.modelUrl,
        materialOverrides: data.materialOverrides as any,
        allowedInteractions: data.allowedInteractions,
        interactionRange: data.interactionRange,
        behavior: data.behavior as any,
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
        ...(data.position && { position: data.position as any }),
        ...(data.rotation && { rotation: data.rotation as any }),
        ...(data.scale && { scale: data.scale as any }),
        ...(data.modelUrl && { modelUrl: data.modelUrl }),
        ...(data.materialOverrides && { materialOverrides: data.materialOverrides as any }),
        ...(data.allowedInteractions && { allowedInteractions: data.allowedInteractions }),
        ...(data.interactionRange && { interactionRange: data.interactionRange }),
        ...(data.behavior && { behavior: data.behavior as any }),
        ...(data.tooltip && { tooltip: data.tooltip }),
        ...(data.audioFeedbackUrl && { audioFeedbackUrl: data.audioFeedbackUrl }),
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
    data: Omit<VRLabObjective, 'id' | 'labId'>;
  }): Promise<VRLabObjective> {
    const { labId, data } = args;

    const objective = await this.prisma.vRLabObjective.create({
      data: {
        id: uuidv4(),
        labId,
        title: data.title,
        description: data.description,
        order: data.order,
        type: data.type,
        criteria: data.criteria as any,
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
        ...(data.criteria && { criteria: data.criteria as any }),
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
    return title
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      + '-' + uuidv4().slice(0, 8);
  }

  private mapToVRLab(lab: any): VRLab {
    return {
      id: lab.id,
      slug: lab.slug,
      title: lab.title,
      description: lab.description,
      category: lab.category,
      difficulty: lab.difficulty,
      scenes: lab.scenes?.map((s: any) => this.mapToVRScene(s)) || [],
      objectives: lab.objectives?.map((o: any) => this.mapToVRLabObjective(o)) || [],
      thumbnailUrl: lab.thumbnailUrl,
      previewVideoUrl: lab.previewVideoUrl,
      estimatedDuration: lab.estimatedDuration,
      requiredDevices: lab.requiredDevices,
      minRequirements: lab.minRequirements,
      completionRate: lab.completionRate,
      averageRating: lab.averageRating,
      totalSessions: lab.totalSessions,
      isPublished: lab.isPublished,
      createdAt: lab.createdAt.toISOString(),
      updatedAt: lab.updatedAt.toISOString(),
      createdBy: lab.createdBy,
      tags: lab.tags,
      prerequisites: lab.prerequisites,
    };
  }

  private mapToVRScene(scene: any): VRScene {
    return {
      id: scene.id,
      labId: scene.labId,
      name: scene.name,
      description: scene.description,
      order: scene.order,
      environmentUrl: scene.environmentUrl,
      skyboxUrl: scene.skyboxUrl,
      lightingPreset: scene.lightingPreset,
      interactables: scene.interactables?.map((i: any) => this.mapToVRInteractable(i)) || [],
      spawnPoints: scene.spawnPoints,
      ambientSoundUrl: scene.ambientSoundUrl,
      narrationUrl: scene.narrationUrl,
      estimatedDuration: scene.estimatedDuration,
    };
  }

  private mapToVRInteractable(interactable: any): VRInteractable {
    return {
      id: interactable.id,
      sceneId: interactable.sceneId,
      name: interactable.name,
      type: interactable.type,
      position: interactable.position,
      rotation: interactable.rotation,
      scale: interactable.scale,
      modelUrl: interactable.modelUrl,
      materialOverrides: interactable.materialOverrides,
      allowedInteractions: interactable.allowedInteractions,
      interactionRange: interactable.interactionRange,
      behavior: interactable.behavior,
      tooltip: interactable.tooltip,
      audioFeedbackUrl: interactable.audioFeedbackUrl,
    };
  }

  private mapToVRLabObjective(objective: any): VRLabObjective {
    return {
      id: objective.id,
      labId: objective.labId,
      title: objective.title,
      description: objective.description,
      order: objective.order,
      type: objective.type,
      criteria: objective.criteria,
      hints: objective.hints,
      points: objective.points,
      isOptional: objective.isOptional,
    };
  }
}
