/**
 * @doc.type service
 * @doc.purpose Persist social notifications and fan out to configured external delivery providers
 * @doc.layer product
 * @doc.pattern Service
 */

type NotificationRecord = {
  id: string;
  tenantId: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  targetType?: string | null;
  targetId?: string | null;
  actorId?: string | null;
  actorName?: string | null;
  actionUrl?: string | null;
  emailSent?: boolean;
  pushSent?: boolean;
};

type NotificationInput = {
  tenantId: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  targetType?: string;
  targetId?: string;
  actorId?: string;
  actorName?: string;
  actionUrl?: string;
};

type NotificationPreferences = {
  types?: Record<string, boolean>;
  channels?: {
    email?: {
      address?: string;
    };
    push?: {
      endpointUrl?: string;
      headers?: Record<string, string>;
    };
  };
};

type MinimalPrisma = {
  socialNotification: {
    create: (args: { data: NotificationInput }) => Promise<NotificationRecord>;
    update?: (args: {
      where: { id: string };
      data: { emailSent?: boolean; pushSent?: boolean };
    }) => Promise<unknown>;
  };
  notificationPreference?: {
    findUnique?: (args: {
      where: { tenantId_userId: { tenantId: string; userId: string } };
    }) => Promise<{
      emailEnabled: boolean;
      pushEnabled: boolean;
      preferences: string | null;
    } | null>;
  };
  user?: {
    findFirst?: (args: {
      where: { tenantId: string; id: string };
      select: { email: true; displayName: true };
    }) => Promise<{ email: string; displayName: string } | null>;
  };
};

type DeliveryResult = {
  emailSent: boolean;
  pushSent: boolean;
};

const RESEND_API_URL = "https://api.resend.com/emails";

export async function createSocialNotification(
  prisma: MinimalPrisma,
  notification: NotificationInput,
): Promise<NotificationRecord> {
  const createdRecord = await prisma.socialNotification.create({
    data: notification,
  });
  const record: NotificationRecord = createdRecord ?? {
    id: "",
    ...notification,
    emailSent: false,
    pushSent: false,
  };

  const delivery = await deliverSocialNotification(prisma, record);
  if (
    prisma.socialNotification.update &&
    record.id &&
    (delivery.emailSent || delivery.pushSent)
  ) {
    await prisma.socialNotification.update({
      where: { id: record.id },
      data: {
        ...(delivery.emailSent ? { emailSent: true } : {}),
        ...(delivery.pushSent ? { pushSent: true } : {}),
      },
    });
  }

  return {
    ...record,
    emailSent: record.emailSent ?? delivery.emailSent,
    pushSent: record.pushSent ?? delivery.pushSent,
  };
}

export async function deliverSocialNotification(
  prisma: MinimalPrisma,
  notification: NotificationRecord,
): Promise<DeliveryResult> {
  const preferencesDelegate = prisma.notificationPreference;
  const userDelegate = prisma.user;

  if (
    !preferencesDelegate?.findUnique ||
    !userDelegate?.findFirst ||
    typeof fetch !== "function"
  ) {
    return { emailSent: false, pushSent: false };
  }

  const [storedPreferences, user] = await Promise.all([
    preferencesDelegate.findUnique({
      where: {
        tenantId_userId: {
          tenantId: notification.tenantId,
          userId: notification.userId,
        },
      },
    }),
    userDelegate.findFirst({
      where: {
        tenantId: notification.tenantId,
        id: notification.userId,
      },
      select: {
        email: true,
        displayName: true,
      },
    }),
  ]);

  if (!user) {
    return { emailSent: false, pushSent: false };
  }

  const parsedPreferences = parsePreferences(storedPreferences?.preferences);
  if (!isNotificationTypeEnabled(parsedPreferences, notification.type)) {
    return { emailSent: false, pushSent: false };
  }

  const [emailSent, pushSent] = await Promise.all([
    shouldSendEmail(storedPreferences)
      ? sendEmailNotification({
          notification,
          user,
          preferences: parsedPreferences,
        })
      : Promise.resolve(false),
    shouldSendPush(storedPreferences)
      ? sendPushNotification({
          notification,
          user,
          preferences: parsedPreferences,
        })
      : Promise.resolve(false),
  ]);

  return { emailSent, pushSent };
}

