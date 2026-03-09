export type TimeRange = 'last1h' | 'last24h' | 'last7d' | 'last30d';

export interface ConnectionStatus {
  isConnected: boolean;
  lastConnectionTime: string;
  uptime: string;
  serverAddress: string;
  latency?: number;
}

export function formatUptime(value?: string | number | Date): string {
  if (!value) {
    return '0s';
  }

  const start = typeof value === 'number' || value instanceof Date ? new Date(value) : new Date(value);
  const now = new Date();
  const diff = Math.max(now.getTime() - start.getTime(), 0);

  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ${seconds % 60}s`;
  }

  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}h ${minutes % 60}m`;
  }

  const days = Math.floor(hours / 24);
  return `${days}d ${hours % 24}h`;
}
