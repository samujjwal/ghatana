/**
 * Policy Management Screen
 *
 * Displays active policies with details, settings, and management options.
 * Allows users to view, edit, and delete security policies.
 *
 * Features:
 * - List all active policies
 * - View policy details and configuration
 * - Edit policy settings
 * - Delete policies
 * - View version history
 * - Create new policies
 *
 * @doc.type component
 * @doc.purpose Policy listing and management interface
 * @doc.layer product
 * @doc.pattern Screen Component
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  FlatList,
} from 'react-native';
import { useAtom } from 'jotai';

import { policyAtom, uiAtom } from '../stores';

interface PolicyItem {
  id: string;
  name: string;
  description: string;
  isActive: boolean;
  rulesCount: number;
  lastModified: string;
  threatLevel: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Policy card component
 *
 * Displays a single policy with summary and actions
 *
 * @param policy - Policy details
 * @param onPress - Card press handler
 * @param onDelete - Delete press handler
 * @returns Policy card JSX
 */
const PolicyCard: React.FC<{
  policy: PolicyItem;
  onPress: () => void;
  onDelete: () => void;
}> = ({ policy, onPress, onDelete }) => (
  <View style={styles.policyCard}>
    <TouchableOpacity style={styles.cardContent} onPress={onPress}>
      <View style={styles.cardHeader}>
        <Text style={styles.policyName}>{policy.name}</Text>
        <View
          style={[
            styles.statusBadge,
            policy.isActive ? styles.statusActive : styles.statusInactive,
          ]}
        >
          <Text style={styles.statusText}>
            {policy.isActive ? '● Active' : '○ Inactive'}
          </Text>
        </View>
      </View>
      <Text style={styles.policyDescription}>{policy.description}</Text>
      <View style={styles.cardFooter}>
        <Text style={styles.metaText}>{policy.rulesCount} rules</Text>
        <Text style={styles.metaText}>
          Modified: {new Date(policy.lastModified).toLocaleDateString()}
        </Text>
      </View>
    </TouchableOpacity>
    <View style={styles.cardActions}>
      <TouchableOpacity
        style={styles.editButton}
        onPress={onPress}
      >
        <Text style={styles.editText}>Edit</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={styles.deleteButton}
        onPress={onDelete}
      >
        <Text style={styles.deleteText}>Delete</Text>
      </TouchableOpacity>
    </View>
  </View>
);

/**
 * PolicyManagementScreen Component
 *
 * Main policies interface showing all active policies with ability to
 * create, edit, delete, and view policy details.
 *
 * @returns Policies screen JSX
 */
