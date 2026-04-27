/**
 * Quick Actions
 *
 * Secondary actions hidden behind progressive disclosure.
 *
 * @doc.type component
 * @doc.purpose Progressive disclosure for advanced features
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';

interface QuickActionsProps {
  onBrowseModules: () => void;
  onViewEnrollments: () => void;
  onViewAchievements: () => void;
}

export function QuickActions({
  onBrowseModules,
  onViewEnrollments,
  onViewAchievements,
}: QuickActionsProps): React.ReactElement {
  const [expanded, setExpanded] = useState(false);

  return (
    <View style={styles.container}>
      <TouchableOpacity 
        style={styles.header} 
        onPress={() => setExpanded(!expanded)}
        accessibilityLabel={expanded ? 'Collapse more options' : 'Expand more options'}
        accessibilityRole="button"
      >
        <Text style={styles.title}>More Options</Text>
        <Text style={styles.expandIcon}>{expanded ? '▼' : '▶'}</Text>
      </TouchableOpacity>

      {expanded && (
        <View style={styles.actionsGrid}>
          <TouchableOpacity style={styles.actionItem} onPress={onBrowseModules} accessibilityLabel="Browse all modules" accessibilityRole="button">
            <View style={[styles.iconContainer, { backgroundColor: '#EEF2FF' }]}>
              <Text style={styles.icon}>📚</Text>
            </View>
            <Text style={styles.actionLabel}>Browse All</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.actionItem} onPress={onViewEnrollments} accessibilityLabel="View my learning enrollments" accessibilityRole="button">
            <View style={[styles.iconContainer, { backgroundColor: '#ECFDF5' }]}>
              <Text style={styles.icon}>📝</Text>
            </View>
            <Text style={styles.actionLabel}>My Learning</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.actionItem} onPress={onViewAchievements} accessibilityLabel="View my achievements" accessibilityRole="button">
            <View style={[styles.iconContainer, { backgroundColor: '#FEF3C7' }]}>
              <Text style={styles.icon}>🏆</Text>
            </View>
            <Text style={styles.actionLabel}>Achievements</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.actionItem} accessible={false}>
            <View style={[styles.iconContainer, { backgroundColor: '#FCE7F3' }]}>
              <Text style={styles.icon}>💬</Text>
            </View>
            <Text style={styles.actionLabel}>Study Groups</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  title: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6B7280',
  },
  expandIcon: {
    fontSize: 12,
    color: '#6B7280',
  },
  actionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingTop: 16,
    gap: 12,
  },
  actionItem: {
    width: '22%',
    alignItems: 'center',
  },
  iconContainer: {
    width: 48,
    height: 48,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
  },
  icon: {
    fontSize: 24,
  },
  actionLabel: {
    fontSize: 11,
    color: '#4B5563',
    textAlign: 'center',
  },
});
