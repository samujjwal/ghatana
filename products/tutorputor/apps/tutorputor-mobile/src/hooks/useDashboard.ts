/**
 * useDashboard Hook
 *
 * Fetches dashboard data including enrollments, recommendations, and stats.
 *
 * @doc.type hook
 * @doc.purpose Dashboard data fetching
 * @doc.layer product
 * @doc.pattern React Query Hook
 */

import { useQuery } from '@tanstack/react-query';
import { getSessionSnapshot } from '../storage/NativeSessionStorage';

interface UserInfo {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
}

interface Enrollment {
  id: string;
  moduleId: string;
  status: 'active' | 'completed' | 'paused' | 'expired';
  progress: number;
  progressPercent: number;
  lastAccessedAt?: string;
  timeSpentSeconds: number;
  moduleTitle?: string;
  moduleDescription?: string;
}

interface RecommendedModule {
  id: string;
  title: string;
  slug: string;
  description?: string;
  tags: string[];
  estimatedMinutes?: number;
  difficulty?: string;
  domain?: string;
}

interface DashboardStats {
  totalEnrollments: number;
  completedModules: number;
  averageProgress: number;
}

interface DashboardData {
  user: UserInfo;
  currentEnrollments: Enrollment[];
  recommendedModules: RecommendedModule[];
  stats: DashboardStats;
}

export async function fetchDashboard(): Promise<DashboardData> {
  const session = getSessionSnapshot();
  const token = session.accessToken;

  if (!token || !session.tenantId) {
    throw new Error('Authenticated tenant session required to fetch dashboard');
  }

  const response = await fetch('/api/v1/learning/dashboard', {
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-Tenant-ID': session.tenantId,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch dashboard: ${response.statusText}`);
  }

  return response.json();
}

export function useDashboard() {
  return useQuery<DashboardData, Error>({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}
