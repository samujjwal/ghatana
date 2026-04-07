import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

interface EmergencyAccessScreenProps {
  onAuthenticate: () => void;
}

export function EmergencyAccessScreen({ onAuthenticate }: EmergencyAccessScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Emergency access</Text>
      <Text style={styles.summary}>Biometric confirmation is required before showing high-risk records offline.</Text>
      <Pressable onPress={onAuthenticate} style={styles.button}>
        <Text style={styles.buttonText}>Verify biometrics</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 14 },
  title: { fontWeight: '700', fontSize: 22, color: '#102243' },
  summary: { color: '#4b5c77' },
  button: { backgroundColor: '#7f1d1d', borderRadius: 16, padding: 14, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: '700' },
});