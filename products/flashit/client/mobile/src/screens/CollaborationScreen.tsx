/**
 * Collaboration Screen for Flashit Mobile
 * Shared spheres, invitations, and collaborative activity
 *
 * @doc.type screen
 * @doc.purpose Manage sphere sharing and collaboration
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  TextInput,
  Alert,
  RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useApi } from '../contexts/ApiContext';

type CollabTab = 'shared' | 'invitations' | 'activity';

interface SharedSphere {
  id: string;
  name: string;
  type: string;
  ownerName: string;
  memberCount: number;
  role: 'OWNER' | 'EDITOR' | 'VIEWER';
  lastActivity?: string;
}

interface Invitation {
  id: string;
  sphereName: string;
  inviterName: string;
  inviterEmail: string;
  role: string;
  createdAt: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
}

interface ActivityItem {
  id: string;
  message: string;
  userName: string;
  sphereName: string;
  createdAt: string;
  type: 'moment_added' | 'sphere_shared' | 'comment' | 'reaction';
}

export default function CollaborationScreen() {
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<CollabTab>('shared');
  const [refreshing, setRefreshing] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [selectedSphereId, setSelectedSphereId] = useState<string | null>(null);

  const { data: sharedSpheres, isLoading: sharedLoading } = useQuery({
    queryKey: ['collaboration', 'shared'],
    queryFn: async () => {
      // Fetch spheres and filter shared ones
      const spheres = await apiClient.getSpheres();
      const list = Array.isArray(spheres) ? spheres : (spheres as any)?.spheres || [];
      return list
        .filter((s: any) => s.visibility !== 'PRIVATE' || s.accessCount > 0)
        .map((s: any) => ({
          id: s.id,
          name: s.name,
          type: s.type,
          ownerName: 'You',
          memberCount: (s.accessCount || 0) + 1,
          role: 'OWNER' as const,
          lastActivity: s.updatedAt,
        }));
    },
  });

  const { data: invitations, isLoading: invitationsLoading } = useQuery<Invitation[]>({
    queryKey: ['collaboration', 'invitations'],
    queryFn: async () => {
      // Placeholder — replace with actual invitation API
      return [];
    },
  });

  const { data: activity, isLoading: activityLoading } = useQuery<ActivityItem[]>({
    queryKey: ['collaboration', 'activity'],
    queryFn: async () => {
      // Placeholder — replace with actual activity feed API
      return [];
    },
  });

  const onRefresh = async () => {
    setRefreshing(true);
    await queryClient.invalidateQueries({ queryKey: ['collaboration'] });
    setRefreshing(false);
  };

  const handleInvite = () => {
    if (!inviteEmail.trim() || !selectedSphereId) {
      Alert.alert('Missing Info', 'Please select a sphere and enter an email address.');
      return;
    }
    Alert.alert(
      'Invite Sent',
      `Invitation sent to ${inviteEmail} for the selected sphere.`,
      [{ text: 'OK', onPress: () => setInviteEmail('') }]
    );
  };

  const renderTab = (tab: CollabTab, label: string, count?: number) => (
    <TouchableOpacity
      key={tab}
      style={[styles.tab, activeTab === tab && styles.tabActive]}
      onPress={() => setActiveTab(tab)}
      accessibilityRole="tab"
      accessibilityState={{ selected: activeTab === tab }}
    >
      <Text style={[styles.tabText, activeTab === tab && styles.tabTextActive]}>
        {label}
        {count !== undefined && count > 0 && (
          <Text style={styles.badge}> ({count})</Text>
        )}
      </Text>
    </TouchableOpacity>
  );

  const renderSharedSphere = (item: SharedSphere) => (
    <TouchableOpacity key={item.id} style={styles.card}>
      <View style={styles.cardHeader}>
        <Ionicons name="grid" size={20} color="#6366f1" />
        <View style={styles.cardInfo}>
          <Text style={styles.cardTitle}>{item.name}</Text>
          <Text style={styles.cardSubtitle}>
            {item.memberCount} member{item.memberCount !== 1 ? 's' : ''} • {item.role}
          </Text>
        </View>
        <View style={[styles.roleBadge, { backgroundColor: getRoleColor(item.role) }]}>
          <Text style={styles.roleText}>{item.role}</Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  const renderInvitation = (item: Invitation) => (
    <View key={item.id} style={styles.card}>
      <View style={styles.cardHeader}>
        <Ionicons name="mail-outline" size={20} color="#f59e0b" />
        <View style={styles.cardInfo}>
          <Text style={styles.cardTitle}>{item.sphereName}</Text>
          <Text style={styles.cardSubtitle}>
            From {item.inviterName} • {item.role}
          </Text>
        </View>
      </View>
      {item.status === 'PENDING' && (
        <View style={styles.inviteActions}>
          <TouchableOpacity style={styles.acceptBtn} accessibilityLabel="Accept invitation">
            <Ionicons name="checkmark" size={16} color="#ffffff" />
            <Text style={styles.acceptText}>Accept</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.declineBtn} accessibilityLabel="Decline invitation">
            <Ionicons name="close" size={16} color="#ef4444" />
            <Text style={styles.declineText}>Decline</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );

  const renderActivityItem = (item: ActivityItem) => (
    <View key={item.id} style={styles.activityRow}>
      <Ionicons
        name={getActivityIcon(item.type)}
        size={18}
        color="#64748b"
        style={styles.activityIcon}
      />
      <View style={styles.activityContent}>
        <Text style={styles.activityText}>
          <Text style={styles.activityBold}>{item.userName}</Text>{' '}
          {item.message} in{' '}
          <Text style={styles.activityBold}>{item.sphereName}</Text>
        </Text>
        <Text style={styles.activityTime}>
          {new Date(item.createdAt).toLocaleDateString()}
        </Text>
      </View>
    </View>
  );

  const isLoading = activeTab === 'shared' ? sharedLoading : activeTab === 'invitations' ? invitationsLoading : activityLoading;

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      showsVerticalScrollIndicator={false}
    >
      {/* Tabs */}
      <View style={styles.tabRow}>
        {renderTab('shared', 'Shared Spheres', sharedSpheres?.length)}
        {renderTab('invitations', 'Invitations', invitations?.length)}
        {renderTab('activity', 'Activity')}
      </View>

      {isLoading ? (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color="#0ea5e9" />
        </View>
      ) : (
        <>
          {/* Shared Tab */}
          {activeTab === 'shared' && (
            <View style={styles.section}>
              {/* Invite bar */}
              <View style={styles.inviteSection}>
                <Text style={styles.inviteLabel}>Invite a collaborator</Text>
                <View style={styles.inviteRow}>
                  <TextInput
                    style={styles.inviteInput}
                    placeholder="Email address"
                    placeholderTextColor="#94a3b8"
                    value={inviteEmail}
                    onChangeText={setInviteEmail}
                    keyboardType="email-address"
                    autoCapitalize="none"
                    accessibilityLabel="Collaborator email"
                  />
                  <TouchableOpacity style={styles.inviteButton} onPress={handleInvite}>
                    <Ionicons name="send" size={18} color="#ffffff" />
                  </TouchableOpacity>
                </View>
              </View>

              {(sharedSpheres || []).length === 0 ? (
                <View style={styles.emptyState}>
                  <Ionicons name="people-outline" size={48} color="#cbd5e1" />
                  <Text style={styles.emptyTitle}>No shared spheres</Text>
                  <Text style={styles.emptySubtitle}>
                    Share a sphere to collaborate with others
                  </Text>
                </View>
              ) : (
                (sharedSpheres || []).map(renderSharedSphere)
              )}
            </View>
          )}

          {/* Invitations Tab */}
          {activeTab === 'invitations' && (
            <View style={styles.section}>
              {(invitations || []).length === 0 ? (
                <View style={styles.emptyState}>
                  <Ionicons name="mail-outline" size={48} color="#cbd5e1" />
                  <Text style={styles.emptyTitle}>No invitations</Text>
                  <Text style={styles.emptySubtitle}>
                    Invitations to collaborate will appear here
                  </Text>
                </View>
              ) : (
                (invitations || []).map(renderInvitation)
              )}
            </View>
          )}

          {/* Activity Tab */}
          {activeTab === 'activity' && (
            <View style={styles.section}>
              {(activity || []).length === 0 ? (
                <View style={styles.emptyState}>
                  <Ionicons name="pulse-outline" size={48} color="#cbd5e1" />
                  <Text style={styles.emptyTitle}>No activity yet</Text>
                  <Text style={styles.emptySubtitle}>
                    Collaborative activity will show up here
                  </Text>
                </View>
              ) : (
                (activity || []).map(renderActivityItem)
              )}
            </View>
          )}
        </>
      )}
    </ScrollView>
  );
}

