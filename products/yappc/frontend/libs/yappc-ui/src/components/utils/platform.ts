/**
 * Platform detection utility to help adapt UI for different platforms
 */

/**
 *
 */
export type Platform = 'web' | 'desktop' | 'mobile';

/**
 * Detects the current platform based on environment and user agent
 */
export function detectPlatform(): Platform {
  // Check if running in Tauri
  const isTauri = window.__TAURI__ !== undefined;
  
  // Check if running in Capacitor
  const isCapacitor = window.Capacitor !== undefined;
  
  // Check if running on mobile device
  const isMobileDevice = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
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
 */
export function isDesktop(): boolean {
  return detectPlatform() === 'desktop';
}

/**
 * Returns true if the current platform is mobile (Capacitor or mobile browser)
 */
export function isMobile(): boolean {
  return detectPlatform() === 'mobile';
}

/**
 * Returns true if the current platform is web (browser, not Tauri or Capacitor)
 */
export function isWeb(): boolean {
  return detectPlatform() === 'web';
}

// Add TypeScript declarations for platform-specific globals
declare global {
  /**
   *
   */
  interface Window {
    __TAURI__?: unknown;
    Capacitor?: unknown;
  }
}
