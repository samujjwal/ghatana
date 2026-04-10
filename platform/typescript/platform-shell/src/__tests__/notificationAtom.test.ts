import { describe, it, expect, beforeEach, vi } from "vitest";
import { createStore } from "jotai";
import {
  notificationsAtom,
  unreadCountAtom,
  notificationPanelOpenAtom,
  pushNotificationAtom,
  markReadAtom,
  markAllReadAtom,
  type Notification,
  type NotificationSeverity,
} from "@ghatana/state";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeNotification(
  overrides: Partial<Omit<Notification, "id" | "read" | "createdAt">> = {}
): Omit<Notification, "id" | "read" | "createdAt"> {
  return {
    severity: "info" as NotificationSeverity,
    title: "Test notification",
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// notificationsAtom
// ---------------------------------------------------------------------------

describe("notificationsAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("starts empty", () => {
    expect(store.get(notificationsAtom)).toHaveLength(0);
  });

  it("can be set directly", () => {
    const notif: Notification = {
      id: "n1",
      severity: "success",
      title: "Done",
      read: false,
      createdAt: new Date().toISOString(),
    };
    store.set(notificationsAtom, [notif]);
    expect(store.get(notificationsAtom)).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// pushNotificationAtom
// ---------------------------------------------------------------------------

describe("pushNotificationAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("appends a notification and sets read=false", () => {
    store.set(pushNotificationAtom, makeNotification({ title: "Hello" }));
    const notifications = store.get(notificationsAtom);
    expect(notifications).toHaveLength(1);
    expect(notifications[0]!.title).toBe("Hello");
    expect(notifications[0]!.read).toBe(false);
  });

  it("auto-generates an id and createdAt", () => {
    store.set(pushNotificationAtom, makeNotification());
    const notif = store.get(notificationsAtom)[0]!;
    expect(typeof notif.id).toBe("string");
    expect(notif.id.length).toBeGreaterThan(0);
    expect(typeof notif.createdAt).toBe("string");
  });

  it("prepends new notifications (most recent first)", () => {
    store.set(pushNotificationAtom, makeNotification({ title: "First" }));
    store.set(pushNotificationAtom, makeNotification({ title: "Second" }));
    const notifications = store.get(notificationsAtom);
    expect(notifications[0]!.title).toBe("Second");
    expect(notifications[1]!.title).toBe("First");
  });

  it("preserves optional source, message, href fields", () => {
    store.set(
      pushNotificationAtom,
      makeNotification({ source: "aep", message: "Detailed", href: "/aep/runs/1" })
    );
    const notif = store.get(notificationsAtom)[0]!;
    expect(notif.source).toBe("aep");
    expect(notif.message).toBe("Detailed");
    expect(notif.href).toBe("/aep/runs/1");
  });

  it("caps the list at 100 entries", () => {
    // Pre-fill 99 notifications
    const existing: Notification[] = Array.from({ length: 99 }, (_, i) => ({
      id: `old-${i}`,
      severity: "info" as NotificationSeverity,
      title: `Old ${i}`,
      read: false,
      createdAt: new Date().toISOString(),
    }));
    store.set(notificationsAtom, existing);

    // Push 2 more → total would be 101, but cap is 100
    store.set(pushNotificationAtom, makeNotification({ title: "A" }));
    store.set(pushNotificationAtom, makeNotification({ title: "B" }));

    const notifications = store.get(notificationsAtom);
    expect(notifications.length).toBe(100);
    // Most recent should be first
    expect(notifications[0]!.title).toBe("B");
  });
});

// ---------------------------------------------------------------------------
// unreadCountAtom (derived)
// ---------------------------------------------------------------------------

describe("unreadCountAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("is 0 when empty", () => {
    expect(store.get(unreadCountAtom)).toBe(0);
  });

  it("counts only unread notifications", () => {
    const notifications: Notification[] = [
      { id: "1", severity: "info", title: "A", read: false, createdAt: new Date().toISOString() },
      { id: "2", severity: "success", title: "B", read: true, createdAt: new Date().toISOString() },
      { id: "3", severity: "warning", title: "C", read: false, createdAt: new Date().toISOString() },
    ];
    store.set(notificationsAtom, notifications);
    expect(store.get(unreadCountAtom)).toBe(2);
  });

  it("is 0 when all notifications are read", () => {
    const notifications: Notification[] = [
      { id: "1", severity: "info", title: "A", read: true, createdAt: new Date().toISOString() },
      { id: "2", severity: "success", title: "B", read: true, createdAt: new Date().toISOString() },
    ];
    store.set(notificationsAtom, notifications);
    expect(store.get(unreadCountAtom)).toBe(0);
  });

  it("updates reactively when a notification is pushed", () => {
    expect(store.get(unreadCountAtom)).toBe(0);
    store.set(pushNotificationAtom, makeNotification());
    expect(store.get(unreadCountAtom)).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// markReadAtom
// ---------------------------------------------------------------------------

describe("markReadAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("marks a single notification as read by id", () => {
    const notifications: Notification[] = [
      { id: "n1", severity: "info", title: "A", read: false, createdAt: new Date().toISOString() },
      { id: "n2", severity: "error", title: "B", read: false, createdAt: new Date().toISOString() },
    ];
    store.set(notificationsAtom, notifications);
    store.set(markReadAtom, "n1");
    const updated = store.get(notificationsAtom);
    expect(updated.find((n) => n.id === "n1")?.read).toBe(true);
    expect(updated.find((n) => n.id === "n2")?.read).toBe(false);
  });

  it("is a no-op for unknown id", () => {
    const initial: Notification[] = [
      { id: "n1", severity: "info", title: "A", read: false, createdAt: new Date().toISOString() },
    ];
    store.set(notificationsAtom, initial);
    store.set(markReadAtom, "unknown");
    expect(store.get(notificationsAtom)[0]!.read).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// markAllReadAtom
// ---------------------------------------------------------------------------

describe("markAllReadAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("marks all notifications as read", () => {
    const notifications: Notification[] = [
      { id: "1", severity: "info", title: "A", read: false, createdAt: new Date().toISOString() },
      { id: "2", severity: "warning", title: "B", read: false, createdAt: new Date().toISOString() },
      { id: "3", severity: "success", title: "C", read: true, createdAt: new Date().toISOString() },
    ];
    store.set(notificationsAtom, notifications);
    store.set(markAllReadAtom);
    const updated = store.get(notificationsAtom);
    expect(updated.every((n) => n.read)).toBe(true);
  });

  it("reduces unread count to 0", () => {
    store.set(pushNotificationAtom, makeNotification({ title: "X" }));
    store.set(pushNotificationAtom, makeNotification({ title: "Y" }));
    expect(store.get(unreadCountAtom)).toBe(2);
    store.set(markAllReadAtom);
    expect(store.get(unreadCountAtom)).toBe(0);
  });

  it("is a no-op on an empty list", () => {
    store.set(markAllReadAtom);
    expect(store.get(notificationsAtom)).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// notificationPanelOpenAtom
// ---------------------------------------------------------------------------

describe("notificationPanelOpenAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("starts as false", () => {
    expect(store.get(notificationPanelOpenAtom)).toBe(false);
  });

  it("can be toggled to true", () => {
    store.set(notificationPanelOpenAtom, true);
    expect(store.get(notificationPanelOpenAtom)).toBe(true);
  });

  it("can be toggled back to false", () => {
    store.set(notificationPanelOpenAtom, true);
    store.set(notificationPanelOpenAtom, false);
    expect(store.get(notificationPanelOpenAtom)).toBe(false);
  });
});
