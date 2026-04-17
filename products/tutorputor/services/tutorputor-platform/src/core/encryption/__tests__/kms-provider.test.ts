/**
 * KMS Provider Tests
 *
 * @doc.type tests
 * @doc.purpose Unit tests for KMS providers
 * @doc.layer platform
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  EnvironmentKMSProvider,
  VaultKMSProvider,
  createKMSProvider,
} from "../kms-provider";

// Mock global fetch for Vault tests
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe("EnvironmentKMSProvider", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    vi.resetModules();
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
    vi.restoreAllMocks();
  });

  it("uses FIELD_ENCRYPTION_KEY when available", () => {
    process.env.FIELD_ENCRYPTION_KEY = "a".repeat(64); // 32 bytes in hex

    const provider = new EnvironmentKMSProvider("test-key");
    expect(provider.getKeyId()).toBe("env://test-key");
  });

  it("throws error for invalid key length", () => {
    process.env.FIELD_ENCRYPTION_KEY = "short-key";

    expect(() => new EnvironmentKMSProvider()).toThrow(
      "64 hex characters",
    );
  });

  it("generates and decrypts data key successfully", async () => {
    process.env.FIELD_ENCRYPTION_KEY = "a".repeat(64);

    const provider = new EnvironmentKMSProvider();
    const { plaintextKey, encryptedKey } = await provider.generateDataKey();

    expect(plaintextKey).toHaveLength(32);
    expect(encryptedKey.length).toBeGreaterThan(32);

    const decrypted = await provider.decryptDataKey(encryptedKey);
    expect(decrypted.equals(plaintextKey)).toBe(true);
  });

  it("produces different keys on each generation", async () => {
    process.env.FIELD_ENCRYPTION_KEY = "a".repeat(64);

    const provider = new EnvironmentKMSProvider();
    const key1 = await provider.generateDataKey();
    const key2 = await provider.generateDataKey();

    expect(key1.plaintextKey.equals(key2.plaintextKey)).toBe(false);
  });

  it("passes health check", async () => {
    process.env.FIELD_ENCRYPTION_KEY = "a".repeat(64);

    const provider = new EnvironmentKMSProvider();
    const healthy = await provider.healthCheck();

    expect(healthy).toBe(true);
  });

  it("fails health check on error", async () => {
    process.env.FIELD_ENCRYPTION_KEY = "a".repeat(64);

    const provider = new EnvironmentKMSProvider();
    // Corrupt the provider by accessing private member
    (provider as unknown as { masterKey: Buffer }).masterKey = Buffer.alloc(0);

    const healthy = await provider.healthCheck();
    expect(healthy).toBe(false);
  });
});

describe("VaultKMSProvider", () => {
  beforeEach(() => {
    mockFetch.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("constructs with correct configuration", () => {
    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "test-token",
      "my-key",
    );
    expect(provider.getKeyId()).toContain("vault");
    expect(provider.getKeyId()).toContain("my-key");
  });

  it("generates data key from Vault", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          data: {
            plaintext: Buffer.from("test-key-32-bytes-long!!!").toString("base64"),
            ciphertext: "vault-encrypted-key",
          },
        }),
    } as Response);

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "test-token",
      "my-key",
    );

    const result = await provider.generateDataKey();

    expect(mockFetch).toHaveBeenCalledWith(
      "http://vault:8200/v1/transit/datakey/plaintext/my-key",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "X-Vault-Token": "test-token",
        }),
      }),
    );
    expect(result.plaintextKey.toString()).toBe("test-key-32-bytes-long!!!");
  });

  it("decrypts data key with Vault", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          data: {
            plaintext: Buffer.from("decrypted-key").toString("base64"),
          },
        }),
    } as Response);

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "test-token",
      "my-key",
    );

    const encryptedKey = Buffer.from("encrypted-data");
    const result = await provider.decryptDataKey(encryptedKey);

    expect(mockFetch).toHaveBeenCalledWith(
      "http://vault:8200/v1/transit/decrypt/my-key",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining(encryptedKey.toString("base64")),
      }),
    );
    expect(result.toString()).toBe("decrypted-key");
  });

  it("handles Vault API errors", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 403,
      text: () => Promise.resolve("permission denied"),
    } as Response);

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "bad-token",
      "my-key",
    );

    await expect(provider.generateDataKey()).rejects.toThrow("403");
  });

  it("handles network errors", async () => {
    mockFetch.mockRejectedValueOnce(new Error("Network error"));

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "token",
      "key",
    );

    await expect(provider.generateDataKey()).rejects.toThrow("Network error");
  });

  it("passes health check when Vault is active", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
    } as Response);

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "token",
      "key",
    );

    const healthy = await provider.healthCheck();
    expect(healthy).toBe(true);
  });

  it("passes health check for standby Vault (429)", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 429,
    } as Response);

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "token",
      "key",
    );

    const healthy = await provider.healthCheck();
    expect(healthy).toBe(true);
  });

  it("fails health check on network error", async () => {
    mockFetch.mockRejectedValueOnce(new Error("Connection refused"));

    const provider = new VaultKMSProvider(
      "http://vault:8200",
      "token",
      "key",
    );

    const healthy = await provider.healthCheck();
    expect(healthy).toBe(false);
  });

  it("removes trailing slash from Vault address", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          data: {
            plaintext: Buffer.from("key").toString("base64"),
            ciphertext: "encrypted",
          },
        }),
    } as Response);

    const provider = new VaultKMSProvider(
      "http://vault:8200/",
      "token",
      "key",
    );

    await provider.generateDataKey();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.not.stringContaining("8200//"),
      expect.any(Object),
    );
  });
});

describe("createKMSProvider", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    vi.resetModules();
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  it("creates AWS provider when KMS_TYPE=aws", () => {
    process.env.KMS_TYPE = "aws";
    process.env.AWS_KMS_KEY_ID = "arn:aws:kms:region:account:key/123";
    process.env.AWS_REGION = "us-west-2";

    // Should not throw
    const provider = createKMSProvider();
    expect(provider.getKeyId()).toBe("arn:aws:kms:region:account:key/123");
  });

  it("throws error for AWS provider without key ID", () => {
    process.env.KMS_TYPE = "aws";
    delete process.env.AWS_KMS_KEY_ID;

    expect(() => createKMSProvider()).toThrow("AWS_KMS_KEY_ID is required");
  });

  it("creates Vault provider when KMS_TYPE=vault", () => {
    process.env.KMS_TYPE = "vault";
    process.env.VAULT_ADDR = "http://vault:8200";
    process.env.VAULT_TOKEN = "test-token";
    process.env.VAULT_TRANSIT_KEY = "my-key";

    const provider = createKMSProvider();
    expect(provider.getKeyId()).toContain("vault");
    expect(provider.getKeyId()).toContain("my-key");
  });

  it("throws error for Vault provider without address", () => {
    process.env.KMS_TYPE = "vault";
    delete process.env.VAULT_ADDR;
    process.env.VAULT_TOKEN = "token";

    expect(() => createKMSProvider()).toThrow("VAULT_ADDR and VAULT_TOKEN");
  });

  it("creates environment provider as fallback", () => {
    process.env.KMS_TYPE = undefined;
    process.env.FIELD_ENCRYPTION_KEY = "a".repeat(64);

    const provider = createKMSProvider();
    expect(provider.getKeyId()).toContain("env");
  });

  it("uses default Vault key name when not specified", () => {
    process.env.KMS_TYPE = "vault";
    process.env.VAULT_ADDR = "http://vault:8200";
    process.env.VAULT_TOKEN = "token";

    const provider = createKMSProvider();
    expect(provider.getKeyId()).toContain("tutorputor");
  });
});
