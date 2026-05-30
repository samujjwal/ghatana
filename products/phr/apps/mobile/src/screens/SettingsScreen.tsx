import React from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { t, setLocale, getLocale } from '../i18n/phrMobileI18n';
import { phiClearAll, phiIsBiometricPolicyEnabled } from '../services/phiEncryptedStorage';
import { clearDashboardOffline, getDashboardOfflineTimestamp } from '../services/offlineStore';
import type { MobileSession } from '../types';

function newCorrelationId(): string {
  return crypto.randomUUID();
}


interface SettingsScreenProps {
  onSyncOffline: () => void;
  onLogout: () => void;
  syncMessage: string;
  session: MobileSession;
}

export function SettingsScreen({ onSyncOffline, onLogout, syncMessage, session }: SettingsScreenProps): React.ReactElement {
  const [cacheTimestamp, setCacheTimestamp] = React.useState<number | null>(null);
  const [cacheActionMessage, setCacheActionMessage] = React.useState<string>('');
  const [biometricPolicyState, setBiometricPolicyState] = React.useState<'checking' | 'enabled' | 'disabled' | 'unavailable'>('checking');
  const [currentLocale, setCurrentLocale] = React.useState<string>('en');
  
  React.useEffect(() => {
    let active = true;
    void getDashboardOfflineTimestamp()
      .then((timestamp) => {
        if (active) {
          setCacheTimestamp(timestamp);
        }
      })
      .catch(() => {
        if (active) {
          setCacheTimestamp(null);
        }
      });
    void phiIsBiometricPolicyEnabled()
      .then((biometricEnabled) => {
        if (active) {
          setBiometricPolicyState(biometricEnabled ? 'enabled' : 'disabled');
        }
      })
      .catch(() => {
        if (active) {
          setBiometricPolicyState('unavailable');
        }
      });
    setCurrentLocale(getLocale());
    return () => {
      active = false;
    };
  }, []);

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

  const handleClearCache = (): void => {
    void Promise.all([phiClearAll(), clearDashboardOffline()])
      .then(() => {
        setCacheTimestamp(null);
        setCacheActionMessage(t('settings.cacheCleared'));
      })
      .catch(() => {
        setCacheActionMessage(t('settings.cacheClearFailed'));
      });
  };

  const handleLanguageChange = (newLocale: string): void => {
    void setLocale(newLocale).then(() => {
      setCurrentLocale(newLocale);
    });
  };

  const availableLocales: { code: string; label: string }[] = [
    { code: 'en', label: 'English' },
    { code: 'ne', label: 'नेपाली' },
  ];

  const isCacheStale = cacheTimestamp ? Date.now() - cacheTimestamp > 24 * 60 * 60 * 1000 : true;
  const biometricPolicyLabelByState: Record<typeof biometricPolicyState, string> = {
    checking: t('settings.biometricPolicyChecking'),
    enabled: t('settings.biometricPolicyEnabled'),
    disabled: t('settings.biometricPolicyDisabled'),
    unavailable: t('settings.biometricPolicyUnavailable'),
  };
  const privacyStatus = biometricPolicyLabelByState[biometricPolicyState];

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.sectionTitle}>{t('settings.profile')}</Text>
      <View style={styles.card}>
        <Text style={styles.label}>{t('login.title')}</Text>
        <Text style={styles.value}>{session.name}</Text>
        <Text style={styles.label}>{t('login.nationalIdLabel')}</Text>
        <Text style={styles.value}>{session.principalId}</Text>
        <Text style={styles.label}>{t('settings.languageLabel')}</Text>
        <View style={styles.localeContainer}>
          {availableLocales.map((locale) => (
            <Pressable
              key={locale.code}
              onPress={() => handleLanguageChange(locale.code)}
              style={[
                styles.localeButton,
                currentLocale === locale.code ? styles.localeButtonActive : styles.localeButtonInactive,
              ]}
              accessibilityRole="radio"
              accessibilityState={{ selected: currentLocale === locale.code }}
              accessibilityLabel={locale.label}
            >
              <Text
                style={[
                  styles.localeButtonText,
                  currentLocale === locale.code ? styles.localeButtonTextActive : styles.localeButtonTextInactive,
                ]}
              >
                {locale.label}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      <Text style={styles.sectionTitle}>{t('settings.cacheStatus')}</Text>
      <View style={styles.card}>
        <Text style={styles.label}>{t('settings.lastSync', { time: cacheTimestamp ? new Date(cacheTimestamp).toLocaleString() : t('settings.never') })}</Text>
        <Text style={[styles.value, isCacheStale ? styles.stale : styles.fresh]}>
          {isCacheStale ? t('settings.cacheStale') : t('settings.cacheFresh')}
        </Text>
        <Text style={styles.label}>{t('settings.encryptionStatus')}</Text>
        <Text style={styles.value}>{t('settings.encryptionEnabled')}</Text>
        <Text style={styles.label}>{t('settings.biometricPolicyStatus')}</Text>
        <Text style={styles.value}>{privacyStatus}</Text>
        <Text style={styles.description}>{t('settings.cacheDescription')}</Text>
        <Pressable
          onPress={onSyncOffline}
          style={styles.button}
          accessibilityRole="button"
          accessibilityLabel={t('settings.refreshCache')}
          accessibilityHint={t('settings.refreshCacheHint')}
        >
          <Text style={styles.buttonText}>{t('settings.refreshCache')}</Text>
        </Pressable>
        <Pressable
          onPress={handleClearCache}
          style={styles.clearButton}
          accessibilityRole="button"
          accessibilityLabel={t('settings.clearCache')}
          accessibilityHint={t('settings.clearCacheHint')}
        >
          <Text style={styles.clearButtonText}>{t('settings.clearCache')}</Text>
        </Pressable>
        <Text style={styles.summary}>{syncMessage}</Text>
        {cacheActionMessage ? <Text style={styles.summary} accessibilityLiveRegion="polite">{cacheActionMessage}</Text> : null}
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
  localeContainer: { flexDirection: 'row', gap: 8, marginTop: 4 },
  localeButton: { flex: 1, padding: 12, borderRadius: 8, borderWidth: 1, borderColor: '#d5dded', alignItems: 'center' },
  localeButtonActive: { backgroundColor: '#123c84', borderColor: '#123c84' },
  localeButtonInactive: { backgroundColor: '#fff' },
  localeButtonText: { fontSize: 14, fontWeight: '600' },
  localeButtonTextActive: { color: '#fff' },
  localeButtonTextInactive: { color: '#4b5c77' },
  fresh: { color: '#059669' },
  stale: { color: '#dc2626' },
  description: { color: '#4b5c77', fontSize: 13, marginTop: 4 },
  button: { backgroundColor: '#123c84', borderRadius: 16, padding: 14, alignItems: 'center', marginTop: 8 },
  buttonText: { color: '#fff', fontWeight: '700' },
  clearButton: { backgroundColor: '#fff', borderRadius: 16, padding: 14, alignItems: 'center', marginTop: 8, borderWidth: 1, borderColor: '#7f1d1d' },
  clearButtonText: { color: '#7f1d1d', fontWeight: '700' },
  summary: { color: '#4b5c77', marginTop: 8, fontSize: 13 },
  logoutButton: { backgroundColor: '#7f1d1d', borderRadius: 16, padding: 14, alignItems: 'center', marginTop: 16 },
  logoutButtonText: { color: '#fff', fontWeight: '700' },
});
