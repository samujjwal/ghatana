import {
  SECONDARY_TEST_TENANT_ID,
  TERTIARY_TEST_TENANT_ID,
  TEST_TENANT_ID,
} from "@/__tests__/test-utils/tenants";
import { beforeEach, describe, expect, it } from "vitest";
import SessionBootstrap, {
  MissingTenantContextError,
} from "../../lib/auth/session";
import { TokenStorage } from "../../lib/auth/tokenStorage";

describe("SessionBootstrap", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TokenStorage.clear();
  });

  it("rejects reserved default tenant values", () => {
    localStorage.setItem("tenantId", "default");

    expect(SessionBootstrap.getTenantId()).toBeNull();
    expect(() => SessionBootstrap.requireTenantId()).toThrow(
      MissingTenantContextError,
    );
  });

  it("migrates a legacy localStorage tenant into the session bootstrap store", () => {
    localStorage.setItem("tenantId", TEST_TENANT_ID);

    expect(SessionBootstrap.getTenantId()).toBe(TEST_TENANT_ID);
    expect(sessionStorage.getItem("dc:session:tenantId")).toBe(TEST_TENANT_ID);
  });

  it("persists explicit tenant context to both session and compatibility storage", () => {
    SessionBootstrap.setTenantId(SECONDARY_TEST_TENANT_ID);

    expect(sessionStorage.getItem("dc:session:tenantId")).toBe(
      SECONDARY_TEST_TENANT_ID,
    );
    expect(localStorage.getItem("tenantId")).toBe(SECONDARY_TEST_TENANT_ID);
  });

  it("clears both tenant and token state together", () => {
    SessionBootstrap.setTenantId(TERTIARY_TEST_TENANT_ID);
    TokenStorage.set("token-1");
    SessionBootstrap.setShellRole("admin");

    SessionBootstrap.clear();

    expect(SessionBootstrap.getTenantId()).toBeNull();
    expect(TokenStorage.get()).toBeNull();
    expect(SessionBootstrap.getShellRole()).toBe("primary-user");
  });

  it("persists shell role in session bootstrap snapshots", () => {
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
    SessionBootstrap.setShellRole("operator");
    SessionBootstrap.setProductViewMode("steward");

    expect(SessionBootstrap.getShellRole()).toBe("operator");
    expect(SessionBootstrap.getProductViewMode()).toBe("steward");
    expect(SessionBootstrap.bootstrap().shellRole).toBe("operator");
    expect(SessionBootstrap.bootstrap().productViewMode).toBe("steward");
    expect(sessionStorage.getItem("dc:session:shellRole")).toBe("operator");
    expect(sessionStorage.getItem("dc:session:productViewMode")).toBe(
      "steward",
    );
  });

  it("reports cookie-backed auth mode in bootstrap snapshots", () => {
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
    TokenStorage.enableCookieSession();

    expect(SessionBootstrap.bootstrap()).toMatchObject({
      isAuthenticated: true,
      authMode: "cookie-session",
      sessionExpiringSoon: false,
    });
  });

  it("normalizes unknown shell roles back to the primary-user default", () => {
    sessionStorage.setItem("dc:session:shellRole", "super-admin");

    expect(SessionBootstrap.getShellRole()).toBe("primary-user");
  });
});
