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

type DashboardFetchResponse = {
  ok: boolean;
  json: () => Promise<unknown>;
};

type MockFetchFn = jest.Mock<
  Promise<DashboardFetchResponse>,
  [string, { headers?: Record<string, string> }?]
>;

const mockGetSessionSnapshot =
  getSessionSnapshot as jest.MockedFunction<typeof getSessionSnapshot>;
let fetchMock: MockFetchFn;

describe("mobile useDashboard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    fetchMock = jest.fn() as MockFetchFn;
    (globalThis as unknown as { fetch: typeof fetch }).fetch =
      fetchMock as unknown as typeof fetch;
  });

  it("fetches the learner dashboard with authenticated tenant headers", async () => {
    mockGetSessionSnapshot.mockReturnValue({
      accessToken: "access-token-123",
      refreshToken: "refresh-token-123",
      tenantId: "tenant-42",
    });
    fetchMock.mockResolvedValue({
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

    expect(fetchMock).toHaveBeenCalledWith("/api/v1/learning/dashboard", {
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
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
