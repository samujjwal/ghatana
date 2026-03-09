/**
 * Concept Editor Component Tests
 *
 * Test suite for ConceptEditor component:
 * - Concept form submission
 * - Keyword management (add/remove tags)
 * - Concept list display
 * - Edit and delete functionality
 * - Difficulty level selection
 * - Error handling and validation
 *
 * @doc.type test
 * @doc.purpose Unit tests for concept authoring UI
 * @doc.layer product
 * @doc.pattern Component Test
 */

import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, beforeEach, vi } from "vitest";
import ConceptEditor from "../ConceptEditor";

// Mock fetch
global.fetch = vi.fn();

const queryClient = new QueryClient({
    defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
    },
});

const renderWithProviders = (component: React.ReactElement) => {
    return render(
        <QueryClientProvider client={queryClient}>
            {component}
        </QueryClientProvider>
    );
};

const mockDomainId = "domain-physics-001";

describe("ConceptEditor", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        queryClient.clear();
    });

    describe("Concept List Display", () => {
        it("Displays loading state initially", () => {
            (global.fetch as any).mockReturnValueOnce(
                new Promise(() => { }) // Never resolves to show loading
            );

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);
            expect(screen.getByText(/loading/i)).toBeInTheDocument();
        });

        it("Displays list of concepts for domain", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "concept-1",
                            domainId: mockDomainId,
                            name: "Newton's Laws",
                            description: "Three fundamental laws of motion",
                            level: "INTERMEDIATE",
                            keywords: JSON.stringify(["force", "motion", "acceleration"]),
                        },
                        {
                            id: "concept-2",
                            domainId: mockDomainId,
                            name: "Energy Conservation",
                            description: "Energy cannot be created or destroyed",
                            level: "ADVANCED",
                            keywords: JSON.stringify(["energy", "conservation"]),
                        },
                    ],
                }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            await waitFor(() => {
                expect(screen.getByText("Newton's Laws")).toBeInTheDocument();
                expect(screen.getByText("Energy Conservation")).toBeInTheDocument();
            });
        });

        it("Displays empty state when no concepts exist", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            await waitFor(() => {
                expect(screen.getByText(/no concepts yet/i)).toBeInTheDocument();
            });
        });

        it("Displays error message on API failure", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: false,
                statusText: "Internal Server Error",
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            await waitFor(() => {
                expect(screen.getByText(/error loading concepts/i)).toBeInTheDocument();
            });
        });
    });

    describe("Create Concept Form", () => {
        it("Displays form when create button is clicked", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            expect(screen.getByLabelText(/concept name/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/difficulty/i)).toBeInTheDocument();
        });

        it("Submits form with correct payload", async () => {
            (global.fetch as any)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({ data: [] }),
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: {
                            id: "concept-new-1",
                            domainId: mockDomainId,
                            name: "Friction",
                            description: "Force opposing motion",
                        },
                    }),
                });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            await userEvent.type(screen.getByLabelText(/concept name/i), "Friction");
            await userEvent.type(
                screen.getByLabelText(/description/i),
                "Force opposing motion"
            );
            await userEvent.selectOptions(
                screen.getByLabelText(/difficulty/i),
                "INTERMEDIATE"
            );

            const submitButton = screen.getByRole("button", { name: /create/i });
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    `/admin/api/v1/content/domains/${mockDomainId}/concepts`,
                    expect.objectContaining({
                        method: "POST",
                        body: expect.stringContaining("Friction"),
                    })
                );
            });
        });

        it("Displays validation errors for required fields", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const submitButton = screen.getByRole("button", { name: /create/i });
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(screen.getByText(/required/i)).toBeInTheDocument();
            });
        });
    });

    describe("Keyword Management", () => {
        it("Adds keyword when Enter is pressed", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const keywordInput = screen.getByLabelText(/keywords/i);
            await userEvent.type(keywordInput, "physics");
            fireEvent.keyDown(keywordInput, { key: "Enter", code: "Enter" });

            expect(screen.getByText("physics")).toBeInTheDocument();
        });

        it("Displays multiple keywords as tags", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const keywordInput = screen.getByLabelText(/keywords/i);

            // Add multiple keywords
            await userEvent.type(keywordInput, "physics");
            fireEvent.keyDown(keywordInput, { key: "Enter", code: "Enter" });

            await userEvent.type(keywordInput, "motion");
            fireEvent.keyDown(keywordInput, { key: "Enter", code: "Enter" });

            await userEvent.type(keywordInput, "force");
            fireEvent.keyDown(keywordInput, { key: "Enter", code: "Enter" });

            expect(screen.getByText("physics")).toBeInTheDocument();
            expect(screen.getByText("motion")).toBeInTheDocument();
            expect(screen.getByText("force")).toBeInTheDocument();
        });

        it("Removes keyword when close button is clicked", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const keywordInput = screen.getByLabelText(/keywords/i);
            await userEvent.type(keywordInput, "physics");
            fireEvent.keyDown(keywordInput, { key: "Enter", code: "Enter" });

            const closeButton = screen.getByRole("button", {
                name: /remove.*physics/i,
            });
            await userEvent.click(closeButton);

            expect(screen.queryByText("physics")).not.toBeInTheDocument();
        });
    });

    describe("Difficulty Selection", () => {
        it("Displays difficulty dropdown with all options", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const difficultySelect = screen.getByLabelText(/difficulty/i);
            expect(difficultySelect).toBeInTheDocument();

            await userEvent.click(difficultySelect);

            expect(screen.getByText("Foundational")).toBeInTheDocument();
            expect(screen.getByText("Intermediate")).toBeInTheDocument();
            expect(screen.getByText("Advanced")).toBeInTheDocument();
        });

        it("Selects difficulty correctly", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const difficultySelect = screen.getByLabelText(
                /difficulty/i
            ) as HTMLSelectElement;
            await userEvent.selectOptions(difficultySelect, "ADVANCED");

            expect(difficultySelect.value).toBe("ADVANCED");
        });
    });

    describe("Edit Concept", () => {
        it("Opens edit form with current concept data", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "concept-1",
                            domainId: mockDomainId,
                            name: "Newton's Laws",
                            description: "Three fundamental laws",
                            level: "INTERMEDIATE",
                            keywords: JSON.stringify(["force", "motion"]),
                        },
                    ],
                }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const editButton = await waitFor(() =>
                screen.getByRole("button", { name: /edit/i })
            );
            await userEvent.click(editButton);

            expect(screen.getByDisplayValue("Newton's Laws")).toBeInTheDocument();
            expect(screen.getByDisplayValue("Three fundamental laws")).toBeInTheDocument();
        });

        it("Submits edit request with updated data", async () => {
            (global.fetch as any)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: [
                            {
                                id: "concept-1",
                                domainId: mockDomainId,
                                name: "Newton's Laws",
                                description: "Three fundamental laws",
                                level: "INTERMEDIATE",
                                keywords: JSON.stringify(["force", "motion"]),
                            },
                        ],
                    }),
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: { id: "concept-1", description: "Updated description" },
                    }),
                });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const editButton = await waitFor(() =>
                screen.getByRole("button", { name: /edit/i })
            );
            await userEvent.click(editButton);

            const descriptionInput = screen.getByDisplayValue("Three fundamental laws");
            await userEvent.clear(descriptionInput);
            await userEvent.type(descriptionInput, "Updated description");

            const saveButton = screen.getByRole("button", { name: /save/i });
            await userEvent.click(saveButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    `/admin/api/v1/content/domains/${mockDomainId}/concepts/concept-1`,
                    expect.objectContaining({
                        method: "PATCH",
                    })
                );
            });
        });
    });

    describe("Delete Concept", () => {
        it("Shows confirmation dialog on delete click", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "concept-1",
                            domainId: mockDomainId,
                            name: "Newton's Laws",
                            description: "Three fundamental laws",
                            level: "INTERMEDIATE",
                            keywords: JSON.stringify([]),
                        },
                    ],
                }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const deleteButton = await waitFor(() =>
                screen.getByRole("button", { name: /delete/i })
            );
            await userEvent.click(deleteButton);

            expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
        });

        it("Deletes concept on confirmation", async () => {
            (global.fetch as any)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: [
                            {
                                id: "concept-1",
                                domainId: mockDomainId,
                                name: "Newton's Laws",
                                description: "Three fundamental laws",
                                level: "INTERMEDIATE",
                                keywords: JSON.stringify([]),
                            },
                        ],
                    }),
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({}),
                });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const deleteButton = await waitFor(() =>
                screen.getByRole("button", { name: /delete/i })
            );
            await userEvent.click(deleteButton);

            const confirmButton = screen.getByRole("button", { name: /confirm/i });
            await userEvent.click(confirmButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    `/admin/api/v1/content/domains/${mockDomainId}/concepts/concept-1`,
                    expect.objectContaining({
                        method: "DELETE",
                    })
                );
            });
        });
    });

    describe("Form Validation", () => {
        it("Prevents submission with empty concept name", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            const submitButton = screen.getByRole("button", { name: /create/i });
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(screen.getByText(/required/i)).toBeInTheDocument();
            });
        });

        it("Prevents submission with empty description", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<ConceptEditor domainId={mockDomainId} />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new concept/i })
            );
            await userEvent.click(createButton);

            await userEvent.type(screen.getByLabelText(/concept name/i), "Test Concept");

            const submitButton = screen.getByRole("button", { name: /create/i });
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(screen.getByText(/description is required/i)).toBeInTheDocument();
            });
        });
    });
});
