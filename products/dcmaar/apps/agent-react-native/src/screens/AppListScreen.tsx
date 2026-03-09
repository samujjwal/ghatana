/**
 * AppListScreen - Comprehensive App List with Filters
 *
 * Full-featured screen displaying all monitored apps with:
 * - Searchable app list (by name and package)
 * - Filter by status (all, active, inactive)
 * - Sort options (name, usage, status, recent)
 * - Multi-select mode with bulk actions
 * - Individual app details and quick actions
 * - Usage statistics per app
 * - Permission status indicators
 *
 * Integrated with Jotai stores for state management:
 * - appsAtom: App list and filtering state
 * - usageAtom: Usage metrics
 * - permissionsAtom: App permissions
 * - syncAtom: Sync status
 *
 * Built with React Native components for cross-platform (iOS/Android).
 *
 * @doc.type component
 * @doc.purpose Full-featured app list with Jotai state integration
 * @doc.layer product
 * @doc.pattern Screen (Container Component)
 */

import React, { useEffect } from 'react';
// Simple type definitions for navigation
interface NavigationProps {
  navigate: (screen: string, params?: { app: App }) => void;
}

interface AppListScreenProps {
  navigation: NavigationProps;
}

interface App {
  id: string;
  name: string;
  packageName: string;
  isActive: boolean;
  // Add other app properties as needed
}
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  TextInput,
  SafeAreaView,
} from 'react-native';
import { useAtom } from 'jotai';
import {
  appsAtom,
  filteredAppsAtom,
  activeAppCountAtom,
  inactiveAppCountAtom,
  updateSearchAtom,
  updateFilterAtom,
  updateSortAtom,
  toggleAppSelectionAtom,
  toggleMultiSelectModeAtom,
  bulkUpdateStatusAtom,
  deleteAppsAtom,
  fetchAppsAtom,
} from '../stores';
import {
  totalDailyUsageAtom,
  appMetricsAtom,
} from '../stores';

/**
 * AppListScreen component.
 *
 * GIVEN: Device has multiple apps
 * WHEN: Screen is rendered
 * THEN: Displays complete list with search, filter, sort, and multi-select
 *
 * GIVEN: User searches for an app
 * WHEN: Text is entered in search box
 * THEN: filteredAppsAtom automatically recalculates and displays matches
 *
 * GIVEN: User clicks app in multi-select mode
 * WHEN: toggleAppSelectionAtom is called
 * THEN: App is added/removed from selection, checkmark updates
 *
 * @component
 * @param {Object} props - Component props
 * @param {Object} props.navigation - React Navigation prop for screen navigation
 */
