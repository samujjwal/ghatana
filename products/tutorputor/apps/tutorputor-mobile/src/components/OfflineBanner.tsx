/**
 * Offline Banner Component
 *
 * Displays when device is offline
 *
 * @doc.type component
 * @doc.purpose Offline indicator banner
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

interface OfflineBannerProps {
  isOffline: boolean;
}

export function OfflineBanner({ isOffline }: OfflineBannerProps): React.ReactElement | null {
  if (!isOffline) return null;

  return (
    <View style={styles.container}>
      <Text style={styles.text}>⚠️ Offline Mode</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#FEF3C7',
    padding: 8,
    alignItems: 'center',
  },
  text: {
    color: '#92400E',
    fontSize: 14,
    fontWeight: '500',
  },
});
