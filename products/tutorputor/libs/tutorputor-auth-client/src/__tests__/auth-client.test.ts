import { describe, it, expect, beforeEach } from "vitest";
import {
  decodeJwtPayload,
  extractTokenClaims,
  isTokenExpired,
  hasValidAccessToken,
  secondsUntilExpiry,
} from "../token.js";
import { InMemoryAuthTokenStorage } from "../storage.js";
import { buildAuthHeaders, buildMultipartAuthHeaders, extractBearerToken } from "../headers.js";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeJwt(payload: object, expOffsetSeconds = 3600): string {
  const header = { alg: "HS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claims = { iat: now, exp: now + expOffsetSeconds, ...payload };

  const encodeBase64Url = (obj: object): string =>
    btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  return `${encodeBase64Url(header)}.${encodeBase64Url(claims)}.fakesig`;
}

const VALID_TOKEN = makeJwt({
  sub: "user-123",
  email: "learner@example.com",
  displayName: "Test Learner",
  role: "student",
  tenantId: "tenant-abc",
});

const EXPIRED_TOKEN = makeJwt(
  { sub: "user-123", email: "learner@example.com", displayName: "X", role: "student", tenantId: "t" },
  -60,
);

// ---------------------------------------------------------------------------
// Token tests
// ---------------------------------------------------------------------------

describe("decodeJwtPayload", () => {
  it("returns parsed payload for a well-formed JWT", () => {
    const payload = decodeJwtPayload(VALID_TOKEN);
    expect(payload).not.toBeNull();
    expect(payload?.["sub"]).toBe("user-123");
  });

  it("returns null for a non-JWT string", () => {
    expect(decodeJwtPayload("not.a.jwt.at.all.extra")).toBeNull();
  });

  it("returns null for an empty string", () => {
    expect(decodeJwtPayload("")).toBeNull();
  });
});

describe("extractTokenClaims", () => {
  it("extracts canonical TutorPutor claims from a valid token", () => {
    const claims = extractTokenClaims(VALID_TOKEN);
    expect(claims).not.toBeNull();
    expect(claims?.sub).toBe("user-123");
    expect(claims?.email).toBe("learner@example.com");
    expect(claims?.role).toBe("student");
    expect(claims?.tenantId).toBe("tenant-abc");
  });

  it("returns null when required fields are missing", () => {
    const token = makeJwt({ sub: "u1" }); // missing email, role, tenantId
    expect(extractTokenClaims(token)).toBeNull();
  });
});

describe("isTokenExpired", () => {
  it("returns false for a non-expired token", () => {
    expect(isTokenExpired(VALID_TOKEN)).toBe(false);
  });

  it("returns true for an expired token", () => {
    expect(isTokenExpired(EXPIRED_TOKEN)).toBe(true);
  });

  it("returns true for a malformed token", () => {
    expect(isTokenExpired("garbage")).toBe(true);
  });
});

describe("secondsUntilExpiry", () => {
  it("returns a positive number for a non-expired token", () => {
    expect(secondsUntilExpiry(VALID_TOKEN)).toBeGreaterThan(0);
  });

  it("returns a negative number for an expired token", () => {
    expect(secondsUntilExpiry(EXPIRED_TOKEN)).toBeLessThan(0);
  });
});

describe("hasValidAccessToken", () => {
  it("returns true when a valid access token is present", () => {
    expect(hasValidAccessToken({ accessToken: VALID_TOKEN })).toBe(true);
  });

  it("returns false when access token is missing", () => {
    expect(hasValidAccessToken({})).toBe(false);
  });

  it("returns false when access token is expired", () => {
    expect(hasValidAccessToken({ accessToken: EXPIRED_TOKEN })).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Storage tests
// ---------------------------------------------------------------------------

describe("InMemoryAuthTokenStorage", () => {
  let storage: InMemoryAuthTokenStorage;

  beforeEach(() => {
    storage = new InMemoryAuthTokenStorage();
  });

  it("returns null before any token is stored", async () => {
    expect(await storage.retrieve()).toBeNull();
  });

  it("stores and retrieves a token pair", async () => {
    await storage.store({ accessToken: "access", refreshToken: "refresh" });
    const retrieved = await storage.retrieve();
    expect(retrieved?.accessToken).toBe("access");
    expect(retrieved?.refreshToken).toBe("refresh");
  });

  it("clears the stored token pair", async () => {
    await storage.store({ accessToken: "access", refreshToken: "refresh" });
    await storage.clear();
    expect(await storage.retrieve()).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Headers tests
// ---------------------------------------------------------------------------

describe("buildAuthHeaders", () => {
  it("includes Content-Type and Authorization when token is provided", () => {
    const headers = buildAuthHeaders("my-token");
    expect(headers["Content-Type"]).toBe("application/json");
    expect(headers["Authorization"]).toBe("Bearer my-token");
  });

  it("omits Authorization when token is null", () => {
    const headers = buildAuthHeaders(null);
    expect(headers["Content-Type"]).toBe("application/json");
    expect(headers["Authorization"]).toBeUndefined();
  });

  it("merges extra headers, caller wins on conflict", () => {
    const headers = buildAuthHeaders("tok", { "X-Tenant-ID": "t1", "Content-Type": "text/plain" });
    expect(headers["X-Tenant-ID"]).toBe("t1");
    expect(headers["Content-Type"]).toBe("text/plain");
  });
});

describe("buildMultipartAuthHeaders", () => {
  it("includes only Authorization header when token is provided", () => {
    const headers = buildMultipartAuthHeaders("tok");
    expect(headers["Authorization"]).toBe("Bearer tok");
    expect(headers["Content-Type"]).toBeUndefined();
  });
});

describe("extractBearerToken", () => {
  it("extracts token from a well-formed Authorization header", () => {
    expect(extractBearerToken("Bearer my-access-token")).toBe("my-access-token");
  });

  it("returns null for missing header", () => {
    expect(extractBearerToken(null)).toBeNull();
  });

  it("returns null for non-Bearer scheme", () => {
    expect(extractBearerToken("Basic dXNlcjpwYXNz")).toBeNull();
  });
});