function parsePreferences(
  preferences: string | null | undefined,
): NotificationPreferences {
  if (!preferences) {
    return {};
  }

  try {
    const parsed = JSON.parse(preferences) as NotificationPreferences;
    return typeof parsed === "object" && parsed ? parsed : {};
  } catch {
    return {};
  }
}

function isNotificationTypeEnabled(
  preferences: NotificationPreferences,
  type: string,
): boolean {
  const typeSetting = preferences.types?.[type];
  return typeSetting ?? true;
}

function shouldSendEmail(
  storedPreferences: { emailEnabled: boolean } | null,
): boolean {
  return storedPreferences?.emailEnabled ?? true;
}

function shouldSendPush(
  storedPreferences: { pushEnabled: boolean } | null,
): boolean {
  return storedPreferences?.pushEnabled ?? true;
}

async function sendEmailNotification(args: {
  notification: NotificationRecord;
  user: { email: string; displayName: string };
  preferences: NotificationPreferences;
}): Promise<boolean> {
  if (process.env.NOTIFICATIONS_EMAIL_PROVIDER !== "resend") {
    return false;
  }

  const apiKey = process.env.RESEND_API_KEY;
  const from = process.env.NOTIFICATIONS_FROM_EMAIL;
  const to = args.preferences.channels?.email?.address ?? args.user.email;
  if (!apiKey || !from || !to) {
    return false;
  }

  const response = await fetch(RESEND_API_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject: args.notification.title,
      text: buildEmailBody(args),
    }),
  });

  return response.ok;
}

async function sendPushNotification(args: {
  notification: NotificationRecord;
  user: { email: string; displayName: string };
  preferences: NotificationPreferences;
}): Promise<boolean> {
  if (process.env.NOTIFICATIONS_PUSH_PROVIDER !== "generic-webhook") {
    return false;
  }

  const endpointUrl =
    args.preferences.channels?.push?.endpointUrl ??
    process.env.NOTIFICATIONS_PUSH_WEBHOOK_URL;
  if (!endpointUrl) {
    return false;
  }

  const response = await fetch(endpointUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...buildPushHeaders(args.preferences.channels?.push?.headers),
      ...buildDefaultPushAuthHeaders(),
    },
    body: JSON.stringify({
      recipient: {
        userId: args.notification.userId,
        email: args.user.email,
        displayName: args.user.displayName,
      },
      notification: {
        id: args.notification.id,
        type: args.notification.type,
        title: args.notification.title,
        body: args.notification.body,
        targetType: args.notification.targetType ?? undefined,
        targetId: args.notification.targetId ?? undefined,
        actorId: args.notification.actorId ?? undefined,
        actorName: args.notification.actorName ?? undefined,
        actionUrl: args.notification.actionUrl ?? undefined,
      },
    }),
  });

  return response.ok;
}

function buildPushHeaders(
  headers: Record<string, string> | undefined,
): Record<string, string> {
  return headers ?? {};
}

function buildDefaultPushAuthHeaders(): Record<string, string> {
  const headerName = process.env.NOTIFICATIONS_PUSH_WEBHOOK_AUTH_HEADER;
  const headerValue = process.env.NOTIFICATIONS_PUSH_WEBHOOK_AUTH_TOKEN;
  if (!headerName || !headerValue) {
    return {};
  }

  return {
    [headerName]: headerValue,
  };
}

function buildEmailBody(args: {
  notification: NotificationRecord;
  user: { displayName: string };
}): string {
  return [
    `Hello ${args.user.displayName},`,
    "",
    args.notification.body,
    args.notification.actionUrl
      ? `Open in TutorPutor: ${args.notification.actionUrl}`
      : null,
  ]
    .filter(Boolean)
    .join("\n");
}
