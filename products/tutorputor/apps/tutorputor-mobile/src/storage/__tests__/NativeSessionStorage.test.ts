/**
 * @doc.type test
 * @doc.purpose Regression tests for native session persistence and request header creation
 * @doc.layer product
 * @doc.pattern UnitTest
 */

jest.mock("react-native-mmkv", () => {
  const store = new Map();

  class MMKV {
    constructor() {}

    getString(key) {
      return store.get(key);
    }

    set(key, value) {
      store.set(key, value);
    }

    delete(key) {
      store.delete(key);
    }
  }

  return { MMKV };
});

import {
  clearSession,
  createSessionHeaders,
  getSessionSnapshot,
  installNativeSessionStorageShim,
  setSessionValue,
} from "../NativeSessionStorage";

describe("NativeSessionStorage", () => {
  beforeEach(() => {
    clearSession();
    const globalScope = globalThis;
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

    expect(globalThis.localStorage).toBeDefined();

    globalThis.localStorage.setItem("auth_token", "shim-token");
    globalThis.localStorage.setItem("tenant_id", "tenant-shim");

    expect(getSessionSnapshot()).toEqual({
      accessToken: "shim-token",
      refreshToken: null,
      tenantId: "tenant-shim",
    });

    globalThis.localStorage.clear();

    expect(getSessionSnapshot()).toEqual({
      accessToken: null,
      refreshToken: null,
      tenantId: null,
    });
  });
});
