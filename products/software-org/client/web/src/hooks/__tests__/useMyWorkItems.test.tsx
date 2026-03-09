/**
 * useMyWorkItems Hook Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for useMyWorkItems hook
 * @doc.layer product
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import { useMyWorkItems, useWorkItem } from '../useMyWorkItems';
import * as workItemsApi from '@/services/api/workItemsApi';

// Mock the API
vi.mock('@/services/api/workItemsApi', () => ({
    workItemsApi: {
        getMyWorkItems: vi.fn(),
        getWorkItem: vi.fn(),
        updateWorkItemStatus: vi.fn(),
        updateWorkItemPlan: vi.fn(),
        updateAcceptanceCriteria: vi.fn(),
        completeWorkItem: vi.fn(),
    },
}));

describe('useMyWorkItems', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: {
                    retry: false,
                },
            },
        });
        vi.clearAllMocks();
    });

    const wrapper = ({ children }: { children: React.ReactNode }) => (
        <QueryClientProvider client={queryClient}>
            <JotaiProvider>{children}</JotaiProvider>
        </QueryClientProvider>
    );

    it('should fetch work items', async () => {
        const mockItems: {
            id: string;
            title: string;
            type: 'story' | 'bug' | 'task' | 'epic' | 'spike';
            status: 'ready' | 'in-progress' | 'in-review' | 'staging' | 'deployed' | 'done' | 'blocked' | 'backlog';
            priority: 'p0' | 'p1' | 'p2' | 'p3';
            service?: string;
            assignee: { id: string; name: string };
            updatedAt: string;
        }[] = [
                {
                    id: 'WI-1234',
                    title: 'Test Story',
                    type: 'story',
                    status: 'ready',
                    priority: 'p1',
                    service: 'test-service',
                    assignee: { id: 'eng-1', name: 'Test User' },
                    updatedAt: new Date().toISOString(),
                },
            ];

        vi.mocked(workItemsApi.workItemsApi.getMyWorkItems).mockResolvedValue(mockItems);

        const { result } = renderHook(() => useMyWorkItems(), { wrapper });

        await waitFor(() => {
            expect(result.current.isLoading).toBe(false);
        });

        expect(result.current.workItems).toEqual(mockItems);
        expect(result.current.isError).toBe(false);
    });

    it('should handle errors', async () => {
        vi.mocked(workItemsApi.workItemsApi.getMyWorkItems).mockRejectedValue(
            new Error('Failed to fetch')
        );

        const { result } = renderHook(() => useMyWorkItems(), { wrapper });

        await waitFor(() => {
            expect(result.current.isError).toBe(true);
        });

        expect(result.current.error).toBeTruthy();
    });
});

describe('useWorkItem', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: {
                    retry: false,
                },
            },
        });
        vi.clearAllMocks();
    });

    const wrapper = ({ children }: { children: React.ReactNode }) => (
        <QueryClientProvider client={queryClient}>
            <JotaiProvider>{children}</JotaiProvider>
        </QueryClientProvider>
    );

    it('should fetch a single work item', async () => {
        const mockItem: {
            id: string;
            title: string;
            description: string;
            type: 'story';
            status: 'ready';
            priority: 'p1';
            assignee: { id: string; name: string; avatar: string };
            reporter: { id: string; name: string };
            service: string;
            labels: string[];
            acceptanceCriteria: never[];
            linkedPullRequests: never[];
            linkedPipelines: never[];
            contextLinks: never[];
            createdAt: string;
            updatedAt: string;
        } = {
            id: 'WI-1234',
            title: 'Test Story',
            description: 'Test description',
            type: 'story',
            status: 'ready',
            priority: 'p1',
            assignee: { id: 'eng-1', name: 'Test User', avatar: '' },
            reporter: { id: 'pm-1', name: 'PM User' },
            service: 'test-service',
            labels: ['test'],
            acceptanceCriteria: [],
            linkedPullRequests: [],
            linkedPipelines: [],
            contextLinks: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };

        vi.mocked(workItemsApi.workItemsApi.getWorkItem).mockResolvedValue(mockItem);

        const { result } = renderHook(() => useWorkItem('WI-1234'), { wrapper });

        await waitFor(() => {
            expect(result.current.isLoading).toBe(false);
        });

        expect(result.current.data).toEqual(mockItem);
    });
});
