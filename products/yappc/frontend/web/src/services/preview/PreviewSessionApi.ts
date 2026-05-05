import { authService } from '@/services/auth/AuthService';

export interface PreviewSessionContext {
  projectId: string;
  artifactId: string;
}

export interface PreviewSessionIssueResponse {
  sessionId: string;
  sessionToken: string;
  expiresAt: string;
}

function buildHeaders(): HeadersInit {
  const token = authService.getAuthToken();
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function issuePreviewSession(
  context: PreviewSessionContext,
): Promise<PreviewSessionIssueResponse> {
  const response = await fetch('/api/v1/yappc/preview/sessions', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      projectId: context.projectId,
      artifactId: context.artifactId,
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to create preview session (${response.status})`);
  }

  return response.json() as Promise<PreviewSessionIssueResponse>;
}

export async function validatePreviewSessionToken(
  sessionToken: string,
): Promise<{ valid: boolean; reason?: string }> {
  const response = await fetch('/api/v1/yappc/preview/sessions/validate', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({ sessionToken }),
  });

  if (!response.ok) {
    throw new Error(`Failed to validate preview session (${response.status})`);
  }

  return response.json() as Promise<{ valid: boolean; reason?: string }>;
}
