import { describe, it, expect, beforeEach } from "vitest";
import {
  readFromStorage,
  writeToStorage,
  removeFromStorage,
  clearMemoryStore,
} from "../persistence";

describe("memory storage backend", () => {
  beforeEach(() => {
    clearMemoryStore();
  });

  it("returns null for missing key", () => {
    const result = readFromStorage<string>("missing", { storage: "memory" });
    expect(result).toBeNull();
  });

  it("round-trips a primitive value", () => {
    writeToStorage("test:number", 42, { storage: "memory" });
    const result = readFromStorage<number>("test:number", { storage: "memory" });
    expect(result).toBe(42);
  });

  it("round-trips an object value", () => {
    const user = { id: "u1", name: "Alice" };
    writeToStorage("test:user", user, { storage: "memory" });
    const result = readFromStorage<typeof user>("test:user", { storage: "memory" });
    expect(result).toEqual(user);
  });

  it("round-trips an array value", () => {
    writeToStorage("test:arr", [1, 2, 3], { storage: "memory" });
    const result = readFromStorage<number[]>("test:arr", { storage: "memory" });
    expect(result).toEqual([1, 2, 3]);
  });

  it("removeFromStorage removes the key", () => {
    writeToStorage("test:remove", "bye", { storage: "memory" });
    removeFromStorage("test:remove", "memory");
    const result = readFromStorage<string>("test:remove", { storage: "memory" });
    expect(result).toBeNull();
  });

  it("clearMemoryStore clears all keys", () => {
    writeToStorage("k1", "a", { storage: "memory" });
    writeToStorage("k2", "b", { storage: "memory" });
    clearMemoryStore();
    expect(readFromStorage<string>("k1", { storage: "memory" })).toBeNull();
    expect(readFromStorage<string>("k2", { storage: "memory" })).toBeNull();
  });
});

describe("versioned migration", () => {
  beforeEach(() => {
    clearMemoryStore();
  });

  it("applies migration function when version mismatch", () => {
    // Write with version 1
    writeToStorage("test:migrate", { name: "old" }, { storage: "memory", version: 1 });

    // Read with version 2 + migration
    const result = readFromStorage<{ name: string; label: string }>(
      "test:migrate",
      {
        storage: "memory",
        version: 2,
        migrate: (stored) => {
          const s = stored as { name: string };
          return { name: s.name, label: s.name.toUpperCase() };
        },
      }
    );
    expect(result).toEqual({ name: "old", label: "OLD" });
  });

  it("skips migration when versions match", () => {
    writeToStorage("test:no-migrate", { x: 1 }, { storage: "memory", version: 3 });
    const migrateFn = (v: unknown) => ({ x: 99 });
    const result = readFromStorage<{ x: number }>("test:no-migrate", {
      storage: "memory",
      version: 3,
      migrate: migrateFn,
    });
    // Migration should NOT be called — version matches
    expect(result).toEqual({ x: 1 });
  });
});