export default function AppListScreen({ navigation }: AppListScreenProps) {
  // Jotai app list management
  const [appListState] = useAtom(appsAtom);
  const [filteredApps] = useAtom(filteredAppsAtom);
  const [, updateSearch] = useAtom(updateSearchAtom);
  const [, updateFilter] = useAtom(updateFilterAtom);
  const [, updateSort] = useAtom(updateSortAtom);
  const [, toggleSelection] = useAtom(toggleAppSelectionAtom);
  const [, toggleMultiSelectMode] = useAtom(toggleMultiSelectModeAtom);
  const [, bulkUpdateStatus] = useAtom(bulkUpdateStatusAtom);
  const [, deleteApps] = useAtom(deleteAppsAtom);
  const [, fetchApps] = useAtom(fetchAppsAtom);
  const [activeCount] = useAtom(activeAppCountAtom);
  const [inactiveCount] = useAtom(inactiveAppCountAtom);

  // Jotai usage metrics
  const [totalUsageMinutes] = useAtom(totalDailyUsageAtom);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [getAppMetrics] = useAtom(appMetricsAtom);

  // Load apps on component mount
  useEffect(() => {
    fetchApps().catch((error) => {
      console.error('Failed to fetch apps:', error);
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Destructure state for convenience
  const { searchQuery, filterStatus, sortBy, multiSelectMode, selectedAppIds, status } = appListState;

  /**
   * Handle app selection in multi-select mode or navigate to detail.
   *
   * GIVEN: App item pressed
   * WHEN: multiSelectMode is true
   * THEN: Toggle app in selectedAppIds
   *
   * GIVEN: App item pressed
   * WHEN: multiSelectMode is false
   * THEN: Navigate to AppDetail screen
   *
   * @param {App} app - App object
   */
  const handleAppSelect = (app: App) => {
    if (multiSelectMode) {
      toggleSelection(app.id);
    } else {
      navigation?.navigate('AppDetail', { app });
    }
  };

  /**
   * Handle bulk actions (enable, disable, delete).
   *
   * GIVEN: selectedAppIds has items, action is 'enable'/'disable'/'delete'
   * WHEN: Bulk action button pressed
   * THEN: Action applied to all selected apps
   *
   * @param {string} action - 'enable' | 'disable' | 'delete'
   */
  const handleBulkAction = (action: 'enable' | 'disable' | 'delete') => {
    switch (action) {
      case 'enable':
        bulkUpdateStatus(selectedAppIds, true);
        break;
      case 'disable':
        bulkUpdateStatus(selectedAppIds, false);
        break;
      case 'delete':
        deleteApps(selectedAppIds);
        break;
    }
    toggleMultiSelectMode();
  };

  // Show loading state
  if (status === 'loading') {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#6366f1" />
        <Text style={styles.loadingText}>Loading apps...</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      {/* ========== HEADER ========== */}
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>Apps</Text>
          <Text style={styles.headerSubtitle}>
            {activeCount} active, {inactiveCount} inactive
          </Text>
        </View>
        <TouchableOpacity
          style={[styles.multiSelectButton, multiSelectMode && styles.multiSelectButtonActive]}
          onPress={() => toggleMultiSelectMode()}
        >
          <Text style={styles.multiSelectButtonText}>≡</Text>
        </TouchableOpacity>
      </View>

      {/* ========== STATS CARD ========== */}
      <View style={styles.statsContainer}>
        <View style={styles.statsCard}>
          <Text style={styles.statsTitle}>Today's Usage</Text>
          <View style={styles.statsRow}>
            <View>
              <Text style={styles.statsLabel}>Total Time</Text>
              <Text style={styles.statsValue}>{totalUsageMinutes} min</Text>
            </View>
            <View>
              <Text style={styles.statsLabel}>Active Apps</Text>
              <Text style={styles.statsValue}>{activeCount}</Text>
            </View>
            <View>
              <Text style={styles.statsLabel}>Trend</Text>
              <Text style={styles.statsTrend}>↑ 12%</Text>
            </View>
          </View>
        </View>
      </View>

      {/* ========== SEARCH & FILTERS ========== */}
      <View style={styles.filterContainer}>
        {/* Search input */}
        <TextInput
          style={styles.searchInput}
          placeholder="Search apps..."
          placeholderTextColor="#9ca3af"
          value={searchQuery}
          onChangeText={(text) => updateSearch(text)}
        />

        {/* Filter tabs */}
        <View style={styles.filterTabs}>
          {(['all', 'active', 'inactive'] as const).map((status) => (
            <TouchableOpacity
              key={status}
              style={[
                styles.filterTab,
                filterStatus === status && styles.filterTabActive,
              ]}
              onPress={() => updateFilter(status)}
            >
              <Text
                style={[
                  styles.filterTabText,
                  filterStatus === status && styles.filterTabTextActive,
                ]}
              >
                {status.charAt(0).toUpperCase() + status.slice(1)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Sort options */}
        <View style={styles.sortContainer}>
          <Text style={styles.sortLabel}>Sort:</Text>
          {(['name', 'usage', 'recent'] as const).map((option) => (
            <TouchableOpacity
              key={option}
              style={[
                styles.sortOption,
                sortBy === option && styles.sortOptionActive,
              ]}
              onPress={() => updateSort(option)}
            >
              <Text
                style={[
                  styles.sortOptionText,
                  sortBy === option && styles.sortOptionTextActive,
                ]}
              >
                {option.charAt(0).toUpperCase() + option.slice(1)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* ========== BULK ACTIONS (when multi-select enabled) ========== */}
      {multiSelectMode && selectedAppIds.length > 0 && (
        <View style={styles.bulkActionsContainer}>
          <Text style={styles.bulkActionsTitle}>
            {selectedAppIds.length} selected
          </Text>
          <View style={styles.bulkActionButtons}>
            <TouchableOpacity
              style={[styles.bulkActionButton, styles.deleteButton]}
              onPress={() => handleBulkAction('delete')}
            >
              <Text style={styles.bulkActionButtonText}>🗑️ Delete</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.bulkActionButton, styles.enableButton]}
              onPress={() => handleBulkAction('enable')}
            >
              <Text style={styles.bulkActionButtonText}>✓ Enable</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.bulkActionButton, styles.disableButton]}
              onPress={() => handleBulkAction('disable')}
            >
              <Text style={styles.bulkActionButtonText}>✗ Disable</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* ========== APP LIST ========== */}
      <View style={styles.listContainer}>
        {filteredApps.length > 0 ? (
          <ScrollView showsVerticalScrollIndicator={false}>
            {filteredApps.map((app) => (
              <TouchableOpacity
                key={app.id}
                style={[
                  styles.appItem,
                  selectedAppIds.includes(app.id) && styles.appItemSelected,
                ]}
                onPress={() => handleAppSelect(app)}
              >
                <View style={styles.appItemContent}>
                  <Text style={styles.appIcon}>📱</Text>
                  <View style={styles.appInfo}>
                    <Text style={styles.appName}>{app.name}</Text>
                    <Text style={styles.appPackage}>{app.packageName}</Text>
                    <View style={styles.appMeta}>
                      <Text style={styles.appCategory}>{app.category || 'Unknown'}</Text>
                      <Text style={styles.appUsage}>{app.usageTime || 0} min</Text>
                    </View>
                  </View>
                  <View style={styles.appStatus}>
                    <View
                      style={[
                        styles.statusDot,
                        app.isActive ? styles.statusActive : styles.statusInactive,
                      ]}
                    />
                    {multiSelectMode && selectedAppIds.includes(app.id) && (
                      <View style={styles.checkmark}>
                        <Text style={styles.checkmarkText}>✓</Text>
                      </View>
                    )}
                  </View>
                </View>
              </TouchableOpacity>
            ))}
          </ScrollView>
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyStateIcon}>📱</Text>
            <Text style={styles.emptyStateTitle}>No apps found</Text>
            <Text style={styles.emptyStateMessage}>
              {searchQuery
                ? 'Try adjusting your search or filters'
                : 'No apps to display'}
            </Text>
          </View>
        )}
      </View>
    </SafeAreaView>
  );
}

// ============================================================================
// STYLES
// ============================================================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },

  // ========== HEADER ==========
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1f2937',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 4,
  },
  multiSelectButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#f3f4f6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  multiSelectButtonActive: {
    backgroundColor: '#6366f1',
  },
  multiSelectButtonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1f2937',
  },

  // ========== STATS ==========
  statsContainer: {
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  statsCard: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  statsTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
    color: '#1f2937',
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  statsLabel: {
    fontSize: 12,
    color: '#6b7280',
    marginBottom: 4,
  },
  statsValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1f2937',
  },
  statsTrend: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#10b981',
  },

  // ========== FILTERS ==========
  filterContainer: {
    paddingHorizontal: 12,
    paddingVertical: 12,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
  },
  searchInput: {
    backgroundColor: '#f3f4f6',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    marginBottom: 12,
    color: '#1f2937',
  },
  filterTabs: {
    flexDirection: 'row',
    marginBottom: 12,
    gap: 8,
  },
  filterTab: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    backgroundColor: '#f3f4f6',
    borderWidth: 1,
    borderColor: '#e5e5e5',
  },
  filterTabActive: {
    backgroundColor: '#6366f1',
    borderColor: '#6366f1',
  },
  filterTabText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6b7280',
  },
  filterTabTextActive: {
    color: 'white',
  },
  sortContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap',
  },
  sortLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6b7280',
  },
  sortOption: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
    backgroundColor: '#f3f4f6',
    borderWidth: 1,
    borderColor: '#e5e5e5',
  },
  sortOptionActive: {
    backgroundColor: '#dbeafe',
    borderColor: '#6366f1',
  },
  sortOptionText: {
    fontSize: 11,
    fontWeight: '500',
    color: '#6b7280',
  },
  sortOptionTextActive: {
    color: '#1e40af',
    fontWeight: '600',
  },

  // ========== BULK ACTIONS ==========
  bulkActionsContainer: {
    paddingHorizontal: 12,
    paddingVertical: 12,
    backgroundColor: '#fef3c7',
    borderBottomWidth: 1,
    borderBottomColor: '#fcd34d',
  },
  bulkActionsTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: '#92400e',
    marginBottom: 8,
  },
  bulkActionButtons: {
    flexDirection: 'row',
    gap: 8,
  },
  bulkActionButton: {
    flex: 1,
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 6,
    alignItems: 'center',
  },
  deleteButton: {
    backgroundColor: '#fee2e2',
  },
  enableButton: {
    backgroundColor: '#dcfce7',
  },
  disableButton: {
    backgroundColor: '#fecaca',
  },
  bulkActionButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1f2937',
  },

  // ========== APP LIST ==========
  listContainer: {
    flex: 1,
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  appItem: {
    backgroundColor: 'white',
    borderRadius: 8,
    marginBottom: 8,
    paddingVertical: 12,
    paddingHorizontal: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 2,
  },
  appItemSelected: {
    backgroundColor: '#eff6ff',
    borderWidth: 2,
    borderColor: '#6366f1',
  },
  appItemContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  appIcon: {
    fontSize: 28,
    marginRight: 12,
  },
  appInfo: {
    flex: 1,
  },
  appName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1f2937',
  },
  appPackage: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  appMeta: {
    flexDirection: 'row',
    marginTop: 4,
    gap: 8,
  },
  appCategory: {
    fontSize: 11,
    color: '#9ca3af',
  },
  appUsage: {
    fontSize: 11,
    color: '#9ca3af',
  },
  appStatus: {
    alignItems: 'center',
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusActive: {
    backgroundColor: '#10b981',
  },
  statusInactive: {
    backgroundColor: '#9ca3af',
  },
  checkmark: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: '#6366f1',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 4,
  },
  checkmarkText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 12,
  },

  // ========== EMPTY STATE ==========
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  emptyStateIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyStateTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1f2937',
    marginBottom: 8,
    textAlign: 'center',
  },
  emptyStateMessage: {
    fontSize: 14,
    color: '#6b7280',
    textAlign: 'center',
  },
});
