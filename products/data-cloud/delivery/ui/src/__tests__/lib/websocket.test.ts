/**
 * Tests for WebSocket URL validation helpers
 *
 * Covers: FINDING-DC-UI-M2 (WebSocket URL validation)
 *
 * @doc.type test
 * @doc.purpose Unit tests for validateWebSocketUrl and deriveDefaultWebSocketUrl
 * @doc.layer frontend
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  deriveDefaultWebSocketUrl,
  validateWebSocketUrl,
} from "../../lib/websocket/client";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("validateWebSocketUrl", () => {
  it("accepts a valid ws: URL", () => {
    expect(validateWebSocketUrl("ws://localhost:8080/ws")).toBe(
      "ws://localhost:8080/ws",
    );
  });

  it("accepts a valid wss: URL", () => {
    expect(validateWebSocketUrl("wss://api.example.com/ws")).toBe(
      "wss://api.example.com/ws",
    );
  });

  it("throws on a non-URL string", () => {
    expect(() => validateWebSocketUrl("not-a-url")).toThrow(
      /could not be parsed/i,
    );
  });

  it("throws on http: protocol", () => {
    expect(() => validateWebSocketUrl("http://example.com")).toThrow(
      /ws: or wss:/i,
    );
  });

  it("throws on https: protocol", () => {
    expect(() => validateWebSocketUrl("https://example.com")).toThrow(
      /ws: or wss:/i,
    );
  });

  it("throws on javascript: protocol", () => {
    expect(() => validateWebSocketUrl("javascript:alert(1)")).toThrow();
  });

  it("throws on ws: in production mode", () => {
    vi.stubEnv("PROD", true);
    expect(() => validateWebSocketUrl("ws://example.com/ws")).toThrow(
      /not allowed in production/i,
    );
  });

  it("allows ws: in development mode", () => {
    vi.stubEnv("PROD", false);
    expect(validateWebSocketUrl("ws://localhost:8080/ws")).toContain("ws://");
  });
});

describe("deriveDefaultWebSocketUrl", () => {
  it("uses wss when location.protocol is https:", () => {
    vi.stubGlobal("window", {
      location: { protocol: "https:", host: "example.com" },
    });
    expect(deriveDefaultWebSocketUrl()).toBe("wss://example.com/ws");
  });

  it("uses ws when location.protocol is http:", () => {
    vi.stubGlobal("window", {
      location: { protocol: "http:", host: "localhost:3000" },
    });
    expect(deriveDefaultWebSocketUrl()).toBe("ws://localhost:3000/ws");
  });

  it("uses custom path", () => {
    vi.stubGlobal("window", {
      location: { protocol: "http:", host: "localhost:3000" },
    });
    expect(deriveDefaultWebSocketUrl("/events")).toBe(
      "ws://localhost:3000/events",
    );
  });
});
