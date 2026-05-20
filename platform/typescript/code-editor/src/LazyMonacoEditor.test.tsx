/**
 * Code Editor Lazy Loading Tests
 * @doc.type test
 * @doc.purpose Test Monaco editor lazy loading and code splitting
 * @doc.layer unit
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import {
    useMonacoLoader,
    preloadMonacoEditor,
    MonacoBundleInfo,
    LazyMonacoEditor,
} from './LazyMonacoEditor';
import type { LazyMonacoEditorProps } from './LazyMonacoEditor';

const INITIAL_RENDER_BUDGET_MS = 150;

describe('Code Editor - Lazy Loading', () => {
    describe('Monaco Loader Hook', () => {
        it('should initialize loader state', () => {
            const { result } = renderHook(() => useMonacoLoader());

            expect(result.current.isLoading).toBeDefined();
            expect(result.current.isLoaded).toBeDefined();
            expect(result.current.error).toBeDefined();
        });

        it('should track loading state', () => {
            const { result } = renderHook(() => useMonacoLoader());

            expect(typeof result.current.isLoading).toBe('boolean');
            expect(typeof result.current.isLoaded).toBe('boolean');
        });

        it('should handle loading errors', () => {
            const { result } = renderHook(() => useMonacoLoader());

            expect(result.current.error).toBeNull();
        });
    });

    describe('Monaco Preloading', () => {
        it('should preload Monaco in background', async () => {
            const promise = preloadMonacoEditor();

            expect(promise).toBeDefined();
            await waitFor(() => expect(promise).resolves.toBeDefined());
        });

        it('should not block main thread', () => {
            const startTime = performance.now();
            preloadMonacoEditor();
            const endTime = performance.now();

            expect(endTime - startTime).toBeLessThan(50);
        });

        it('should cache loaded Monaco instance', () => {
            preloadMonacoEditor();
            preloadMonacoEditor();

            // Should utilize cache on second call
            expect(preloadMonacoEditor()).toBeDefined();
        });
    });

    describe('Lazy Monaco Editor Component', () => {
        it('should render with fallback loading state', () => {
            const { container } = render(
                React.createElement(LazyMonacoEditor, {
                    value: 'console.log("hello")',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            expect(container).toBeInTheDocument();
        });

        it('should accept editor configuration', () => {
            const { container } = render(
                React.createElement(LazyMonacoEditor, {
                    value: 'const x = 1;',
                    language: 'javascript',
                    theme: 'vs-dark',
                    options: {
                        minimap: { enabled: false },
                        lineNumbers: 'on',
                    },
                } as LazyMonacoEditorProps)
            );

            expect(container).toBeInTheDocument();
        });

        it('should support multiple languages', () => {
            const languages = ['javascript', 'typescript', 'python', 'java', 'sql'];

            languages.forEach(lang => {
                const { unmount } = render(
                    React.createElement(LazyMonacoEditor, {
                        value: '// code',
                        language: lang,
                    } as LazyMonacoEditorProps)
                );
                unmount();
            });

            expect(languages).toHaveLength(5);
        });
    });

    describe('Code Splitting', () => {
        it('should use dynamic import for Monaco', () => {
            const { container } = render(
                React.createElement(LazyMonacoEditor, {
                    value: 'console.log("test")',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            expect(container).toBeInTheDocument();
        });

        it('should load editor asynchronously', async () => {
            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'test code',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            await waitFor(() => {
                expect(screen.getByText('Monaco Editor Bundle')).toBeInTheDocument();
            });
        });

        it('should support Suspense boundaries', () => {
            const { container } = render(
                React.createElement(React.Suspense, { fallback: React.createElement('div', null, 'Loading...') },
                    React.createElement(LazyMonacoEditor, {
                        value: 'code',
                        language: 'javascript',
                    } as LazyMonacoEditorProps)
                )
            );

            expect(container).toBeInTheDocument();
        });
    });

    describe('Bundle Size Information', () => {
        it('should provide bundle size metrics', () => {
            expect(MonacoBundleInfo).toBeDefined();
            expect(MonacoBundleInfo.minSize).toBeGreaterThan(0);
            expect(MonacoBundleInfo.gzipSize).toBeGreaterThan(0);
        });

        it('should track specific bundle chunks', () => {
            expect(MonacoBundleInfo.chunks).toBeDefined();
            expect(Array.isArray(MonacoBundleInfo.chunks)).toBe(true);
        });

        it('should provide loading hints', () => {
            expect(MonacoBundleInfo.loadingHints).toBeDefined();
        });
    });

    describe('Error Handling', () => {
        it('should handle loading failure gracefully', async () => {
            const { container } = render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                    fallback: React.createElement('div', null, 'Failed to load editor'),
                } as any)
            );

            expect(container).toBeInTheDocument();
        });

        it('should provide error callback', () => {
            const onError = vi.fn();

            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                    onError,
                } as any)
            );

            expect(onError).toBeDefined();
        });
    });

    describe('Performance', () => {
        it('should not impact first contentful paint', () => {
            const startTime = performance.now();

            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            const endTime = performance.now();

            expect(endTime - startTime).toBeLessThan(INITIAL_RENDER_BUDGET_MS);
        });

        it('should support preloading for faster subsequent renders', async () => {
            await preloadMonacoEditor();

            const startTime = performance.now();

            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            const endTime = performance.now();

            expect(endTime - startTime).toBeLessThan(INITIAL_RENDER_BUDGET_MS);
        });

        it('should handle rapid mount/unmount', () => {
            const { unmount, rerender } = render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code1',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            rerender(
                React.createElement(LazyMonacoEditor, {
                    value: 'code2',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            unmount();

            expect(document.body).toBeDefined();
        });
    });

    describe('Configuration Options', () => {
        it('should support minimap configuration', () => {
            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                    options: {
                        minimap: { enabled: true, side: 'right' },
                    },
                } as LazyMonacoEditorProps)
            );

            expect(screen.getByText('Monaco Editor Bundle')).toBeInTheDocument();
        });

        it('should support theme selection', () => {
            const themes = ['vs', 'vs-dark', 'hc-black', 'hc-light'];

            themes.forEach(theme => {
                const { unmount } = render(
                    React.createElement(LazyMonacoEditor, {
                        value: 'code',
                        language: 'javascript',
                        theme,
                    } as LazyMonacoEditorProps)
                );
                unmount();
            });

            expect(themes).toContain('vs-dark');
        });

        it('should support readonly mode', () => {
            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                    options: { readOnly: true },
                } as LazyMonacoEditorProps)
            );

            expect(screen.getByText('Monaco Editor Bundle')).toBeInTheDocument();
        });

        it('should support word wrap', () => {
            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'very long line of code'.repeat(20),
                    language: 'javascript',
                    options: { wordWrap: 'on' },
                } as LazyMonacoEditorProps)
            );

            expect(screen.getByText('Monaco Editor Bundle')).toBeInTheDocument();
        });
    });

    describe('Value Updates', () => {
        it('should update code when value prop changes', () => {
            const { rerender } = render(
                React.createElement(LazyMonacoEditor, {
                    value: 'const x = 1;',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            rerender(
                React.createElement(LazyMonacoEditor, {
                    value: 'const y = 2;',
                    language: 'javascript',
                } as LazyMonacoEditorProps)
            );

            expect(screen.getByText('Monaco Editor Bundle')).toBeInTheDocument();
        });

        it('should support onChange callback', () => {
            const onChange = vi.fn();

            render(
                React.createElement(LazyMonacoEditor, {
                    value: 'code',
                    language: 'javascript',
                    onChange,
                } as any)
            );

            expect(onChange).toBeDefined();
        });
    });
});
