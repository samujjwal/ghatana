import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("layout i18n guard", () => {
  it("uses i18n keys for key shell labels instead of hardcoded strings", () => {
    const layoutPath = path.join(__dirname, "../../layouts/DefaultLayout.tsx");
    const source = fs.readFileSync(layoutPath, "utf-8");

    expect(source).toMatch(/t\(["']layout\.searchPlaceholder["']\)/);
    expect(source).toMatch(/t\(["']layout\.collapse["']\)/);
    expect(source).toMatch(/t\(["']layout\.viewModePresetTitle["']\)/);
    expect(source).toMatch(/t\(["']layout\.viewModePresetDescription["']\)/);
    expect(source).toMatch(/t\(["']layout\.footerProduct["']\)/);

    expect(source).not.toContain(">Search...</span>");
    expect(source).not.toContain(">Collapse</span>");
    expect(source).not.toContain("View mode preset");
  });

  it("does not reintroduce Product mode wording in active UI source", () => {
    const filesToGuard = [
      path.join(__dirname, "../../layouts/DefaultLayout.tsx"),
      path.join(__dirname, "../../lib/auth/session.ts"),
      path.join(__dirname, "../../pages/IntelligentHub.tsx"),
    ] as const;

    for (const filePath of filesToGuard) {
      const source = fs.readFileSync(filePath, "utf-8");
      expect(
        source,
        `Found forbidden Product mode wording in ${path.basename(filePath)}`,
      ).not.toContain("Product mode");
    }
  });
});
