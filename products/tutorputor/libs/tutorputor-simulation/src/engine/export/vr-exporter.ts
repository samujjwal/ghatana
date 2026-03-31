/**
 * VR Simulation Exporter
 *
 * Converts USP simulation manifests into VR-ready transport packages.
 *
 * @doc.type service
 * @doc.purpose Export simulation manifests to WebXR and Unity-friendly packages
 * @doc.layer product
 * @doc.pattern VR Export
 */

import type { SimulationManifest, SimEntity } from "@tutorputor/contracts/v1/simulation";

export interface VR3DPosition {
  x: number;
  y: number;
  z: number;
}

export interface VR3DRotation {
  x: number;
  y: number;
  z: number;
  w: number;
}

export interface VR3DScale {
  x: number;
  y: number;
  z: number;
}

export interface VRRequirements {
  minGpuMemory: number;
  minRam: number;
  minRefreshRate: number;
  requiresControllers: boolean;
  requiresHandTracking: boolean;
  requiresPassthrough: boolean;
}

export type VRInteractionType =
  | "grab"
  | "point"
  | "teleport"
  | "rotate"
  | "scale"
  | "trigger"
  | "voice"
  | "gesture";

export interface VRExportNode {
  id: string;
  name: string;
  entityType: string;
  position: VR3DPosition;
  rotation: VR3DRotation;
  scale: VR3DScale;
  modelHint: "sphere" | "cube" | "capsule" | "plane" | "graph-node";
  material: {
    color?: string;
    emissive?: string;
  };
  interactions: VRInteractionType[];
}

export interface WebXRPackage {
  format: "webxr";
  manifestId: string;
  scene: {
    title: string;
    description?: string;
    environment: "lab" | "classroom" | "outdoor" | "abstract";
    nodes: VRExportNode[];
    cameras: Array<{
      id: string;
      position: VR3DPosition;
      lookAt: VR3DPosition;
    }>;
  };
  interactions: Array<{
    stepId: string;
    description?: string;
    allowedInteractions: VRInteractionType[];
  }>;
  requirements: VRRequirements;
}

export interface UnityPackage {
  format: "unity";
  manifestId: string;
  prefabs: Array<{
    id: string;
    prefabName: string;
    position: VR3DPosition;
    scale: VR3DScale;
    components: string[];
  }>;
  scripts: Array<{
    fileName: string;
    behavior: string;
  }>;
  scene: {
    sceneName: string;
    lightingPreset: string;
    cameraStart: VR3DPosition;
  };
}

export class VRSimulationExporter {
  exportToWebXR(manifest: SimulationManifest): WebXRPackage {
    const nodes = manifest.initialEntities.map((entity) =>
      this.convertEntityToVRNode(entity),
    );

    return {
      format: "webxr",
      manifestId: manifest.id,
      scene: {
        title: manifest.title,
        ...(manifest.description ? { description: manifest.description } : {}),
        environment: inferEnvironment(manifest),
        nodes,
        cameras: [
          {
            id: "main-camera",
            position: {
              x: manifest.canvas.width / 2,
              y: manifest.canvas.height / 2,
              z: Math.max(manifest.canvas.width, manifest.canvas.height),
            },
            lookAt: {
              x: manifest.canvas.width / 2,
              y: manifest.canvas.height / 2,
              z: 0,
            },
          },
        ],
      },
      interactions: manifest.steps.map((step) => ({
        stepId: step.id,
        ...(step.description ? { description: step.description } : {}),
        allowedInteractions: deriveAllowedInteractions(step.actions.length),
      })),
      requirements: {
        minGpuMemory: 2048,
        minRam: 4096,
        minRefreshRate: 72,
        requiresControllers: true,
        requiresHandTracking: false,
        requiresPassthrough: false,
      },
    };
  }

  exportToUnity(manifest: SimulationManifest): UnityPackage {
    return {
      format: "unity",
      manifestId: manifest.id,
      prefabs: manifest.initialEntities.map((entity) => ({
        id: entity.id,
        prefabName: toPrefabName(entity),
        position: toPosition(entity),
        scale: toScale(entity),
        components: [
          "Transform",
          "MeshRenderer",
          ...(isPhysicsEntity(entity) ? ["Rigidbody"] : []),
          "XRGrabInteractable",
        ],
      })),
      scripts: manifest.steps.map((step, index) => ({
        fileName: `Step${index + 1}Controller.cs`,
        behavior: step.title ?? `Step ${index + 1}`,
      })),
      scene: {
        sceneName: `${manifest.title.replace(/\s+/g, "")}VRScene`,
        lightingPreset: inferEnvironment(manifest),
        cameraStart: {
          x: manifest.canvas.width / 2,
          y: manifest.canvas.height / 2,
          z: Math.max(manifest.canvas.width, manifest.canvas.height),
        },
      },
    };
  }

  private convertEntityToVRNode(entity: SimEntity): VRExportNode {
    return {
      id: entity.id,
      name: entity.label ?? entity.id,
      entityType: entity.type,
      position: toPosition(entity),
      rotation: {
        x: 0,
        y: 0,
        z: entity.rotation ?? 0,
        w: 1,
      },
      scale: toScale(entity),
      modelHint: inferModelHint(entity.type),
      material: {
        ...(entity.color ? { color: entity.color } : {}),
        ...(entity.strokeColor ? { emissive: entity.strokeColor } : {}),
      },
      interactions: inferInteractions(entity.type),
    };
  }
}

function inferEnvironment(manifest: SimulationManifest): WebXRPackage["scene"]["environment"] {
  switch (manifest.domain) {
    case "MEDICINE":
    case "CHEMISTRY":
      return "lab";
    case "BIOLOGY":
      return "outdoor";
    default:
      return "classroom";
  }
}

function toPosition(entity: SimEntity): VR3DPosition {
  return {
    x: entity.x,
    y: entity.y,
    z: entity.z ?? 0,
  };
}

function toScale(entity: SimEntity): VR3DScale {
  return {
    x: entity.width ?? entity.scale ?? 1,
    y: entity.height ?? entity.scale ?? 1,
    z: entity.scale ?? 1,
  };
}

function inferModelHint(entityType: string): VRExportNode["modelHint"] {
  if (entityType.includes("node")) return "graph-node";
  if (entityType.includes("boundary")) return "plane";
  if (entityType.includes("cell")) return "sphere";
  return "cube";
}

function inferInteractions(entityType: string): VRInteractionType[] {
  if (entityType.includes("pointer") || entityType.includes("sensor")) {
    return ["point", "trigger"];
  }
  if (entityType.includes("boundary")) {
    return ["point"];
  }
  return ["grab", "point", "trigger"];
}

function deriveAllowedInteractions(actionCount: number): VRInteractionType[] {
  if (actionCount >= 3) {
    return ["grab", "point", "trigger", "rotate"];
  }
  if (actionCount === 2) {
    return ["grab", "point", "trigger"];
  }
  return ["point", "trigger"];
}

function toPrefabName(entity: SimEntity): string {
  return `${entity.type.replace(/[^a-zA-Z0-9]/g, "")}Prefab`;
}

function isPhysicsEntity(entity: SimEntity): boolean {
  return (
    entity.type === "rigidBody" ||
    entity.type === "particle" ||
    entity.type === "spring"
  );
}
