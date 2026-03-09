// Admin configuration for add-on management.
// Replace ADMIN_SECRET with a strong secret in production and keep it out of VCS.
// The ADMIN_SECRET can be provided at build time via Vite env var VITE_ADMIN_SECRET.
// This keeps real secrets out of the repository. If the env var is not provided
// we fall back to a local-development placeholder.
type ViteEnv = { VITE_ADMIN_SECRET?: string } | undefined;
const metaEnv = (
  typeof import.meta !== 'undefined' ? (import.meta as unknown as { env?: ViteEnv }).env : undefined
) as ViteEnv;
const envSecret = metaEnv && metaEnv.VITE_ADMIN_SECRET ? metaEnv.VITE_ADMIN_SECRET : undefined;

export const ADMIN_CONFIG = {
  ADMIN_SECRET: envSecret || 'replace-me-with-a-strong-secret',
};

export default ADMIN_CONFIG;