export default function PolicyManagementScreen() {
  const [, setPolicies] = useAtom(policyAtom);
  const [, setUIState] = useAtom(uiAtom);
  const [policiesList, setPoliciesList] = useState<PolicyItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  /**
   * Initialize policies on mount
   *
   * GIVEN: Component mounts
   * WHEN: useEffect with empty deps runs
   * THEN: Load current policies from atom
   */
  useEffect(() => {
    loadPolicies();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * Load policies from store
   *
   * GIVEN: Component initializing or refresh requested
   * WHEN: loadPolicies() called
   * THEN: Fetch and display policies
   */
  const loadPolicies = useCallback(async () => {
    try {
      setIsLoading(true);
      // In production, fetch from guardianApi.getPolicies()
      const mockPolicies: PolicyItem[] = [
        {
          id: '1',
          name: 'Camera Monitoring',
          description: 'Monitor unauthorized camera access',
          isActive: true,
          rulesCount: 3,
          lastModified: new Date().toISOString(),
          threatLevel: 'high',
        },
        {
          id: '2',
          name: 'Location Tracking',
          description: 'Detect suspicious location data access',
          isActive: true,
          rulesCount: 2,
          lastModified: new Date(Date.now() - 86400000).toISOString(),
          threatLevel: 'medium',
        },
        {
          id: '3',
          name: 'Contact Access',
          description: 'Monitor contacts data access patterns',
          isActive: false,
          rulesCount: 4,
          lastModified: new Date(Date.now() - 172800000).toISOString(),
          threatLevel: 'low',
        },
      ];
      setPoliciesList(mockPolicies);
    } finally {
      setIsLoading(false);
    }
  }, []);

  /**
   * Handle policy edit
   *
   * GIVEN: User taps edit on a policy
   * WHEN: handleEditPolicy() called with policy
   * THEN: Navigate to policy editor screen
   */
  const handleEditPolicy = useCallback(
    (policy: PolicyItem) => {
      // In production, navigate to policy editor
      Alert.alert(
        'Edit Policy',
        `Edit ${policy.name}?`,
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Edit',
            onPress: () => {
              // navigation.navigate('PolicyEditor', { policy });
              const notificationId = `notif-${Date.now()}`;
              setUIState((prev) => ({
                ...prev,
                notifications: [
                  ...prev.notifications,
                  {
                    id: notificationId,
                    type: 'info',
                    message: 'Policy editor coming soon',
                    duration: 3000,
                    createdAt: new Date(),
                  },
                ],
              }));

              setTimeout(() => {
                setUIState((prev) => ({
                  ...prev,
                  notifications: prev.notifications.filter(
                    (n) => n.id !== notificationId
                  ),
                }));
              }, 3000);
            },
          },
        ]
      );
    },
    [setUIState]
  );

  /**
   * Handle policy delete
   *
   * GIVEN: User confirms policy deletion
   * WHEN: handleDeletePolicy() called
   * THEN: Remove policy from list and atom
   */
  const handleDeletePolicy = useCallback(
    (policyId: string) => {
      const policy = policiesList.find((p) => p.id === policyId);
      if (!policy) return;

      Alert.alert(
        'Delete Policy',
        `Delete "${policy.name}"? This cannot be undone.`,
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Delete',
            style: 'destructive',
            onPress: () => {
              setPoliciesList((prev) => prev.filter((p) => p.id !== policyId));
              setPolicies((prev) => ({
                ...prev,
              }));

              const notificationId = `notif-${Date.now()}`;
              setUIState((prev) => ({
                ...prev,
                notifications: [
                  ...prev.notifications,
                  {
                    id: notificationId,
                    type: 'success',
                    message: 'Policy deleted successfully',
                    duration: 3000,
                    createdAt: new Date(),
                  },
                ],
              }));

              setTimeout(() => {
                setUIState((prev) => ({
                  ...prev,
                  notifications: prev.notifications.filter(
                    (n) => n.id !== notificationId
                  ),
                }));
              }, 3000);
            },
          },
        ]
      );
    },
    [policiesList, setPolicies, setUIState]
  );

  /**
   * Handle create new policy
   *
   * GIVEN: User taps create new policy button
   * WHEN: handleCreatePolicy() called
   * THEN: Navigate to policy creator
   */
  const handleCreatePolicy = useCallback(() => {
    Alert.alert(
      'Create Policy',
      'Create a new security policy',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Create',
          onPress: () => {
            const notificationId = `notif-${Date.now()}`;
            setUIState((prev) => ({
              ...prev,
              notifications: [
                ...prev.notifications,
                {
                  id: notificationId,
                  type: 'info',
                  message: 'Policy creator coming soon',
                  duration: 3000,
                  createdAt: new Date(),
                },
              ],
            }));

            setTimeout(() => {
              setUIState((prev) => ({
                ...prev,
                notifications: prev.notifications.filter(
                  (n) => n.id !== notificationId
                ),
              }));
            }, 3000);
          },
        },
      ]
    );
  }, [setUIState]);

  const activePolicies = policiesList.filter((p) => p.isActive).length;

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#6366f1" />
        <Text style={styles.loadingText}>Loading policies...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>Policies</Text>
          <Text style={styles.headerSubtitle}>
            Manage security policies and rules
          </Text>
        </View>
        <View style={styles.statusBadge}>
          <Text style={styles.statusText}>
            {activePolicies} active
          </Text>
        </View>
      </View>

      {/* Summary */}
      <View style={styles.summary}>
        <View style={styles.summaryItem}>
          <Text style={styles.summaryLabel}>Total Policies</Text>
          <Text style={styles.summaryValue}>{policiesList.length}</Text>
        </View>
        <View style={styles.summaryDivider} />
        <View style={styles.summaryItem}>
          <Text style={styles.summaryLabel}>Total Rules</Text>
          <Text style={styles.summaryValue}>
            {policiesList.reduce((sum, p) => sum + p.rulesCount, 0)}
          </Text>
        </View>
      </View>

      {/* Policies List */}
      <FlatList
        data={policiesList}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <PolicyCard
            policy={item}
            onPress={() => handleEditPolicy(item)}
            onDelete={() => handleDeletePolicy(item.id)}
          />
        )}
        scrollEnabled={false}
        contentContainerStyle={styles.listContainer}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No policies yet</Text>
            <Text style={styles.emptySubtext}>
              Create your first policy to get started
            </Text>
          </View>
        }
      />

      {/* Create Button */}
      <TouchableOpacity
        style={styles.createButton}
        onPress={handleCreatePolicy}
      >
        <Text style={styles.createText}>+ Create Policy</Text>
      </TouchableOpacity>

      {/* Footer */}
      <View style={styles.footer}>
        <TouchableOpacity onPress={loadPolicies}>
          <Text style={styles.refreshLink}>Refresh</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f9fafb',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#6b7280',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1f2937',
    marginBottom: 4,
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#6b7280',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#e0e7ff',
    borderRadius: 6,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6366f1',
  },
  summary: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginVertical: 12,
    paddingHorizontal: 12,
    paddingVertical: 12,
    backgroundColor: '#ffffff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  summaryItem: {
    flex: 1,
    alignItems: 'center',
  },
  summaryDivider: {
    width: 1,
    backgroundColor: '#e5e7eb',
    marginHorizontal: 12,
  },
  summaryLabel: {
    fontSize: 12,
    color: '#6b7280',
    marginBottom: 4,
  },
  summaryValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1f2937',
  },
  listContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  policyCard: {
    marginBottom: 12,
    backgroundColor: '#ffffff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    overflow: 'hidden',
  },
  cardContent: {
    padding: 12,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  policyName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1f2937',
    flex: 1,
  },
  statusActive: {
    backgroundColor: '#d1fae5',
  },
  statusInactive: {
    backgroundColor: '#f3f4f6',
  },
  policyDescription: {
    fontSize: 13,
    color: '#6b7280',
    marginBottom: 8,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#f3f4f6',
  },
  metaText: {
    fontSize: 12,
    color: '#9ca3af',
  },
  cardActions: {
    flexDirection: 'row',
    paddingHorizontal: 12,
    paddingBottom: 12,
    paddingTop: 0,
    gap: 8,
  },
  editButton: {
    flex: 1,
    paddingVertical: 8,
    backgroundColor: '#e0e7ff',
    borderRadius: 6,
    alignItems: 'center',
  },
  editText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6366f1',
  },
  deleteButton: {
    flex: 1,
    paddingVertical: 8,
    backgroundColor: '#fee2e2',
    borderRadius: 6,
    alignItems: 'center',
  },
  deleteText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#dc2626',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  emptyText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#6b7280',
    marginBottom: 4,
  },
  emptySubtext: {
    fontSize: 13,
    color: '#9ca3af',
  },
  createButton: {
    marginHorizontal: 16,
    marginBottom: 12,
    paddingVertical: 12,
    backgroundColor: '#6366f1',
    borderRadius: 8,
  },
  createText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#ffffff',
    textAlign: 'center',
  },
  footer: {
    alignItems: 'center',
    paddingVertical: 16,
    borderTopWidth: 1,
    borderTopColor: '#e5e7eb',
  },
  refreshLink: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6366f1',
  },
});
