import {
  MobilePrivacyClearError,
  clearKernelMobilePrivacyState,
  type MobilePrivacyCacheClearer,
  type MobilePrivacyClearReason,
} from "@ghatana/kernel-product-contracts/mobile-privacy";
import { phiClearAll } from "./phiEncryptedStorage";
import { clearDashboardOffline } from "./offlineStore";

export { MobilePrivacyClearError };
export type { MobilePrivacyCacheClearer, MobilePrivacyClearReason };

const defaultMobilePrivacyClearers: readonly MobilePrivacyCacheClearer[] = [
  {
    cacheName: "encrypted-phi",
    clear: phiClearAll,
  },
  {
    cacheName: "dashboard-offline",
    clear: clearDashboardOffline,
  },
];

export async function clearMobilePrivacyState(
  reason: MobilePrivacyClearReason,
  clearers: readonly MobilePrivacyCacheClearer[] = defaultMobilePrivacyClearers,
): Promise<void> {
  await clearKernelMobilePrivacyState(reason, clearers, console);
}
