/**
 * Tests for RuntimeCapabilityRouteGate i18n keys (Pass 11 i18n fixes).
 *
 * @doc.type module
 * @doc.purpose Validate RuntimeCapabilityRouteGate i18n keys are present
 * @doc.layer product
 * @doc.pattern Test
 */
import { describe, expect, it } from "vitest";
import enUS from "../../lib/i18n/locales/en-US.json";
import enGB from "../../lib/i18n/locales/en-GB.json";

describe("RuntimeCapabilityRouteGate i18n Keys", () => {
  it("should have all required runtimeGate keys in en-US", () => {
    expect(enUS.runtimeGate).toBeDefined();
    expect(enUS.runtimeGate.defaultSurfaceName).toBeDefined();
    expect(enUS.runtimeGate.preview).toBeDefined();
    expect(enUS.runtimeGate.degraded).toBeDefined();
    expect(enUS.runtimeGate.loadingMessage).toBeDefined();
  });

  it("should have all required runtimeGate keys in en-GB", () => {
    expect(enGB.runtimeGate).toBeDefined();
    expect(enGB.runtimeGate.defaultSurfaceName).toBeDefined();
    expect(enGB.runtimeGate.preview).toBeDefined();
    expect(enGB.runtimeGate.degraded).toBeDefined();
    expect(enGB.runtimeGate.loadingMessage).toBeDefined();
  });

  it("should have consistent runtimeGate keys across locales", () => {
    const enUSKeys = Object.keys(enUS.runtimeGate || {});
    const enGBKeys = Object.keys(enGB.runtimeGate || {});

    expect(enUSKeys).toEqual(enGBKeys);
  });
});
