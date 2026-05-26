import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

interface SettingsScreenProps {
  onSyncOffline: () => void;
  onLogout: () => void;
  syncMessage: string;
}

export function SettingsScreen({ onSyncOffline, onLogout, syncMessage }: SettingsScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <Pressable onPress={onSyncOffline} style={styles.button}>
        <Text style={styles.buttonText}>Refresh offline cache</Text>
      </Pressable>
      <Text style={styles.summary}>{syncMessage}</Text>
      <Text style={styles.summary}>This device keeps a scoped local cache for record access during low-connectivity visits.</Text>
      <Pressable onPress={onLogout} style={styles.logoutButton} accessibilityRole="button" accessibilityLabel="Sign out">
        <Text style={styles.logoutButtonText}>Sign out</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 14 },
  button: { backgroundColor: '#123c84', borderRadius: 16, padding: 14, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: '700' },
  summary: { color: '#4b5c77' },
  logoutButton: { backgroundColor: '#7f1d1d', borderRadius: 16, padding: 14, alignItems: 'center', marginTop: 8 },
  logoutButtonText: { color: '#fff', fontWeight: '700' },
});