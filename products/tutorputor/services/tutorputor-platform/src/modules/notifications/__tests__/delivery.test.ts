import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createSocialNotification } from "../delivery.js";

describe("notification delivery", () => {
  const originalEnv = { ...process.env };

  beforeEach(() => {
    vi.restoreAllMocks();
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  it("marks emailSent when resend delivery succeeds", async () => {
    process.env.NOTIFICATIONS_EMAIL_PROVIDER = "resend";
    process.env.RESEND_API_KEY = "resend-key";
    process.env.NOTIFICATIONS_FROM_EMAIL = "noreply@tutorputor.test";

    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);

    const prisma = makePrisma({
      preference: {
        emailEnabled: true,
        pushEnabled: false,
        preferences: JSON.stringify({
          channels: {
            email: {
              address: "student@example.com",
            },
          },
        }),
      },
    });

    await createSocialNotification(prisma, makeNotificationInput());

    expect(fetchMock).toHaveBeenCalledWith(
      "https://api.resend.com/emails",
      expect.objectContaining({ method: "POST" }),
    );
    expect(prisma.socialNotification.update).toHaveBeenCalledWith({
      where: { id: "notif-1" },
      data: { emailSent: true },
    });
  });

  it("marks pushSent when generic webhook delivery succeeds", async () => {
    process.env.NOTIFICATIONS_PUSH_PROVIDER = "generic-webhook";
    process.env.NOTIFICATIONS_PUSH_WEBHOOK_URL =
      "https://push.example.com/dispatch";
    process.env.NOTIFICATIONS_PUSH_WEBHOOK_AUTH_HEADER = "x-api-key";
    process.env.NOTIFICATIONS_PUSH_WEBHOOK_AUTH_TOKEN = "push-secret";

    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);

    const prisma = makePrisma({
      preference: {
        emailEnabled: false,
        pushEnabled: true,
        preferences: JSON.stringify({
          channels: {
            push: {
              endpointUrl: "https://tenant-push.example.com/notify",
              headers: {
                "x-tenant-token": "tenant-secret",
              },
            },
          },
        }),
      },
    });

    await createSocialNotification(prisma, makeNotificationInput());

    expect(fetchMock).toHaveBeenCalledWith(
      "https://tenant-push.example.com/notify",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "x-api-key": "push-secret",
          "x-tenant-token": "tenant-secret",
        }),
      }),
    );
    expect(prisma.socialNotification.update).toHaveBeenCalledWith({
      where: { id: "notif-1" },
      data: { pushSent: true },
    });
  });

  it("skips external delivery when the notification type is disabled", async () => {
    process.env.NOTIFICATIONS_EMAIL_PROVIDER = "resend";
    process.env.RESEND_API_KEY = "resend-key";
    process.env.NOTIFICATIONS_FROM_EMAIL = "noreply@tutorputor.test";

    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);

    const prisma = makePrisma({
      preference: {
        emailEnabled: true,
        pushEnabled: true,
        preferences: JSON.stringify({
          types: {
            TOPIC_REPLY: false,
          },
        }),
      },
    });

    await createSocialNotification(prisma, makeNotificationInput());

    expect(fetchMock).not.toHaveBeenCalled();
    expect(prisma.socialNotification.update).not.toHaveBeenCalled();
  });
});

function makePrisma(args: {
  preference: {
    emailEnabled: boolean;
    pushEnabled: boolean;
    preferences: string | null;
  };
}) {
  return {
    socialNotification: {
      create: vi.fn().mockResolvedValue({
        id: "notif-1",
        ...makeNotificationInput(),
      }),
      update: vi.fn().mockResolvedValue(undefined),
    },
    notificationPreference: {
      findUnique: vi.fn().mockResolvedValue(args.preference),
    },
    user: {
      findFirst: vi.fn().mockResolvedValue({
        email: "student@example.com",
        displayName: "Student One",
      }),
    },
  };
}

function makeNotificationInput() {
  return {
    tenantId: "tenant-1",
    userId: "user-1",
    type: "TOPIC_REPLY",
    title: "New reply",
    body: "Someone replied to your post.",
    targetType: "topic",
    targetId: "topic-1",
    actorId: "user-2",
    actorName: "Teacher One",
    actionUrl: "/topics/topic-1",
  };
}
