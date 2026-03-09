import { useQuery } from '@tanstack/react-query';
import { auditClient } from '../services/mockClient';

export const AUDIT_QUERY_KEY = ['audit', 'entries'] as const;

export const useAuditLog = () =>
  useQuery({
    queryKey: AUDIT_QUERY_KEY,
    queryFn: auditClient.fetchAudit,
    staleTime: 30_000,
  });
