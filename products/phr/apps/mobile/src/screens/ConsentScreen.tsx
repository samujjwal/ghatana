import React, { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { revokeConsentGrant } from '../services/phrMobileApi';
import { phiClearAll } from '../services/phiEncryptedStorage';
import { clearDashboardOffline } from '../services/offlineStore';
import { t } from '../i18n/phrMobileI18n';
import type { MobileConsent, MobileSession } from '../types';

function newCorrelationId(): string {
  return crypto.randomUUID();
}


interface ConsentScreenProps {
  consents: MobileConsent[];
  session: MobileSession;
  onConsentRevoked: (grantId: string) => void;
}

export function ConsentScreen({ consents, session, onConsentRevoked }: ConsentScreenProps): React.ReactElement {
  const [revoking, setRevoking] = useState<string | null>(null);

  const handleRevoke = (grantId: string): void => {
    Alert.alert(
      t('consents.revokeConfirm'),
      undefined,
      [
        { text: t('common.cancel'), style: 'cancel' },
        {
          text: t('consents.revoke'),
          style: 'destructive',
          onPress: () => {
            setRevoking(grantId);
            revokeConsentGrant(grantId, session.principalId, session)
              .then(async () => {
                // Clear encrypted PHI cache on consent revocation
                await phiClearAll();
                await clearDashboardOffline();
                onConsentRevoked(grantId);
              })
              .catch((err: unknown) => {
                Alert.alert(
                  t('consents.revokeError'),
                  err instanceof Error ? err.message : t('common.error'),
                );
              })
              .finally(() => {
                setRevoking(null);
              });
          },
        },
      ],
    );
  };

  if (consents.length === 0) {
    return (
      <View style={styles.container}>
        <Text style={styles.empty}>{t('consents.empty')}</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {consents.map((consent) => (
        <View key={consent.id} style={styles.card}>
          <Text style={styles.title}>{consent.grantee}</Text>
          <Text style={styles.summary}>{consent.purpose}</Text>
          <Text style={[styles.badge, consent.active ? styles.badgeActive : styles.badgeInactive]}>
            {consent.active ? t('consents.active') : t('consents.inactive')}
          </Text>
          {consent.active && (
            <Pressable
              onPress={() => handleRevoke(consent.id)}
              disabled={revoking === consent.id}
              style={[styles.revokeButton, revoking === consent.id && styles.revokeButtonDisabled]}
              accessibilityLabel={t('consents.revoke')}
              accessibilityRole="button"
            >
              <Text style={styles.revokeButtonText}>
                {revoking === consent.id ? t('consents.revoking') : t('consents.revoke')}
              </Text>
            </Pressable>
          )}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  empty: { color: '#4b5c77', textAlign: 'center', marginTop: 24 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 14, borderWidth: 1, borderColor: '#d5dded', gap: 6 },
  title: { fontWeight: '700', color: '#102243' },
  summary: { color: '#4b5c77', marginTop: 4 },
  badge: { marginTop: 8, fontWeight: '700', fontSize: 12 },
  badgeActive: { color: '#1a7a4a' },
  badgeInactive: { color: '#6b7280' },
  revokeButton: {
    marginTop: 10,
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 8,
    backgroundColor: '#dc2626',
    alignSelf: 'flex-start',
  },
  revokeButtonDisabled: { backgroundColor: '#9ca3af' },
  revokeButtonText: { color: '#fff', fontWeight: '700', fontSize: 13 },
});
