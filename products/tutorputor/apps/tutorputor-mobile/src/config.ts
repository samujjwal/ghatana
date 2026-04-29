/**
 * Mobile App Configuration
 *
 * Environment-specific configuration for the mobile app.
 * Values are read from environment variables at build time.
 *
 * @doc.type config
 * @doc.purpose Environment configuration
 * @doc.layer product
 */

// API Configuration
export const API_BASE_URL = process.env.TUTORPUTOR_API_URL || process.env.EXPO_PUBLIC_API_URL || 'https://api.tutorputor.com';

// Feature Flags
export const ENABLE_OFFLINE_MODE = process.env.ENABLE_OFFLINE_MODE !== 'false';
export const ENABLE_BACKGROUND_SYNC = process.env.ENABLE_BACKGROUND_SYNC !== 'false';

// Sync Configuration
export const SYNC_MAX_CONCURRENT = parseInt(process.env.SYNC_MAX_CONCURRENT || '3', 10);
export const SYNC_RETRY_DELAY_MS = parseInt(process.env.SYNC_RETRY_DELAY_MS || '1000', 10);

// Debug Configuration
export const ENABLE_DEBUG_LOGGING = process.env.NODE_ENV === 'development';
