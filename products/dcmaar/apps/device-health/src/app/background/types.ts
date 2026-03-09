/**
 * @file Background script type definitions
 * Contains interfaces and types used by the background script
 */

export interface AddonManifest {
  id: string;
  name: string;
  version: string;
  entry: string;
  capabilities?: string[];
}

export interface StoredAddon {
  manifest: AddonManifest;
  files: Record<string, string>;
  verified: boolean;
  enabled: boolean;
}

export interface AddonInstallMessage {
  type: 'DCMAAR_INSTALL_ADDON';
  manifest: AddonManifest;
  files: Record<string, string>;
  signature?: string;
  keyId?: string;
}

export interface EnableAddonMessage {
  type: 'DCMAAR_ENABLE_ADDON';
  id: string;
}

export interface DisableAddonMessage {
  type: 'DCMAAR_DISABLE_ADDON';
  id: string;
}

export type BackgroundMessage =
  | { type: 'DCMAAR_GET_ENABLED_ADDONS' }
  | AddonInstallMessage
  | EnableAddonMessage
  | DisableAddonMessage
  | { type: 'DCMAAR_ADMIN_UNLOCK'; secret: string };

export interface BackgroundResponse<T = unknown> {
  ok: boolean;
  data?: T;
  error?: string;
}

// Admin session management
declare global {
  // This is used to track admin session state in memory
  // eslint-disable-next-line no-var
  var __adminSession: boolean | undefined;
}

/**
 * Checks if there's an active admin session
 * @returns boolean indicating if admin is authenticated
 */
export function isAdminSession(): boolean {
  return !!globalThis.__adminSession;
}

/**
 * Sets the admin session state
 * @param value - The session state to set
 */
export function setAdminSession(value: boolean): void {
  globalThis.__adminSession = value;
}
