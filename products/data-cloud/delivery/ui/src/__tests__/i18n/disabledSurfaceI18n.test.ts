/**
 * Tests for DisabledSurfacePage i18n keys (Pass 11 i18n fixes).
 *
 * @doc.type module
 * @doc.purpose Validate DisabledSurfacePage i18n keys are present
 * @doc.layer product
 * @doc.pattern Test
 */
import { describe, expect, it } from "vitest";
import enUS from "../../lib/i18n/locales/en-US.json";
import enGB from "../../lib/i18n/locales/en-GB.json";

describe("DisabledSurfacePage i18n Keys", () => {
  it("should have all required disabledSurface keys in en-US", () => {
    expect(enUS.disabledSurface).toBeDefined();
    expect(enUS.disabledSurface.degraded).toBeDefined();
    expect(enUS.disabledSurface.unavailable).toBeDefined();
    expect(enUS.disabledSurface.misconfigured).toBeDefined();
    expect(enUS.disabledSurface.disabled).toBeDefined();
    expect(enUS.disabledSurface.disabledMessage).toBeDefined();
    expect(enUS.disabledSurface.degradedMessage).toBeDefined();
    expect(enUS.disabledSurface.unavailableMessage).toBeDefined();
    expect(enUS.disabledSurface.misconfiguredMessage).toBeDefined();
    expect(enUS.disabledSurface.contactAdmin).toBeDefined();
    expect(enUS.disabledSurface.ownerPlane).toBeDefined();
    expect(enUS.disabledSurface.runtimeProfile).toBeDefined();
    expect(enUS.disabledSurface.limitations).toBeDefined();
    expect(enUS.disabledSurface.requiredDependencies).toBeDefined();
    expect(enUS.disabledSurface.dependencyProbes).toBeDefined();
    expect(enUS.disabledSurface.affectedDependencies).toBeDefined();
    expect(enUS.disabledSurface.nextAction).toBeDefined();
    expect(enUS.disabledSurface.viewRemediation).toBeDefined();
    expect(enUS.disabledSurface.goBack).toBeDefined();
    expect(enUS.disabledSurface.goToHome).toBeDefined();
  });

  it("should have all required disabledSurface keys in en-GB", () => {
    expect(enGB.disabledSurface).toBeDefined();
    expect(enGB.disabledSurface.degraded).toBeDefined();
    expect(enGB.disabledSurface.unavailable).toBeDefined();
    expect(enGB.disabledSurface.misconfigured).toBeDefined();
    expect(enGB.disabledSurface.disabled).toBeDefined();
    expect(enGB.disabledSurface.disabledMessage).toBeDefined();
    expect(enGB.disabledSurface.degradedMessage).toBeDefined();
    expect(enGB.disabledSurface.unavailableMessage).toBeDefined();
    expect(enGB.disabledSurface.misconfiguredMessage).toBeDefined();
    expect(enGB.disabledSurface.contactAdmin).toBeDefined();
    expect(enGB.disabledSurface.ownerPlane).toBeDefined();
    expect(enGB.disabledSurface.runtimeProfile).toBeDefined();
    expect(enGB.disabledSurface.limitations).toBeDefined();
    expect(enGB.disabledSurface.requiredDependencies).toBeDefined();
    expect(enGB.disabledSurface.dependencyProbes).toBeDefined();
    expect(enGB.disabledSurface.affectedDependencies).toBeDefined();
    expect(enGB.disabledSurface.nextAction).toBeDefined();
    expect(enGB.disabledSurface.viewRemediation).toBeDefined();
    expect(enGB.disabledSurface.goBack).toBeDefined();
    expect(enGB.disabledSurface.goToHome).toBeDefined();
  });

  it("should have consistent disabledSurface keys across locales", () => {
    const enUSKeys = Object.keys(enUS.disabledSurface || {});
    const enGBKeys = Object.keys(enGB.disabledSurface || {});

    expect(enUSKeys).toEqual(enGBKeys);
  });
});
