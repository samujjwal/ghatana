import { describe, expect, it, vi } from "vitest";
import {
  MobilePrivacyClearError,
  clearKernelMobilePrivacyState,
  type MobilePrivacyCacheClearer,
} from "../MobilePrivacyPlugin.js";

describe("MobilePrivacyPlugin", () => {
  it("clears every registered cache", async () => {
    const encryptedPhi = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const offline = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);

    await clearKernelMobilePrivacyState("logout", [
      { cacheName: "encrypted-phi", clear: encryptedPhi },
      { cacheName: "dashboard-offline", clear: offline },
    ]);

    expect(encryptedPhi).toHaveBeenCalledTimes(1);
    expect(offline).toHaveBeenCalledTimes(1);
  });

  it("attempts every clearer and reports all failures", async () => {
    const reporter = { debug: vi.fn(), warn: vi.fn() };
    const clearers: readonly MobilePrivacyCacheClearer[] = [
      { cacheName: "encrypted-phi", clear: vi.fn<() => Promise<void>>().mockRejectedValue(new Error("failed")) },
      { cacheName: "dashboard-offline", clear: vi.fn<() => Promise<void>>().mockResolvedValue(undefined) },
      { cacheName: "documents-offline", clear: vi.fn<() => Promise<void>>().mockRejectedValue(new Error("failed")) },
    ];

    await expect(clearKernelMobilePrivacyState("session-clear", clearers, reporter)).rejects.toMatchObject({
      reason: "session-clear",
      failedCaches: ["encrypted-phi", "documents-offline"],
    } satisfies Partial<MobilePrivacyClearError>);

    expect(reporter.warn).toHaveBeenCalledTimes(1);
  });
});
