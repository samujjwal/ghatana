/**
 * App Usage Store - Jotai Atoms
 *
 * Manages usage metrics and statistics including:
 * - Daily/hourly app usage tracking
 * - Usage trends and analytics
 * - Total usage calculations
 * - Per-app usage statistics
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose App usage metrics and statistics
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { guardianApi } from '../services/guardianApi';
import { authAtom } from './auth.store';

/**
 * Hourly usage data point.
 *
 * @interface HourlyUsage
 * @property {number} hour - Hour of day (0-23)
 * @property {number} minutes - Total minutes of usage in that hour
 * @property {number} appCount - Number of apps used in that hour
 */
export interface HourlyUsage {
  hour: number;
  minutes: number;
  appCount: number;
}

/**
 * Daily usage summary.
 *
 * @interface DailyUsageSummary
 * @property {Date} date - Date of usage summary
 * @property {number} totalMinutes - Total usage minutes for day
 * @property {number} appCount - Number of unique apps used
 * @property {HourlyUsage[]} hourlyBreakdown - Hour-by-hour breakdown
 * @property {number} trendPercentage - Percentage change from previous day
 */
export interface DailyUsageSummary {
  date: Date;
  totalMinutes: number;
  appCount: number;
  hourlyBreakdown: HourlyUsage[];
  trendPercentage: number;
}

/**
 * Per-app usage metrics.
 *
 * @interface AppUsageMetrics
 * @property {string} appId - ID of app
 * @property {string} appName - App display name
 * @property {number} totalMinutes - Cumulative usage minutes
 * @property {number} dailyAverageMinutes - Average daily usage
 * @property {Date} lastUsedDate - Most recent usage date
 * @property {number} usageCount - Number of times opened
 * @property {number} trendPercentage - Usage trend percentage
 */
export interface AppUsageMetrics {
  appId: string;
  appName: string;
  totalMinutes: number;
  dailyAverageMinutes: number;
  lastUsedDate: Date;
  usageCount: number;
  trendPercentage: number;
}

/**
 * Usage store state.
 *
 * @interface UsageState
 * @property {DailyUsageSummary | null} dailyUsage - Today's usage summary
 * @property {HourlyUsage[]} hourlyUsage - Hourly breakdown for current day
 * @property {Record<string, AppUsageMetrics>} appUsageMap - Per-app metrics
 * @property {DailyUsageSummary[]} weeklyTrend - Week's daily summaries
 * @property {'idle' | 'loading' | 'loaded' | 'error'} status - Loading status
 * @property {string | null} error - Error message if load failed
 */
export interface UsageState {
  dailyUsage: DailyUsageSummary | null;
  hourlyUsage: HourlyUsage[];
  appUsageMap: Record<string, AppUsageMetrics>;
  weeklyTrend: DailyUsageSummary[];
  status: 'idle' | 'loading' | 'loaded' | 'error';
  error: string | null;
}

/**
 * Initial usage state.
 *
 * GIVEN: App initialization
 * WHEN: usageAtom is first accessed
 * THEN: Usage starts with empty metrics, loading status
 */
const initialUsageState: UsageState = {
  dailyUsage: null,
  hourlyUsage: [],
  appUsageMap: {},
  weeklyTrend: [],
  status: 'idle',
  error: null,
};

/**
 * Core usage atom.
 *
 * Holds complete usage state including:
 * - Daily usage summary
 * - Hourly breakdowns
 * - Per-app metrics
 * - Usage trends
 * - Loading and error state
 *
 * Usage (in components):
 * ```typescript
 * const [usageState, setUsageState] = useAtom(usageAtom);
 * ```
 */
export const usageAtom = atom<UsageState>(initialUsageState);

/**
 * Derived atom: Total usage today in minutes.
 *
 * GIVEN: usageAtom with dailyUsage
 * WHEN: totalDailyUsageAtom is read
 * THEN: Returns total minutes used today, or 0 if no data
 *
 * Usage (in components):
 * ```typescript
 * const [totalMinutes] = useAtom(totalDailyUsageAtom);
 * // Display "You've used apps for X minutes today"
 * ```
 */
