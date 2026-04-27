/**
 * @doc.type test
 * @doc.purpose Regression tests for native session persistence and request header creation
 * @doc.layer product
 * @doc.pattern UnitTest
 */

jest.mock("react-native-mmkv", () => {
  const store = new Map<string, string>();

  const mmkvInstance = {
    getString(key: string): string | undefined {
      return store.get(key);
    },
    set(key: string, value: string): void {
      store.set(key, value);
    },
    remove(key: string): boolean {
      store.delete(key);
      return true;
    },
  };

  return {
    createMMKV: () => mmkvInstance,
  };
});

import {
  clearSession,
  createSessionHeaders,
  getSessionSnapshot,
  initSessionStorage,
  installNativeSessionStorageShim,
  setSessionValue,
} from "../NativeSessionStorage";

describe("NativeSessionStorage", () => {
  type LocalStorageLike = {
    setItem: (key: string, value: string) => void;
    clear: () => void;
  };

  beforeEach(() => {
    // initSessionStorage must be called before any session helpers are used.
    // The MMKV mock ignores the encryptionKey argument.
    initSessionStorage("test-encryption-key");
    clearSession();
    const globalScope = globalThis as typeof globalThis & { localStorage?: unknown };
    delete globalScope.localStorage;
  });

  it("returns null session values when nothing is stored", () => {
    expect(getSessionSnapshot()).toEqual({
      accessToken: null,
      refreshToken: null,
      tenantId: null,
    });
  });

  it("creates authenticated headers only when tenant context exists", () => {
    setSessionValue("auth_token", "access-token-123");

    expect(createSessionHeaders({ Accept: "application/json" })).toEqual({
      Authorization: "Bearer access-token-123",
      Accept: "application/json",
    });

    setSessionValue("tenant_id", "tenant-42");

    expect(createSessionHeaders({ Accept: "application/json" })).toEqual({
      Authorization: "Bearer access-token-123",
      "X-Tenant-ID": "tenant-42",
      Accept: "application/json",
    });
  });

  it("installs a localStorage shim backed by the native session store", () => {
    installNativeSessionStorageShim();

    const localStorage = (globalThis as typeof globalThis & { localStorage: LocalStorageLike })
      .localStorage;

    expect(localStorage).toBeDefined();

    localStorage.setItem("auth_token", "shim-token");
    localStorage.setItem("tenant_id", "tenant-shim");

    expect(getSessionSnapshot()).toEqual({
      accessToken: "shim-token",
      refreshToken: null,
      tenantId: "tenant-shim",
    });

    localStorage.clear();

    expect(getSessionSnapshot()).toEqual({
      accessToken: null,
      refreshToken: null,
      tenantId: null,
    });
  });
});
