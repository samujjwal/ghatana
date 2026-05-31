import { useAsyncState, type AsyncError } from "@/hooks/useAsyncState";
import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

describe("useAsyncState", () => {
  it("returns loading state when isLoading is true", () => {
    const { result } = renderHook(() =>
      useAsyncState({ data: null, isLoading: true, error: null }),
    );
    expect(result.current.status).toBe("loading");
    expect(result.current.data).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it("returns error state with classified error", () => {
    const networkError = new Error("network timeout");
    const { result } = renderHook(() =>
      useAsyncState({ data: null, isLoading: false, error: networkError }),
    );
    expect(result.current.status).toBe("error");
    expect(result.current.data).toBeNull();
    const error = result.current.error as AsyncError;
    expect(error.category).toBe("network");
    expect(error.retryable).toBe(true);
  });

  it("returns empty state when data array is empty", () => {
    const { result } = renderHook(() =>
      useAsyncState({ data: [], isLoading: false, error: null }),
    );
    expect(result.current.status).toBe("empty");
  });

  it("returns empty state when isEmpty is true", () => {
    const { result } = renderHook(() =>
      useAsyncState({
        data: "data",
        isLoading: false,
        error: null,
        isEmpty: true,
      }),
    );
    expect(result.current.status).toBe("empty");
  });

  it("returns success state with data", () => {
    const testData = { items: [1, 2, 3] };
    const { result } = renderHook(() =>
      useAsyncState({ data: testData, isLoading: false, error: null }),
    );
    expect(result.current.status).toBe("success");
    expect(result.current.data).toEqual(testData);
    expect(result.current.error).toBeNull();
  });

  it("prioritizes loading over error", () => {
    const { result } = renderHook(() =>
      useAsyncState({ data: null, isLoading: true, error: new Error("fail") }),
    );
    expect(result.current.status).toBe("loading");
  });

  it("classifies auth errors correctly", () => {
    const authError = new Error("unauthorized access");
    const { result } = renderHook(() =>
      useAsyncState({ data: null, isLoading: false, error: authError }),
    );
    expect(result.current.status).toBe("error");
    const error = result.current.error as AsyncError;
    expect(error.category).toBe("auth");
    expect(error.retryable).toBe(false);
  });

  it("classifies validation errors correctly", () => {
    const validationError = new Error("invalid field");
    const { result } = renderHook(() =>
      useAsyncState({ data: null, isLoading: false, error: validationError }),
    );
    expect(result.current.status).toBe("error");
    const error = result.current.error as AsyncError;
    expect(error.category).toBe("validation");
  });

  it("handles unknown error strings gracefully", () => {
    const { result } = renderHook(() =>
      useAsyncState({ data: null, isLoading: false, error: "something broke" }),
    );
    expect(result.current.status).toBe("error");
    const error = result.current.error as AsyncError;
    expect(error.category).toBe("unknown");
    expect(error.retryable).toBe(true);
  });
});