export const totalDailyUsageAtom = atom<number>((get) => {
  const state = get(usageAtom);
  return state.dailyUsage?.totalMinutes ?? 0;
});

/**
 * Derived atom: Average usage per app today.
 *
 * GIVEN: dailyUsage with total minutes and app count
 * WHEN: averageUsagePerAppAtom is read
 * THEN: Returns average minutes per app (total / count), or 0
 *
 * Usage (in components):
 * ```typescript
 * const [avgPerApp] = useAtom(averageUsagePerAppAtom);
 * ```
 */
export const averageUsagePerAppAtom = atom<number>((get) => {
  const state = get(usageAtom);
  if (!state.dailyUsage || state.dailyUsage.appCount === 0) return 0;
  return Math.round(state.dailyUsage.totalMinutes / state.dailyUsage.appCount);
});

/**
 * Derived atom: Top 5 most used apps today.
 *
 * GIVEN: appUsageMap with per-app metrics
 * WHEN: topAppsAtom is read
 * THEN: Returns top 5 apps sorted by usage time (descending)
 *
 * Usage (in components):
 * ```typescript
 * const [topApps] = useAtom(topAppsAtom);
 * // Display top 5 with usage bars
 * ```
 */
export const topAppsAtom = atom<AppUsageMetrics[]>((get) => {
  const state = get(usageAtom);
  return Object.values(state.appUsageMap)
    .sort((a, b) => b.totalMinutes - a.totalMinutes)
    .slice(0, 5);
});

/**
 * Derived atom: Usage trend (percentage change).
 *
 * GIVEN: dailyUsage with trend percentage
 * WHEN: usageTrendAtom is read
 * THEN: Returns trend as percentage (-50 = 50% decrease, +50 = 50% increase)
 *
 * Usage (in components):
 * ```typescript
 * const [trend] = useAtom(usageTrendAtom);
 * // Show up/down arrow with trend percentage
 * ```
 */
export const usageTrendAtom = atom<number>((get) => {
  const state = get(usageAtom);
  return state.dailyUsage?.trendPercentage ?? 0;
});

/**
 * Derived atom: Hourly usage data for chart.
 *
 * GIVEN: hourlyUsage array
 * WHEN: hourlyDataForChartAtom is read
 * THEN: Returns formatted hourly data for chart rendering
 *
 * Usage (in components):
 * ```typescript
 * const [chartData] = useAtom(hourlyDataForChartAtom);
 * // Render bar/line chart with hourly data
 * ```
 */
export const hourlyDataForChartAtom = atom<HourlyUsage[]>((get) => {
  const state = get(usageAtom);
  return state.hourlyUsage;
});

/**
 * Derived atom: Weekly usage trend data.
 *
 * GIVEN: weeklyTrend array with daily summaries
 * WHEN: weeklyTrendDataAtom is read
 * THEN: Returns formatted daily totals for week chart
 *
 * Usage (in components):
 * ```typescript
 * const [weekData] = useAtom(weeklyTrendDataAtom);
 * // Render weekly usage chart (7 bars, one per day)
 * ```
 */
export const weeklyTrendDataAtom = atom<DailyUsageSummary[]>((get) => {
  const state = get(usageAtom);
  return state.weeklyTrend;
});

/**
 * Derived atom: Metrics for specific app.
 *
 * Returns a function that takes appId and returns that app's metrics or null.
 *
 * Usage (in components):
 * ```typescript
 * const [getAppMetrics] = useAtom(appMetricsAtom);
 * const metrics = getAppMetrics('app-id-123');
 * if (metrics) show metrics else show "No data"
 * ```
 */
export const appMetricsAtom = atom<(appId: string) => AppUsageMetrics | null>(
  (get) => {
    const state = get(usageAtom);
    return (appId: string) => {
      return state.appUsageMap[appId] ?? null;
    };
  }
);

