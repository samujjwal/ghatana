/**
 * @doc.type test
 * @doc.purpose Regression tests for mobile dashboard auth/session requirements
 * @doc.layer product
 * @doc.pattern UnitTest
 */

jest.mock("../../storage/NativeSessionStorage", () => ({
  getSessionSnapshot: jest.fn(),
}));

import { fetchDashboard } from "../useDashboard";
import { getSessionSnapshot } from "../../storage/NativeSessionStorage";

const mockGetSessionSnapshot = getSessionSnapshot;

describe("mobile useDashboard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = jest.fn();
  });

  it("fetches the learner dashboard with authenticated tenant headers", async () => {
    mockGetSessionSnapshot.mockReturnValue({
      accessToken: "access-token-123",
      refreshToken: "refresh-token-123",
      tenantId: "tenant-42",
    });
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        user: {
          id: "user-1",
          email: "student@example.com",
          displayName: "Student User",
        },
        currentEnrollments: [],
        recommendedModules: [],
        stats: {
          totalEnrollments: 0,
          completedModules: 0,
          averageProgress: 0,
        },
      }),
    });

    const result = await fetchDashboard();

    expect(global.fetch).toHaveBeenCalledWith("/api/v1/learning/dashboard", {
      headers: {
        Authorization: "Bearer access-token-123",
        "X-Tenant-ID": "tenant-42",
        "Content-Type": "application/json",
      },
    });
    expect(result).toMatchObject({
      user: {
        id: "user-1",
      },
    });
  });

  it("fails before issuing a request when the tenant session is missing", async () => {
    mockGetSessionSnapshot.mockReturnValue({
      accessToken: "access-token-123",
      refreshToken: "refresh-token-123",
      tenantId: null,
    });

    await expect(fetchDashboard()).rejects.toThrow(
      "Authenticated tenant session required to fetch dashboard",
    );
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
