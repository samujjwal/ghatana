import { describe, it, expect } from "vitest";
import { z } from "zod";
import { createConfig, ConfigValidationError, nonEmptyString, urlString, portNumber } from "../schema.js";

describe("createConfig", () => {
  const TestSchema = z.object({
    port: z.number().int().positive(),
    name: z.string().min(1),
    debug: z.boolean().default(false),
  });

  it("parses valid input and returns a Config object", () => {
    const config = createConfig(TestSchema, { port: 3000, name: "my-service" });
    expect(config.get()).toEqual({ port: 3000, name: "my-service", debug: false });
  });

  it("getKey returns a single top-level value", () => {
    const config = createConfig(TestSchema, { port: 8080, name: "svc" });
    expect(config.getKey("port")).toBe(8080);
    expect(config.getKey("name")).toBe("svc");
  });

  it("throws ConfigValidationError on invalid input", () => {
    expect(() =>
      createConfig(TestSchema, { port: -1, name: "" })
    ).toThrow(ConfigValidationError);
  });

  it("ConfigValidationError includes zod issues", () => {
    try {
      createConfig(TestSchema, { port: "not-a-number", name: "" });
    } catch (err) {
      expect(err).toBeInstanceOf(ConfigValidationError);
      if (err instanceof ConfigValidationError) {
        expect(err.issues.length).toBeGreaterThan(0);
      }
    }
  });

  it("validate() does not throw for valid config", () => {
    const config = createConfig(TestSchema, { port: 4000, name: "srv" });
    expect(() => config.validate()).not.toThrow();
  });
});

describe("schema builders", () => {
  it("nonEmptyString rejects empty string", () => {
    const schema = nonEmptyString();
    expect(() => schema.parse("")).toThrow();
    expect(schema.parse("hello")).toBe("hello");
  });

  it("urlString rejects non-URL", () => {
    const schema = urlString();
    expect(() => schema.parse("not-a-url")).toThrow();
    expect(schema.parse("https://example.com")).toBe("https://example.com");
  });

  it("portNumber rejects out-of-range values", () => {
    const schema = portNumber();
    expect(() => schema.parse(0)).toThrow();
    expect(() => schema.parse(65536)).toThrow();
    expect(schema.parse(443)).toBe(443);
    expect(schema.parse(65535)).toBe(65535);
  });
});
