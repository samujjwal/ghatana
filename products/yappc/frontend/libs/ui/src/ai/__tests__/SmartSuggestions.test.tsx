/**
 * Tests for SmartSuggestions Component
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { SmartSuggestions } from '../SmartSuggestions';

import type { IAIService, CompletionResponse } from '@ghatana/yappc-ai/core';

const createMockAIService = (completionText = '1. First suggestion\n2. Second suggestion\n3. Third suggestion'): IAIService => ({
    complete: vi.fn().mockResolvedValue({
        content: completionText,
        model: 'gpt-3.5-turbo',
        finishReason: 'stop',
        usage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
    } as CompletionResponse),
    stream: vi.fn(),
    embed: vi.fn(),
    getTokenCount: vi.fn().mockReturnValue(10),
    healthCheck: vi.fn().mockResolvedValue(true),
});

describe('SmartSuggestions', () => {
    let mockAIService: IAIService;
    let mockOnSelect: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        mockAIService = createMockAIService();
        mockOnSelect = vi.fn();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render with basic props', () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test context"
                    onSelect={mockOnSelect}
                    autoGenerate={false}
                />
            );

            expect(screen.getByText('Smart Suggestions')).toBeInTheDocument();
        });

        it('should show loading state initially when autoGenerate is true', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test context"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            expect(screen.getByRole('progressbar')).toBeInTheDocument();
        });

        it('should render suggestions after generation', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test context"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
                expect(screen.getByText('Second suggestion')).toBeInTheDocument();
                expect(screen.getByText('Third suggestion')).toBeInTheDocument();
            });
        });

        it('should group suggestions by type', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test context"
                    onSelect={mockOnSelect}
                    suggestionTypes={['completion', 'improve']}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('Complete')).toBeInTheDocument();
                expect(screen.getByText('Improve')).toBeInTheDocument();
            });
        });

        it('should show keyboard shortcuts hint', () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test context"
                    onSelect={mockOnSelect}
                    autoGenerate={false}
                />
            );

            expect(screen.getByText(/Use ↑↓ to navigate/i)).toBeInTheDocument();
        });
    });

    describe('Suggestion Generation', () => {
        it('should call AI service with correct context', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Write a function"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(mockAIService.complete).toHaveBeenCalledWith(
                    expect.objectContaining({
                        messages: expect.arrayContaining([
                            expect.objectContaining({
                                content: expect.stringContaining('Write a function'),
                            }),
                        ]),
                    })
                );
            });
        });

        it('should respect maxSuggestionsPerType', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    maxSuggestionsPerType={2}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                const listItems = screen.getAllByRole('button');
                // Should have at most 2 suggestions per type
                expect(listItems.length).toBeLessThanOrEqual(2);
            });
        });

        it('should filter by confidence threshold', async () => {
            const mockService = createMockAIService('Very short');

            render(
                <SmartSuggestions
                    aiService={mockService}
                    context="Test"
                    onSelect={mockOnSelect}
                    minConfidence={0.9}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                // Very short suggestions should be filtered out with high confidence threshold
                expect(screen.queryByText(/First suggestion/i)).not.toBeInTheDocument();
                expect(screen.queryByText(/Second suggestion/i)).not.toBeInTheDocument();
                expect(screen.queryByText(/Third suggestion/i)).not.toBeInTheDocument();
            });
        });
    });

    describe('Keyboard Navigation', () => {
        it('should navigate down with arrow down key', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            const firstItem = screen.getByText('First suggestion').closest('button');
            expect(firstItem).toHaveClass('Mui-selected');

            fireEvent.keyDown(window, { key: 'ArrowDown' });

            const secondItem = screen.getByText('Second suggestion').closest('button');
            await waitFor(() => {
                expect(secondItem).toHaveClass('Mui-selected');
            });
        });

        it('should navigate up with arrow up key', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            // Go down then up
            fireEvent.keyDown(window, { key: 'ArrowDown' });
            fireEvent.keyDown(window, { key: 'ArrowUp' });

            const firstItem = screen.getByText('First suggestion').closest('button');
            await waitFor(() => {
                expect(firstItem).toHaveClass('Mui-selected');
            });
        });

        it('should select suggestion with Enter key', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            fireEvent.keyDown(window, { key: 'Enter' });

            expect(mockOnSelect).toHaveBeenCalledWith(
                expect.objectContaining({
                    text: 'First suggestion',
                })
            );
        });

        it('should dismiss with Escape key', async () => {
            const mockOnDismiss = vi.fn();

            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    onDismiss={mockOnDismiss}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            fireEvent.keyDown(window, { key: 'Escape' });

            expect(mockOnDismiss).toHaveBeenCalled();
        });

        it('should wrap around when navigating past end', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            // Count actual suggestion items using data attribute to avoid counting other buttons
            const totalSuggestions = Array.from(document.querySelectorAll('[data-suggestion-index]')).length;

            // Remember currently selected item, then navigate past the end and assert
            // that selection wrapped back to the same item.
            const before = document.querySelector('.Mui-selected')?.textContent;

            // Navigate down past the end
            for (let i = 0; i < totalSuggestions; i++) {
                fireEvent.keyDown(window, { key: 'ArrowDown' });
            }

            // Should wrap back to the same selected item
            await waitFor(() => {
                const after = document.querySelector('.Mui-selected')?.textContent;
                expect(after).toBe(before);
            });
        });
    });

    describe('Mouse Interactions', () => {
        it('should select suggestion on click', async () => {
            const user = userEvent.setup();

            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            const firstSuggestion = screen.getByText('First suggestion');
            await user.click(firstSuggestion);

            expect(mockOnSelect).toHaveBeenCalledWith(
                expect.objectContaining({
                    text: 'First suggestion',
                })
            );
        });

        it('should update selected index on hover', async () => {
            const user = userEvent.setup();

            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('Second suggestion')).toBeInTheDocument();
            });

            const secondSuggestion = screen.getByText('Second suggestion').closest('button');
            await user.hover(secondSuggestion!);

            // Clicking should select the hovered item
            await user.click(secondSuggestion!);

            expect(mockOnSelect).toHaveBeenCalledWith(
                expect.objectContaining({
                    text: 'Second suggestion',
                })
            );
        });
    });

    describe('Refresh Button', () => {
        it('should regenerate suggestions when refresh is clicked', async () => {
            const user = userEvent.setup();

            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(mockAIService.complete).toHaveBeenCalledTimes(2); // completion + improve
            });

            const refreshButton = screen.getByRole('button', { name: /refresh/i });
            await user.click(refreshButton);

            await waitFor(() => {
                expect(mockAIService.complete).toHaveBeenCalledTimes(4); // 2 more calls
            });
        });
    });

    describe('Dismiss Button', () => {
        it('should call onDismiss when dismiss button is clicked', async () => {
            const user = userEvent.setup();
            const mockOnDismiss = vi.fn();

            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    onDismiss={mockOnDismiss}
                    autoGenerate={false}
                />
            );

            const dismissButton = screen.getByRole('button', { name: /dismiss/i });
            await user.click(dismissButton);

            expect(mockOnDismiss).toHaveBeenCalled();
        });

        it('should not render dismiss button if onDismiss is not provided', () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={false}
                />
            );

            const dismissButton = screen.queryByRole('button', { name: /dismiss/i });
            expect(dismissButton).not.toBeInTheDocument();
        });
    });

    describe('Confidence Display', () => {
        it('should show confidence scores when enabled', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    showConfidence={true}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText(/% confidence/i)).toBeInTheDocument();
            });
        });

        it('should hide confidence scores when disabled', async () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context="Test"
                    onSelect={mockOnSelect}
                    showConfidence={false}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText('First suggestion')).toBeInTheDocument();
            });

            expect(screen.queryByText(/% confidence/i)).not.toBeInTheDocument();
        });
    });

    describe('Error Handling', () => {
        it('should display error message when generation fails', async () => {
            const mockService = createMockAIService();
            mockService.complete = vi.fn().mockRejectedValue(new Error('API Error'));

            render(
                <SmartSuggestions
                    aiService={mockService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText(/API Error/i)).toBeInTheDocument();
            });
        });

        it('should show error for empty context and selection', () => {
            render(
                <SmartSuggestions
                    aiService={mockAIService}
                    context=""
                    selection=""
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            expect(screen.getByText(/No context or selection/i)).toBeInTheDocument();
        });
    });

    describe('Empty State', () => {
        it('should show empty message when no suggestions are generated', async () => {
            const mockService = createMockAIService(''); // Empty response

            render(
                <SmartSuggestions
                    aiService={mockService}
                    context="Test"
                    onSelect={mockOnSelect}
                    autoGenerate={true}
                />
            );

            await waitFor(() => {
                expect(screen.getByText(/No suggestions available/i)).toBeInTheDocument();
            });
        });
    });
});