/**
 * Action atom: Update daily usage.
 *
 * GIVEN: New daily usage summary
 * WHEN: updateDailyUsageAtom is called
 * THEN: Updates dailyUsage in usageAtom
 *
 * Usage (in services):
 * ```typescript
 * const [, updateDaily] = useAtom(updateDailyUsageAtom);
 * updateDaily(newDailySummary);
 * ```
 *
 * @param {DailyUsageSummary} summary - New daily usage summary
 */
export const updateDailyUsageAtom = atom<null, [DailyUsageSummary], void>(
  null,
  (get, set, summary: DailyUsageSummary) => {
    const state = get(usageAtom);
    set(usageAtom, {
      ...state,
      dailyUsage: summary,
    });
  }
);

/**
 * Action atom: Update hourly usage.
 *
 * GIVEN: New hourly breakdown data
 * WHEN: updateHourlyUsageAtom is called
 * THEN: Updates hourlyUsage array
 *
 * Usage (in services):
 * ```typescript
 * const [, updateHourly] = useAtom(updateHourlyUsageAtom);
 * updateHourly(newHourlyData);
 * ```
 *
 * @param {HourlyUsage[]} hourly - Hourly usage data
 */
export const updateHourlyUsageAtom = atom<null, [HourlyUsage[]], void>(
  null,
  (get, set, hourly: HourlyUsage[]) => {
    const state = get(usageAtom);
    set(usageAtom, {
      ...state,
      hourlyUsage: hourly,
    });
  }
);

/**
 * Action atom: Update per-app usage metrics.
 *
 * GIVEN: App ID and usage metrics
 * WHEN: updateAppUsageAtom is called
 * THEN: Updates or inserts app metrics in appUsageMap
 *
 * GIVEN: appId = "app-1", metrics = {totalMinutes: 45, ...}
 * WHEN: updateAppUsageAtom called
 * THEN: appUsageMap["app-1"] = metrics
 *
 * Usage (in services):
 * ```typescript
 * const [, updateAppUsage] = useAtom(updateAppUsageAtom);
 * updateAppUsage(appId, newMetrics);
 * ```
 *
 * @param {string} appId - App identifier
 * @param {AppUsageMetrics} metrics - App usage metrics
 */
export const updateAppUsageAtom = atom<null, [string, AppUsageMetrics], void>(
  null,
  (get, set, appId: string, metrics: AppUsageMetrics) => {
    const state = get(usageAtom);
    set(usageAtom, {
      ...state,
      appUsageMap: {
        ...state.appUsageMap,
        [appId]: metrics,
      },
    });
  }
);

/**
 * Action atom: Update weekly trend data.
 *
 * GIVEN: Array of daily summaries for past 7 days
 * WHEN: updateWeeklyTrendAtom is called
 * THEN: Updates weeklyTrend array
 *
 * Usage (in services):
 * ```typescript
 * const [, updateWeekly] = useAtom(updateWeeklyTrendAtom);
 * updateWeekly(past7DaysSummaries);
 * ```
 *
 * @param {DailyUsageSummary[]} trend - Weekly summaries
 */
export const updateWeeklyTrendAtom = atom<null, [DailyUsageSummary[]], void>(
  null,
  (get, set, trend: DailyUsageSummary[]) => {
    const state = get(usageAtom);
    set(usageAtom, {
      ...state,
      weeklyTrend: trend,
    });
  }
);

/**
 * Action atom: Load usage data from API.
 *
 * GIVEN: App needs current usage data
 * WHEN: fetchUsageAtom is called
 * THEN: Sets status to loading, fetches from API, updates usageAtom
 *
 * GIVEN: Fetch succeeds
 * WHEN: Promise resolves
 * THEN: All usage data updated, status set to 'loaded'
 *
 * GIVEN: Fetch fails
 * WHEN: Promise rejects
 * THEN: status set to 'error', error message stored
 *
 * Usage (in components):
 * ```typescript
 * const [, fetchUsage] = useAtom(fetchUsageAtom);
 * useEffect(() => { fetchUsage(); }, []);
 * ```
 *
 * @async
 * @returns {Promise<UsageState>} Updated usage state
 * @throws {Error} If fetch fails
 */
