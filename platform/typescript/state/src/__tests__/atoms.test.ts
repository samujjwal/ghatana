import { describe, it, expect, beforeEach } from "vitest";
import { createStore } from "jotai";
import {
  createAtom,
  createPersistentAtom,
  createDerivedAtom,
  createAsyncAtom,
  createWritableAtom,
  getRegisteredAtoms,
} from "../atoms";
import { AsyncState } from "../types";

describe("createAtom", () => {
  it("creates an atom with the given initial value", () => {
    const store = createStore();
    const countAtom = createAtom("test:count", 0);
    expect(store.get(countAtom)).toBe(0);
  });

  it("atom is writable", () => {
    const store = createStore();
    const countAtom = createAtom("test:count2", 0);
    store.set(countAtom, 5);
    expect(store.get(countAtom)).toBe(5);
  });

  it("supports updater function", () => {
    const store = createStore();
    const countAtom = createAtom("test:count3", 10);
    store.set(countAtom, (prev) => prev + 1);
    expect(store.get(countAtom)).toBe(11);
  });

  it("registers atom metadata", () => {
    createAtom("test:registered", "hello", "A test atom");
    const meta = getRegisteredAtoms().get("test:registered");
    expect(meta).toBeDefined();
    expect(meta?.key).toBe("test:registered");
    expect(meta?.description).toBe("A test atom");
    expect(meta?.persistent).toBe(false);
  });
});

describe("createPersistentAtom", () => {
  it("creates an atom with initial value (memory storage)", () => {
    const store = createStore();
    const prefsAtom = createPersistentAtom(
      "test:prefs",
      { theme: "light" },
      { storage: "memory" },
    );
    expect(store.get(prefsAtom)).toEqual({ theme: "light" });
  });

  it("registers as persistent", () => {
    createPersistentAtom(
      "test:persistent-meta",
      42,
      { storage: "memory" },
      "Persistent atom",
    );
    const meta = getRegisteredAtoms().get("test:persistent-meta");
    expect(meta?.persistent).toBe(true);
    expect(meta?.description).toBe("Persistent atom");
  });
});

describe("createDerivedAtom", () => {
  it("derives value from other atoms", () => {
    const store = createStore();
    const firstAtom = createAtom("test:first", "Alice");
    const lastAtom = createAtom("test:last", "Smith");
    const fullNameAtom = createDerivedAtom(
      "test:fullName",
      (get) => `${get(firstAtom)} ${get(lastAtom)}`,
    );

    expect(store.get(fullNameAtom)).toBe("Alice Smith");

    store.set(firstAtom, "Bob");
    expect(store.get(fullNameAtom)).toBe("Bob Smith");
  });

  it("is read-only", () => {
    const derived = createDerivedAtom("test:derived-readonly", (get) => 42);
    // Should not have a write function
    expect((derived as { write?: unknown }).write).toBeUndefined();
  });
});

describe("createAsyncAtom", () => {
  it("starts in idle state", () => {
    const store = createStore();
    const usersAtom = createAsyncAtom<string[]>("test:users");
    const state = store.get(usersAtom);
    expect(state.status).toBe("idle");
  });

  it("can transition to success state", () => {
    const store = createStore();
    const atom = createAsyncAtom<number>("test:asyncNum");
    store.set(atom, AsyncState.success(42));
    const state = store.get(atom);
    expect(state.status).toBe("success");
    if (state.status === "success") {
      expect(state.data).toBe(42);
    }
  });

  it("can transition to error state", () => {
    const store = createStore();
    const atom = createAsyncAtom<string>("test:asyncErr");
    const err = new Error("fetch failed");
    store.set(atom, AsyncState.error(err));
    const state = store.get(atom);
    expect(state.status).toBe("error");
    if (state.status === "error") {
      expect(state.error.message).toBe("fetch failed");
    }
  });
});

describe("createWritableAtom", () => {
  it("creates an atom with custom read and write logic", () => {
    const store = createStore();
    const baseAtom = createAtom("test:base", 10);
    const doubledAtom = createWritableAtom(
      "test:doubled",
      (get) => get(baseAtom) * 2,
      (get, set, value: number) => {
        set(baseAtom, value / 2);
      },
    );
    expect(store.get(doubledAtom)).toBe(20);
  });

  it("write function updates base atom", () => {
    const store = createStore();
    const baseAtom = createAtom("test:base2", 10);
    const doubledAtom = createWritableAtom(
      "test:doubled2",
      (get) => get(baseAtom) * 2,
      (get, set, value: number) => {
        set(baseAtom, value / 2);
      },
    );
    store.set(doubledAtom, 40);
    expect(store.get(baseAtom)).toBe(20);
    expect(store.get(doubledAtom)).toBe(40);
  });

  it("does not register metadata (custom atoms skip registration)", () => {
    const baseAtom = createAtom("test:base3", 5);
    const customAtom = createWritableAtom(
      "test:custom",
      (get) => get(baseAtom) + 1,
      (get, set) => {},
    );
    const meta = getRegisteredAtoms().get("test:custom");
    expect(meta).toBeUndefined();
  });
});
