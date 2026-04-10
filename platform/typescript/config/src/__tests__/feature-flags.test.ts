import { describe, it, expect } from "vitest";
import { createFeatureFlags } from "../feature-flags.js";

describe("createFeatureFlags — boolean flags", () => {
  const flags = createFeatureFlags({
    featureA: { type: "boolean", enabled: true },
    featureB: { type: "boolean", enabled: false },
  });

  it("returns true for enabled boolean flag", () => {
    expect(flags.isEnabled("featureA")).toBe(true);
  });

  it("returns false for disabled boolean flag", () => {
    expect(flags.isEnabled("featureB")).toBe(false);
  });

  it("getAll reflects current flag states", () => {
    const all = flags.getAll();
    expect(all.featureA).toBe(true);
    expect(all.featureB).toBe(false);
  });
});

describe("createFeatureFlags — rollout flags", () => {
  const flags = createFeatureFlags({
    betaFeature: { type: "rollout", percentage: 50 },
  });

  it("returns a boolean for a rollout flag", () => {
    const result = flags.isEnabled("betaFeature", { userId: "user-1" });
    expect(typeof result).toBe("boolean");
  });

  it("is deterministic for the same userId", () => {
    const r1 = flags.isEnabled("betaFeature", { userId: "test-user" });
    const r2 = flags.isEnabled("betaFeature", { userId: "test-user" });
    expect(r1).toBe(r2);
  });

  it("0% rollout never enables", () => {
    const neverFlags = createFeatureFlags({
      neverOn: { type: "rollout", percentage: 0 },
    });
    for (let i = 0; i < 100; i++) {
      expect(neverFlags.isEnabled("neverOn", { userId: `user-${i}` })).toBe(false);
    }
  });

  it("100% rollout always enables", () => {
    const alwaysFlags = createFeatureFlags({
      alwaysOn: { type: "rollout", percentage: 100 },
    });
    for (let i = 0; i < 100; i++) {
      expect(alwaysFlags.isEnabled("alwaysOn", { userId: `user-${i}` })).toBe(true);
    }
  });
});

describe("createFeatureFlags — variant flags", () => {
  const flags = createFeatureFlags({
    theme: {
      type: "variant",
      variants: ["light", "dark", "system"] as const,
      default: "light",
    },
  });

  it("getVariant returns one of the declared variants", () => {
    const variant = flags.getVariant("theme", { userId: "user-1" });
    expect(["light", "dark", "system"]).toContain(variant);
  });

  it("getVariant is deterministic for the same userId", () => {
    const v1 = flags.getVariant("theme", { userId: "user-stable" });
    const v2 = flags.getVariant("theme", { userId: "user-stable" });
    expect(v1).toBe(v2);
  });

  it("getVariant respects override map", () => {
    const overrideFlags = createFeatureFlags({
      theme: {
        type: "variant",
        variants: ["light", "dark"] as const,
        default: "light",
        overrides: { dark: ["vip-user"] },
      },
    });
    expect(overrideFlags.getVariant("theme", { userId: "vip-user" })).toBe("dark");
  });

  it("throws when getVariant called on non-variant flag", () => {
    const mixed = createFeatureFlags({
      bool: { type: "boolean", enabled: true },
    });
    expect(() => mixed.getVariant("bool" as never)).toThrow();
  });
});

describe("createFeatureFlags — getAll", () => {
  it("returns snapshot of all flags", () => {
    const flags = createFeatureFlags({
      a: { type: "boolean", enabled: true },
      b: { type: "boolean", enabled: false },
      c: { type: "rollout", percentage: 100 },
    });
    const all = flags.getAll({ userId: "u1" });
    expect(all.a).toBe(true);
    expect(all.b).toBe(false);
    expect(all.c).toBe(true);
  });
});
