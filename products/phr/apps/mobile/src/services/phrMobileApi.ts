import { mobileDashboard } from '../data/mockData';
import type { MobileDashboard } from '../types';
import { loadDashboardOffline, saveDashboardOffline } from './offlineStore';

export async function fetchMobileDashboard(): Promise<MobileDashboard> {
  const cached = await loadDashboardOffline();
  if (cached) {
    return cached;
  }

  await saveDashboardOffline(mobileDashboard);
  return mobileDashboard;
}

export async function syncOfflineDashboard(): Promise<string> {
  await saveDashboardOffline(mobileDashboard);
  return 'Offline cache refreshed';
}