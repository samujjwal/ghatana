import { describe, it, expect } from "vitest";
import { ZodError } from "zod";
import { parseLoginRequest, parseRefreshTokenRequest } from "../schemas/auth.js";
import {
  parseEnrollRequest,
  parseSubmitAttemptRequest,
  parseListModulesQuery,
} from "../schemas/learning.js";
import {
  parseCreateExperienceRequest,
  parseClaimInput,
} from "../schemas/content-studio.js";
import { SlugSchema, NonEmptyStringSchema } from "../schemas/common.js";

// ---------------------------------------------------------------------------
// Auth schema tests
// ---------------------------------------------------------------------------

describe("LoginRequestSchema", () => {
  it("parses a valid login request", () => {
    const result = parseLoginRequest({
      email: "learner@example.com",
      password: "SuperSecret1!",
      tenantId: "tenant-abc",
    });
    expect(result.email).toBe("learner@example.com");
    expect(result.tenantId).toBe("tenant-abc");
  });

  it("rejects an invalid email", () => {
    expect(() =>
      parseLoginRequest({ email: "not-an-email", password: "SuperSecret1!", tenantId: "t" }),
    ).toThrow(ZodError);
  });

  it("rejects a password shorter than 8 characters", () => {
    expect(() =>
      parseLoginRequest({ email: "a@b.com", password: "short", tenantId: "t" }),
    ).toThrow(ZodError);
  });

  it("rejects extra fields (strict)", () => {
    expect(() =>
      parseLoginRequest({
        email: "a@b.com",
        password: "LongPassword1!",
        tenantId: "t",
        extra: "field",
      }),
    ).toThrow(ZodError);
  });
});

describe("RefreshTokenRequestSchema", () => {
  it("parses a valid refresh token request", () => {
    const result = parseRefreshTokenRequest({ refreshToken: "some-token" });
    expect(result.refreshToken).toBe("some-token");
  });

  it("rejects an empty refreshToken", () => {
    expect(() => parseRefreshTokenRequest({ refreshToken: "" })).toThrow(ZodError);
  });
});

// ---------------------------------------------------------------------------
// Learning schema tests
// ---------------------------------------------------------------------------

describe("EnrollRequestSchema", () => {
  it("parses a valid enroll request", () => {
    const moduleId = "550e8400-e29b-41d4-a716-446655440000";
    const result = parseEnrollRequest({ moduleId });
    expect(result.moduleId).toBe(moduleId);
  });

  it("rejects a non-UUID moduleId", () => {
    expect(() => parseEnrollRequest({ moduleId: "not-a-uuid" })).toThrow(ZodError);
  });
});

describe("SubmitAttemptRequestSchema", () => {
  const validAnswer = {
    itemId: "550e8400-e29b-41d4-a716-446655440000",
    value: "answer text",
    confidencePercent: 80,
  };

  it("parses a valid attempt submission", () => {
    const result = parseSubmitAttemptRequest({ answers: [validAnswer] });
    expect(result.answers).toHaveLength(1);
    expect(result.answers[0].confidencePercent).toBe(80);
  });

  it("rejects an empty answers array", () => {
    expect(() => parseSubmitAttemptRequest({ answers: [] })).toThrow(ZodError);
  });

  it("rejects confidence > 100", () => {
    expect(() =>
      parseSubmitAttemptRequest({
        answers: [{ ...validAnswer, confidencePercent: 101 }],
      }),
    ).toThrow(ZodError);
  });
});

describe("ListModulesQuerySchema", () => {
  it("applies defaults when no params are provided", () => {
    const result = parseListModulesQuery({});
    expect(result.limit).toBe(20);
  });

  it("coerces limit to number", () => {
    const result = parseListModulesQuery({ limit: "50" });
    expect(result.limit).toBe(50);
  });

  it("rejects limit > 100", () => {
    expect(() => parseListModulesQuery({ limit: 200 })).toThrow(ZodError);
  });
});

// ---------------------------------------------------------------------------
// Content Studio schema tests
// ---------------------------------------------------------------------------

describe("CreateExperienceRequestSchema", () => {
  it("parses a valid create request", () => {
    const result = parseCreateExperienceRequest({
      title: "Introduction to Calculus",
      domain: "MATH",
      difficulty: "INTRO",
      tags: ["calculus", "math"],
    });
    expect(result.title).toBe("Introduction to Calculus");
    expect(result.tags).toEqual(["calculus", "math"]);
  });

  it("rejects an empty title", () => {
    expect(() =>
      parseCreateExperienceRequest({ title: "", domain: "MATH", difficulty: "INTRO" }),
    ).toThrow(ZodError);
  });

  it("rejects an invalid domain", () => {
    expect(() =>
      parseCreateExperienceRequest({ title: "Test", domain: "INVALID", difficulty: "INTRO" }),
    ).toThrow(ZodError);
  });
});

describe("ClaimSchema", () => {
  it("parses a valid claim", () => {
    const result = parseClaimInput({ statement: "Force equals mass times acceleration." });
    expect(result.statement).toBe("Force equals mass times acceleration.");
    expect(result.evidence).toEqual([]);
  });

  it("rejects an empty statement", () => {
    expect(() => parseClaimInput({ statement: "" })).toThrow(ZodError);
  });

  it("rejects a non-http sourceUrl", () => {
    expect(() =>
      parseClaimInput({ statement: "F=ma", sourceUrls: ["ftp://not-allowed.com"] }),
    ).toThrow(ZodError);
  });
});

// ---------------------------------------------------------------------------
// Common schema tests
// ---------------------------------------------------------------------------

describe("SlugSchema", () => {
  it("accepts valid slugs", () => {
    expect(SlugSchema.parse("intro-to-calculus")).toBe("intro-to-calculus");
    expect(SlugSchema.parse("cs101")).toBe("cs101");
  });

  it("rejects slugs with uppercase", () => {
    expect(() => SlugSchema.parse("Intro-To-Calculus")).toThrow(ZodError);
  });

  it("rejects slugs with trailing hyphens", () => {
    expect(() => SlugSchema.parse("intro-")).toThrow(ZodError);
  });
});

describe("NonEmptyStringSchema", () => {
  it("trims whitespace", () => {
    expect(NonEmptyStringSchema.parse("  hello  ")).toBe("hello");
  });

  it("rejects whitespace-only strings", () => {
    expect(() => NonEmptyStringSchema.parse("   ")).toThrow(ZodError);
  });
});
