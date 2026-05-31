/**
 * Tests for useValidation hooks
 */

import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import {
  useDebouncedValidation,
  useFormValidation,
  useSchemaValidation,
} from "../useValidation";

describe("useFormValidation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("initializes with provided values", () => {
    const schema = z.object({
      name: z.string().min(1),
      email: z.string().email(),
    });

    const initialValues = {
      name: "Test User",
      email: "test@example.com",
    };

    const { result } = renderHook(() =>
      useFormValidation(schema, initialValues),
    );

    expect(result.current.values).toEqual(initialValues);
    expect(result.current.errors).toEqual({});
    expect(result.current.isValid).toBe(true);
  });

  it("validates form data on submit", async () => {
    const schema = z.object({
      name: z.string().min(1),
      email: z.string().email(),
    });

    const initialValues = {
      name: "Test User",
      email: "test@example.com",
    };

    const onSubmit = vi.fn().mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useFormValidation(schema, initialValues),
    );

    act(() => {
      result.current.submit(onSubmit)();
    });

    expect(onSubmit).toHaveBeenCalledWith(initialValues);
  });

  it("sets errors for invalid data on submit", () => {
    const schema = z.object({
      name: z.string().min(1),
      email: z.string().email(),
    });

    const initialValues = {
      name: "",
      email: "invalid",
    };

    const onSubmit = vi.fn();

    const { result } = renderHook(() =>
      useFormValidation(schema, initialValues),
    );

    act(() => {
      result.current.submit(onSubmit)();
    });

    expect(result.current.isValid).toBe(false);
    expect(Object.keys(result.current.errors).length).toBeGreaterThan(0);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("handles field changes", () => {
    const schema = z.object({
      name: z.string().min(1),
      email: z.string().email(),
    });

    const initialValues = {
      name: "",
      email: "",
    };

    const { result } = renderHook(() =>
      useFormValidation(schema, initialValues),
    );

    act(() => {
      result.current.handleChange("name")("Test User");
    });

    expect(result.current.values.name).toBe("Test User");
    expect(result.current.touched.name).toBe(true);
  });

  it("resets form to initial values", () => {
    const schema = z.object({
      name: z.string().min(1),
    });

    const initialValues = {
      name: "Initial",
    };

    const { result } = renderHook(() =>
      useFormValidation(schema, initialValues),
    );

    act(() => {
      result.current.handleChange("name")("Changed");
    });

    expect(result.current.values.name).toBe("Changed");

    act(() => {
      result.current.reset();
    });

    expect(result.current.values).toEqual(initialValues);
    expect(result.current.errors).toEqual({});
  });
});

describe("useSchemaValidation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("validates valid data", () => {
    const schema = z.object({
      id: z.string(),
      value: z.number(),
    });

    const { result } = renderHook(() => useSchemaValidation(schema));

    act(() => {
      const isValid = result.current.validate({ id: "123", value: 42 });
      expect(isValid).toBe(true);
      expect(result.current.isValid).toBe(true);
      expect(result.current.error).toBeNull();
    });
  });

  it("sets error for invalid data", () => {
    const schema = z.object({
      value: z.number(),
    });

    const { result } = renderHook(() => useSchemaValidation(schema));

    let isValid: boolean;
    act(() => {
      isValid = result.current.validate({ value: "not a number" });
    });

    expect(isValid!).toBe(false);
    expect(result.current.isValid).toBe(false);
    expect(result.current.error).toBeTruthy();
  });

  it("clears error", () => {
    const schema = z.object({
      value: z.number(),
    });

    const { result } = renderHook(() => useSchemaValidation(schema));

    act(() => {
      result.current.validate({ value: "invalid" });
    });

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });
});

describe("useDebouncedValidation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("debounces validation calls", () => {
    const schema = z.object({
      email: z.string().email(),
    });

    const { result } = renderHook(() => useDebouncedValidation(schema, 300));

    act(() => {
      result.current.validate({ email: "test@example.com" });
      result.current.validate({ email: "test2@example.com" });
      result.current.validate({ email: "test3@example.com" });
    });

    vi.advanceTimersByTime(300);

    expect(result.current.isValid).toBe(true);
  });

  it("validates after debounce delay", () => {
    const schema = z.object({
      email: z.string().email(),
    });

    const { result } = renderHook(() => useDebouncedValidation(schema, 300));

    act(() => {
      result.current.validate({ email: "invalid" });
    });

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(result.current.isValid).toBe(false);
    expect(result.current.error).toBeTruthy();
  });

  it("clears timeout and error", () => {
    const schema = z.object({
      email: z.string().email(),
    });

    const { result } = renderHook(() => useDebouncedValidation(schema, 300));

    act(() => {
      result.current.validate({ email: "invalid" });
    });

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });
});
