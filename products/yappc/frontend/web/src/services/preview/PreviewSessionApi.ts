import { previewSessions, type PreviewSessionContext, type PreviewSessionIssueResponse, type PreviewSessionValidateResponse } from '@/lib/api/client';

export async function issuePreviewSession(
  context: PreviewSessionContext,
): Promise<PreviewSessionIssueResponse> {
  return previewSessions.issue(context);
}

export async function validatePreviewSessionToken(
  sessionToken: string,
): Promise<PreviewSessionValidateResponse> {
  return previewSessions.validate(sessionToken);
}
