/**
 * Preview Mode Guard Service
 *
 * @doc.type service
 * @doc.purpose Lock dev preview mode out of production builds - requires dev build and explicit feature flag
 * @doc.layer product
 * @doc.pattern Guard Pattern
 */

/**
 * Check if the current build is a dev build
 */
export function isDevBuild(): boolean {
  return import.meta.env.MODE === 'development';
}

/**
 * Check if dev preview mode feature flag is enabled
 */
export function isDevPreviewModeEnabled(): boolean {
  return import.meta.env.VITE_ENABLE_DEV_PREVIEW_MODE === 'true';
}

/**
 * Check if dev preview mode is allowed
 * 
 * Dev preview mode requires:
 * 1. Dev build (MODE=development)
 * 2. Explicit feature flag (VITE_ENABLE_DEV_PREVIEW_MODE=true)
 */
export function isDevPreviewModeAllowed(): boolean {
  return isDevBuild() && isDevPreviewModeEnabled();
}

/**
 * Validate preview mode and throw error if not allowed
 */
export function validatePreviewMode(mode: 'dev' | 'production'): void {
  if (mode === 'dev') {
    if (!isDevBuild()) {
      throw new Error(
        'Dev preview mode is not allowed in production builds. Use production preview mode instead.'
      );
    }
    if (!isDevPreviewModeEnabled()) {
      throw new Error(
        'Dev preview mode requires the VITE_ENABLE_DEV_PREVIEW_MODE feature flag to be set to true.'
      );
    }
  }
}

/**
 * Get the allowed preview mode for the current build
 */
export function getAllowedPreviewMode(): 'dev' | 'production' {
  if (isDevPreviewModeAllowed()) {
    return 'dev';
  }
  return 'production';
}

/**
 * Preview mode validation result
 */
export interface PreviewModeValidationResult {
  readonly allowed: boolean;
  readonly reason?: string;
  readonly allowedMode?: 'dev' | 'production';
}

/**
 * Validate preview mode and return result object
 */
export function checkPreviewModeAllowed(mode: 'dev' | 'production'): PreviewModeValidationResult {
  if (mode === 'production') {
    return { allowed: true, allowedMode: 'production' };
  }

  if (mode === 'dev') {
    if (!isDevBuild()) {
      return {
        allowed: false,
        reason: 'Dev preview mode is not allowed in production builds.',
        allowedMode: 'production',
      };
    }
    if (!isDevPreviewModeEnabled()) {
      return {
        allowed: false,
        reason: 'Dev preview mode requires the VITE_ENABLE_DEV_PREVIEW_MODE feature flag.',
        allowedMode: 'production',
      };
    }
    return { allowed: true, allowedMode: 'dev' };
  }

  return { allowed: false, reason: 'Invalid preview mode.', allowedMode: 'production' };
}
