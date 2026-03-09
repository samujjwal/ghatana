/**
 * MyStoriesCard Component Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for MyStoriesCard component
 * @doc.layer product
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import { MemoryRouter } from 'react-router';
import { MyStoriesCard } from '../MyStoriesCard';

// Mock the useMyWorkItems hook
vi.mock('@/hooks/useMyWorkItems', () => ({
    useMyWorkItems: vi.fn(() => ({
        workItems: [
            {
                id: 'WI-1234',
                title: 'Test Work Item',
                type: 'story',
                status: 'in-progress',
                priority: 'p1',
                service: 'test-service',
                assignee: { id: 'eng-1', name: 'Test User' },
                updatedAt: new Date().toISOString(),
            },
        ],
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
    })),
}));

describe('MyStoriesCard', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: {
                    retry: false,
                },
            },
        });
    });

    const renderWithProviders = (ui: React.ReactElement) => {
        return render(
            <QueryClientProvider client={queryClient}>
                <JotaiProvider>
                    <MemoryRouter>
                        {ui}
                    </MemoryRouter>
                </JotaiProvider>
            </QueryClientProvider>
        );
    };

    it('should render the My Stories card title', async () => {
        renderWithProviders(<MyStoriesCard />);

        await waitFor(() => {
            expect(screen.getByText('My Stories')).toBeInTheDocument();
        });
    });

    it('should render work items', async () => {
        renderWithProviders(<MyStoriesCard />);

        await waitFor(() => {
            expect(screen.getByText('Test Work Item')).toBeInTheDocument();
            expect(screen.getByText('WI-1234')).toBeInTheDocument();
        });
    });

    it('should call onItemClick when an item is clicked', async () => {
        const mockOnClick = vi.fn();
        renderWithProviders(<MyStoriesCard onItemClick={mockOnClick} />);

        await waitFor(() => {
            const item = screen.getByText('Test Work Item');
            item.closest('button')?.click();
        });

        expect(mockOnClick).toHaveBeenCalledWith('WI-1234');
    });
});
