import { useQuery } from '@tanstack/react-query';
import browser from 'webextension-polyfill';

export interface SystemResource {
  label: string;
  value: string;
  status: 'normal' | 'warning' | 'critical';
  trend: string;
  percentage?: number;
}

export const useSystemResources = () => {
  return useQuery({
    queryKey: ['systemResources'],
    queryFn: async (): Promise<SystemResource[]> => {
      try {
        // Try to get system information from background script
        console.log('[HOOK] useSystemResources - Sending GET_SYSTEM_INFO message');
        const response = await browser.runtime.sendMessage({
          type: 'GET_SYSTEM_INFO',
        }) as any;

        console.log('[HOOK] useSystemResources - Received response:', response);

        if (response && response.systemInfo) {
          const { cpu, memory, listeners, storage, io, network } = response.systemInfo;
          
          console.log('[HOOK] useSystemResources - Processing systemInfo:', {
            cpu, memory, listeners, storage, io, network
          });
          
          // Format storage bytes to human-readable format
          const formatBytes = (bytes: number): string => {
            if (bytes === 0) return '0 B';
            if (bytes < 1024) return `${bytes} B`;
            if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
            return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
          };

          const formatQuota = (bytes: number): string => {
            if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
            return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
          };
          
          return [
            {
              label: 'CPU Usage',
              value: `${cpu?.percentage || 0}%`,
              status: (cpu?.percentage || 0) > 80 ? 'critical' : (cpu?.percentage || 0) > 60 ? 'warning' : 'normal',
              trend: cpu?.trend || 'Stable',
              percentage: cpu?.percentage || 0,
            },
            {
              label: 'Memory Usage',
              value: `${memory?.used || '0'} MB`,
              status: (memory?.percentage || 0) > 85 ? 'critical' : (memory?.percentage || 0) > 70 ? 'warning' : 'normal',
              trend: `${memory?.percentage || 0}% of ${memory?.total || '768'} MB`,
              percentage: memory?.percentage || 0,
            },
            {
              label: 'Active Listeners',
              value: String(listeners?.count || 0),
              status: (listeners?.leaks || 0) > 0 ? 'warning' : 'normal',
              trend: listeners?.leaks > 0 ? `${listeners.leaks} potential leaks` : 'No leaks detected',
            },
            {
              label: 'Storage Used',
              value: formatBytes(storage?.used || 0),
              status: (storage?.percentage || 0) > 90 ? 'critical' : (storage?.percentage || 0) > 75 ? 'warning' : 'normal',
              trend: `${storage?.percentage || 0}% of ${formatQuota(storage?.quota || 10485760)}`,
              percentage: storage?.percentage || 0,
            },
            {
              label: 'I/O Operations',
              value: `${io?.operations || 0}/s`,
              status: 'normal',
              trend: `Read: ${io?.readRate || '0'} MB/s, Write: ${io?.writeRate || '0'} MB/s`,
            },
            {
              label: 'Network Usage',
              value: `${network?.activeConnections || 0} conn`,
              status: 'normal',
              trend: `↓ ${network?.downloadSpeed || '0'} MB/s, ↑ ${network?.uploadSpeed || '0'} MB/s`,
            },
          ];
        }

        // Fallback: Try to get real browser storage info
        const storageInfo = await browser.storage.local.getBytesInUse();
        const storageQuota = 5 * 1024 * 1024; // 5MB default
        const storageUsedMB = storageInfo ? (storageInfo / (1024 * 1024)).toFixed(1) : '0';
        const storagePercentage = storageInfo ? Math.round((storageInfo / storageQuota) * 100) : 0;

        // Return default values with some real data
        return [
          {
            label: 'CPU Usage',
            value: 'N/A',
            status: 'normal',
            trend: 'Not available',
          },
          {
            label: 'Memory Usage',
            value: 'N/A',
            status: 'normal',
            trend: 'Not available',
          },
          {
            label: 'Active Listeners',
            value: '0',
            status: 'normal',
            trend: 'No leaks detected',
          },
          {
            label: 'Storage Used',
            value: `${storageUsedMB}MB`,
            status: storagePercentage > 90 ? 'critical' : storagePercentage > 75 ? 'warning' : 'normal',
            trend: `${storagePercentage}% of 5MB`,
            percentage: storagePercentage,
          },
          {
            label: 'I/O Operations',
            value: 'N/A',
            status: 'normal',
            trend: 'Not available',
          },
          {
            label: 'Network Usage',
            value: 'N/A',
            status: 'normal',
            trend: 'Not available',
          },
        ];
      } catch (error) {
        console.error('Failed to fetch system resources:', error);
        return [
          {
            label: 'CPU Usage',
            value: 'Error',
            status: 'normal',
            trend: 'Unable to fetch',
          },
          {
            label: 'Memory Usage',
            value: 'Error',
            status: 'normal',
            trend: 'Unable to fetch',
          },
          {
            label: 'Active Listeners',
            value: 'Error',
            status: 'normal',
            trend: 'Unable to fetch',
          },
          {
            label: 'Storage Used',
            value: 'Error',
            status: 'normal',
            trend: 'Unable to fetch',
          },
          {
            label: 'I/O Operations',
            value: 'Error',
            status: 'normal',
            trend: 'Unable to fetch',
          },
          {
            label: 'Network Usage',
            value: 'Error',
            status: 'normal',
            trend: 'Unable to fetch',
          },
        ];
      }
    },
    refetchInterval: 10000, // Refresh every 10 seconds
    staleTime: 5000,
  });
};
