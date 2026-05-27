import React from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';
import { phiClearAll } from '../services/phiEncryptedStorage';
import { clearDashboardOffline, getDashboardOfflineTimestamp } from '../services/offlineStore';
import type { MobileSession } from '../types';

interface SettingsScreenProps {
  onSyncOffline: () => void;
  onLogout: () => void;
  syncMessage: string;
  session: MobileSession;
}

export function SettingsScreen({ onSyncOffline, onLogout, syncMessage, session }: SettingsScreenProps): React.ReactElement {
  const handleLogout = (): void => {
    Alert.alert(
      t('settings.logoutConfirmTitle'),
      t('settings.logoutConfirmMessage'),
      [
        { text: t('settings.logoutConfirmCancel'), style: 'cancel' },
        {
          text: t('settings.logoutConfirmOk'),
          style: 'destructive',
          onPress: () => {
            // Clear encrypted PHI cache on logout
            void Promise.all([phiClearAll(), clearDashboardOffline()]).then(() => {
              onLogout();
            });
          },
        },
      ],
    );
  };

  const cacheTimestamp = getDashboardOfflineTimestamp();
  const isCacheStale = cacheTimestamp ? Date.now() - cacheTimestamp > 24 * 60 * 60 * 1000 : true;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.sectionTitle}>{t('settings.profile')}</Text>
      <View style={styles.card}>
        <Text style={styles.label}>{t('login.title')}</Text>
        <Text style={styles.value}>{session.name}</Text>
        <Text style={styles.label}>{t('login.nationalIdLabel')}</Text>
        <Text style={styles.value}>{session.principalId}</Text>
        <Text style={styles.label}>{t('settings.languageLabel')}</Text>
        <Text style={styles.value}>{session.role}</Text>
      </View>

      <Text style={styles.sectionTitle}>{t('settings.cacheStatus')}</Text>
      <View style={styles.card}>
        <Text style={styles.label}>{t('settings.lastSync', { time: cacheTimestamp ? new Date(cacheTimestamp).toLocaleString() : 'Never' })}</Text>
        <Text style={[styles.value, isCacheStale ? styles.stale : styles.fresh]}>
          {isCacheStale ? t('settings.cacheStale') : t('settings.cacheFresh')}
        </Text>
        <Text style={styles.description}>{t('settings.cacheDescription')}</Text>
        <Pressable onPress={onSyncOffline} style={styles.button}>
          <Text style={styles.buttonText}>{t('settings.refreshCache')}</Text>
        </Pressable>
        <Text style={styles.summary}>{syncMessage}</Text>
      </View>

      <Pressable onPress={handleLogout} style={styles.logoutButton} accessibilityRole="button" accessibilityLabel={t('settings.logoutButton')}>
        <Text style={styles.logoutButtonText}>{t('settings.logoutButton')}</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f3f8ff' },
  content: { padding: 18, gap: 16 },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: '#0b1b35', marginBottom: 8 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 16, borderWidth: 1, borderColor: '#d5dded', gap: 8 },
  label: { color: '#4b5c77', fontWeight: '600', fontSize: 14 },
  value: { color: '#0b1b35', fontSize: 16 },
  fresh: { color: '#059669' },
  stale: { color: '#dc2626' },
  description: { color: '#4b5c77', fontSize: 13, marginTop: 4 },
  button: { backgroundColor: '#123c84', borderRadius: 16, padding: 14, alignItems: 'center', marginTop: 8 },
  buttonText: { color: '#fff', fontWeight: '700' },
  summary: { color: '#4b5c77', marginTop: 8, fontSize: 13 },
  logoutButton: { backgroundColor: '#7f1d1d', borderRadius: 16, padding: 14, alignItems: 'center', marginTop: 16 },
  logoutButtonText: { color: '#fff', fontWeight: '700' },
});