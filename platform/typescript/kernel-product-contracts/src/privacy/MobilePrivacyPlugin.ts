export type MobilePrivacyClearReason =
  | "logout"
  | "session-clear"
  | "session-expired"
  | "session-scope-changed"
  | "consent-revoked";

export interface MobilePrivacyCacheClearer {
  readonly cacheName: string;
  clear(): Promise<void>;
}

export class MobilePrivacyClearError extends Error {
  constructor(
    public readonly reason: MobilePrivacyClearReason,
    public readonly failedCaches: readonly string[],
  ) {
    super(`Failed to clear mobile privacy caches: ${failedCaches.join(", ")}`);
    this.name = "MobilePrivacyClearError";
  }
}

export async function clearKernelMobilePrivacyState(
  reason: MobilePrivacyClearReason,
  clearers: readonly MobilePrivacyCacheClearer[],
  reporter: Pick<Console, "debug" | "warn"> = console,
): Promise<void> {
  const failedCaches: string[] = [];

  for (const clearer of clearers) {
    try {
      await clearer.clear();
    } catch {
      failedCaches.push(clearer.cacheName);
    }
  }

  if (failedCaches.length > 0) {
    reporter.warn(
      `[kernel.mobilePrivacy] clear_failed reason=${reason} caches=${failedCaches.join(",")}`,
    );
    throw new MobilePrivacyClearError(reason, failedCaches);
  }

  reporter.debug(`[kernel.mobilePrivacy] cleared reason=${reason}`);
}
