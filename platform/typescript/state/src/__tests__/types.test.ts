import { describe, it, expect } from "vitest";
import { AsyncState } from "../types";

describe("AsyncState factories", () => {
  it("idle() returns idle state", () => {
    const s = AsyncState.idle<number>();
    expect(s.status).toBe("idle");
    expect(s.data).toBeNull();
    expect(s.error).toBeNull();
  });

  it("loading() returns loading state", () => {
    const s = AsyncState.loading<string>();
    expect(s.status).toBe("loading");
    expect(s.data).toBeNull();
    expect(s.error).toBeNull();
  });

  it("success(data) returns success state with data", () => {
    const s = AsyncState.success({ id: 1 });
    expect(s.status).toBe("success");
    expect(s.data).toEqual({ id: 1 });
    expect(s.error).toBeNull();
  });

  it("error(err) returns error state with error", () => {
    const err = new Error("oops");
    const s = AsyncState.error<number>(err);
    expect(s.status).toBe("error");
    expect(s.data).toBeNull();
    expect(s.error).toBe(err);
  });
});

describe("AsyncState discriminated narrowing", () => {
  it("narrows to success data after status check", () => {
    const s = AsyncState.success<number[]>([1, 2, 3]);
    if (s.status === "success") {
      // TypeScript should know s.data is number[] here
      expect(s.data.length).toBe(3);
    }
  });

  it("narrows to error after status check", () => {
    const err = new Error("fail");
    const s = AsyncState.error<string>(err);
    if (s.status === "error") {
      expect(s.error.message).toBe("fail");
    }
  });
});
