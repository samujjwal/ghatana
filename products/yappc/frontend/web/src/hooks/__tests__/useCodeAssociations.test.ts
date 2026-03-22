/**
 * Unit Tests for useCodeAssociations Hook
 * 
 * @doc.type test
 * @doc.purpose Unit tests for code association hooks
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import {
    useCodeAssociations,
    useCreateCodeAssociation,
    useDeleteCodeAssociation,
    getAssociationIcon,
    getAssociationColor,
} from '../useCodeAssociations';

// Mock fetch
global.fetch = jest.fn();

describe('useCodeAssociations', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: { retry: false },
                mutations: { retry: false },
            },
        });
        (fetch as jest.Mock).mockClear();
    });

    const wrapper = ({ children }: { children: React.ReactNode }) => (
        <QueryClientProvider client= { queryClient } > { children } </QueryClientProvider>
  );

describe('useCodeAssociations', () => {
    it('fetches code associations for an artifact', async () => {
        const mockData = [
            {
                id: 'assoc-1',
                artifactId: 'art-1',
                codeArtifactId: 'code-1',
                relationship: 'IMPLEMENTATION',
            },
        ];

        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: true,
            json: async () => mockData,
        });

        const { result } = renderHook(() => useCodeAssociations('art-1'), { wrapper });

        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(fetch).toHaveBeenCalledWith('/api/artifacts/art-1/code-associations');
        expect(result.current.data).toEqual(mockData);
    });

    it('handles fetch errors', async () => {
        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: false,
            status: 500,
        });

        const { result } = renderHook(() => useCodeAssociations('art-1'), { wrapper });

        await waitFor(() => expect(result.current.isError).toBe(true));
    });

    it('does not fetch when artifactId is empty', () => {
        const { result } = renderHook(() => useCodeAssociations(''), { wrapper });

        expect(result.current.isFetching).toBe(false);
        expect(fetch).not.toHaveBeenCalled();
    });
});

describe('useCreateCodeAssociation', () => {
    it('creates a code association', async () => {
        const input = {
            artifactId: 'art-1',
            codeArtifactId: 'code-1',
            relationship: 'IMPLEMENTATION' as const,
        };

        const mockResponse = {
            id: 'assoc-1',
            ...input,
            createdAt: '2026-01-17T00:00:00Z',
            updatedAt: '2026-01-17T00:00:00Z',
        };

        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: true,
            json: async () => mockResponse,
        });

        const { result } = renderHook(() => useCreateCodeAssociation(), { wrapper });

        result.current.mutate(input);

        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(fetch).toHaveBeenCalledWith('/api/code-associations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(input),
        });

        expect(result.current.data).toEqual(mockResponse);
    });

    it('invalidates queries after creation', async () => {
        const input = {
            artifactId: 'art-1',
            codeArtifactId: 'code-1',
            relationship: 'TEST' as const,
        };

        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: true,
            json: async () => ({ id: 'assoc-1', ...input }),
        });

        const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

        const { result } = renderHook(() => useCreateCodeAssociation(), { wrapper });

        result.current.mutate(input);

        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['codeAssociations', 'art-1'] });
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['codeAssociations', 'code-1'] });
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['artifacts'] });
    });

    it('handles creation errors', async () => {
        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: false,
            status: 409,
        });

        const { result } = renderHook(() => useCreateCodeAssociation(), { wrapper });

        result.current.mutate({
            artifactId: 'art-1',
            codeArtifactId: 'code-1',
            relationship: 'IMPLEMENTATION',
        });

        await waitFor(() => expect(result.current.isError).toBe(true));
    });
});

describe('useDeleteCodeAssociation', () => {
    it('deletes a code association', async () => {
        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: true,
            json: async () => ({ success: true }),
        });

        const { result } = renderHook(() => useDeleteCodeAssociation(), { wrapper });

        result.current.mutate('assoc-1');

        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(fetch).toHaveBeenCalledWith('/api/code-associations/assoc-1', {
            method: 'DELETE',
        });
    });

    it('invalidates queries after deletion', async () => {
        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: true,
            json: async () => ({ success: true }),
        });

        const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

        const { result } = renderHook(() => useDeleteCodeAssociation(), { wrapper });

        result.current.mutate('assoc-1');

        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['codeAssociations'] });
        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['artifacts'] });
    });

    it('handles deletion errors', async () => {
        (fetch as jest.Mock).mockResolvedValueOnce({
            ok: false,
            status: 404,
        });

        const { result } = renderHook(() => useDeleteCodeAssociation(), { wrapper });

        result.current.mutate('nonexistent-id');

        await waitFor(() => expect(result.current.isError).toBe(true));
    });
});

describe('helper functions', () => {
    it('returns correct icons for relationship types', () => {
        expect(getAssociationIcon('IMPLEMENTATION')).toBe('⚙️');
        expect(getAssociationIcon('TEST')).toBe('🧪');
        expect(getAssociationIcon('DOCUMENTATION')).toBe('📄');
        expect(getAssociationIcon('MOCK')).toBe('🎭');
    });

    it('returns correct colors for relationship types', () => {
        expect(getAssociationColor('IMPLEMENTATION')).toBe('primary');
        expect(getAssociationColor('TEST')).toBe('success');
        expect(getAssociationColor('DOCUMENTATION')).toBe('info');
        expect(getAssociationColor('MOCK')).toBe('warning');
    });

    it('returns default values for unknown types', () => {
        expect(getAssociationIcon('UNKNOWN' as unknown)).toBe('🔗');
        expect(getAssociationColor('UNKNOWN' as unknown)).toBe('default');
    });
});
});
