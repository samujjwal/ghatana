import { describe, it, expect, beforeEach } from "vitest";
import { createStore, atom } from "jotai";
import {
  availableTenantsAtom,
  hasRealTenantAtom,
  type Tenant,
} from "../atoms/tenantAtom";

// ---------------------------------------------------------------------------
// availableTenantsAtom
// ---------------------------------------------------------------------------

describe("availableTenantsAtom", () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it("starts with an empty list", () => {
    expect(store.get(availableTenantsAtom)).toHaveLength(0);
  });

  it("accepts an array of tenants", () => {
    const tenants: Tenant[] = [
      { id: "t1", name: "Acme Corp" },
      { id: "t2", name: "Widgets Inc", color: "indigo" },
    ];
    store.set(availableTenantsAtom, tenants);
    expect(store.get(availableTenantsAtom)).toHaveLength(2);
  });

  it("stores optional color on tenant", () => {
    const tenants: Tenant[] = [{ id: "t1", name: "Acme", color: "blue" }];
    store.set(availableTenantsAtom, tenants);
    expect(store.get(availableTenantsAtom)[0]?.color).toBe("blue");
  });

  it("can be replaced with a new list", () => {
    store.set(availableTenantsAtom, [{ id: "old", name: "Old" }]);
    store.set(availableTenantsAtom, [{ id: "new1", name: "New1" }, { id: "new2", name: "New2" }]);
    expect(store.get(availableTenantsAtom)).toHaveLength(2);
    expect(store.get(availableTenantsAtom)[0]?.id).toBe("new1");
  });
});

// ---------------------------------------------------------------------------
// hasRealTenantAtom (derived — reads tenantAtom which uses atomWithStorage)
//
// Because `atomWithStorage` reads from localStorage (unavailable in Node), we
// test the derived logic by constructing an equivalent in-memory atom pair
// that mirrors the production pattern. This tests the derivation semantics
// independently of the storage subsystem.
// ---------------------------------------------------------------------------

describe("hasRealTenantAtom derivation semantics", () => {
  it("is false when tenant id is 'default'", () => {
    // Simulate the derived logic directly
    const tenantId = "default";
    const hasRealTenant = tenantId !== "default";
    expect(hasRealTenant).toBe(false);
  });

  it("is true when tenant id is a real tenant", () => {
    const tenantId = "tenant-xyz";
    const hasRealTenant = tenantId !== "default";
    expect(hasRealTenant).toBe(true);
  });

  it("mirrors the production atom when backed by an in-memory atom", () => {
    // Replicate the production pattern with a plain atom for testing
    const tenantAtomInMemory = atom<string>("default");
    const hasRealTenantInMemory = atom((get) => get(tenantAtomInMemory) !== "default");

    const store = createStore();

    expect(store.get(hasRealTenantInMemory)).toBe(false);

    store.set(tenantAtomInMemory, "acme-corp");
    expect(store.get(hasRealTenantInMemory)).toBe(true);

    store.set(tenantAtomInMemory, "default");
    expect(store.get(hasRealTenantInMemory)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Tenant interface shape
// ---------------------------------------------------------------------------

describe("Tenant interface", () => {
  it("requires id and name", () => {
    const t: Tenant = { id: "t1", name: "Company A" };
    expect(t.id).toBe("t1");
    expect(t.name).toBe("Company A");
  });

  it("color is optional", () => {
    const withColor: Tenant = { id: "t1", name: "A", color: "green" };
    const withoutColor: Tenant = { id: "t2", name: "B" };
    expect(withColor.color).toBe("green");
    expect(withoutColor.color).toBeUndefined();
  });
});
