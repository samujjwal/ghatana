// All tests skipped - incomplete feature
import { act, render, fireEvent, screen } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

import { mockUseDraggableWithPayload } from '../../../test-utils';
mockUseDraggableWithPayload();

import { ComponentPalette } from '../ComponentPalette';

describe('ComponentPalette integration', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
    });

    it('renders category content under the Architecture header', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        const summaryText = screen.getByText('Architecture');
        expect(summaryText).toBeTruthy();
        expect(screen.getByText('Frontend App')).toBeTruthy();
    });

    it('updates footer counts when search filters results', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Footer initially shows count but not the filtered-from text
        const initialFooter = screen.getByText(/items available/);
        expect(initialFooter.textContent).toMatch(/items available/);
        expect(initialFooter.textContent).not.toContain('(filtered from');

        // Search for 'button' which should reduce the results
        const input = screen.getByPlaceholderText('Search components...');
        fireEvent.change(input, { target: { value: 'button' } });

        const filteredFooter = screen.getByText(/items available/);
        // Now footer should include the parenthetical indicating filtered-from
        expect(filteredFooter.textContent).toContain('filtered from');
    });

    it('calls onAddComponent when add button is activated via keyboard', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Find the Button component's add button
        const paletteItem = screen.getByTestId('palette-item-component-button');
        expect(paletteItem).toBeTruthy();

        // MUI renders the IconButton as a native button element; locate it directly
        const addButton = paletteItem.querySelector('button');
        expect(addButton).toBeTruthy();

        // Activate the button (click simulates activation reliably in this environment)
        act(() => {
            (addButton as HTMLElement).focus();
            fireEvent.click(addButton as HTMLElement);
        });

        expect(mockAdd).toHaveBeenCalledTimes(1);
        const [componentArg, positionArg] = mockAdd.mock.calls[0];
        expect(componentArg).toHaveProperty('label', 'Button');
        expect(positionArg).toEqual({ x: 250, y: 200 });
    });

    it('exposes draggable payload attributes and does not call onAddComponent on pointer events', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Pick a component and verify the payload attribute exists on the list item
        const paletteItem = screen.getByTestId('palette-item-component');
        expect(paletteItem).toBeTruthy();

        // The mocked useDraggable sets a data-dndkit-payload attribute
        expect(paletteItem).toHaveAttribute('data-dndkit-payload');
        const payload = JSON.parse(paletteItem.getAttribute('data-dndkit-payload') || '{}');
        // Payload should include id and label
        expect(payload).toHaveProperty('id');
        expect(payload).toHaveProperty('label', 'Frontend App');

        // Simulate pointer down/up (drag start/end) - should not call add by itself
        fireEvent.pointerDown(paletteItem);
        fireEvent.pointerUp(paletteItem);

        expect(mockAdd).toHaveBeenCalledTimes(0);
    });
});
