import { describe, it, expect, beforeEach } from "vitest";
import { createStore } from "jotai";
import {
  authTokenAtom,
  isAuthenticatedAtom,
  isTokenExpiredAtom,
  currentUserEmailAtom,
} from "../platform-shell-atoms/authAtom";
import {
  notificationsAtom,
  pushNotificationAtom,
  markReadAtom,
  markAllReadAtom,
  unreadCountAtom,
  notificationPanelOpenAtom,
} from "../platform-shell-atoms/notificationAtom";
import {
  tenantAtom,
  hasRealTenantAtom,
  availableTenantsAtom,
} from "../platform-shell-atoms/tenantAtom";
import type { Notification } from "../platform-shell-atoms/notificationAtom";
import type { Tenant } from "../platform-shell-atoms/tenantAtom";

describe("authAtom", () => {
  beforeEach(() => {
    // Reset auth token to null before each test
    const store = createStore();
    store.set(authTokenAtom, null);
  });

  it("authTokenAtom starts as null", () => {
    const store = createStore();
    expect(store.get(authTokenAtom)).toBeNull();
  });

  it("authTokenAtom can be set to a valid token", () => {
    const store = createStore();
    const token = {
      accessToken: "jwt-token",
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
      sub: "user-123",
      email: "test@example.com",
      tenants: ["tenant-1", "tenant-2"],
      roles: ["admin"],
    };
    store.set(authTokenAtom, token);
    expect(store.get(authTokenAtom)).toEqual(token);
  });

  it("isAuthenticatedAtom returns false when token is null", () => {
    const store = createStore();
    expect(store.get(isAuthenticatedAtom)).toBe(false);
  });

  it("isAuthenticatedAtom returns true when token is set", () => {
    const store = createStore();
    const token = {
      accessToken: "jwt-token",
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
      sub: "user-123",
      email: "test@example.com",
      tenants: ["tenant-1"],
      roles: [],
    };
    store.set(authTokenAtom, token);
    expect(store.get(isAuthenticatedAtom)).toBe(true);
  });

  it("isTokenExpiredAtom returns true when token is null", () => {
    const store = createStore();
    expect(store.get(isTokenExpiredAtom)).toBe(true);
  });

  it("isTokenExpiredAtom returns false for valid future token", () => {
    const store = createStore();
    const token = {
      accessToken: "jwt-token",
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
      sub: "user-123",
      email: "test@example.com",
      tenants: ["tenant-1"],
      roles: [],
    };
    store.set(authTokenAtom, token);
    expect(store.get(isTokenExpiredAtom)).toBe(false);
  });

  it("isTokenExpiredAtom returns true for expired token", () => {
    const store = createStore();
    const token = {
      accessToken: "jwt-token",
      expiresAt: Math.floor(Date.now() / 1000) - 100,
      sub: "user-123",
      email: "test@example.com",
      tenants: ["tenant-1"],
      roles: [],
    };
    store.set(authTokenAtom, token);
    expect(store.get(isTokenExpiredAtom)).toBe(true);
  });

  it("currentUserEmailAtom returns null when unauthenticated", () => {
    const store = createStore();
    expect(store.get(currentUserEmailAtom)).toBeNull();
  });

  it("currentUserEmailAtom returns email when authenticated", () => {
    const store = createStore();
    const token = {
      accessToken: "jwt-token",
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
      sub: "user-123",
      email: "user@example.com",
      tenants: ["tenant-1"],
      roles: [],
    };
    store.set(authTokenAtom, token);
    expect(store.get(currentUserEmailAtom)).toBe("user@example.com");
  });
});

