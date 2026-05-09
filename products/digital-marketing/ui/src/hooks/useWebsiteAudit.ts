import { useQuery } from '@tanstack/react-query';
import { getLatestWebsiteAudit, type WebsiteAuditReport } from '@/api/website-audit';

export function useLatestWebsiteAudit(workspaceId: string | null): {
  report: WebsiteAuditReport | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const { data, isLoading, isError, error } = useQuery<WebsiteAuditReport, Error>({
    queryKey: ['website-audit', 'latest', workspaceId],
    queryFn: () => getLatestWebsiteAudit(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 60_000,
  });

  return {
    report: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
  };
}
