/**
 * Platform Detection Utilities
 *
 * Platform detection utility to help adapt UI for different platforms
 *
 * @migrated-from @ghatana/yappc-ui/utils/platform
 */

/**
 * Supported platform types
 */
export type Platform = 'web' | 'desktop' | 'mobile';

/**
 * Detects the current platform based on environment and user agent
 *
 * @returns The detected platform
 *
 * @example
 * ```typescript
 * const platform = detectPlatform();
 * if (platform === 'mobile') {
 *   // Show mobile-specific UI
 * }
 * ```
 */
export function detectPlatform(): Platform {
  // Check if running in Tauri (desktop)
  const isTauri = typeof window !== 'undefined' && window.__TAURI__ !== undefined;

  // Check if running in Capacitor (mobile/hybrid)
  const isCapacitor = typeof window !== 'undefined' && window.Capacitor !== undefined;

  // Check if running on mobile device via user agent
  const isMobileDevice =
    typeof navigator !== 'undefined' &&
    /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
      navigator.userAgent
    );

  if (isTauri) {
    return 'desktop';
  } else if (isCapacitor || isMobileDevice) {
    return 'mobile';
  } else {
    return 'web';
  }
}

/**
 * Returns true if the current platform is desktop (Tauri)
 *
 * @example
 * ```typescript
 * if (isDesktop()) {
 *   // Enable desktop-specific features
 * }
 * ```
 */
export function isDesktop(): boolean {
  return detectPlatform() === 'desktop';
}

/**
 * Returns true if the current platform is mobile (Capacitor or mobile browser)
 *
 * @example
 * ```typescript
 * if (isMobile()) {
 *   // Use touch-optimized UI
 * }
 * ```
 */
export function isMobile(): boolean {
  return detectPlatform() === 'mobile';
}

/**
 * Returns true if the current platform is web (browser, not Tauri or Capacitor)
 *
 * @example
 * ```typescript
 * if (isWeb()) {
 *   // Use web-specific features
 * }
 * ```
 */
export function isWeb(): boolean {
  return detectPlatform() === 'web';
}

/**
 * Returns true if the user agent is a mobile device
 */
export function isMobileUserAgent(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
    navigator.userAgent
  );
}

/**
 * Returns true if the device has touch support
 */
export function isTouchDevice(): boolean {
  if (typeof window === 'undefined') return false;
  return (
    'ontouchstart' in window ||
    navigator.maxTouchPoints > 0 ||
    ('msMaxTouchPoints' in navigator && (navigator as unknown as Record<string, number>).msMaxTouchPoints > 0)
  );
}

/**
 * Get user agent string
 */
export function getUserAgent(): string {
  if (typeof navigator === 'undefined') return '';
  return navigator.userAgent;
}

/**
 * Detect operating system
 */
export function getOS(): 'windows' | 'macos' | 'linux' | 'ios' | 'android' | 'unknown' {
  if (typeof navigator === 'undefined') return 'unknown';

  const ua = navigator.userAgent.toLowerCase();

  if (ua.includes('win')) return 'windows';
  if (ua.includes('mac')) return 'macos';
  if (ua.includes('linux')) return 'linux';
  if (ua.includes('iphone') || ua.includes('ipad')) return 'ios';
  if (ua.includes('android')) return 'android';

  return 'unknown';
}

/**
 * Detect browser
 */
export function getBrowser():
  | 'chrome'
  | 'firefox'
  | 'safari'
  | 'edge'
  | 'opera'
  | 'unknown' {
  if (typeof navigator === 'undefined') return 'unknown';

  const ua = navigator.userAgent.toLowerCase();

  if (ua.includes('edg/')) return 'edge';
  if (ua.includes('opr/') || ua.includes('opera')) return 'opera';
  if (ua.includes('chrome')) return 'chrome';
  if (ua.includes('safari') && !ua.includes('chrome')) return 'safari';
  if (ua.includes('firefox')) return 'firefox';

  return 'unknown';
}

// TypeScript declarations for platform-specific globals
declare global {
  interface Window {
    __TAURI__?: unknown;
    Capacitor?: unknown;
  }
}
