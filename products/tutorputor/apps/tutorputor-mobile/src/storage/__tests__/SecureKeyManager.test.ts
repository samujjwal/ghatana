/**
 * @doc.type test
 * @doc.purpose Unit tests for SecureKeyManager encryption key lifecycle
 * @doc.layer product
 * @doc.pattern UnitTest
 */

// The manual mock at src/__mocks__/react-native-keychain.ts is loaded via
// moduleNameMapper in jest.config.js.

// Provide globalThis.crypto.getRandomValues
Object.defineProperty(globalThis, "crypto", {
  value: {
    getRandomValues: (arr: Uint8Array) => {
      for (let i = 0; i < arr.length; i++) {
        arr[i] = Math.floor(Math.random() * 256);
      }
      return arr;
    },
  },
  configurable: true,
});

import * as Keychain from "react-native-keychain";
import { deleteMmkvEncryptionKey, getMmkvEncryptionKey } from "../SecureKeyManager";

const mockKeychain = Keychain as typeof Keychain & { __resetStore: () => void };

describe("SecureKeyManager", () => {
  let getGenericPasswordSpy: jest.SpyInstance;
  let setGenericPasswordSpy: jest.SpyInstance;
  let resetGenericPasswordSpy: jest.SpyInstance;

  beforeEach(() => {
    mockKeychain.__resetStore();
    getGenericPasswordSpy = jest.spyOn(Keychain, "getGenericPassword");
    setGenericPasswordSpy = jest.spyOn(Keychain, "setGenericPassword");
    resetGenericPasswordSpy = jest.spyOn(Keychain, "resetGenericPassword");
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("generates and stores a 64-char hex key on first call", async () => {
    const key = await getMmkvEncryptionKey();

    expect(key).toHaveLength(64);
    expect(key).toMatch(/^[0-9a-f]+$/);
    expect(setGenericPasswordSpy).toHaveBeenCalledTimes(1);
  });

  it("returns the same key on subsequent calls without regenerating", async () => {
    const first = await getMmkvEncryptionKey();
    const second = await getMmkvEncryptionKey();

    expect(first).toBe(second);
    // setGenericPassword only called once (during creation, not on retrieval)
    expect(setGenericPasswordSpy).toHaveBeenCalledTimes(1);
    expect(getGenericPasswordSpy).toHaveBeenCalledTimes(2);
  });

  it("generates a fresh key after deletion", async () => {
    const original = await getMmkvEncryptionKey();
    await deleteMmkvEncryptionKey();
    const fresh = await getMmkvEncryptionKey();

    // The keys are random; in an extremely unlikely collision they could match,
    // but probability is ~1 in 2^256 — safe to assert inequality here.
    expect(fresh).not.toBe(original);
    expect(setGenericPasswordSpy).toHaveBeenCalledTimes(2);
  });

  it("calls resetGenericPassword on deletion", async () => {
    await getMmkvEncryptionKey();
    await deleteMmkvEncryptionKey();

    expect(resetGenericPasswordSpy).toHaveBeenCalledTimes(1);
  });
});
