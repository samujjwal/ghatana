/**
 * KeyboardShortcutsManager Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { KeyboardShortcutsHelp } from '../KeyboardShortcutsManager';

describe('KeyboardShortcutsHelp (from KeyboardShortcutsManager)', () => {
    it('renders nothing when isOpen=false', () => {
        render(<KeyboardShortcutsHelp isOpen={false} onClose={vi.fn()} />);
        expect(screen.queryByTestId('keyboard-shortcuts-help')).toBeNull();
    });

    it('renders overlay when isOpen=true', () => {
        render(<KeyboardShortcutsHelp isOpen={true} onClose={vi.fn()} />);
        expect(screen.getByTestId('keyboard-shortcuts-help')).toBeTruthy();
    });

    it('renders Keyboard Shortcuts heading', () => {
        render(<KeyboardShortcutsHelp isOpen={true} onClose={vi.fn()} />);
        expect(screen.getByText('Keyboard Shortcuts')).toBeTruthy();
    });

    it('calls onClose when backdrop is clicked', () => {
        const onClose = vi.fn();
        render(<KeyboardShortcutsHelp isOpen={true} onClose={onClose} />);
        // First div with fixed inset-0 bg-black is the backdrop
        const backdrop = document.querySelector('.fixed.inset-0.bg-black\\/50') as HTMLElement;
        if (backdrop) fireEvent.click(backdrop);
        expect(onClose).toHaveBeenCalled();
    });

    it('calls onClose on Escape key', () => {
        const onClose = vi.fn();
        render(<KeyboardShortcutsHelp isOpen={true} onClose={onClose} />);
        fireEvent.keyDown(window, { key: 'Escape' });
        expect(onClose).toHaveBeenCalled();
    });

    it('shows global category shortcuts', () => {
        render(<KeyboardShortcutsHelp isOpen={true} onClose={vi.fn()} />);
        expect(screen.getByText('Command Palette (search everything)')).toBeTruthy();
    });
});
