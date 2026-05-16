/**
 * studioRuntimeContext - resolves and validates the authenticated runtime
 * identity required for Kernel lifecycle mutations.
 *
 * @doc.type module
 * @doc.purpose Provide a typed, fail-closed auth/scope context for Studio
 * @doc.layer platform
 * @doc.pattern Provider
 */

export interface StudioRuntimeIdentity {
  readonly baseUrl: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly authToken: string;
  readonly userId: string;
}

export type StudioRuntimeContextState =
  | { readonly status: 'configured'; readonly identity: StudioRuntimeIdentity }
  | { readonly status: 'unconfigured'; readonly missingFields: readonly string[] };

const REQUIRED_ENV_FIELDS = [
  'VITE_GHATANA_KERNEL_API_BASE_URL',
  'VITE_STUDIO_TENANT_ID',
  'VITE_STUDIO_WORKSPACE_ID',
  'VITE_STUDIO_PROJECT_ID',
  'VITE_STUDIO_AUTH_TOKEN',
  'VITE_STUDIO_USER_ID',
] as const;

type RequiredEnvField = (typeof REQUIRED_ENV_FIELDS)[number];

/**
 * Resolves the Studio runtime context from environment variables.
 * Returns a typed discriminated union so callers handle both states.
 */
export function resolveStudioRuntimeContext(
  env: Readonly<Record<string, unknown>> | undefined =
    (import.meta as ImportMeta & { readonly env?: Record<string, unknown> }).env,
): StudioRuntimeContextState {
  const missing: string[] = [];

  function read(field: RequiredEnvField): string | undefined {
    const value = env?.[field];
    if (typeof value !== 'string' || value.trim().length === 0) {
      missing.push(field);
      return undefined;
    }
    return value.trim();
  }

  const baseUrl = read('VITE_GHATANA_KERNEL_API_BASE_URL');
  const tenantId = read('VITE_STUDIO_TENANT_ID');
  const workspaceId = read('VITE_STUDIO_WORKSPACE_ID');
  const projectId = read('VITE_STUDIO_PROJECT_ID');
  const authToken = read('VITE_STUDIO_AUTH_TOKEN');
  const userId = read('VITE_STUDIO_USER_ID');

  if (missing.length > 0 || !baseUrl || !tenantId || !workspaceId || !projectId || !authToken || !userId) {
    return { status: 'unconfigured', missingFields: missing };
  }

  return {
    status: 'configured',
    identity: { baseUrl, tenantId, workspaceId, projectId, authToken, userId },
  };
}

/**
 * Returns the user ID from a configured runtime context, or `undefined` when
 * context is not yet configured. Use this to resolve the approver identity in
 * approval decisions — never fall back to a hardcoded string.
 */
export function resolveStudioUserId(context: StudioRuntimeContextState): string | undefined {
  if (context.status === 'configured') {
    return context.identity.userId;
  }
  return undefined;
}
