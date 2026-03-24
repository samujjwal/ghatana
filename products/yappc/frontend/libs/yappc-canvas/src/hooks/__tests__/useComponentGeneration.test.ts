/**
 * Tests for useComponentGeneration hook
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useComponentGeneration } from '../useComponentGeneration';
import type { Node } from '@xyflow/react';

// Mock AICodeGenerationService
vi.mock('../../integration/aiCodeGeneration', () => ({
    AICodeGenerationService: vi.fn().mockImplementation(() => ({
        generateService: vi.fn().mockResolvedValue({
            code: 'export const Component = () => <div>Test</div>;',
        }),
    })),
}));

describe('useComponentGeneration', () => {
    const mockNode: Node = {
        id: '1',
        type: 'wireframe',
        position: { x: 0, y: 0 },
        data: {
            label: 'ProfileCard',
            props: [
                { name: 'user', type: 'User', required: true },
                { name: 'editable', type: 'boolean', required: false },
            ],
        },
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Initialization', () => {
        it('should initialize with default state', () => {
            const { result } = renderHook(() => useComponentGeneration());

            expect(result.current.isGenerating).toBe(false);
            expect(result.current.error).toBeNull();
            expect(result.current.lastGenerated).toBeNull();
            expect(result.current.previewCode).toBeNull();
        });

        it('should initialize with custom options', () => {
            const { result } = renderHook(() =>
                useComponentGeneration({
                    defaultFramework: 'react',
                    defaultStyling: 'tailwind',
                    defaultTypescript: true,
                })
            );

            expect(result.current.isGenerating).toBe(false);
        });
    });

    describe('Component Generation', () => {
        it('should generate React component', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            let generated;
            await act(async () => {
                generated = await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                    includeTests: false,
                    includeStorybook: false,
                });
            });

            expect(generated).toBeDefined();
            expect(generated?.componentFile.filename).toBe('ProfileCard.tsx');
            expect(generated?.componentFile.content).toContain('Component');
        });

        it('should generate with tests', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            let generated;
            await act(async () => {
                generated = await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                    includeTests: true,
                    includeStorybook: false,
                });
            });

            expect(generated?.testFile).toBeDefined();
            expect(generated?.testFile?.filename).toBe('ProfileCard.test.tsx');
        });

        it('should generate with Storybook', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            let generated;
            await act(async () => {
                generated = await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                    includeTests: false,
                    includeStorybook: true,
                });
            });

            expect(generated?.storyFile).toBeDefined();
            expect(generated?.storyFile?.filename).toBe('ProfileCard.stories.tsx');
        });

        it('should set isGenerating during generation', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            const generatePromise = act(async () => {
                await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                });
            });

            // isGenerating should be true during generation
            expect(result.current.isGenerating).toBe(true);

            await generatePromise;

            expect(result.current.isGenerating).toBe(false);
        });

        it('should update lastGenerated after generation', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            expect(result.current.lastGenerated).toBeNull();

            await act(async () => {
                await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                });
            });

            expect(result.current.lastGenerated).not.toBeNull();
            expect(result.current.lastGenerated?.componentFile.filename).toBe('ProfileCard.tsx');
        });

        it('should set preview code after generation', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            expect(result.current.previewCode).toBeNull();

            await act(async () => {
                await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                });
            });

            expect(result.current.previewCode).not.toBeNull();
        });
    });

    describe('Storybook Generation', () => {
        it('should generate Storybook stories', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            let storyContent = '';
            await act(async () => {
                storyContent = await result.current.generateStorybook(mockNode);
            });

            expect(storyContent).toContain('ProfileCard');
            expect(storyContent).toContain('Meta');
            expect(storyContent).toContain('Story');
        });

        it('should generate stories with custom variants', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            const variants = [
                { name: 'Default', args: { user: { id: 1, name: 'John' }, editable: false } },
                { name: 'Editable', args: { user: { id: 1, name: 'John' }, editable: true } },
            ];

            let storyContent = '';
            await act(async () => {
                storyContent = await result.current.generateStorybook(mockNode, variants);
            });

            expect(storyContent).toContain('Default');
            expect(storyContent).toContain('Editable');
        });
    });

    describe('File Operations', () => {
        it('should download files', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            await act(async () => {
                await result.current.generateComponent(mockNode, {
                    framework: 'react',
                    styling: 'tailwind',
                    typescript: true,
                    props: mockNode.data.props,
                    includeTests: true,
                    includeStorybook: true,
                });
            });

            // Mock document.createElement and URL.createObjectURL
            const mockCreateElement = vi.spyOn(document, 'createElement');
            const mockCreateObjectURL = vi.spyOn(URL, 'createObjectURL');
            mockCreateObjectURL.mockReturnValue('blob:mock-url');

            act(() => {
                if (result.current.lastGenerated) {
                    result.current.downloadFiles(result.current.lastGenerated);
                }
            });

            expect(mockCreateElement).toHaveBeenCalled();
        });

        it('should copy to clipboard', async () => {
            const { result } = renderHook(() => useComponentGeneration());

            const mockWriteText = vi.fn().mockResolvedValue(undefined);
            Object.assign(navigator, {
                clipboard: {
                    writeText: mockWriteText,
                },
            });

            await act(async () => {
                await result.current.copyToClipboard('test content');
            });

            expect(mockWriteText).toHaveBeenCalledWith('test content');
        });
    });

    describe('Preview Management', () => {
        it('should set preview code', () => {
            const { result } = renderHook(() => useComponentGeneration());

            expect(result.current.previewCode).toBeNull();

            act(() => {
                result.current.setPreviewCode('const Component = () => <div />;');
            });

            expect(result.current.previewCode).toBe('const Component = () => <div />;');
        });

        it('should clear preview code', () => {
            const { result } = renderHook(() => useComponentGeneration());

            act(() => {
                result.current.setPreviewCode('test code');
            });

            expect(result.current.previewCode).toBe('test code');

            act(() => {
                result.current.setPreviewCode(null);
            });

            expect(result.current.previewCode).toBeNull();
        });
    });

    describe('Error Handling', () => {
        it('should handle generation errors', async () => {
            // Mock AI service to throw error
            vi.mock('../../integration/aiCodeGeneration', () => ({
                AICodeGenerationService: vi.fn().mockImplementation(() => ({
                    generateService: vi.fn().mockRejectedValue(new Error('AI service error')),
                })),
            }));

            const { result } = renderHook(() => useComponentGeneration());

            await expect(async () => {
                await act(async () => {
                    await result.current.generateComponent(mockNode, {
                        framework: 'react',
                        styling: 'tailwind',
                        typescript: true,
                        props: mockNode.data.props,
                    });
                });
            }).rejects.toThrow();

            expect(result.current.error).not.toBeNull();
        });
    });
});
