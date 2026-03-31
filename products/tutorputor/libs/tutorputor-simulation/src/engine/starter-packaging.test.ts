import { describe, expect, it } from "vitest";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import {
  createSimulationStarterManifest,
  exportSimulationStarterPackage,
  resolveSimulationStarterReference,
} from "./starter-packaging";

describe("starter packaging", () => {
  it("creates tenant-scoped manifest drafts from starter ids", () => {
    const manifest = createSimulationStarterManifest({
      starterRef: "starter-newton-cart",
      manifestId: "draft-cart",
      tenantId: "tenant-1" as TenantId,
      authorId: "user-1" as UserId,
      title: "Newton Cart Draft",
    });

    expect(manifest).not.toBeNull();
    expect(manifest?.id).toBe("draft-cart");
    expect(manifest?.tenantId).toBe("tenant-1");
    expect(manifest?.authorId).toBe("user-1");
    expect(manifest?.title).toBe("Newton Cart Draft");
  });

  it("resolves legacy preset references for bootstrap flows", () => {
    const resolved = resolveSimulationStarterReference("preset-photosynthesis");

    expect(resolved?.starter.id).toBe("starter-photosynthesis-cycle");
    expect(resolved?.matchedBy).toBe("legacy_preset");
  });

  it("exports manifest bundles from legacy preset references", () => {
    const exported = exportSimulationStarterPackage({
      starterRef: "preset-binary-search",
      format: "manifest",
      tenantId: "tenant-2" as TenantId,
    });

    expect(exported?.starterId).toBe("starter-binary-search");
    expect(exported?.exportFormat).toBe("manifest");
    expect(exported?.manifest.tenantId).toBe("tenant-2");
  });

  it("exports VR bundles from curated starters", () => {
    const webxr = exportSimulationStarterPackage({
      starterRef: "starter-cardiac-cycle",
      format: "webxr",
    });
    const unity = exportSimulationStarterPackage({
      starterRef: "starter-cardiac-cycle",
      format: "unity",
    });

    expect(webxr?.exportFormat).toBe("webxr");
    expect((webxr?.packageData as { format?: string }).format).toBe("webxr");
    expect(unity?.exportFormat).toBe("unity");
    expect((unity?.packageData as { format?: string }).format).toBe("unity");
  });
});
