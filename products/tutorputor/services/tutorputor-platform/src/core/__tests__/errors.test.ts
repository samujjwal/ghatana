/**
 * @doc.type test
 * @doc.purpose Unit tests for the typed error hierarchy
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect } from "vitest";
import {
  TutorputorError,
  ValidationError,
  NotFoundError,
  AuthorizationError,
  ConflictError,
  PaymentError,
  SubscriptionError,
  ExternalServiceError,
  RateLimitError,
  isTutorputorError,
} from "../errors";

describe("TutorputorError (base class)", () => {
  it("captures message, code, statusCode, and isRetryable", () => {
    const err = new TutorputorError(
      "something failed",
      "CUSTOM_ERR",
      500,
      true,
    );
    expect(err.message).toBe("something failed");
    expect(err.code).toBe("CUSTOM_ERR");
    expect(err.statusCode).toBe(500);
    expect(err.isRetryable).toBe(true);
  });

  it("defaults isRetryable to false", () => {
    const err = new TutorputorError("msg", "CODE", 400);
    expect(err.isRetryable).toBe(false);
  });

  it("is an instance of Error", () => {
    const err = new TutorputorError("msg", "CODE", 400);
    expect(err).toBeInstanceOf(Error);
  });

  it("sets name to the constructor name", () => {
    const err = new TutorputorError("msg", "CODE", 400);
    expect(err.name).toBe("TutorputorError");
  });
});

describe("ValidationError", () => {
  it("has statusCode 400 and isRetryable false", () => {
    const err = new ValidationError("email is required");
    expect(err.statusCode).toBe(400);
    expect(err.isRetryable).toBe(false);
    expect(err.code).toBe("VALIDATION_ERROR");
  });

  it("accepts a custom code", () => {
    const err = new ValidationError("bad field", "FIELD_REQUIRED");
    expect(err.code).toBe("FIELD_REQUIRED");
  });

  it("is an instance of TutorputorError", () => {
    expect(new ValidationError("x")).toBeInstanceOf(TutorputorError);
  });
});

describe("NotFoundError", () => {
  it("builds message from resource and id", () => {
    const err = new NotFoundError("User", "user-123");
    expect(err.message).toBe("User not found: user-123");
    expect(err.statusCode).toBe(404);
    expect(err.code).toBe("NOT_FOUND");
  });

  it("accepts resource without id", () => {
    const err = new NotFoundError("Configuration");
    expect(err.message).toBe("Configuration not found");
  });

  it("is an instance of TutorputorError", () => {
    expect(new NotFoundError("X")).toBeInstanceOf(TutorputorError);
  });

  it("isRetryable is false", () => {
    expect(new NotFoundError("X").isRetryable).toBe(false);
  });
});

describe("AuthorizationError", () => {
  it("has statusCode 403 and default message", () => {
    const err = new AuthorizationError();
    expect(err.statusCode).toBe(403);
    expect(err.code).toBe("FORBIDDEN");
    expect(err.message).toBe("Insufficient permissions");
  });

  it("accepts a custom message", () => {
    const err = new AuthorizationError("Admin only");
    expect(err.message).toBe("Admin only");
  });
});

describe("ConflictError", () => {
  it("has statusCode 409", () => {
    const err = new ConflictError("Already exists");
    expect(err.statusCode).toBe(409);
    expect(err.code).toBe("CONFLICT");
  });

  it("accepts a custom code", () => {
    const err = new ConflictError("Already published", "ALREADY_PUBLISHED");
    expect(err.code).toBe("ALREADY_PUBLISHED");
  });
});

describe("PaymentError", () => {
  it("has statusCode 402 and isRetryable false", () => {
    const err = new PaymentError("Card declined");
    expect(err.statusCode).toBe(402);
    expect(err.isRetryable).toBe(false);
    expect(err.code).toBe("PAYMENT_FAILED");
  });

  it("accepts a custom code", () => {
    const err = new PaymentError("Invalid card", "CARD_INVALID");
    expect(err.code).toBe("CARD_INVALID");
  });
});

describe("SubscriptionError", () => {
  it("has statusCode 402", () => {
    const err = new SubscriptionError("No active subscription");
    expect(err.statusCode).toBe(402);
    expect(err.code).toBe("SUBSCRIPTION_ERROR");
  });
});

describe("ExternalServiceError", () => {
  it("has statusCode 502 and isRetryable true", () => {
    const err = new ExternalServiceError("Stripe");
    expect(err.statusCode).toBe(502);
    expect(err.isRetryable).toBe(true);
    expect(err.code).toBe("EXTERNAL_SERVICE_ERROR");
    expect(err.message).toContain("Stripe");
  });

  it("includes cause in message when provided", () => {
    const err = new ExternalServiceError("gRPC", "connection refused");
    expect(err.message).toContain("gRPC");
    expect(err.message).toContain("connection refused");
  });
});

describe("RateLimitError", () => {
  it("has statusCode 429 and isRetryable true", () => {
    const err = new RateLimitError();
    expect(err.statusCode).toBe(429);
    expect(err.isRetryable).toBe(true);
    expect(err.code).toBe("RATE_LIMIT_EXCEEDED");
    expect(err.message).toBe("Rate limit exceeded");
  });

  it("accepts a custom message", () => {
    const err = new RateLimitError("AI quota exceeded for tenant");
    expect(err.message).toBe("AI quota exceeded for tenant");
  });
});

describe("isTutorputorError type guard", () => {
  it("returns true for any TutorputorError subclass", () => {
    expect(isTutorputorError(new NotFoundError("X"))).toBe(true);
    expect(isTutorputorError(new ValidationError("x"))).toBe(true);
    expect(isTutorputorError(new PaymentError("x"))).toBe(true);
    expect(isTutorputorError(new ExternalServiceError("svc"))).toBe(true);
    expect(isTutorputorError(new RateLimitError())).toBe(true);
  });

  it("returns false for a plain Error", () => {
    expect(isTutorputorError(new Error("plain"))).toBe(false);
  });

  it("returns false for non-error values", () => {
    expect(isTutorputorError("string")).toBe(false);
    expect(isTutorputorError(null)).toBe(false);
    expect(isTutorputorError(undefined)).toBe(false);
    expect(isTutorputorError(42)).toBe(false);
  });
});

describe("instanceof checks across subclass hierarchy", () => {
  it("PaymentError is an instance of TutorputorError and Error", () => {
    const err = new PaymentError("x");
    expect(err).toBeInstanceOf(PaymentError);
    expect(err).toBeInstanceOf(TutorputorError);
    expect(err).toBeInstanceOf(Error);
  });

  it("different subclasses are not instances of each other", () => {
    const notFound = new NotFoundError("X");
    expect(notFound).not.toBeInstanceOf(ValidationError);
    expect(notFound).not.toBeInstanceOf(PaymentError);
  });
});
