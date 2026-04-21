import { describe, it, expect, beforeEach } from "vitest";
import {
  registerBrandPreset,
  registerBrandPresets,
  getBrandPreset,
  getBrandPresets,
  setBrandPresetLoader,
  loadBrandPresets,
  applyBrandPreset,
  loadAndApplyBrandPreset,
} from "../themeManager";
import type { BrandPreset, BrandPresetInput } from "../brandPresets";

describe("themeManager", () => {
  describe("registerBrandPreset", () => {
    it("should register a single brand preset", () => {
      const preset: BrandPresetInput = {
        id: "test-brand",
        name: "Test Brand",
        layers: [],
      };
      registerBrandPreset(preset);
      const retrieved = getBrandPreset("test-brand");
      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe("test-brand");
    });

    it("should throw on duplicate preset id", () => {
      const preset: BrandPresetInput = {
        id: "duplicate",
        name: "Duplicate",
        layers: [],
      };
      registerBrandPreset(preset);
      expect(() => registerBrandPreset(preset)).toThrow();
    });
  });

  describe("registerBrandPresets", () => {
    it("should register multiple presets", () => {
      const presets: BrandPresetInput[] = [
        { id: "brand1", name: "Brand 1", layers: [] },
        { id: "brand2", name: "Brand 2", layers: [] },
      ];
      registerBrandPresets(presets);
      expect(getBrandPreset("brand1")).toBeDefined();
      expect(getBrandPreset("brand2")).toBeDefined();
    });
  });

  describe("getBrandPreset", () => {
    it("should return undefined for non-existent preset", () => {
      const result = getBrandPreset("non-existent");
      expect(result).toBeUndefined();
    });
  });

  describe("getBrandPresets", () => {
    it("should return empty array initially", () => {
      const presets = getBrandPresets();
      expect(presets).toEqual([]);
    });

    it("should return all registered presets", () => {
      registerBrandPreset({ id: "a", name: "A", layers: [] });
      registerBrandPreset({ id: "b", name: "B", layers: [] });
      const presets = getBrandPresets();
      expect(presets).toHaveLength(2);
    });
  });

  describe("setBrandPresetLoader", () => {
    it("should set a custom loader function", () => {
      const loader = async () => [{ id: "loaded", name: "Loaded", layers: [] }];
      setBrandPresetLoader(loader);
      // Loader is set - actual load would be async
      expect(() => setBrandPresetLoader(loader)).not.toThrow();
    });
  });

  describe("loadBrandPresets", () => {
    it("should load presets using custom loader", async () => {
      const loader = async () => [
        { id: "async-brand", name: "Async Brand", layers: [] },
      ];
      setBrandPresetLoader(loader);
      await loadBrandPresets();
      const preset = getBrandPreset("async-brand");
      expect(preset).toBeDefined();
      expect(preset?.name).toBe("Async Brand");
    });
  });

  describe("applyBrandPreset", () => {
    it("should apply brand preset to theme by id", () => {
      const preset: BrandPresetInput = {
        id: "brand-x",
        name: "Brand X",
        layers: [
          {
            id: "brand-x-layer",
            name: "Brand X Layer",
            type: "brand",
            overrides: { palette: { primary: "#ff0000" } } as any,
          },
        ],
      };
      registerBrandPreset(preset);
      const result = applyBrandPreset("brand-x");
      expect(result).toBeDefined();
      expect(result.layers).toHaveLength(1);
    });

    it("should throw for non-existent preset id", () => {
      expect(() => applyBrandPreset("non-existent")).toThrow();
    });

    it("should respect mode option", () => {
      const preset: BrandPresetInput = {
        id: "brand-mode",
        name: "Brand Mode",
        mode: "dark",
        layers: [],
      };
      registerBrandPreset(preset);
      const lightTheme = applyBrandPreset("brand-mode", { mode: "light" });
      expect(lightTheme.mode).toBe("light");
    });
  });

  describe("loadAndApplyBrandPreset", () => {
    it("should load and apply preset by id", async () => {
      const preset: BrandPresetInput = {
        id: "load-apply",
        name: "Load Apply",
        layers: [],
      };
      registerBrandPreset(preset);
      const result = await loadAndApplyBrandPreset("load-apply");
      expect(result).toBeDefined();
    });

    it("should throw for non-existent preset", async () => {
      await expect(loadAndApplyBrandPreset("non-existent")).rejects.toThrow();
    });
  });
});