export const fetchUsageAtom = atom<null, [], Promise<UsageState>>(
  null,
  async (get, set) => {
    const state = get(usageAtom);
    set(usageAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      // Derive usage metrics from the app list (usageTime in ms, lastSeen as timestamp)
      const { data: appDataList } = await guardianApi.getApps(tenantId);

      const appUsageMap: Record<string, AppUsageMetrics> = {};
      let totalMinutes = 0;
      const uniqueAppsUsedToday = new Set<string>();
      const today = new Date().toDateString();

      for (const app of appDataList) {
        const totalMins = Math.round(app.usageTime / 60_000);
        const lastUsed = app.lastSeen ? new Date(app.lastSeen) : new Date(0);
        const usedToday = lastUsed.toDateString() === today;

        if (usedToday) {
          totalMinutes += totalMins;
          uniqueAppsUsedToday.add(app.id);
        }

        appUsageMap[app.id] = {
          appId: app.id,
          appName: app.name,
          totalMinutes: totalMins,
          dailyAverageMinutes: Math.round(totalMins / 7), // rough approximation
          lastUsedDate: lastUsed,
          usageCount: 1, // backend doesn't expose session count
          trendPercentage: 0, // trend requires historical data
        };
      }

      // Build a sparse 24-hour breakdown from app lastSeen timestamps
      const hourlyMap: Record<number, HourlyUsage> = {};
      for (let h = 0; h < 24; h++) {
        hourlyMap[h] = { hour: h, minutes: 0, appCount: 0 };
      }
      for (const app of appDataList) {
        if (!app.lastSeen) continue;
        const lastUsed = new Date(app.lastSeen);
        if (lastUsed.toDateString() === today) {
          const h = lastUsed.getHours();
          hourlyMap[h].minutes += Math.round(app.usageTime / 60_000);
          hourlyMap[h].appCount += 1;
        }
      }
      const hourlyUsage = Object.values(hourlyMap);

      const dailyUsage: DailyUsageSummary = {
        date: new Date(),
        totalMinutes,
        appCount: uniqueAppsUsedToday.size,
        hourlyBreakdown: hourlyUsage,
        trendPercentage: 0,
      };

      const updatedState: UsageState = {
        dailyUsage,
        hourlyUsage,
        appUsageMap,
        weeklyTrend: state.weeklyTrend, // preserved; updated separately
        status: 'loaded',
        error: null,
      };

      set(usageAtom, updatedState);
      return updatedState;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to fetch usage metrics';
      set(usageAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });
      throw error;
    }
  }
);

/**
 * Action atom: Reset daily statistics.
 *
 * GIVEN: End of day, need to reset for new day
 * WHEN: resetDailyStatsAtom is called
 * THEN: Clears hourly data, resets daily usage, starts fresh for new day
 *
 * Usage (in services - called at midnight):
 * ```typescript
 * const [, resetStats] = useAtom(resetDailyStatsAtom);
 * resetStats(); // Called by scheduler at midnight
 * ```
 */
export const resetDailyStatsAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(usageAtom);
    const emptyHourly: HourlyUsage[] = Array.from({ length: 24 }, (_, i) => ({
      hour: i,
      minutes: 0,
      appCount: 0,
    }));

    set(usageAtom, {
      ...state,
      dailyUsage: {
        date: new Date(),
        totalMinutes: 0,
        appCount: 0,
        hourlyBreakdown: emptyHourly,
        trendPercentage: 0,
      },
      hourlyUsage: emptyHourly,
    });
  }
);

/**
 * Action atom: Clear usage error.
 *
 * GIVEN: Error message is displayed
 * WHEN: User dismisses error
 * THEN: clearUsageErrorAtom clears the error message
 *
 * Usage (in components):
 * ```typescript
 * const [, clearError] = useAtom(clearUsageErrorAtom);
 * <TouchableOpacity onPress={() => clearError()} />
 * ```
 */
export const clearUsageErrorAtom = atom<null, [], void>(null, (get, set) => {
  const state = get(usageAtom);
  set(usageAtom, {
    ...state,
    error: null,
  });
});
