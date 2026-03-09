/**
 * Domain Editor Page Component Tests
 *
 * Test suite for DomainEditorPage component:
 * - Create domain form submission
 * - Domain list display
 * - Edit domain functionality
 * - Delete domain with confirmation
 * - Publish domain workflow
 * - Error handling and loading states
 *
 * @doc.type test
 * @doc.purpose Unit tests for domain authoring UI
 * @doc.layer product
 * @doc.pattern Component Test
 */

import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, beforeEach, vi } from "vitest";
import DomainEditorPage from "../DomainEditorPage";

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

describe("DomainEditorPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        queryClient.clear();
    });

    describe("Domain List Display", () => {
        it("Displays loading state initially", () => {
            (global.fetch as any).mockReturnValueOnce(
                new Promise(() => { }) // Never resolves to show loading
            );

            renderWithProviders(<DomainEditorPage />);
            expect(screen.getByText(/loading/i)).toBeInTheDocument();
        });

        it("Displays list of domains from API", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "PUBLISHED",
                            createdAt: "2024-01-01",
                        },
                        {
                            id: "domain-2",
                            domain: "CHEMISTRY",
                            title: "Chemistry Essentials",
                            description: "Chemistry course",
                            author: "admin",
                            status: "DRAFT",
                            createdAt: "2024-01-02",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            await waitFor(() => {
                expect(screen.getByText("Physics Fundamentals")).toBeInTheDocument();
                expect(screen.getByText("Chemistry Essentials")).toBeInTheDocument();
            });
        });

        it("Displays empty state when no domains exist", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<DomainEditorPage />);

            await waitFor(() => {
                expect(screen.getByText(/no domains yet/i)).toBeInTheDocument();
            });
        });

        it("Displays error message on API failure", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: false,
                statusText: "Internal Server Error",
            });

            renderWithProviders(<DomainEditorPage />);

            await waitFor(() => {
                expect(screen.getByText(/error loading domains/i)).toBeInTheDocument();
            });
        });
    });

    describe("Create Domain Form", () => {
        it("Displays form when create button is clicked", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<DomainEditorPage />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new domain/i })
            );
            await userEvent.click(createButton);

            expect(screen.getByLabelText(/domain type/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
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
                            id: "new-domain-1",
                            domain: "PHYSICS",
                            title: "New Physics Course",
                            description: "New physics course",
                        },
                    }),
                });

            renderWithProviders(<DomainEditorPage />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new domain/i })
            );
            await userEvent.click(createButton);

            // Fill form
            await userEvent.selectOptions(
                screen.getByLabelText(/domain type/i),
                "PHYSICS"
            );
            await userEvent.type(
                screen.getByLabelText(/title/i),
                "New Physics Course"
            );
            await userEvent.type(
                screen.getByLabelText(/description/i),
                "New physics course"
            );

            // Submit
            const submitButton = screen.getByRole("button", { name: /create/i });
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    "/admin/api/v1/content/domains",
                    expect.objectContaining({
                        method: "POST",
                        body: expect.stringContaining("New Physics Course"),
                    })
                );
            });
        });

        it("Displays validation errors", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<DomainEditorPage />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new domain/i })
            );
            await userEvent.click(createButton);

            // Try to submit without filling required fields
            const submitButton = screen.getByRole("button", { name: /create/i });
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(screen.getByText(/required/i)).toBeInTheDocument();
            });
        });

        it("Closes form on cancel", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ data: [] }),
            });

            renderWithProviders(<DomainEditorPage />);

            const createButton = await waitFor(() =>
                screen.getByRole("button", { name: /new domain/i })
            );
            await userEvent.click(createButton);

            const cancelButton = screen.getByRole("button", { name: /cancel/i });
            await userEvent.click(cancelButton);

            expect(screen.queryByLabelText(/domain type/i)).not.toBeInTheDocument();
        });
    });

    describe("Edit Domain", () => {
        it("Opens edit form with current domain data", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "PUBLISHED",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            const editButton = await waitFor(() =>
                screen.getByRole("button", { name: /edit/i })
            );
            await userEvent.click(editButton);

            expect(screen.getByDisplayValue("Physics Fundamentals")).toBeInTheDocument();
            expect(screen.getByDisplayValue("Physics course")).toBeInTheDocument();
        });

        it("Submits edit request with updated data", async () => {
            (global.fetch as any)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: [
                            {
                                id: "domain-1",
                                domain: "PHYSICS",
                                title: "Physics Fundamentals",
                                description: "Physics course",
                                author: "admin",
                                status: "PUBLISHED",
                            },
                        ],
                    }),
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: {
                            id: "domain-1",
                            title: "Physics Fundamentals - Updated",
                        },
                    }),
                });

            renderWithProviders(<DomainEditorPage />);

            const editButton = await waitFor(() =>
                screen.getByRole("button", { name: /edit/i })
            );
            await userEvent.click(editButton);

            const titleInput = screen.getByDisplayValue("Physics Fundamentals");
            await userEvent.clear(titleInput);
            await userEvent.type(titleInput, "Physics Fundamentals - Updated");

            const saveButton = screen.getByRole("button", { name: /save/i });
            await userEvent.click(saveButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    "/admin/api/v1/content/domains/domain-1",
                    expect.objectContaining({
                        method: "PATCH",
                    })
                );
            });
        });
    });

    describe("Delete Domain", () => {
        it("Shows confirmation dialog on delete click", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "PUBLISHED",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            const deleteButton = await waitFor(() =>
                screen.getByRole("button", { name: /delete/i })
            );
            await userEvent.click(deleteButton);

            expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
        });

        it("Deletes domain on confirmation", async () => {
            (global.fetch as any)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: [
                            {
                                id: "domain-1",
                                domain: "PHYSICS",
                                title: "Physics Fundamentals",
                                description: "Physics course",
                                author: "admin",
                                status: "PUBLISHED",
                            },
                        ],
                    }),
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({}),
                });

            renderWithProviders(<DomainEditorPage />);

            const deleteButton = await waitFor(() =>
                screen.getByRole("button", { name: /delete/i })
            );
            await userEvent.click(deleteButton);

            const confirmButton = screen.getByRole("button", { name: /confirm/i });
            await userEvent.click(confirmButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    "/admin/api/v1/content/domains/domain-1",
                    expect.objectContaining({
                        method: "DELETE",
                    })
                );
            });
        });

        it("Cancels deletion on cancel button", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "PUBLISHED",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            const deleteButton = await waitFor(() =>
                screen.getByRole("button", { name: /delete/i })
            );
            await userEvent.click(deleteButton);

            const cancelButton = screen.getAllByRole("button", { name: /cancel/i })[0];
            await userEvent.click(cancelButton);

            expect(screen.queryByText(/are you sure/i)).not.toBeInTheDocument();
        });
    });

    describe("Publish Domain", () => {
        it("Shows publish button for draft domains", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "DRAFT",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            const publishButton = await waitFor(() =>
                screen.getByRole("button", { name: /publish/i })
            );
            expect(publishButton).toBeInTheDocument();
        });

        it("Publishes domain successfully", async () => {
            (global.fetch as any)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: [
                            {
                                id: "domain-1",
                                domain: "PHYSICS",
                                title: "Physics Fundamentals",
                                description: "Physics course",
                                author: "admin",
                                status: "DRAFT",
                            },
                        ],
                    }),
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => ({
                        data: {
                            id: "domain-1",
                            status: "PUBLISHED",
                        },
                    }),
                });

            renderWithProviders(<DomainEditorPage />);

            const publishButton = await waitFor(() =>
                screen.getByRole("button", { name: /publish/i })
            );
            await userEvent.click(publishButton);

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    "/admin/api/v1/content/domains/domain-1/publish",
                    expect.objectContaining({
                        method: "POST",
                    })
                );
            });
        });
    });

    describe("Status Badges", () => {
        it("Shows PUBLISHED badge for published domains", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "PUBLISHED",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            const publishedBadge = await waitFor(() =>
                screen.getByText(/published/i)
            );
            expect(publishedBadge).toHaveClass("bg-green-100");
        });

        it("Shows DRAFT badge for draft domains", async () => {
            (global.fetch as any).mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    data: [
                        {
                            id: "domain-1",
                            domain: "PHYSICS",
                            title: "Physics Fundamentals",
                            description: "Physics course",
                            author: "admin",
                            status: "DRAFT",
                        },
                    ],
                }),
            });

            renderWithProviders(<DomainEditorPage />);

            const draftBadge = await waitFor(() =>
                screen.getByText(/draft/i)
            );
            expect(draftBadge).toHaveClass("bg-gray-100");
        });
    });
});
