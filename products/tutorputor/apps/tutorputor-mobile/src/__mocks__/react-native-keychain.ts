/**
 * Manual mock for react-native-keychain.
 * Used in Jest tests that run before the native module is installed.
 */

const store = new Map<string, string>();

export const ACCESSIBLE = {
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'WHEN_UNLOCKED_THIS_DEVICE_ONLY' as const,
};

export async function getGenericPassword(
  options: { service: string },
): Promise<{ username: string; password: string } | false> {
  const password = store.get(options.service);
  return password ? { username: 'mmkv-encryption-key', password } : false;
}

export async function setGenericPassword(
  _username: string,
  password: string,
  options: { service: string; accessible?: string },
): Promise<true> {
  store.set(options.service, password);
  return true;
}

export async function resetGenericPassword(options: { service: string }): Promise<true> {
  store.delete(options.service);
  return true;
}

/** Test helper — resets backing store between tests. */
export function __resetStore(): void {
  store.clear();
}
