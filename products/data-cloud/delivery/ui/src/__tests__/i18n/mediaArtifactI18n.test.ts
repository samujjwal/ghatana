/**
 * Tests for MediaArtifact i18n keys (Pass 11 i18n fixes).
 *
 * @doc.type module
 * @doc.purpose Validate MediaArtifact i18n keys are present
 * @doc.layer product
 * @doc.pattern Test
 */
import { describe, expect, it } from "vitest";
import enUS from "../../lib/i18n/locales/en-US.json";
import enGB from "../../lib/i18n/locales/en-GB.json";

describe("MediaArtifact i18n Keys", () => {
  it("should have all required MediaArtifact keys in en-US", () => {
    expect(enUS.mediaArtifact).toBeDefined();
    expect(enUS.mediaArtifact.title).toBeDefined();
    expect(enUS.mediaArtifact.create).toBeDefined();
    expect(enUS.mediaArtifact.processingState).toBeDefined();
    expect(enUS.mediaArtifact.lifecycle).toBeDefined();
  });

  it("should have all required MediaArtifact keys in en-GB", () => {
    expect(enGB.mediaArtifact).toBeDefined();
    expect(enGB.mediaArtifact.title).toBeDefined();
    expect(enGB.mediaArtifact.create).toBeDefined();
    expect(enGB.mediaArtifact.processingState).toBeDefined();
    expect(enGB.mediaArtifact.lifecycle).toBeDefined();
  });

  it("should have consistent keys across locales", () => {
    const enUSKeys = Object.keys(enUS.mediaArtifact || {});
    const enGBKeys = Object.keys(enGB.mediaArtifact || {});

    expect(enUSKeys).toEqual(enGBKeys);
  });
});
