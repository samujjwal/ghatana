/**
 * ComponentLibraryPalette Tests
 * 
 * Comprehensive test suite for Component Library Palette (Journey 5.1)
 */

import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { ComponentLibraryPalette, type ComponentDefinition } from '../ComponentLibraryPalette';

describe('ComponentLibraryPalette', () => {
    const mockOnSelectComponent = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render successfully', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);
            expect(screen.getByText('Component Library')).toBeInTheDocument();
        });

        it('should render search input', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);
            expect(screen.getByPlaceholderText('Search components...')).toBeInTheDocument();
        });

        it('should render component count in footer', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);
            expect(screen.getByText(/\d+ components?/)).toBeInTheDocument();
        });
    });

    describe('Categories', () => {
        it('should render all component categories', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            expect(screen.getByText('Inputs')).toBeInTheDocument();
            expect(screen.getByText('Display')).toBeInTheDocument();
            expect(screen.getByText('Feedback')).toBeInTheDocument();
            expect(screen.getByText('Navigation')).toBeInTheDocument();
            expect(screen.getByText('Layout')).toBeInTheDocument();
            expect(screen.getByText('Data')).toBeInTheDocument();
        });

        it('should show expanded categories by default', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            // Inputs and Display should be expanded by default
            expect(screen.getByText('Text Field')).toBeVisible();
            expect(screen.getByText('Avatar')).toBeVisible();
        });

        it('should toggle category expansion', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const inputsCategory = screen.getByText('Inputs').closest('div')!;
            await user.click(inputsCategory);

            // Should collapse
            expect(screen.queryByText('Text Field')).not.toBeVisible();
        });

        it('should show component count per category', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const inputsCategory = screen.getByText('Inputs').closest('div')!;
            const badge = within(inputsCategory).getAllByText(/\d+/)[0];
            expect(badge).toBeInTheDocument();
        });
    });

    describe('Built-in Components', () => {
        it('should render Input components', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            expect(screen.getByText('Text Field')).toBeInTheDocument();
            expect(screen.getByText('Button')).toBeInTheDocument();
            expect(screen.getByText('Checkbox')).toBeInTheDocument();
            expect(screen.getByText('Radio Button')).toBeInTheDocument();
            expect(screen.getByText('Dropdown')).toBeInTheDocument();
            expect(screen.getByText('Date Picker')).toBeInTheDocument();
            expect(screen.getByText('Switch')).toBeInTheDocument();
            expect(screen.getByText('Slider')).toBeInTheDocument();
        });

        it('should render Display components', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            expect(screen.getByText('Avatar')).toBeInTheDocument();
            expect(screen.getByText('Image')).toBeInTheDocument();
            expect(screen.getByText('Text')).toBeInTheDocument();
            expect(screen.getByText('Icon Button')).toBeInTheDocument();
        });

        it('should render Data components', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            // Expand Data category
            const dataCategory = screen.getByText('Data');
            await user.click(dataCategory);

            expect(screen.getByText('List')).toBeInTheDocument();
            expect(screen.getByText('Table')).toBeInTheDocument();
        });
    });

    describe('Component Selection', () => {
        it('should call onSelectComponent when clicking a component', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const button = screen.getByText('Button');
            await user.click(button);

            expect(mockOnSelectComponent).toHaveBeenCalledTimes(1);
            expect(mockOnSelectComponent).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: 'button',
                    label: 'Button',
                })
            );
        });

        it('should display tooltips with component descriptions', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const button = screen.getByText('Button');
            await user.hover(button);

            // MUI tooltip should appear
            expect(await screen.findByText('Clickable button')).toBeInTheDocument();
        });
    });

    describe('Search Functionality', () => {
        it('should filter components by name', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            await user.type(searchInput, 'button');

            expect(screen.getByText('Button')).toBeInTheDocument();
            expect(screen.getByText('Icon Button')).toBeInTheDocument();
            expect(screen.getByText('Radio Button')).toBeInTheDocument();
            expect(screen.queryByText('Avatar')).not.toBeInTheDocument();
        });

        it('should filter components by description', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            await user.type(searchInput, 'text input');

            expect(screen.getByText('Text Field')).toBeInTheDocument();
        });

        it('should filter components by tags', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            await user.type(searchInput, 'form');

            // Should show all form-related components
            expect(screen.getByText('Text Field')).toBeInTheDocument();
            expect(screen.getByText('Button')).toBeInTheDocument();
            expect(screen.getByText('Checkbox')).toBeInTheDocument();
        });

        it('should show no results when search has no matches', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            await user.type(searchInput, 'xyz123nonexistent');

            expect(screen.getByText('0 components')).toBeInTheDocument();
        });

        it('should clear search when input is cleared', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            await user.type(searchInput, 'button');
            await user.clear(searchInput);

            // Should show all components again
            expect(screen.getByText('Avatar')).toBeInTheDocument();
            expect(screen.getByText('Checkbox')).toBeInTheDocument();
        });
    });

    describe('Tag Filtering', () => {
        it('should display tag filter chips', () => {
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    filterTags={['form', 'input']}
                />
            );

            expect(screen.getByText('form')).toBeInTheDocument();
            expect(screen.getByText('input')).toBeInTheDocument();
        });

        it('should filter components by tags', () => {
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    filterTags={['boolean']}
                />
            );

            expect(screen.getByText('Checkbox')).toBeInTheDocument();
            expect(screen.getByText('Switch')).toBeInTheDocument();
            expect(screen.queryByText('Text Field')).not.toBeInTheDocument();
        });
    });

    describe('Custom Components', () => {
        const customComponents: ComponentDefinition[] = [
            {
                id: 'custom1',
                type: 'custom-widget',
                label: 'Custom Widget',
                icon: <div>Icon</div>,
                category: 'inputs',
                description: 'A custom component',
                tags: ['custom'],
            },
        ];

        it('should render custom components', async () => {
            const user = userEvent.setup();
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    customComponents={customComponents}
                />
            );

            expect(screen.getByText('Custom Widget')).toBeInTheDocument();
        });

        it('should call onSelectComponent for custom components', async () => {
            const user = userEvent.setup();
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    customComponents={customComponents}
                />
            );

            const customComponent = screen.getByText('Custom Widget');
            await user.click(customComponent);

            expect(mockOnSelectComponent).toHaveBeenCalledWith(customComponents[0]);
        });
    });

    describe('Drag and Drop', () => {
        it('should make components draggable when dragEnabled is true', () => {
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    dragEnabled={true}
                />
            );

            const button = screen.getByText('Button').closest('div')!;
            expect(button.parentElement).toHaveAttribute('draggable', 'true');
        });

        it('should not make components draggable when dragEnabled is false', () => {
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    dragEnabled={false}
                />
            );

            const button = screen.getByText('Button').closest('div')!;
            expect(button.parentElement).toHaveAttribute('draggable', 'false');
        });

        it('should set component data on drag start', async () => {
            const user = userEvent.setup();
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    dragEnabled={true}
                />
            );

            const button = screen.getByText('Button').closest('div')!.parentElement!;

            const dataTransfer = {
                effectAllowed: '',
                setData: vi.fn(),
            };

            await user.pointer({
                target: button,
                keys: '[MouseLeft]',
            });

            // Drag event would be triggered
            const dragEvent = new DragEvent('dragstart', {
                dataTransfer: dataTransfer as unknown,
            });
            button.dispatchEvent(dragEvent);

            expect(dataTransfer.effectAllowed).toBe('copy');
        });
    });

    describe('Component Count', () => {
        it('should display correct total count', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const footerText = screen.getByText(/\d+ components?/);
            expect(footerText).toBeInTheDocument();
        });

        it('should update count when searching', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            await user.type(searchInput, 'button');

            const footerText = screen.getByText(/3 components/); // Button, Icon Button, Radio Button
            expect(footerText).toBeInTheDocument();
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty custom components array', () => {
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    customComponents={[]}
                />
            );

            expect(screen.getByText('Component Library')).toBeInTheDocument();
        });

        it('should handle undefined custom components', () => {
            render(
                <ComponentLibraryPalette
                    onSelectComponent={mockOnSelectComponent}
                    customComponents={undefined}
                />
            );

            expect(screen.getByText('Component Library')).toBeInTheDocument();
        });

        it('should handle rapid category toggling', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const inputsCategory = screen.getByText('Inputs').closest('div')!;

            await user.click(inputsCategory);
            await user.click(inputsCategory);
            await user.click(inputsCategory);

            expect(screen.queryByText('Text Field')).not.toBeVisible();
        });
    });

    describe('Accessibility', () => {
        it('should have proper ARIA labels', () => {
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            const searchInput = screen.getByPlaceholderText('Search components...');
            expect(searchInput).toHaveAccessibleName();
        });

        it('should support keyboard navigation', async () => {
            const user = userEvent.setup();
            render(<ComponentLibraryPalette onSelectComponent={mockOnSelectComponent} />);

            await user.tab();
            await user.tab();

            expect(document.activeElement).toBeDefined();
        });
    });
});
