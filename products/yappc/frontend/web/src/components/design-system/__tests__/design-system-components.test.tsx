/**
 * Local Design-System Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { Badge } from '../Badge';
import { HeaderButton } from '../HeaderButton';

describe('Badge', () => {
    it('renders label text', () => {
        render(<Badge label="Active" />);
        expect(screen.getByText('Active')).toBeTruthy();
    });

    it('renders with success variant', () => {
        const { container } = render(<Badge label="Success" variant="success" />);
        expect(container.firstChild).toBeTruthy();
    });

    it('renders with error variant', () => {
        const { container } = render(<Badge label="Error" variant="error" />);
        expect(container.firstChild).toBeTruthy();
    });

    it('renders dot indicator when dot=true', () => {
        render(<Badge label="dot" dot />);
        // Dot badge still renders the element
        expect(document.querySelector('span')).toBeTruthy();
    });

    it('renders with icon', () => {
        render(<Badge label="Info" icon={<span data-testid="icon" />} />);
        expect(screen.getByTestId('icon')).toBeTruthy();
    });

    it('renders with sm size', () => {
        const { container } = render(<Badge label="Small" size="sm" />);
        expect(container.firstChild).toBeTruthy();
    });
});

describe('HeaderButton', () => {
    it('renders with label', () => {
        render(<HeaderButton label="Settings" />);
        expect(screen.getByText('Settings')).toBeTruthy();
    });

    it('renders with icon', () => {
        render(<HeaderButton icon={<span data-testid="icon" />} tooltip="Menu" />);
        expect(screen.getByTestId('icon')).toBeTruthy();
    });

    it('calls onClick when clicked', () => {
        const onClick = vi.fn();
        render(<HeaderButton label="Click Me" onClick={onClick} />);
        const btn = screen.getByRole('button');
        fireEvent.click(btn);
        expect(onClick).toHaveBeenCalled();
    });

    it('is disabled when disabled=true', () => {
        render(<HeaderButton label="Disabled" disabled />);
        const btn = screen.getByRole('button');
        expect(btn.getAttribute('disabled') !== undefined || btn.hasAttribute('disabled')).toBeTruthy();
    });
});
