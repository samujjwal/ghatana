import { useQuery } from '@tanstack/react-query';
import browser from 'webextension-polyfill';

export interface Activity {
  id: string;
  type: string;
  timestamp: string;
  source: string;
  details?: string;
  timeAgo: string;
}

function formatDistanceToNow(date: Date): string {
  try {
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (diff < 60) return 'just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  } catch {
    return 'unknown';
  }
}

export const useRecentActivity = (limit: number = 5) => {
  return useQuery({
    queryKey: ['recentActivity', limit],
    queryFn: async (): Promise<Activity[]> => {
      try {
        const result = await browser.storage.local.get('dcmaar_extension_events');
        const dataStr = result.dcmaar_extension_events;

        if (!dataStr) return [];

        const data = typeof dataStr === 'string' ? JSON.parse(dataStr) : dataStr;
        const events = Array.isArray(data.events) ? data.events : [];

        return events
          .slice(-limit)
          .reverse()
          .map((event: any, idx: number) => ({
            id: event.id || String(idx),
            type: event.type || 'unknown',
            timestamp: event.ingestedAt || event.timestamp || new Date().toISOString(),
            source: event.source || 'extension',
            details: event.message || event.details,
            timeAgo: formatDistanceToNow(new Date(event.ingestedAt || event.timestamp)),
          }));
      } catch (error) {
        console.error('Failed to fetch recent activity:', error);
        return [];
      }
    },
    refetchInterval: 5000,
  });
};
