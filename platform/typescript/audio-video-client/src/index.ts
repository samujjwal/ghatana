/**
 * @ghatana/audio-video-client
 *
 * HTTP service clients for the Ghatana Audio-Video platform.
 *
 * All clients are fetch-based, ESM-compatible, and work in both browsers and
 * modern Node.js environments that provide the global `fetch` API.
 */

// Low-level HTTP base
export { BaseHttpClient } from './BaseHttpClient.js';
export type { ClientConfig } from './BaseHttpClient.js';

// Service-specific clients
export { SttClient } from './SttClient.js';
export { TtsClient } from './TtsClient.js';
export { VisionClient } from './VisionClient.js';

// Unified client (preferred entry-point)
export { AudioVideoClient } from './AudioVideoClient.js';
