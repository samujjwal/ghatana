jest.mock("../mobilePrivacyPlugin", () => ({
  clearMobilePrivacyState: jest.fn(() => Promise.resolve()),
}));

import * as SecureStore from "expo-secure-store";
import { clearMobilePrivacyState } from "../mobilePrivacyPlugin";
import {
  clearMobileSession,
  loadMobileSession,
  saveMobileSession,
} from "../mobileSessionStore";
import type { MobileSession } from "../../types";

const mockClearMobilePrivacyState =
  clearMobilePrivacyState as jest.MockedFunction<typeof clearMobilePrivacyState>;

const liveSession: MobileSession = {
  tenantId: "tenant-1",
  principalId: "patient-1",
  role: "patient",
  persona: "patient",
  tier: "core",
  facilityId: "facility-1",
  name: "Patient One",
  expiresAt: "2099-01-01T00:00:00.000Z",
};

describe("mobileSessionStore", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("clears session through the generalized mobile privacy plugin", async () => {
    await clearMobileSession();

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith(
      "phr-mobile-session-v1",
    );
    expect(mockClearMobilePrivacyState).toHaveBeenCalledWith("session-clear");
  });

  it("clears privacy state with session-expired reason for expired sessions", async () => {
    await saveMobileSession({
      ...liveSession,
      expiresAt: "2000-01-01T00:00:00.000Z",
    });

    await expect(loadMobileSession()).resolves.toBeNull();
    expect(mockClearMobilePrivacyState).toHaveBeenCalledWith("session-expired");
  });

  it("clears privacy state with session-scope-changed reason for scope drift", async () => {
    await saveMobileSession(liveSession);

    await expect(
      loadMobileSession({
        ...liveSession,
        persona: "caregiver",
      }),
    ).resolves.toBeNull();

    expect(mockClearMobilePrivacyState).toHaveBeenCalledWith(
      "session-scope-changed",
    );
  });

  it("clears privacy state when facility scope changes", async () => {
    await saveMobileSession(liveSession);

    await expect(
      loadMobileSession({
        ...liveSession,
        facilityId: "facility-2",
      }),
    ).resolves.toBeNull();

    expect(mockClearMobilePrivacyState).toHaveBeenCalledWith(
      "session-scope-changed",
    );
  });
});
