import AsyncStorage from '@react-native-async-storage/async-storage';
import type { MobileDashboard } from '../types';

const DASHBOARD_KEY = 'phr-mobile-dashboard';

export async function saveDashboardOffline(dashboard: MobileDashboard): Promise<void> {
  await AsyncStorage.setItem(DASHBOARD_KEY, JSON.stringify(dashboard));
}

export async function loadDashboardOffline(): Promise<MobileDashboard | null> {
  const raw = await AsyncStorage.getItem(DASHBOARD_KEY);
  return raw ? (JSON.parse(raw) as MobileDashboard) : null;
}