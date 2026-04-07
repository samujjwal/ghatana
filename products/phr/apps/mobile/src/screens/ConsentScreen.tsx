import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { MobileConsent } from '../types';

interface ConsentScreenProps {
  consents: MobileConsent[];
}

export function ConsentScreen({ consents }: ConsentScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      {consents.map((consent) => (
        <View key={consent.id} style={styles.card}>
          <Text style={styles.title}>{consent.grantee}</Text>
          <Text style={styles.summary}>{consent.purpose}</Text>
          <Text style={styles.badge}>{consent.active ? 'Active' : 'Inactive'}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 14, borderWidth: 1, borderColor: '#d5dded' },
  title: { fontWeight: '700', color: '#102243' },
  summary: { color: '#4b5c77', marginTop: 6 },
  badge: { marginTop: 10, color: '#215298', fontWeight: '700' },
});