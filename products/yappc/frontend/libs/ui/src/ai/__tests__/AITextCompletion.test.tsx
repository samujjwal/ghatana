/**
 * Tests for AITextCompletion Component
 */

import { render, screen, /* fireEvent */ waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { AITextCompletion } from '../AITextCompletion';

import type { IAIService, CompletionResponse } from '@ghatana/yappc-ai/core';

// Mock AI Service
const createMockAIService = (overrides?: Partial<IAIService>): IAIService => ({
    complete: vi.fn().mockResolvedValue({
        content: 'This is a test completion',
        model: 'gpt-3.5-turbo',
        finishReason: 'stop',
        usage: { promptTokens: 10, completionTokens: 5, totalTokens: 15 },
    } as CompletionResponse),
    stream: vi.fn(),
    embed: vi.fn(),
    getTokenCount: vi.fn().mockReturnValue(10),
    healthCheck: vi.fn().mockResolvedValue(true),
    ...overrides,
});

describe.skip('AITextCompletion', () => {
    let mockAIService: IAIService;
    let mockOnChange: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        mockAIService = createMockAIService();
        mockOnChange = vi.fn();
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    describe('Rendering', () => {
        it('should render with basic props', () => {
            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                />
            );

            expect(screen.getByRole('textbox')).toBeInTheDocument();
        });

        it('should render with custom label', () => {
            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    label="Custom Label"
                />
            );

            expect(screen.getByLabelText('Custom Label')).toBeInTheDocument();
        });

        it('should render with custom placeholder', () => {
            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    placeholder="Type something..."
                />
            );

            expect(screen.getByPlaceholderText('Type something...')).toBeInTheDocument();
        });

        it('should render multiline when specified', () => {
            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    multiline
                    rows={4}
                />
            );

            const textbox = screen.getByRole('textbox');
            expect(textbox.tagName).toBe('TEXTAREA');
        });
    });

    describe('User Input', () => {
        it('should call onChange when text is entered', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Hello');

            expect(mockOnChange).toHaveBeenCalled();
        });

        it('should update value prop', () => {
            const { rerender } = render(
                <AITextCompletion
                    aiService={mockAIService}
                    value="Initial"
                    onChange={mockOnChange}
                />
            );

            expect(screen.getByRole('textbox')).toHaveValue('Initial');

            rerender(
                <AITextCompletion
                    aiService={mockAIService}
                    value="Updated"
                    onChange={mockOnChange}
                />
            );

            expect(screen.getByRole('textbox')).toHaveValue('Updated');
        });
    });

    describe('AI Completion', () => {
        it('should trigger completion after typing enough text', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    minLength={5}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Hello World');

            // Fast-forward past debounce
            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(mockAIService.complete).toHaveBeenCalled();
            });
        });

        it('should not trigger completion if text is too short', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    minLength={10}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Short');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(mockAIService.complete).not.toHaveBeenCalled();
            });
        });

        it('should show loading state while completing', async () => {
            const user = userEvent.setup({ delay: null });
            let resolveComplete: (value: CompletionResponse) => void;

            const mockService = createMockAIService({
                complete: vi.fn().mockReturnValue(
                    new Promise((resolve) => {
                        resolveComplete = resolve;
                    })
                ),
            });

            render(
                <AITextCompletion
                    aiService={mockService}
                    value=""
                    onChange={mockOnChange}
                    minLength={5}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Hello World');

            vi.advanceTimersByTime(100);

            // Wait for CircularProgress to appear (MUI uses SVG, not progressbar role)
            await waitFor(() => {
                const svg = document.querySelector('svg[class*="MuiCircularProgress"]');
                expect(svg).toBeInTheDocument();
            });

            // Resolve the completion
            resolveComplete!({
                content: 'Completion',
                model: 'gpt-3.5-turbo',
                finishReason: 'stop',
                usage: { promptTokens: 10, completionTokens: 5, totalTokens: 15 },
            });
        });

        it('should display completion suggestion', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    minLength={5}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Hello World');

            vi.advanceTimersByTime(100);

            // The completion is mocked to resolve, so we just wait for it to be processed
            await waitFor(
                () => {
                    expect(screen.getByText('This is a test completion')).toBeInTheDocument();
                },
                { timeout: 1000 }
            );
        });
    });

    describe('Keyboard Interactions', () => {
        it('should accept suggestion with Tab key', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value="Hello"
                    onChange={mockOnChange}
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, ' World');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(screen.getByText('This is a test completion')).toBeInTheDocument();
            });

            // Press Tab to accept
            await user.tab();

            expect(mockOnChange).toHaveBeenCalledWith(
                expect.stringContaining('This is a test completion')
            );
        });

        it('should reject suggestion with Escape key', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value="Hello"
                    onChange={mockOnChange}
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, ' World');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(screen.getByText('This is a test completion')).toBeInTheDocument();
            });

            // Press Escape to reject
            await user.keyboard('{Escape}');

            await waitFor(() => {
                expect(screen.queryByText('This is a test completion')).not.toBeInTheDocument();
            });
        });
    });

    describe('Button Interactions', () => {
        it('should accept suggestion when accept button is clicked', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value="Hello"
                    onChange={mockOnChange}
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, ' World');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(screen.getByText('This is a test completion')).toBeInTheDocument();
            });

            // Click accept button (check icon)
            const acceptButton = screen.getByRole('button', { name: /accept/i });
            await user.click(acceptButton);

            expect(mockOnChange).toHaveBeenCalledWith(
                expect.stringContaining('This is a test completion')
            );
        });

        it('should reject suggestion when reject button is clicked', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value="Hello"
                    onChange={mockOnChange}
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, ' World');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(screen.getByText('This is a test completion')).toBeInTheDocument();
            });

            // Click reject button (close icon)
            const rejectButton = screen.getByRole('button', { name: /reject/i });
            await user.click(rejectButton);

            await waitFor(() => {
                expect(screen.queryByText('This is a test completion')).not.toBeInTheDocument();
            });
        });
    });

    describe('Error Handling', () => {
        it('should display error message when completion fails', async () => {
            const user = userEvent.setup({ delay: null });
            const mockService = createMockAIService({
                complete: vi.fn().mockRejectedValue(new Error('API Error')),
            });

            render(
                <AITextCompletion
                    aiService={mockService}
                    value=""
                    onChange={mockOnChange}
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Hello');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(screen.getByText(/error/i)).toBeInTheDocument();
            });
        });
    });

    describe('Stats Tracking', () => {
        it('should increment accepted count when suggestions are accepted', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            await user.type(input, 'Hello');

            vi.advanceTimersByTime(100);

            await waitFor(() => {
                expect(screen.getByText('This is a test completion')).toBeInTheDocument();
            });

            // Accept suggestion
            await user.tab();

            await waitFor(() => {
                expect(screen.getByText(/1 suggestion.*accepted/i)).toBeInTheDocument();
            });
        });
    });

    describe('Disabled State', () => {
        it('should not trigger completion when disabled', async () => {
            const user = userEvent.setup({ delay: null });

            render(
                <AITextCompletion
                    aiService={mockAIService}
                    value=""
                    onChange={mockOnChange}
                    disabled
                    minLength={1}
                    debounceMs={100}
                />
            );

            const input = screen.getByRole('textbox');
            expect(input).toBeDisabled();

            await user.type(input, 'Hello');
            vi.advanceTimersByTime(100);

            expect(mockAIService.complete).not.toHaveBeenCalled();
        });
    });
});
