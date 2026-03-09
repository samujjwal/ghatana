// Trusted public keys for add-on verification
// Each key has an id and an SPKI PEM string. Rotate keys by adding new entries.
// File-provided default keys (useful for local development and tests).
const FILE_KEYS: { id: string; spkiPem: string }[] = [
  {
    id: 'dev-key-1',
    // Example ECDSA P-256 public key in SPKI PEM format. Replace with your real key(s).
    spkiPem: `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETf8kQKc7Cqk6WQ9mX1qZrF6m7QK+
Yp0x8YVb1jM6x1b8Y0xv1QvL3z1k2a3r6+7M1q9z1G6r2f3h4Y5z0a==
-----END PUBLIC KEY-----`,
  },
  {
    id: 'example-key-1',
    spkiPem: `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEkzwqSnj88QqNC8Ywtl+4cMZPtEHV
BK0AYXiTNqYrSAqvMh/WNisGcSm1YIZMDZKLDAOWhgxetaFEdiofoK1orQ==
-----END PUBLIC KEY-----`,
  },
];

// If the build provides VITE_ADDON_PUBLIC_KEYS (a JSON string of the same
// structure), parse it and merge with file keys. This allows CI to inject
// production keys without committing them to source control.
function loadEnvKeys(): { id: string; spkiPem: string }[] {
  try {
    if (typeof import.meta === 'undefined') return FILE_KEYS;
    const meta = import.meta as unknown as { env?: { VITE_ADDON_PUBLIC_KEYS?: string } };
    const raw =
      meta.env && meta.env.VITE_ADDON_PUBLIC_KEYS ? meta.env.VITE_ADDON_PUBLIC_KEYS : undefined;
    if (!raw) return FILE_KEYS;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return FILE_KEYS;
    // Basic validation
    const valid = parsed.filter((p: unknown) => {
      if (!p || typeof p !== 'object') return false;
      const asObj = p as Record<string, unknown>;
      return typeof asObj.id === 'string' && typeof asObj.spkiPem === 'string';
    }) as { id: string; spkiPem: string }[];
    // Merge with file keys, preferring env keys when ids conflict
    const map = new Map<string, string>();
    for (const k of FILE_KEYS) map.set(k.id, k.spkiPem);
    for (const k of valid) map.set(k.id, k.spkiPem);
    return Array.from(map.entries()).map(([id, spkiPem]) => ({ id, spkiPem }));
  } catch {
    return FILE_KEYS;
  }
}

export const ADDON_PUBLIC_KEYS: { id: string; spkiPem: string }[] = loadEnvKeys();

export default ADDON_PUBLIC_KEYS;
