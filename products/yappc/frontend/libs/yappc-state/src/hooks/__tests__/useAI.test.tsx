/**
 * useAI Hook Tests
 *
 * Tests useCopilot, useAIInsights, and useAIPredictions hooks.
 * Fetch calls are intercepted with vi.fn(); Jotai + QueryClient
 * providers are shared via a standard wrapper.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';

import { useCopilot } from '../useAI';
import { useAIInsights } from '../useAI';
import { useAIPredictions } from '../useAI';

// ---------------------------------------------------------------------------
// Mock global fetch
// ---------------------------------------------------------------------------

const mockFetch = vi.fn();
global.fetch = mockFetch;

function makeGqlResponse(data: unknown) {
    return {
        ok: true,
        json: () => Promise.resolve({ data }),
    } as Response;
}

// ---------------------------------------------------------------------------
// Test wrapper
// ---------------------------------------------------------------------------

function makeWrapper() {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    });

    const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
        <JotaiProvider>
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        </JotaiProvider>
    );

    return { Wrapper, queryClient };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeCopilotMessage(overrides: Record<string, unknown> = {}) {
    return {
        id: 'msg-1',
        role: 'user',
        content: 'Hello',
        createdAt: '2025-01-01T00:00:00.000Z',
        ...overrides,
    };
}

function makeInsight(overrides: Record<string, unknown> = {}) {
    return {
        id: 'insight-1',
        type: 'CODE_QUALITY',
        category: 'performance',
        severity: 'MEDIUM',
        title: 'Slow query detected',
        description: 'This query may slow down the system',
        confidence: 0.85,
        actionItems: ['Add index'],
        projectId: 'proj-1',
        createdAt: '2025-01-01T00:00:00.000Z',
        ...overrides,
    };
}

function makePrediction(overrides: Record<string, unknown> = {}) {
    return {
        id: 'pred-1',
        type: 'RISK',
        target: 'deployment',
        probability: 0.7,
        timeframe: '2 weeks',
        description: 'High chance of deployment failure',
        factors: ['missing tests', 'high complexity'],
        createdAt: '2025-01-01T00:00:00.000Z',
        ...overrides,
    };
}

// ===========================================================================
// useCopilot
// ===========================================================================

describe('useCopilot — initial state', () => {
    beforeEach(() => vi.clearAllMocks());

    it('session is null initially', () => {
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        expect(result.current.session).toBeNull();
    });

    it('isSending is false initially', () => {
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        expect(result.current.isSending).toBe(false);
    });

    it('error is null initially', () => {
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        expect(result.current.error).toBeNull();
    });
});

describe('useCopilot — sendMessage', () => {
    beforeEach(() => vi.clearAllMocks());

    it('creates a session when sending the first message', async () => {
        const userMsg = makeCopilotMessage({ id: 'msg-u', role: 'user', content: 'Hi' });
        const assistantMsg = makeCopilotMessage({
            id: 'msg-a',
            role: 'assistant',
            content: 'Hello!',
        });
        mockFetch.mockResolvedValue(
            makeGqlResponse({
                sendCopilotMessage: {
                    sessionId: 'session-1',
                    userMessage: userMsg,
                    assistantMessage: assistantMsg,
                },
            })
        );

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        await act(async () => {
            await result.current.sendMessage({ message: 'Hi' });
        });

        expect(result.current.session).not.toBeNull();
        expect(result.current.session!.id).toBe('session-1');
    });

    it('appends messages to an existing session', async () => {
        const firstUser = makeCopilotMessage({ id: 'm-1', role: 'user', content: 'First' });
        const firstAssistant = makeCopilotMessage({
            id: 'm-2',
            role: 'assistant',
            content: 'Resp 1',
        });

        // First send — creates session
        mockFetch.mockResolvedValueOnce(
            makeGqlResponse({
                sendCopilotMessage: {
                    sessionId: 'session-1',
                    userMessage: firstUser,
                    assistantMessage: firstAssistant,
                },
            })
        );

        const secondUser = makeCopilotMessage({ id: 'm-3', role: 'user', content: 'Second' });
        const secondAssistant = makeCopilotMessage({
            id: 'm-4',
            role: 'assistant',
            content: 'Resp 2',
        });

        // Second send — appends to session
        mockFetch.mockResolvedValueOnce(
            makeGqlResponse({
                sendCopilotMessage: {
                    sessionId: 'session-1',
                    userMessage: secondUser,
                    assistantMessage: secondAssistant,
                },
            })
        );

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        await act(async () => {
            await result.current.sendMessage({ message: 'First' });
        });

        await act(async () => {
            await result.current.sendMessage({ message: 'Second' });
        });

        // Session exists with at least the accumulated messages
        expect(result.current.session).not.toBeNull();
    });

    it('sets error state when the GraphQL request fails', async () => {
        mockFetch.mockResolvedValue({
            ok: true,
            json: () => Promise.resolve({ errors: [{ message: 'Copilot unavailable' }] }),
        });

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        await act(async () => {
            await result.current.sendMessage({ message: 'fail' }).catch(() => { });
        });

        await waitFor(() => {
            expect(result.current.error).not.toBeNull();
        });
    });
});

describe('useCopilot — clearSession', () => {
    it('resets session to null', async () => {
        const userMsg = makeCopilotMessage({ id: 'msg-u', role: 'user' });
        const assistantMsg = makeCopilotMessage({ id: 'msg-a', role: 'assistant' });
        mockFetch.mockResolvedValue(
            makeGqlResponse({
                sendCopilotMessage: {
                    sessionId: 'session-1',
                    userMessage: userMsg,
                    assistantMessage: assistantMsg,
                },
            })
        );

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useCopilot(), { wrapper: Wrapper });

        await act(async () => {
            await result.current.sendMessage({ message: 'Hello' });
        });

        expect(result.current.session).not.toBeNull();

        act(() => {
            result.current.clearSession();
        });

        expect(result.current.session).toBeNull();
    });
});

// ===========================================================================
// useAIInsights
// ===========================================================================

describe('useAIInsights — initial state', () => {
    beforeEach(() => vi.clearAllMocks());

    it('starts with empty insights array', () => {
        mockFetch.mockResolvedValue(makeGqlResponse({ aiInsights: [] }));
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIInsights('proj-1'), {
            wrapper: Wrapper,
        });

        expect(result.current.insights).toEqual([]);
    });
});

describe('useAIInsights — fetching', () => {
    beforeEach(() => vi.clearAllMocks());

    it('populates insights after successful fetch', async () => {
        const insight = makeInsight();
        mockFetch.mockResolvedValue(makeGqlResponse({ aiInsights: [insight] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIInsights('proj-1'), {
            wrapper: Wrapper,
        });

        await waitFor(() => {
            expect(result.current.insights).toHaveLength(1);
        });
        expect(result.current.insights[0].id).toBe('insight-1');
    });

    it('sets isLoading to false after fetch completes', async () => {
        mockFetch.mockResolvedValue(makeGqlResponse({ aiInsights: [] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIInsights('proj-1'), {
            wrapper: Wrapper,
        });

        await waitFor(() => {
            expect(result.current.isLoading).toBe(false);
        });
    });
});

describe('useAIInsights — dismissInsight', () => {
    beforeEach(() => vi.clearAllMocks());

    it('removes a dismissed insight from the list', async () => {
        mockFetch.mockResolvedValue(
            makeGqlResponse({
                aiInsights: [
                    makeInsight({ id: 'insight-1' }),
                    makeInsight({ id: 'insight-2' }),
                ],
            })
        );

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIInsights('proj-1'), {
            wrapper: Wrapper,
        });

        await waitFor(() => {
            expect(result.current.insights).toHaveLength(2);
        });

        act(() => {
            result.current.dismissInsight('insight-1');
        });

        expect(result.current.insights.find((i) => i.id === 'insight-1')).toBeUndefined();
        expect(result.current.insights).toHaveLength(1);
    });
});

// ===========================================================================
// useAIPredictions
// ===========================================================================

describe('useAIPredictions — initial state', () => {
    beforeEach(() => vi.clearAllMocks());

    it('starts with empty predictions array', () => {
        mockFetch.mockResolvedValue(makeGqlResponse({ predictions: [] }));
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIPredictions('proj-1'), {
            wrapper: Wrapper,
        });

        expect(result.current.predictions).toEqual([]);
    });
});

describe('useAIPredictions — fetching', () => {
    beforeEach(() => vi.clearAllMocks());

    it('populates predictions after successful fetch', async () => {
        const prediction = makePrediction();
        mockFetch.mockResolvedValue(makeGqlResponse({ predictions: [prediction] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIPredictions('proj-1'), {
            wrapper: Wrapper,
        });

        await waitFor(() => {
            expect(result.current.predictions).toHaveLength(1);
        });
        expect(result.current.predictions[0].id).toBe('pred-1');
        expect(result.current.predictions[0].probability).toBe(0.7);
    });

    it('sets isLoading to false after fetch completes', async () => {
        mockFetch.mockResolvedValue(makeGqlResponse({ predictions: [] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIPredictions('proj-1'), {
            wrapper: Wrapper,
        });

        await waitFor(() => {
            expect(result.current.isLoading).toBe(false);
        });
    });

    it('returns empty predictions when projectId is null', async () => {
        mockFetch.mockResolvedValue(makeGqlResponse({ predictions: [] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useAIPredictions(null), { wrapper: Wrapper });

        await waitFor(() => {
            expect(result.current.isLoading).toBe(false);
        });
        expect(result.current.predictions).toEqual([]);
    });
});
