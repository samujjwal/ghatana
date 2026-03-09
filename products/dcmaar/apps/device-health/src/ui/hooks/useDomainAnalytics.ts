import { useQuery } from '@tanstack/react-query';
import browser from 'webextension-polyfill';

export interface DomainStats {
  domain: string;
  visits: number;
  timeSpent: number;
  lastVisit: number;
  firstVisit: number;
}

export type TimeFilter = '1hr' | '1day' | '1week' | '1month';

export const useDomainAnalytics = (timeFilter: TimeFilter = '1day') => {
  return useQuery({
    queryKey: ['domainAnalytics', timeFilter],
    queryFn: async (): Promise<DomainStats[]> => {
      try {
        console.log('[HOOK] useDomainAnalytics - Fetching dcmaar_domain_stats');
        const result = await browser.storage.local.get('dcmaar_domain_stats');
        console.log('[HOOK] useDomainAnalytics - Raw result:', result);
        
        const statsData = result.dcmaar_domain_stats;

        if (!statsData) {
          console.log('[HOOK] useDomainAnalytics - No domain stats found');
          return [];
        }

        const stats = typeof statsData === 'string' ? JSON.parse(statsData) : statsData;
        console.log('[HOOK] useDomainAnalytics - Parsed stats:', stats);
        
        const domains = stats.domains || {};
        console.log('[HOOK] useDomainAnalytics - Domains:', domains);

        // Calculate time filter in milliseconds
        const now = Date.now();
        const timeFilters: Record<TimeFilter, number> = {
          '1hr': 60 * 60 * 1000,
          '1day': 24 * 60 * 60 * 1000,
          '1week': 7 * 24 * 60 * 60 * 1000,
          '1month': 30 * 24 * 60 * 60 * 1000,
        };

        const filterTime = timeFilters[timeFilter];
        const cutoffTime = now - filterTime;

        // Convert to array and filter by time
        const domainStats: DomainStats[] = Object.entries(domains)
          .filter(([_, data]: [string, any]) => data.lastVisit >= cutoffTime)
          .map(([domain, data]: [string, any]) => ({
            domain,
            visits: data.visits || 0,
            timeSpent: data.timeSpent || 0,
            lastVisit: data.lastVisit || 0,
            firstVisit: data.firstVisit || 0,
          }))
          .sort((a, b) => b.visits - a.visits) // Sort by visits descending
          .slice(0, 10); // Top 10

        console.log('[HOOK] useDomainAnalytics - Returning domain stats:', domainStats);
        return domainStats;
      } catch (error) {
        console.error('[HOOK] useDomainAnalytics - Error:', error);
        return [];
      }
    },
    refetchInterval: 10000,
    staleTime: 5000,
  });
};
