/**
 * Simulation Starter Packaging
 *
 * Bridges curated starter simulations with tenant-scoped manifest drafting
 * and VR export packaging, while remaining compatible with legacy preset ids.
 *
 * @doc.type module
 * @doc.purpose Reuse starter catalog data for bootstrap and export flows
 * @doc.layer product
 * @doc.pattern Packaging
 */

import type { SimulationManifest } from "@tutorputor/contracts/v1/simulation";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import {
  type UnityPackage,
  type WebXRPackage,
  VRSimulationExporter,
} from "./export/vr-exporter";
import {
  resolveSimulationStarter,
  type SimulationStarter,
} from "./starter-catalog";

export interface CreateSimulationStarterManifestInput {
  starterRef: string;
  manifestId?: string;
  tenantId?: TenantId;
  authorId?: UserId;
  title?: string;
  description?: string;
}

export interface ResolvedSimulationStarter {
  requestedRef: string;
  matchedBy: "starter_id" | "legacy_preset";
  starter: SimulationStarter;
}

export interface SimulationStarterExportPackage {
  requestedRef: string;
  matchedBy: "starter_id" | "legacy_preset";
  starterId: string;
  exportFormat: "manifest" | "webxr" | "unity";
  manifest: SimulationManifest;
  packageData: SimulationManifest | WebXRPackage | UnityPackage;
}

export function resolveSimulationStarterReference(
  starterRef: string,
): ResolvedSimulationStarter | null {
  const resolved = resolveSimulationStarter(starterRef);
  if (!resolved) {
    return null;
  }

  return {
    requestedRef: starterRef,
    matchedBy: resolved.matchedBy,
    starter: resolved.starter,
  };
}

export function createSimulationStarterManifest(
  input: CreateSimulationStarterManifestInput,
): SimulationManifest | null {
  const resolved = resolveSimulationStarterReference(input.starterRef);
  if (!resolved) {
    return null;
  }

  const manifest = structuredClone(resolved.starter.manifest);

  if (input.manifestId) {
    manifest.id = input.manifestId as SimulationManifest["id"];
  }
  if (input.tenantId) {
    manifest.tenantId = input.tenantId;
  }
  if (input.authorId) {
    manifest.authorId = input.authorId;
  }
  if (input.title) {
    manifest.title = input.title;
  }
  if (input.description) {
    manifest.description = input.description;
  }
  manifest.updatedAt = new Date().toISOString();

  return manifest;
}

export function exportSimulationStarterPackage(input: {
  starterRef: string;
  format?: "manifest" | "webxr" | "unity";
  manifestId?: string;
  tenantId?: TenantId;
  authorId?: UserId;
  title?: string;
  description?: string;
}): SimulationStarterExportPackage | null {
  const resolved = resolveSimulationStarterReference(input.starterRef);
  if (!resolved) {
    return null;
  }

  const manifest = createSimulationStarterManifest(input);
  if (!manifest) {
    return null;
  }

  const exporter = new VRSimulationExporter();
  const exportFormat = input.format ?? "manifest";
  const packageData =
    exportFormat === "webxr"
      ? exporter.exportToWebXR(manifest)
      : exportFormat === "unity"
        ? exporter.exportToUnity(manifest)
        : manifest;

  return {
    requestedRef: input.starterRef,
    matchedBy: resolved.matchedBy,
    starterId: resolved.starter.id,
    exportFormat,
    manifest,
    packageData,
  };
}