function getRoleColor(role: string): string {
  switch (role) {
    case 'OWNER': return '#6366f1';
    case 'EDITOR': return '#0ea5e9';
    case 'VIEWER': return '#94a3b8';
    default: return '#94a3b8';
  }
}

function getActivityIcon(type: string): keyof typeof Ionicons.glyphMap {
  switch (type) {
    case 'moment_added': return 'add-circle-outline';
    case 'sphere_shared': return 'share-outline';
    case 'comment': return 'chatbubble-outline';
    case 'reaction': return 'heart-outline';
    default: return 'ellipse-outline';
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 48,
  },
  tabRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingTop: 16,
    gap: 8,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
  },
  tabActive: {
    backgroundColor: '#e0f2fe',
  },
  tabText: {
    fontSize: 13,
    fontWeight: '500',
    color: '#94a3b8',
  },
  tabTextActive: {
    color: '#0ea5e9',
    fontWeight: '600',
  },
  badge: {
    color: '#0ea5e9',
    fontWeight: '700',
  },
  section: {
    padding: 16,
  },
  inviteSection: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  inviteLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#334155',
    marginBottom: 8,
  },
  inviteRow: {
    flexDirection: 'row',
    gap: 8,
  },
  inviteInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: '#1e293b',
  },
  inviteButton: {
    backgroundColor: '#0ea5e9',
    borderRadius: 8,
    width: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  cardInfo: {
    flex: 1,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1e293b',
  },
  cardSubtitle: {
    fontSize: 12,
    color: '#94a3b8',
    marginTop: 2,
  },
  roleBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  roleText: {
    fontSize: 11,
    color: '#ffffff',
    fontWeight: '600',
  },
  inviteActions: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 12,
  },
  acceptBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#10b981',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  acceptText: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 13,
  },
  declineBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderWidth: 1,
    borderColor: '#fecaca',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  declineText: {
    color: '#ef4444',
    fontWeight: '600',
    fontSize: 13,
  },
  activityRow: {
    flexDirection: 'row',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  activityIcon: {
    marginRight: 10,
    marginTop: 2,
  },
  activityContent: {
    flex: 1,
  },
  activityText: {
    fontSize: 14,
    color: '#334155',
    lineHeight: 20,
  },
  activityBold: {
    fontWeight: '600',
    color: '#1e293b',
  },
  activityTime: {
    fontSize: 12,
    color: '#94a3b8',
    marginTop: 4,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 48,
  },
  emptyTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#334155',
    marginTop: 16,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#94a3b8',
    marginTop: 8,
    textAlign: 'center',
  },
});
