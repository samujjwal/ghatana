/**
 * Test suite for OmnipresentAITutor component
 *
 * @doc.type tests
 * @doc.purpose Unit tests for the omnipresent AI tutor component
 * @doc.layer product
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { OmnipresentAITutor } from "../OmnipresentAITutor";

// Mock fetch globally
const mockFetch = vi.fn();
global.fetch = mockFetch;

// Helper to get the submit button (icon-only button with type="submit")
function getSubmitButton() {
  const buttons = screen.getAllByRole("button");
  const submitButton = buttons.find(
    (btn) => btn.getAttribute("type") === "submit",
  );
  if (!submitButton) {
    throw new Error("Submit button not found");
  }
  return submitButton;
}

// Create a fresh QueryClient for each test
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

function renderWithProviders(
  ui: React.ReactElement,
  { route = "/dashboard" } = {},
) {
  const queryClient = createTestQueryClient();
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

describe("OmnipresentAITutor", () => {
  beforeEach(() => {
    mockFetch.mockClear();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("Visibility", () => {
    it("renders floating button on non-AI-tutor pages", () => {
      renderWithProviders(<OmnipresentAITutor />, { route: "/dashboard" });

      const button = screen.getByRole("button", { name: /ai tutor/i });
      expect(button).toBeInTheDocument();
    });

    it("does not render on the AI Tutor page", () => {
      renderWithProviders(<OmnipresentAITutor />, { route: "/ai-tutor" });

      const button = screen.queryByRole("button", { name: /ai tutor/i });
      expect(button).not.toBeInTheDocument();
    });

    it("renders on pathways page", () => {
      renderWithProviders(<OmnipresentAITutor />, { route: "/pathways" });

      const button = screen.getByRole("button", { name: /ai tutor/i });
      expect(button).toBeInTheDocument();
    });
  });

  describe("Panel Toggle", () => {
    it("opens panel when floating button is clicked", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      const button = screen.getByRole("button", { name: /ai tutor/i });
      fireEvent.click(button);

      await waitFor(() => {
        expect(screen.getByText(/I'm your AI tutor/i)).toBeInTheDocument();
      });
    });

    it("closes panel when close button is clicked", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      // Open the panel
      const floatingButton = screen.getByRole("button", { name: /ai tutor/i });
      fireEvent.click(floatingButton);

      await waitFor(() => {
        expect(screen.getByText(/I'm your AI tutor/i)).toBeInTheDocument();
      });

      // Close the panel
      const closeButton = screen.getByRole("button", { name: /close/i });
      fireEvent.click(closeButton);

      // Panel should be hidden (opacity-0, pointer-events-none) but floating button visible
      await waitFor(() => {
        const floatingBtn = screen.getByRole("button", { name: /ai tutor/i });
        // Floating button should be visible (scale-100)
        expect(floatingBtn.className).toContain("scale-100");
      });
    });

    it("closes panel when minimize button is clicked", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      // Open the panel
      const floatingButton = screen.getByRole("button", { name: /ai tutor/i });
      fireEvent.click(floatingButton);

      await waitFor(() => {
        expect(screen.getByText(/I'm your AI tutor/i)).toBeInTheDocument();
      });

      // Minimize the panel
      const minimizeButton = screen.getByRole("button", { name: /minimize/i });
      fireEvent.click(minimizeButton);

      // Panel should be hidden (opacity-0, pointer-events-none) but floating button visible
      await waitFor(() => {
        const floatingBtn = screen.getByRole("button", { name: /ai tutor/i });
        // Floating button should be visible (scale-100)
        expect(floatingBtn.className).toContain("scale-100");
      });
    });
  });

  describe("Welcome Message", () => {
    it("shows welcome message when panel is opened", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      const button = screen.getByRole("button", { name: /ai tutor/i });
      fireEvent.click(button);

      await waitFor(() => {
        expect(
          screen.getByText(/I can help you understand concepts/i),
        ).toBeInTheDocument();
      });
    });
  });

  describe("Message Submission", () => {
    it("sends message when form is submitted", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: {
              answer: "This is a test response from the AI tutor.",
              citations: [],
              followUpQuestions: ["Would you like to learn more?"],
              safety: { blocked: false },
            },
          }),
      });

      renderWithProviders(<OmnipresentAITutor />);

      // Open panel
      const floatingButton = screen.getByRole("button", { name: /ai tutor/i });
      fireEvent.click(floatingButton);

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      // Type and submit message
      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "What is photosynthesis?" } });

      const submitButton = getSubmitButton();
      fireEvent.click(submitButton);

      // Wait for API call
      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          "/api/v1/ai/tutor/query",
          expect.objectContaining({
            method: "POST",
            headers: expect.objectContaining({
              "Content-Type": "application/json",
            }),
          }),
        );
      });
    });

    it("shows user message in chat after submission", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: {
              answer: "Test response",
              safety: { blocked: false },
            },
          }),
      });

      renderWithProviders(<OmnipresentAITutor />);

      // Open panel
      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      // Submit message
      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test question" } });
      fireEvent.click(getSubmitButton());

      // Check user message appears
      await waitFor(() => {
        expect(screen.getByText("Test question")).toBeInTheDocument();
      });
    });

    it("clears input after submission", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: { answer: "Test", safety: { blocked: false } },
          }),
      });

      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i) as HTMLInputElement;
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      await waitFor(() => {
        expect(input.value).toBe("");
      });
    });

    it("does not submit empty messages", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const submitButton = getSubmitButton();
      fireEvent.click(submitButton);

      // API should not be called
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe("Error Handling", () => {
    it("displays error message when API fails", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ error: "Server error" }),
      });

      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      await waitFor(() => {
        expect(screen.getByText(/error/i)).toBeInTheDocument();
      });
    });

    it("handles network errors gracefully", async () => {
      mockFetch.mockRejectedValueOnce(new Error("Network error"));

      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      await waitFor(() => {
        expect(screen.getByText(/error/i)).toBeInTheDocument();
      });
    });
  });

  describe("Quick Actions", () => {
    it("renders quick action buttons", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByText(/Explain this concept/i)).toBeInTheDocument();
        expect(screen.getByText(/Give me an example/i)).toBeInTheDocument();
        expect(
          screen.getByText(/What should I learn next/i),
        ).toBeInTheDocument();
      });
    });

    it("clicking quick action fills input", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByText(/Explain this concept/i)).toBeInTheDocument();
      });

      const quickAction = screen.getByText(/Explain this concept/i);
      fireEvent.click(quickAction);

      const input = screen.getByPlaceholderText(/ask/i) as HTMLInputElement;
      expect(input.value).toBe("Explain this concept");
    });
  });

  describe("Context Awareness", () => {
    it("includes page context in API request for pathways page", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: { answer: "Test", safety: { blocked: false } },
          }),
      });

      renderWithProviders(<OmnipresentAITutor />, { route: "/pathways" });

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          "/api/v1/ai/tutor/query",
          expect.objectContaining({
            body: expect.stringContaining("learning paths"),
          }),
        );
      });
    });

    it("includes page context in API request for analytics page", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: { answer: "Test", safety: { blocked: false } },
          }),
      });

      renderWithProviders(<OmnipresentAITutor />, { route: "/analytics" });

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          "/api/v1/ai/tutor/query",
          expect.objectContaining({
            body: expect.stringContaining("analytics"),
          }),
        );
      });
    });
  });

  describe("Loading State", () => {
    it("shows loading indicator while waiting for response", async () => {
      let resolvePromise: (value: unknown) => void;
      mockFetch.mockReturnValueOnce(
        new Promise((resolve) => {
          resolvePromise = resolve;
        }),
      );

      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      // Check for loading state (disabled button or spinner)
      await waitFor(() => {
        const submitButton = getSubmitButton();
        expect(submitButton).toBeDisabled();
      });

      // Resolve the promise
      resolvePromise!({
        ok: true,
        json: () =>
          Promise.resolve({
            response: { answer: "Test", safety: { blocked: false } },
          }),
      });
    });

    it("disables input while loading", async () => {
      let resolvePromise: (value: unknown) => void;
      mockFetch.mockReturnValueOnce(
        new Promise((resolve) => {
          resolvePromise = resolve;
        }),
      );

      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/ask/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/ask/i);
      fireEvent.change(input, { target: { value: "Test" } });
      fireEvent.click(getSubmitButton());

      await waitFor(() => {
        expect(input).toBeDisabled();
      });

      resolvePromise!({
        ok: true,
        json: () =>
          Promise.resolve({
            response: { answer: "Test", safety: { blocked: false } },
          }),
      });
    });
  });

  describe("Accessibility", () => {
    it("has accessible labels for all interactive elements", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      // Floating button has accessible name
      const floatingButton = screen.getByRole("button", { name: /ai tutor/i });
      expect(floatingButton).toHaveAccessibleName();

      fireEvent.click(floatingButton);

      await waitFor(() => {
        // Close button exists with title attribute
        const closeButton = screen.getByTitle("Close");
        expect(closeButton).toBeInTheDocument();

        // Input has accessible label
        const input = screen.getByPlaceholderText(/ask/i);
        expect(input).toBeInTheDocument();
      });
    });

    it("focuses input when panel opens", async () => {
      renderWithProviders(<OmnipresentAITutor />);

      fireEvent.click(screen.getByRole("button", { name: /ai tutor/i }));

      await waitFor(() => {
        const input = screen.getByPlaceholderText(/ask/i);
        expect(document.activeElement).toBe(input);
      });
    });
  });

  describe("Mobile Responsiveness", () => {
    it("renders correctly at different viewport sizes", () => {
      // This is a basic test - in real e2e tests we'd use viewport manipulation
      renderWithProviders(<OmnipresentAITutor />);

      const button = screen.getByRole("button", { name: /ai tutor/i });
      expect(button).toHaveClass("fixed");
    });
  });
});
