// All tests skipped - incomplete feature
import { render, fireEvent, screen, within } from '@testing-library/react';
import React from 'react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

import { mockUseDraggableWithPayload } from '../../../test-utils';
mockUseDraggableWithPayload();

import { ComponentPalette } from '../ComponentPalette';

describe.skip('ComponentPalette integration', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
    });

    it('toggles category expand/collapse when header is clicked', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Locate the AccordionSummary's interactive button by finding the category text
        const summaryText = screen.getByText('Architecture');
        const summaryButton = summaryText.closest('[role="button"]') as HTMLElement | null;
        expect(summaryButton).toBeTruthy();

        // Initially the accordion should be expanded (aria-expanded="true")
        expect(summaryButton!.getAttribute('aria-expanded')).toBe('true');

        // Click summary to collapse
        fireEvent.click(summaryButton!);

        // After collapse aria-expanded should be false
        expect(summaryButton!.getAttribute('aria-expanded')).toBe('false');

        // Click again to expand and ensure aria-expanded toggles back to true
        fireEvent.click(summaryButton!);
        expect(summaryButton!.getAttribute('aria-expanded')).toBe('true');
    });

    it('updates footer counts when search filters results', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Footer initially shows count but not the filtered-from text
        const initialFooter = screen.getByText(/components available/);
        expect(initialFooter.textContent).toMatch(/components available/);
        expect(initialFooter.textContent).not.toContain('(filtered from');

        // Search for 'button' which should reduce the results
        const input = screen.getByPlaceholderText('Search components...');
        fireEvent.change(input, { target: { value: 'button' } });

        const filteredFooter = screen.getByText(/components available/);
        // Now footer should include the parenthetical indicating filtered-from
        expect(filteredFooter.textContent).toContain('filtered from');
    });

    it('calls onAddComponent when add button is activated via keyboard', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Find the Button component's add button
        const itemLabel = screen.getByText('Button');
        const listItem = itemLabel.closest('li');
        expect(listItem).toBeTruthy();

        // MUI renders the IconButton as a plain <button>; locate it directly
        const addButton = listItem!.querySelector('button');
        expect(addButton).toBeTruthy();

        // Activate the button (click simulates activation reliably in this environment)
        (addButton as HTMLElement).focus();
        fireEvent.click(addButton as HTMLElement);

        expect(mockAdd).toHaveBeenCalledTimes(1);
        const [componentArg, positionArg] = mockAdd.mock.calls[0];
        expect(componentArg).toHaveProperty('label', 'Button');
        expect(positionArg).toEqual({ x: 250, y: 200 });
    });

    it('exposes draggable payload attributes and does not call onAddComponent on pointer events', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Pick a component and verify the payload attribute exists on the list item
        const labelNode = screen.getByText('Frontend App');
        const listItem = labelNode.closest('li');
        expect(listItem).toBeTruthy();

        // The mocked useDraggable sets a data-dndkit-payload attribute
        expect(listItem).toHaveAttribute('data-dndkit-payload');
        const payload = JSON.parse(listItem!.getAttribute('data-dndkit-payload') || '{}');
        // Payload should include id and label
        expect(payload).toHaveProperty('id');
        expect(payload).toHaveProperty('label', 'Frontend App');

        // Simulate pointer down/up (drag start/end) - should not call add by itself
        fireEvent.pointerDown(listItem!);
        fireEvent.pointerUp(listItem!);

        expect(mockAdd).toHaveBeenCalledTimes(0);
    });
});
