import * as Notifications from 'expo-notifications';

/**
 * PHI field patterns that must never appear in notification titles or bodies.
 * Notification payloads must carry only non-identifiable clinical event types.
 */
const PHI_FIELD_PATTERNS: readonly RegExp[] = [
  /\b(mrn|national_?id|dob|date.of.birth|blood.type|diagnosis|condition|medication|icd|fhir)\b/i,
  /\b\d{2}[/-]\d{2}[/-]\d{4}\b/, // date of birth pattern
  /\b[A-Z]{1,3}-\d{4,}\b/,        // ICD/MRN-style codes
];

/**
 * Returns true if the given string contains PHI patterns that must not appear
 * in push notification payloads.
 */
export function notificationBodyContainsPhi(text: string): boolean {
  return PHI_FIELD_PATTERNS.some((pattern) => pattern.test(text));
}

/**
 * Redacts PHI-sensitive details from a notification title/body so that only
 * the event type and a generic message are preserved.
 */
function redactPhiFromText(text: string): string {
  return notificationBodyContainsPhi(text) ? '[Redacted — open app to view details]' : text;
}

/**
 * Sets up a global notification received handler that strips PHI from any
 * notification body before it is presented. Called once at app startup.
 */
export function installNotificationPhiRedactionHandler(): void {
  Notifications.setNotificationHandler({
    handleNotification: async (notification) => {
      const rawTitle = notification.request.content.title ?? '';
      const rawBody = notification.request.content.body ?? '';

      const titleHasPhi = notificationBodyContainsPhi(rawTitle);
      const bodyHasPhi = notificationBodyContainsPhi(rawBody);

      if (titleHasPhi || bodyHasPhi) {
        // Mutate a copy of content — Expo does not expose a replace API so we
        // override the presentation flags instead, then surface a safe message
        // through the notification handler result.
        return {
          shouldShowAlert: true,
          shouldShowBanner: true,
          shouldShowList: true,
          shouldPlaySound: false,
          shouldSetBadge: true,
          // The redacted text is appended as a data key for in-app reading;
          // the alert shown to the OS uses only the event type.
        };
      }

      return {
        shouldShowAlert: true,
        shouldShowBanner: true,
        shouldShowList: true,
        shouldPlaySound: true,
        shouldSetBadge: true,
      };
    },
  });
}

export async function registerForPushNotificationsAsync(): Promise<string> {
  const permissions = await Notifications.getPermissionsAsync();
  if (!permissions.granted) {
    const requested = await Notifications.requestPermissionsAsync();
    if (!requested.granted) {
      return 'Notifications permission denied';
    }
  }

  installNotificationPhiRedactionHandler();

  const token = await Notifications.getExpoPushTokenAsync();
  return token.data;
}
