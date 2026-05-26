/**
 * Ambient type declarations for third-party modules whose @types packages
 * are not yet installed in the workspace. These mirror the minimal surface
 * used by the PHR mobile app.
 *
 * Remove an entry once the real typings are available via node_modules.
 */

declare module '@react-native-community/netinfo' {
  export interface NetInfoState {
    isConnected: boolean | null;
    isInternetReachable: boolean | null;
    type: string;
    details: Record<string, unknown> | null;
  }

  type NetInfoChangeHandler = (state: NetInfoState) => void;

  interface NetInfoStatic {
    addEventListener(listener: NetInfoChangeHandler): () => void;
    fetch(): Promise<NetInfoState>;
  }

  const NetInfo: NetInfoStatic;
  export default NetInfo;
}

declare module 'expo-secure-store' {
  export interface SecureStoreOptions {
    keychainAccessible?: string;
  }

  export const AFTER_FIRST_UNLOCK: string;
  export const WHEN_UNLOCKED: string;

  export function setItemAsync(
    key: string,
    value: string,
    options?: SecureStoreOptions,
  ): Promise<void>;

  export function getItemAsync(
    key: string,
    options?: SecureStoreOptions,
  ): Promise<string | null>;

  export function deleteItemAsync(
    key: string,
    options?: SecureStoreOptions,
  ): Promise<void>;
}
