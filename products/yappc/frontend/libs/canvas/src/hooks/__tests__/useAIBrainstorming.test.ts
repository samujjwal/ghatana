/**
 * Tests for useAIBrainstorming Hook
 *
 * Tests AI brainstorming functionality including:
 * - Idea generation from prompts
 * - Node spawning with auto-layout
 * - Error handling
 * - Integration with AI service
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ReactFlowProvider } from '@xyflow/react';
import { useAIBrainstorming } from '../useAIBrainstorming';
import type { IAIService, CompletionResponse } from '@ghatana/yappc-ai/core';

// Mock React Flow hooks
vi.mock('@xyflow/react', async () => {
    const actual = await vi.importActual('@xyflow/react');
    return {
        ...actual,
        useReactFlow: () => ({
            getNodes: vi.fn(() => [
                {
                    id: 'test-source',
                    type: 'aiPrompt',
                    position: { x: 400, y: 300 },
                    data: { label: 'Test' },
                },
            ]),
            setNodes: vi.fn(),
            setEdges: vi.fn(),
            addNodes: vi.fn(),
            addEdges: vi.fn(),
        }),
    };
});

// Mock AI Service
const createMockAIService = (response?: Partial<CompletionResponse>): IAIService => ({
    provider: 'openai',
    config: {
        apiKey: 'test-key',
        model: 'gpt-4',
        maxTokens: 1000,
    },
    complete: vi.fn().mockResolvedValue({
        content: 'Generated ideas:\n1. Dashboard\n2. Profile\n3. Login\n4. API\n5. Database',
        model: 'gpt-4',
        finishReason: 'stop',
        usage: { promptTokens: 50, completionTokens: 100, totalTokens: 150 },
        ...response,
    } as CompletionResponse),
    stream: vi.fn(),
    embed: vi.fn(),
    getTokenCount: vi.fn().mockReturnValue(50),
    healthCheck: vi.fn().mockResolvedValue({ status: 'ok', latency: 100 }),
});

describe('useAIBrainstorming', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('initialization', () => {
        it('should initialize with default state', () => {
            const { result } = renderHook(() => useAIBrainstorming());

            expect(result.current.response).toBeNull();
            expect(result.current.loading).toBe(false);
            expect(result.current.error).toBeNull();
            expect(typeof result.current.generateIdeas).toBe('function');
            expect(typeof result.current.spawnNodes).toBe('function');
            expect(typeof result.current.clearResponse).toBe('function');
        });
    });

    describe('generateIdeas', () => {
        it('should generate ideas without AI service (mock mode)', async () => {
            const onBrainstormComplete = vi.fn();
            const { result } = renderHook(() =>
                useAIBrainstorming({
                    autoSpawn: false,
                    onBrainstormComplete,
                })
            );

            await act(async () => {
                await result.current.generateIdeas('Create a user profile feature');
            });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            expect(result.current.response).not.toBeNull();
            expect(result.current.response?.prompt).toBe('Create a user profile feature');
            expect(result.current.response?.suggestions).toHaveLength(5);
            expect(onBrainstormComplete).toHaveBeenCalledWith(
                expect.objectContaining({
                    prompt: 'Create a user profile feature',
                    suggestions: expect.any(Array),
                })
            );
        });

        it('should generate ideas with AI service', async () => {
            const mockAIService = createMockAIService();
            const { result } = renderHook(() =>
                useAIBrainstorming({
                    aiService: mockAIService,
                    autoSpawn: false,
                })
            );

            await act(async () => {
                await result.current.generateIdeas('Build an e-commerce platform');
            });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            expect(mockAIService.complete).toHaveBeenCalledWith(
                expect.stringContaining('Build an e-commerce platform'),
                expect.objectContaining({
                    model: 'gpt-4',
                    temperature: 0.8,
                })
            );
            expect(result.current.response?.tokenUsage).toEqual({
                promptTokens: 50,
                completionTokens: 100,
                totalTokens: 150,
            });
        });

        it('should set loading state during generation', async () => {
            const { result } = renderHook(() => useAIBrainstorming({ autoSpawn: false }));

            let loadingDuringGeneration = false;

            act(() => {
                result.current.generateIdeas('Test prompt').then(() => {
                    // Check will happen after promise resolves
                });
            });

            // Check loading is true immediately after calling generateIdeas
            if (result.current.loading) {
                loadingDuringGeneration = true;
            }

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            expect(loadingDuringGeneration).toBe(true);
        });

        it('should handle empty prompt', async () => {
            const onError = vi.fn();
            const { result } = renderHook(() => useAIBrainstorming({ onError }));

            await act(async () => {
                await result.current.generateIdeas('   ');
            });

            expect(result.current.error).not.toBeNull();
            expect(result.current.error?.message).toContain('empty');
            expect(onError).toHaveBeenCalledWith(expect.any(Error));
        });

        it('should handle AI service errors', async () => {
            const mockAIService = createMockAIService();
            (mockAIService.complete as unknown).mockRejectedValue(new Error('API rate limit exceeded'));

            const onError = vi.fn();
            const { result } = renderHook(() =>
                useAIBrainstorming({
                    aiService: mockAIService,
                    onError,
                })
            );

            await act(async () => {
                await result.current.generateIdeas('Test prompt');
            });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            expect(result.current.error).not.toBeNull();
            expect(result.current.error?.message).toContain('rate limit');
            expect(onError).toHaveBeenCalled();
        });

        it('should call onBrainstormStart callback', async () => {
            const onBrainstormStart = vi.fn();
            const { result } = renderHook(() =>
                useAIBrainstorming({ onBrainstormStart, autoSpawn: false })
            );

            await act(async () => {
                await result.current.generateIdeas('Test prompt');
            });

            expect(onBrainstormStart).toHaveBeenCalledWith('Test prompt');
        });

        it('should auto-spawn nodes when enabled', async () => {
            const onNodesSpawned = vi.fn();
            const { result } = renderHook(() =>
                useAIBrainstorming({
                    autoSpawn: true,
                    onNodesSpawned,
                })
            );

            await act(async () => {
                await result.current.generateIdeas('Create profile screen');
            });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            // Should have called onNodesSpawned with nodes and edges
            expect(onNodesSpawned).toHaveBeenCalledWith(
                expect.any(Array),
                expect.any(Array)
            );
        });

        it('should parse profile-related prompts correctly', async () => {
            const { result } = renderHook(() => useAIBrainstorming({ autoSpawn: false }));

            await act(async () => {
                await result.current.generateIdeas('Create a user profile with edit functionality');
            });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            const suggestions = result.current.response?.suggestions || [];

            // Should include profile-related nodes
            const hasProfileView = suggestions.some((s) => s.label.toLowerCase().includes('profile'));
            const hasEditMode = suggestions.some((s) => s.label.toLowerCase().includes('edit'));
            const hasAPI = suggestions.some((s) => s.type === 'apiEndpoint');
            const hasDatabase = suggestions.some((s) => s.type === 'database');

            expect(hasProfileView).toBe(true);
            expect(hasEditMode).toBe(true);
            expect(hasAPI).toBe(true);
            expect(hasDatabase).toBe(true);
        });

        it('should respect suggestionCount option', async () => {
            const { result } = renderHook(() =>
                useAIBrainstorming({
                    suggestionCount: 3,
                    autoSpawn: false,
                })
            );

            await act(async () => {
                await result.current.generateIdeas('Test prompt');
            });

            await waitFor(() => {
                expect(result.current.loading).toBe(false);
            });

            expect(result.current.response?.suggestions).toHaveLength(3);
        });
    });

    describe('spawnNodes', () => {
        it('should spawn nodes without source node', () => {
            const onNodesSpawned = vi.fn();
            const { result } = renderHook(() => useAIBrainstorming({ onNodesSpawned }));

            const suggestions = [
                {
                    type: 'uiScreen' as const,
                    label: 'Dashboard',
                    persona: 'ux' as const,
                },
                {
                    type: 'apiEndpoint' as const,
                    label: 'GET /api/data',
                    persona: 'developer' as const,
                },
            ];

            act(() => {
                result.current.spawnNodes(suggestions);
            });

            expect(onNodesSpawned).toHaveBeenCalledWith(
                expect.arrayContaining([
                    expect.objectContaining({ type: 'uiScreen' }),
                    expect.objectContaining({ type: 'apiEndpoint' }),
                ]),
                []
            );
        });

        it('should spawn nodes with source node and edges', () => {
            const onNodesSpawned = vi.fn();
            const { result } = renderHook(() => useAIBrainstorming({ onNodesSpawned }));

            const suggestions = [
                {
                    type: 'uiScreen' as const,
                    label: 'Profile',
                    persona: 'ux' as const,
                },
            ];

            act(() => {
                result.current.spawnNodes(suggestions, 'test-source');
            });

            expect(onNodesSpawned).toHaveBeenCalledWith(
                expect.any(Array),
                expect.arrayContaining([
                    expect.objectContaining({
                        source: 'test-source',
                        type: 'smoothstep',
                        animated: true,
                    }),
                ])
            );
        });

        it('should mark spawned nodes as aiGenerated', () => {
            const onNodesSpawned = vi.fn();
            const { result } = renderHook(() => useAIBrainstorming({ onNodesSpawned }));

            const suggestions = [
                {
                    type: 'database' as const,
                    label: 'Users',
                    persona: 'architect' as const,
                },
            ];

            act(() => {
                result.current.spawnNodes(suggestions);
            });

            const [nodes] = onNodesSpawned.mock.calls[0];
            expect(nodes[0].data.aiGenerated).toBe(true);
            expect(nodes[0].data.lastUpdated).toBeTruthy();
        });
    });

    describe('clearResponse', () => {
        it('should clear response and error', async () => {
            const { result } = renderHook(() => useAIBrainstorming({ autoSpawn: false }));

            await act(async () => {
                await result.current.generateIdeas('Test prompt');
            });

            await waitFor(() => {
                expect(result.current.response).not.toBeNull();
            });

            act(() => {
                result.current.clearResponse();
            });

            expect(result.current.response).toBeNull();
            expect(result.current.error).toBeNull();
        });
    });
});
