/**
 * Sync Status Bar Component
 *
 * Shows sync status indicator
 *
 * @doc.type component
 * @doc.purpose Sync status indicator
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useOfflineState } from '../hooks/useOffline';

export function SyncStatusBar(): JSX.Element | null {
  const { pendingMutations, syncStatus } = useOfflineState();

  if (syncStatus === 'idle' && pendingMutations === 0) {
    return null;
  }

  const getStatusText = () => {
    if (syncStatus === 'syncing') return '🔄 Syncing...';
    if (syncStatus === 'error') return '❌ Sync failed';
    if (pendingMutations > 0) return `⏳ ${pendingMutations} pending`;
    return '';
  };

  const getStatusColor = () => {
    if (syncStatus === 'syncing') return '#DBEAFE';
    if (syncStatus === 'error') return '#FEE2E2';
    return '#FEF3C7';
  };

  return (
    <View style={[styles.container, { backgroundColor: getStatusColor() }]}>
      <Text style={styles.text}>{getStatusText()}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 4,
    alignItems: 'center',
  },
  text: {
    fontSize: 12,
    fontWeight: '500',
  },
});
