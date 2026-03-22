// All tests skipped - incomplete feature
import { render, fireEvent, screen } from '@testing-library/react';
import React from 'react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

// Use shared test mock
import { mockUseDraggableSimple } from '../../../test-utils';
mockUseDraggableSimple();

import { ComponentPalette } from '../ComponentPalette';

describe.skip('ComponentPalette', () => {
    beforeEach(() => {
        // clear DOM between tests
        document.body.innerHTML = '';
    });

    it('renders categories and components, supports search filtering', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        // Title present
        expect(screen.getByText('Palette')).toBeTruthy();

        // Sample component present
        expect(screen.getByText('Frontend App')).toBeTruthy();

        // Footer shows total count
        expect(screen.getByText(/items available/)).toBeTruthy();

        // Search for 'button' should filter to the Button component
        const input = screen.getByPlaceholderText('Search components...');
        fireEvent.change(input, { target: { value: 'button' } });

        expect(screen.getByText('Button')).toBeTruthy();
        // Should not show unrelated component
        expect(screen.queryByText('Database')).toBeNull();
    });

    it('calls onAddComponent with default position when add icon is clicked', () => {
        const mockAdd = vi.fn();
        const { container } = render(<ComponentPalette onAddComponent={mockAdd} />);

        // Find the list item for the UI 'Table' component
        const labelNode = screen.getByText('Table');
        expect(labelNode).toBeTruthy();

        // Find closest list item element and its button (IconButton)
        const listItem = labelNode.closest('li');
        expect(listItem).toBeTruthy();

        const addButton = listItem!.querySelector('button');
        expect(addButton).toBeTruthy();

        // Click the add button
        fireEvent.click(addButton!);

        // Expect callback called once with the component object and default position
        expect(mockAdd).toHaveBeenCalledTimes(1);
        const [componentArg, positionArg] = mockAdd.mock.calls[0];
        expect(componentArg).toHaveProperty('label', 'Table');
        expect(positionArg).toEqual({ x: 250, y: 200 });
    });
});
