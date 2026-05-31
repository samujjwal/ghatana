import {
  clearMobilePrivacyState,
  MobilePrivacyClearError,
  type MobilePrivacyCacheClearer,
} from "../mobilePrivacyPlugin";

describe("mobilePrivacyPlugin", () => {
  it("clears every registered mobile privacy cache for a reason", async () => {
    const encryptedPhi = jest.fn<Promise<void>, []>().mockResolvedValue();
    const dashboardOffline = jest.fn<Promise<void>, []>().mockResolvedValue();

    await clearMobilePrivacyState("logout", [
      { cacheName: "encrypted-phi", clear: encryptedPhi },
      { cacheName: "dashboard-offline", clear: dashboardOffline },
    ]);

    expect(encryptedPhi).toHaveBeenCalledTimes(1);
    expect(dashboardOffline).toHaveBeenCalledTimes(1);
  });

  it("attempts all clearers and reports every failed cache", async () => {
    const consoleWarn = jest.spyOn(console, "warn").mockImplementation(() => {});
    const encryptedPhi = jest.fn<Promise<void>, []>().mockRejectedValue(new Error("failed"));
    const dashboardOffline = jest.fn<Promise<void>, []>().mockResolvedValue();
    const documentsOffline = jest.fn<Promise<void>, []>().mockRejectedValue(new Error("failed"));
    const clearers: readonly MobilePrivacyCacheClearer[] = [
      { cacheName: "encrypted-phi", clear: encryptedPhi },
      { cacheName: "dashboard-offline", clear: dashboardOffline },
      { cacheName: "documents-offline", clear: documentsOffline },
    ];

    await expect(clearMobilePrivacyState("session-clear", clearers)).rejects.toMatchObject({
      reason: "session-clear",
      failedCaches: ["encrypted-phi", "documents-offline"],
    } satisfies Partial<MobilePrivacyClearError>);

    expect(dashboardOffline).toHaveBeenCalledTimes(1);
    consoleWarn.mockRestore();
  });
});
