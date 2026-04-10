import { describe, it, expect } from "vitest";
import { z } from "zod";
import { loadEnv, BaseEnvSchema, ConfigValidationError } from "../index.js";

describe("loadEnv", () => {
  it("parses valid environment variables", () => {
    const env = loadEnv(BaseEnvSchema, {
      NODE_ENV: "production",
      LOG_LEVEL: "warn",
    });
    expect(env.NODE_ENV).toBe("production");
    expect(env.LOG_LEVEL).toBe("warn");
  });

  it("applies defaults for optional fields", () => {
    const env = loadEnv(BaseEnvSchema, {});
    expect(env.NODE_ENV).toBe("development");
    expect(env.LOG_LEVEL).toBe("info");
  });

  it("throws ConfigValidationError for invalid NODE_ENV", () => {
    expect(() =>
      loadEnv(BaseEnvSchema, { NODE_ENV: "staging" })
    ).toThrow(ConfigValidationError);
  });

  it("strips unknown environment variables", () => {
    const env = loadEnv(BaseEnvSchema, {
      NODE_ENV: "test",
      UNKNOWN_SECRET: "sensitive",
    });
    expect((env as Record<string, unknown>)["UNKNOWN_SECRET"]).toBeUndefined();
  });

  it("supports schema extension for service-specific vars", () => {
    const MyEnv = BaseEnvSchema.extend({
      PORT: z.coerce.number().int().positive().default(3000),
      API_KEY: z.string().min(1),
    });
    const env = loadEnv(MyEnv, { API_KEY: "secret" });
    expect(env.PORT).toBe(3000);
    expect(env.API_KEY).toBe("secret");
  });

  it("error message lists problematic variables", () => {
    try {
      loadEnv(
        BaseEnvSchema.extend({ DATABASE_URL: z.string().url() }),
        { DATABASE_URL: "not-a-url" }
      );
    } catch (err) {
      expect(err).toBeInstanceOf(ConfigValidationError);
      if (err instanceof ConfigValidationError) {
        expect(err.message).toContain("DATABASE_URL");
      }
    }
  });
});
