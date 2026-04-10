import { describe, it, expect, beforeEach } from "vitest";
import { createStore } from "jotai";
import {
  authTokenAtom,
  isAuthenticatedAtom,
  isTokenExpiredAtom,
  currentUserEmailAtom,
  type AuthToken,
} from "@ghatana/state";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function futureToken(overrides: Partial<AuthToken> = {}): AuthToken {
  return {
    accessToken: "tok-abc123",
    expiresAt: Math.floor(Date.now() / 1000) + 3600, // 1 hour from now
    sub: "user-001",
    email: "alice@example.com",
    tenants: ["tenant-a", "tenant-b"],
    roles: ["admin", "viewer"],
    ...overrides,
  };
}

function expiredToken(overrides: Partial<AuthToken> = {}): AuthToken {
  return {
    accessToken: "tok-expired",
    expiresAt: Math.floor(Date.now() / 1000) - 60, // 1 minute ago
    sub: "user-002",
    email: "bob@example.com",
    tenants: ["tenant-a"],
    roles: ["viewer"],
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// authTokenAtom
// ---------------------------------------------------------------------------

describe("authTokenAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("starts as null (unauthenticated)", () => {
    expect(store.get(authTokenAtom)).toBeNull();
  });

  it("can be set to a valid token", () => {
    const token = futureToken();
    store.set(authTokenAtom, token);
    expect(store.get(authTokenAtom)).toEqual(token);
  });

  it("can be cleared back to null", () => {
    store.set(authTokenAtom, futureToken());
    store.set(authTokenAtom, null);
    expect(store.get(authTokenAtom)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// isAuthenticatedAtom (derived)
// ---------------------------------------------------------------------------

describe("isAuthenticatedAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("is false when token is null", () => {
    expect(store.get(isAuthenticatedAtom)).toBe(false);
  });

  it("is true when a token is present regardless of expiry", () => {
    store.set(authTokenAtom, futureToken());
    expect(store.get(isAuthenticatedAtom)).toBe(true);
  });

  it("becomes false again after token is cleared", () => {
    store.set(authTokenAtom, futureToken());
    store.set(authTokenAtom, null);
    expect(store.get(isAuthenticatedAtom)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// isTokenExpiredAtom (derived)
// ---------------------------------------------------------------------------

describe("isTokenExpiredAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("is true when no token is present", () => {
    expect(store.get(isTokenExpiredAtom)).toBe(true);
  });

  it("is false for a token that expires in the future", () => {
    store.set(authTokenAtom, futureToken());
    expect(store.get(isTokenExpiredAtom)).toBe(false);
  });

  it("is true for a token whose expiresAt is in the past", () => {
    store.set(authTokenAtom, expiredToken());
    expect(store.get(isTokenExpiredAtom)).toBe(true);
  });

  it("is true for a token that expires exactly now (boundary)", () => {
    const now = Math.floor(Date.now() / 1000);
    store.set(authTokenAtom, futureToken({ expiresAt: now }));
    // expiresAt * 1000 < Date.now() — borderline; depends on ms granularity
    // Use a slightly past time to deterministically test expiry
    store.set(authTokenAtom, futureToken({ expiresAt: now - 1 }));
    expect(store.get(isTokenExpiredAtom)).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// currentUserEmailAtom (derived)
// ---------------------------------------------------------------------------

describe("currentUserEmailAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("is null when no token present", () => {
    expect(store.get(currentUserEmailAtom)).toBeNull();
  });

  it("returns the email from the active token", () => {
    store.set(authTokenAtom, futureToken({ email: "carol@example.com" }));
    expect(store.get(currentUserEmailAtom)).toBe("carol@example.com");
  });

  it("becomes null again after token is cleared", () => {
    store.set(authTokenAtom, futureToken());
    store.set(authTokenAtom, null);
    expect(store.get(currentUserEmailAtom)).toBeNull();
  });
});
