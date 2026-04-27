/**
 * SmartForm Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { SmartFormField } from '../SmartForm';

describe('SmartFormField', () => {
    it('renders label', () => {
        render(
            <SmartFormField fieldName="name" label="Full Name">
                <input />
            </SmartFormField>
        );
        expect(screen.getByText('Full Name')).toBeTruthy();
    });

    it('renders children', () => {
        render(
            <SmartFormField fieldName="name" label="Name">
                <input data-testid="input" />
            </SmartFormField>
        );
        expect(screen.getByTestId('input')).toBeTruthy();
    });

    it('shows confidence chip when confidence > 0.5', () => {
        render(
            <SmartFormField fieldName="name" label="Name" confidence={0.8}>
                <input />
            </SmartFormField>
        );
        expect(screen.getByText(/80% confident/)).toBeTruthy();
    });

    it('does not show confidence chip when confidence <= 0.5', () => {
        render(
            <SmartFormField fieldName="name" label="Name" confidence={0.3}>
                <input />
            </SmartFormField>
        );
        expect(screen.queryByText(/confident/)).toBeNull();
    });

    it('shows reasoning when confidence > 0.5 and reasoning provided', () => {
        render(
            <SmartFormField fieldName="name" label="Name" confidence={0.9} reasoning="Based on your history">
                <input />
            </SmartFormField>
        );
        expect(screen.getByText('Based on your history')).toBeTruthy();
    });

    it('calls onAcceptSuggestion when Accept clicked', () => {
        const onAccept = vi.fn();
        render(
            <SmartFormField fieldName="name" label="Name" confidence={0.9} onAcceptSuggestion={onAccept}>
                <input />
            </SmartFormField>
        );
        fireEvent.click(screen.getByText('Accept'));
        expect(onAccept).toHaveBeenCalled();
    });

    it('calls onRejectSuggestion when Reject clicked', () => {
        const onReject = vi.fn();
        render(
            <SmartFormField fieldName="name" label="Name" confidence={0.9} onRejectSuggestion={onReject}>
                <input />
            </SmartFormField>
        );
        fireEvent.click(screen.getByText('Dismiss'));
        expect(onReject).toHaveBeenCalled();
    });
});
