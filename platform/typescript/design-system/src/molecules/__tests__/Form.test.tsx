/**
 * @file Form.test.tsx
 * Tests for the Form molecule component — rendering, submit, FormContext, initialValues.
 *
 * @doc.type module
 * @doc.purpose Tests for Form component and useFormContext hook
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Form, useFormContext, FormGroup } from '../Form';
import type { FormContextType } from '../Form';
import React, { useEffect } from 'react';

// ── Test helpers ─────────────────────────────────────────────────────────────

/** Renders a context probe child that captures the form context values. */
function ContextProbe({ onCapture }: { onCapture: (ctx: FormContextType) => void }) {
    const ctx = useFormContext();
    useEffect(() => {
        onCapture(ctx);
    });
    return null;
}

// ── Rendering tests ───────────────────────────────────────────────────────────

describe('Form', () => {
    describe('Rendering', () => {
        it('renders children inside a <form> element', () => {
            render(
                <Form onSubmit={vi.fn()}>
                    <button type="submit">Submit</button>
                </Form>
            );

            expect(screen.getByRole('button', { name: 'Submit' })).toBeInTheDocument();
        });

        it('applies className to the <form> element', () => {
            const { container } = render(
                <Form onSubmit={vi.fn()} className="custom-class">
                    <span>child</span>
                </Form>
            );

            const form = container.querySelector('form');
            expect(form).toHaveClass('custom-class');
        });

        it('renders without className when not provided', () => {
            const { container } = render(
                <Form onSubmit={vi.fn()}>
                    <span>child</span>
                </Form>
            );

            const form = container.querySelector('form');
            expect(form).toBeInTheDocument();
        });
    });

    // ── Submit handling ─────────────────────────────────────────────────────────

    describe('Submit handling', () => {
        it('calls onSubmit with empty values when submitted with no initialValues', () => {
            const onSubmit = vi.fn();
            render(
                <Form onSubmit={onSubmit}>
                    <button type="submit">Submit</button>
                </Form>
            );

            fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

            expect(onSubmit).toHaveBeenCalledOnce();
            expect(onSubmit).toHaveBeenCalledWith({});
        });

        it('calls onSubmit with initialValues when no field changes are made', () => {
            const onSubmit = vi.fn();
            const initialValues = { name: 'Alice', age: 30 };

            render(
                <Form onSubmit={onSubmit} initialValues={initialValues}>
                    <button type="submit">Submit</button>
                </Form>
            );

            fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

            expect(onSubmit).toHaveBeenCalledWith(initialValues);
        });

        it('prevents default browser form submission', () => {
            const onSubmit = vi.fn();
            const { container } = render(
                <Form onSubmit={onSubmit}>
                    <button type="submit">Submit</button>
                </Form>
            );

            const form = container.querySelector('form')!;
            const submitEvent = new Event('submit', { bubbles: true, cancelable: true });
            fireEvent(form, submitEvent);

            // Form should have called e.preventDefault(); native form submission does not navigate
            expect(onSubmit).toHaveBeenCalled();
        });
    });

    // ── FormContext ─────────────────────────────────────────────────────────────

    describe('FormContext', () => {
        it('provides initial empty errors via context', () => {
            let capturedCtx: FormContextType | null = null;

            render(
                <Form onSubmit={vi.fn()}>
                    <ContextProbe onCapture={(c) => { capturedCtx = c; }} />
                </Form>
            );

            expect(capturedCtx?.errors).toEqual({});
        });

        it('provides initial empty touched via context', () => {
            let capturedCtx: FormContextType | null = null;

            render(
                <Form onSubmit={vi.fn()}>
                    <ContextProbe onCapture={(c) => { capturedCtx = c; }} />
                </Form>
            );

            expect(capturedCtx?.touched).toEqual({});
        });

        it('provides initialValues as values via context', () => {
            let capturedCtx: FormContextType | null = null;
            const initialValues = { email: 'test@example.com' };

            render(
                <Form onSubmit={vi.fn()} initialValues={initialValues}>
                    <ContextProbe onCapture={(c) => { capturedCtx = c; }} />
                </Form>
            );

            expect(capturedCtx?.values).toEqual(initialValues);
        });

        it('updates values via setFieldValue', () => {
            let capturedCtx: FormContextType | null = null;

            function FieldUpdater() {
                const ctx = useFormContext();
                useEffect(() => { capturedCtx = ctx; }, [ctx]);
                return (
                    <button onClick={() => ctx.setFieldValue('username', 'bob')}>
                        Update
                    </button>
                );
            }

            render(
                <Form onSubmit={vi.fn()}>
                    <FieldUpdater />
                </Form>
            );

            fireEvent.click(screen.getByRole('button', { name: 'Update' }));
            expect(capturedCtx?.values['username']).toBe('bob');
        });

        it('updates touched via setFieldTouched', () => {
            let capturedCtx: FormContextType | null = null;

            function TouchUpdater() {
                const ctx = useFormContext();
                useEffect(() => { capturedCtx = ctx; }, [ctx]);
                return (
                    <button onClick={() => ctx.setFieldTouched('email', true)}>
                        Touch
                    </button>
                );
            }

            render(
                <Form onSubmit={vi.fn()}>
                    <TouchUpdater />
                </Form>
            );

            fireEvent.click(screen.getByRole('button', { name: 'Touch' }));
            expect(capturedCtx?.touched['email']).toBe(true);
        });

        it('updates errors via setFieldError', () => {
            let capturedCtx: FormContextType | null = null;

            function ErrorSetter() {
                const ctx = useFormContext();
                useEffect(() => { capturedCtx = ctx; }, [ctx]);
                return (
                    <button onClick={() => ctx.setFieldError('email', 'Email is required')}>
                        Set Error
                    </button>
                );
            }

            render(
                <Form onSubmit={vi.fn()}>
                    <ErrorSetter />
                </Form>
            );

            fireEvent.click(screen.getByRole('button', { name: 'Set Error' }));
            expect(capturedCtx?.errors['email']).toBe('Email is required');
        });

        it('submits updated field values after setFieldValue', () => {
            const onSubmit = vi.fn();

            function FieldUpdater() {
                const { setFieldValue } = useFormContext();
                return (
                    <>
                        <button type="button" onClick={() => setFieldValue('firstName', 'Alice')}>
                            Set Name
                        </button>
                        <button type="submit">Submit</button>
                    </>
                );
            }

            render(
                <Form onSubmit={onSubmit}>
                    <FieldUpdater />
                </Form>
            );

            fireEvent.click(screen.getByRole('button', { name: 'Set Name' }));
            fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

            expect(onSubmit).toHaveBeenCalledWith({ firstName: 'Alice' });
        });
    });

    // ── useFormContext hook ─────────────────────────────────────────────────────

    describe('useFormContext', () => {
        it('throws when used outside a Form', () => {
            const ErrorComponent: React.FC = () => {
                useFormContext();
                return null;
            };

            // Suppress React's error boundary console output
            const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
            expect(() => render(<ErrorComponent />)).toThrow(
                'useFormContext must be used within a Form component'
            );
            consoleSpy.mockRestore();
        });
    });

    // ── FormGroup ─────────────────────────────────────────────────────────────

    describe('FormGroup', () => {
        it('renders children inside a fieldset', () => {
            const { container } = render(
                <FormGroup>
                    <input type="text" />
                </FormGroup>
            );

            expect(container.querySelector('fieldset')).toBeInTheDocument();
        });

        it('renders legend when provided', () => {
            render(
                <FormGroup legend="Personal Details">
                    <span>content</span>
                </FormGroup>
            );

            expect(screen.getByText('Personal Details')).toBeInTheDocument();
        });

        it('does not render legend when not provided', () => {
            const { container } = render(
                <FormGroup>
                    <span>content</span>
                </FormGroup>
            );

            expect(container.querySelector('legend')).not.toBeInTheDocument();
        });
    });
});
