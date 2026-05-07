import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { ModulePage } from "../ModulePage";

const navigateMock = vi.fn();
const mockMutate = vi.fn();
const mockEnrollInModule = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>(
    "react-router-dom",
  );
  return {
    ...actual,
    useNavigate: () => navigateMock,
    useParams: () => ({ slug: "kinematics-basics" }),
  };
});

vi.mock("../../hooks/useModuleBySlug", () => ({
  useModuleBySlug: vi.fn(),
}));

vi.mock("../../hooks/useProgressUpdate", () => ({
  useProgressUpdate: vi.fn(),
}));

vi.mock("../../api/tutorputorClient", () => ({
  apiClient: {
    enrollInModule: (...args: unknown[]) => mockEnrollInModule(...args),
  },
}));

import { useModuleBySlug } from "../../hooks/useModuleBySlug";
import { useProgressUpdate } from "../../hooks/useProgressUpdate";

const mockUseModuleBySlug = vi.mocked(useModuleBySlug);
const mockUseProgressUpdate = vi.mocked(useProgressUpdate);

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ModulePage", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mockMutate.mockReset();
    mockEnrollInModule.mockReset();
    mockEnrollInModule.mockResolvedValue({
      enrollment: { id: "enrollment-1" },
    });
    mockUseProgressUpdate.mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as never);
  });

  it("submits a progress update with the next step delta for enrolled learners", async () => {
    mockUseModuleBySlug.mockReturnValue({
      data: {
        module: {
          id: "module-1",
          title: "Kinematics Basics",
          slug: "kinematics-basics",
          description: "Understand speed, velocity, and acceleration.",
          domain: "PHYSICS",
          difficulty: "beginner",
          estimatedTimeMinutes: 30,
          learningObjectives: [
            {
              id: "objective-1",
              label: "Explain acceleration",
              taxonomyLevel: "understand",
            },
          ],
          contentBlocks: [
            { id: "block-1", blockType: "text", payload: { markdown: "Block 1." } },
            { id: "block-2", blockType: "text", payload: { markdown: "Block 2." } },
            { id: "block-3", blockType: "text", payload: { markdown: "Block 3." } },
            { id: "block-4", blockType: "text", payload: { markdown: "Block 4." } },
          ],
        },
        userEnrollment: {
          id: "enrollment-1",
          moduleId: "module-1",
          userId: "learner-1",
          status: "active",
          progressPercent: 25,
          timeSpentSeconds: 600,
          enrolledAt: "2026-04-20T00:00:00.000Z",
        },
      },
      isLoading: false,
      error: null,
    } as never);

    renderWithProviders(<ModulePage />);

    await userEvent.click(
      screen.getByRole("button", { name: /mark step completed/i }),
    );

    // 4 blocks, at 25% (1 done) → completing next step → 2/4 = 50%
    expect(mockMutate).toHaveBeenCalledWith({
      enrollmentId: "enrollment-1",
      progressPercent: 50,
      timeSpentSecondsDelta: 60,
    });
  });

  it("caps progress at 100% on the final step", async () => {
    mockUseModuleBySlug.mockReturnValue({
      data: {
        module: {
          id: "module-1",
          title: "Kinematics Basics",
          slug: "kinematics-basics",
          description: "Understand speed, velocity, and acceleration.",
          domain: "PHYSICS",
          difficulty: "beginner",
          estimatedTimeMinutes: 30,
          learningObjectives: [],
          contentBlocks: [
            { id: "block-1", blockType: "text", payload: { markdown: "Block 1." } },
            { id: "block-2", blockType: "text", payload: { markdown: "Block 2." } },
          ],
        },
        userEnrollment: {
          id: "enrollment-1",
          moduleId: "module-1",
          userId: "learner-1",
          status: "active",
          progressPercent: 100,
          timeSpentSeconds: 1200,
          enrolledAt: "2026-04-20T00:00:00.000Z",
        },
      },
      isLoading: false,
      error: null,
    } as never);

    renderWithProviders(<ModulePage />);

    await userEvent.click(
      screen.getByRole("button", { name: /mark step completed/i }),
    );

    expect(mockMutate).toHaveBeenCalledWith(
      expect.objectContaining({ progressPercent: 100 }),
    );
  });

  it("starts module enrollment for learners without an existing enrollment", async () => {
    mockUseModuleBySlug.mockReturnValue({
      data: {
        module: {
          id: "module-1",
          title: "Kinematics Basics",
          slug: "kinematics-basics",
          description: "Understand speed, velocity, and acceleration.",
          domain: "PHYSICS",
          difficulty: "beginner",
          estimatedTimeMinutes: 30,
          learningObjectives: [],
          contentBlocks: [],
        },
        userEnrollment: null,
      },
      isLoading: false,
      error: null,
    } as never);

    renderWithProviders(<ModulePage />);

    await userEvent.click(screen.getByRole("button", { name: /start module/i }));

    await waitFor(() => {
      expect(mockEnrollInModule).toHaveBeenCalledWith("module-1");
    });
  });
});