/**
 * Dialogs Component Tests
 * @description Tests for StandardModal and StandardDrawer
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import React from 'react';
import { StandardModal } from '../StandardModal';
import { StandardDrawer } from '../StandardDrawer';

const TestButton = (props: React.ButtonHTMLAttributes<HTMLButtonElement>): React.ReactElement =>
    React.createElement('button', props);

// ============================================================================
// StandardModal
// ============================================================================

describe('StandardModal', () => {
    it('renders nothing when isOpen=false', () => {
        render(<StandardModal isOpen={false} onClose={vi.fn()}>content</StandardModal>);
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });

    it('renders dialog when isOpen=true', () => {
        render(<StandardModal isOpen={true} onClose={vi.fn()}>content</StandardModal>);
        expect(document.querySelector('[role="dialog"]')).toBeTruthy();
    });

    it('renders children content', () => {
        render(<StandardModal isOpen={true} onClose={vi.fn()}>Hello Modal</StandardModal>);
        expect(screen.getByText('Hello Modal')).toBeTruthy();
    });

    it('renders title when provided', () => {
        render(<StandardModal isOpen={true} onClose={vi.fn()} title="My Title">content</StandardModal>);
        expect(screen.getByText('My Title')).toBeTruthy();
    });

    it('renders close button by default', () => {
        render(<StandardModal isOpen={true} onClose={vi.fn()} title="Title">content</StandardModal>);
        const closeBtn = document.querySelector('[aria-label="Close dialog"]');
        expect(closeBtn).toBeTruthy();
    });

    it('calls onClose when close button clicked', () => {
        const onClose = vi.fn();
        render(<StandardModal isOpen={true} onClose={onClose} title="Title">content</StandardModal>);
        const closeBtn = document.querySelector('[aria-label="Close dialog"]') as HTMLElement;
        fireEvent.click(closeBtn);
        expect(onClose).toHaveBeenCalled();
    });

    it('calls onClose when Escape key pressed', () => {
        const onClose = vi.fn();
        render(<StandardModal isOpen={true} onClose={onClose}>content</StandardModal>);
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(onClose).toHaveBeenCalled();
    });

    it('renders actions when provided', () => {
        render(
            <StandardModal isOpen={true} onClose={vi.fn()} actions={<TestButton>Confirm</TestButton>}>
                content
            </StandardModal>
        );
        expect(screen.getByText('Confirm')).toBeTruthy();
    });

    it('does not render close button when showCloseButton=false', () => {
        render(
            <StandardModal isOpen={true} onClose={vi.fn()} showCloseButton={false}>
                content
            </StandardModal>
        );
        expect(document.querySelector('[aria-label="Close dialog"]')).toBeNull();
    });
});

// ============================================================================
// StandardDrawer
// ============================================================================

describe('StandardDrawer', () => {
    it('renders nothing when isOpen=false', () => {
        render(<StandardDrawer isOpen={false} onClose={vi.fn()}>content</StandardDrawer>);
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });

    it('renders when isOpen=true', () => {
        render(<StandardDrawer isOpen={true} onClose={vi.fn()}>drawer content</StandardDrawer>);
        expect(screen.getByText('drawer content')).toBeTruthy();
    });

    it('renders title when provided', () => {
        render(<StandardDrawer isOpen={true} onClose={vi.fn()} title="Drawer Title">content</StandardDrawer>);
        expect(screen.getByText('Drawer Title')).toBeTruthy();
    });

    it('calls onClose when Escape key pressed', () => {
        const onClose = vi.fn();
        render(<StandardDrawer isOpen={true} onClose={onClose}>content</StandardDrawer>);
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(onClose).toHaveBeenCalled();
    });

    it('calls onClose when close button clicked', () => {
        const onClose = vi.fn();
        render(<StandardDrawer isOpen={true} onClose={onClose} title="Title">content</StandardDrawer>);
        const closeBtn = document.querySelector('[aria-label="Close drawer"]') as HTMLElement;
        if (closeBtn) fireEvent.click(closeBtn);
        expect(onClose).toHaveBeenCalled();
    });

    it('renders actions when provided', () => {
        render(
            <StandardDrawer isOpen={true} onClose={vi.fn()} actions={<TestButton>Save</TestButton>}>
                content
            </StandardDrawer>
        );
        expect(screen.getByText('Save')).toBeTruthy();
    });
});
