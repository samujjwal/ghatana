/**
 * @group unit
 * @tier U
 *
 * Tests for @ghatana/forms — useField and useFormState hooks.
 */
import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';

import { useField, useFormState } from '../hooks';

// ─── useField ────────────────────────────────────────────────────────────────

describe('useField', () => {
  it('initialises with the provided default value', () => {
    const { result } = renderHook(() => useField('hello'));
    expect(result.current.value).toBe('hello');
  });

  it('initialises with touched = false', () => {
    const { result } = renderHook(() => useField(''));
    expect(result.current.touched).toBe(false);
  });

  it('initialises with no error', () => {
    const { result } = renderHook(() => useField(''));
    expect(result.current.error).toBeUndefined();
  });

  it('onChange updates the value and marks the field as touched', () => {
    const { result } = renderHook(() => useField(''));
    act(() => result.current.onChange('new value'));
    expect(result.current.value).toBe('new value');
    expect(result.current.touched).toBe(true);
  });

  it('onChange replaces the previous value on repeated calls', () => {
    const { result } = renderHook(() => useField('a'));
    act(() => result.current.onChange('b'));
    act(() => result.current.onChange('c'));
    expect(result.current.value).toBe('c');
  });

  it('onInputChange reads e.target.value and marks the field as touched', () => {
    const { result } = renderHook(() => useField(''));
    const fakeEvent = { target: { value: 'typed' } } as React.ChangeEvent<HTMLInputElement>;
    act(() => result.current.onInputChange(fakeEvent));
    expect(result.current.value).toBe('typed');
    expect(result.current.touched).toBe(true);
  });

  it('setError stores an error message', () => {
    const { result } = renderHook(() => useField(''));
    act(() => result.current.setError('Required'));
    expect(result.current.error).toBe('Required');
  });

  it('setError with undefined clears a previously set error', () => {
    const { result } = renderHook(() => useField(''));
    act(() => result.current.setError('Bad input'));
    act(() => result.current.setError(undefined));
    expect(result.current.error).toBeUndefined();
  });

  it('supports a numeric initial value', () => {
    const { result } = renderHook(() => useField(42));
    expect(result.current.value).toBe(42);
    act(() => result.current.onChange(99));
    expect(result.current.value).toBe(99);
  });
});

// ─── useFormState ─────────────────────────────────────────────────────────────

describe('useFormState', () => {
  it('initialises with isSubmitting = false', () => {
    const { result } = renderHook(() => useFormState());
    expect(result.current.isSubmitting).toBe(false);
  });

  it('initialises with isSuccess = false', () => {
    const { result } = renderHook(() => useFormState());
    expect(result.current.isSuccess).toBe(false);
  });

  it('initialises with no error', () => {
    const { result } = renderHook(() => useFormState());
    expect(result.current.error).toBeUndefined();
  });

  it('startSubmit sets isSubmitting = true and clears previous error', () => {
    const { result } = renderHook(() => useFormState());
    act(() => result.current.onError('previous error'));
    act(() => result.current.startSubmit());
    expect(result.current.isSubmitting).toBe(true);
    expect(result.current.error).toBeUndefined();
  });

  it('onSuccess sets isSuccess = true and clears isSubmitting', () => {
    const { result } = renderHook(() => useFormState());
    act(() => result.current.startSubmit());
    act(() => result.current.onSuccess());
    expect(result.current.isSuccess).toBe(true);
    expect(result.current.isSubmitting).toBe(false);
  });

  it('onError stores the error message and clears isSubmitting', () => {
    const { result } = renderHook(() => useFormState());
    act(() => result.current.startSubmit());
    act(() => result.current.onError('Network failure'));
    expect(result.current.error).toBe('Network failure');
    expect(result.current.isSubmitting).toBe(false);
  });

  it('reset returns all state to initial values', () => {
    const { result } = renderHook(() => useFormState());
    act(() => result.current.startSubmit());
    act(() => result.current.onError('Boom'));
    act(() => result.current.reset());
    expect(result.current.isSubmitting).toBe(false);
    expect(result.current.isSuccess).toBe(false);
    expect(result.current.error).toBeUndefined();
  });
});