describe("notificationAtom", () => {
  beforeEach(() => {
    const store = createStore();
    store.set(notificationsAtom, []);
  });

  it("notificationsAtom starts as empty array", () => {
    const store = createStore();
    expect(store.get(notificationsAtom)).toEqual([]);
  });

  it("pushNotificationAtom adds a notification", () => {
    const store = createStore();
    store.set(pushNotificationAtom, {
      severity: "info",
      title: "Test",
      message: "Test message",
    });
    const notifications = store.get(notificationsAtom);
    expect(notifications).toHaveLength(1);
    expect(notifications[0].title).toBe("Test");
    expect(notifications[0].read).toBe(false);
  });

  it("pushNotificationAtom prepends to existing notifications", () => {
    const store = createStore();
    store.set(pushNotificationAtom, {
      severity: "info",
      title: "Test 1",
      message: "Message 1",
    });
    store.set(pushNotificationAtom, {
      severity: "success",
      title: "Test 2",
      message: "Message 2",
    });
    const notifications = store.get(notificationsAtom);
    expect(notifications).toHaveLength(2);
    expect(notifications[0].title).toBe("Test 2");
  });

  it("markReadAtom marks notification as read", () => {
    const store = createStore();
    store.set(pushNotificationAtom, {
      severity: "info",
      title: "Test",
      message: "Test message",
    });
    const notifications = store.get(notificationsAtom);
    const id = notifications[0].id;
    store.set(markReadAtom, id);
    const updated = store.get(notificationsAtom);
    expect(updated[0].read).toBe(true);
  });

  it("markAllReadAtom marks all as read", () => {
    const store = createStore();
    store.set(pushNotificationAtom, {
      severity: "info",
      title: "Test 1",
      message: "Message 1",
    });
    store.set(pushNotificationAtom, {
      severity: "success",
      title: "Test 2",
      message: "Message 2",
    });
    store.set(markAllReadAtom);
    const notifications = store.get(notificationsAtom);
    expect(notifications.every((n) => n.read)).toBe(true);
  });

  it("unreadCountAtom counts unread notifications", () => {
    const store = createStore();
    store.set(pushNotificationAtom, {
      severity: "info",
      title: "Test 1",
      message: "Message 1",
    });
    store.set(pushNotificationAtom, {
      severity: "success",
      title: "Test 2",
      message: "Message 2",
    });
    expect(store.get(unreadCountAtom)).toBe(2);
    const notifications = store.get(notificationsAtom);
    store.set(markReadAtom, notifications[0].id);
    expect(store.get(unreadCountAtom)).toBe(1);
  });

  it("notificationPanelOpenAtom starts as false", () => {
    const store = createStore();
    expect(store.get(notificationPanelOpenAtom)).toBe(false);
  });

  it("notificationPanelOpenAtom can be toggled", () => {
    const store = createStore();
    store.set(notificationPanelOpenAtom, true);
    expect(store.get(notificationPanelOpenAtom)).toBe(true);
  });
});

describe("tenantAtom", () => {
  it("tenantAtom defaults to 'default'", () => {
    const store = createStore();
    expect(store.get(tenantAtom)).toBe("default");
  });

  it("tenantAtom can be set to a tenant ID", () => {
    const store = createStore();
    store.set(tenantAtom, "tenant-123");
    expect(store.get(tenantAtom)).toBe("tenant-123");
  });

  it("hasRealTenantAtom returns false for default", () => {
    const store = createStore();
    expect(store.get(hasRealTenantAtom)).toBe(false);
  });

  it("hasRealTenantAtom returns true for real tenant", () => {
    const store = createStore();
    store.set(tenantAtom, "tenant-123");
    expect(store.get(hasRealTenantAtom)).toBe(true);
  });

  it("availableTenantsAtom starts as empty array", () => {
    const store = createStore();
    expect(store.get(availableTenantsAtom)).toEqual([]);
  });

  it("availableTenantsAtom can be set", () => {
    const store = createStore();
    const tenants = [
      { id: "tenant-1", name: "Tenant 1" },
      { id: "tenant-2", name: "Tenant 2" },
    ];
    store.set(availableTenantsAtom, tenants);
    expect(store.get(availableTenantsAtom)).toEqual(tenants);
  });
});
