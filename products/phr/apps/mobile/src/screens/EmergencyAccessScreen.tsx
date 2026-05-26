import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';

interface EmergencyAccessScreenProps {
  onAuthenticate: () => Promise<boolean>;
}

type EmergencyState = 'locked' | 'verifying' | 'authorized' | 'denied';

export function EmergencyAccessScreen({ onAuthenticate }: EmergencyAccessScreenProps): React.ReactElement {
  const [state, setState] = React.useState<EmergencyState>('locked');
  const [reason, setReason] = React.useState('');

  const handleVerify = async (): Promise<void> => {
    setState('verifying');
    const granted = await onAuthenticate();
    setState(granted ? 'authorized' : 'denied');
  };

  if (state === 'verifying') {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color="#7f1d1d" />
        <Text style={styles.summary}>{t('emergency.requesting')}</Text>
      </View>
    );
  }

  if (state === 'authorized') {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>{t('emergency.authorized')}</Text>
        <Text style={styles.summary}>
          {t('emergency.reasonLabel')}: {reason || t('emergency.reasonPlaceholder')}
        </Text>
        <Text style={styles.auditNote}>
          This session has been logged for audit review. All PHI accessed in this session is recorded.
        </Text>
      </View>
    );
  }

  if (state === 'denied') {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>{t('emergency.denied')}</Text>
        <Text style={styles.summary}>{t('emergency.error')}</Text>
        <Pressable onPress={() => setState('locked')} style={styles.button} accessibilityRole="button">
          <Text style={styles.buttonText}>{t('common.retry')}</Text>
        </Pressable>
      </View>
    );
  }

  // state === 'locked'
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{t('emergency.title')}</Text>
      <Text style={styles.summary}>
        {t('emergency.biometricPrompt')}
      </Text>
      <Text style={styles.label}>{t('emergency.reasonLabel')}</Text>
      <TextInput
        style={styles.reasonInput}
        placeholder={t('emergency.reasonPlaceholder')}
        placeholderTextColor="#9ca3af"
        value={reason}
        onChangeText={setReason}
        multiline
        numberOfLines={3}
        accessibilityLabel={t('emergency.reasonLabel')}
        accessibilityHint={t('emergency.reasonPlaceholder')}
      />
      <Pressable
        onPress={() => { if (reason.trim()) { void handleVerify(); } }}
        style={[styles.button, !reason.trim() && styles.buttonDisabled]}
        accessibilityRole="button"
        accessibilityState={{ disabled: !reason.trim() }}
        accessibilityLabel={t('emergency.requestButton')}
      >
        <Text style={styles.buttonText}>{t('emergency.requestButton')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 14 },
  title: { fontWeight: '700', fontSize: 22, color: '#102243' },
  label: { fontWeight: '600', fontSize: 14, color: '#4b5c77' },
  summary: { color: '#4b5c77' },
  auditNote: { color: '#7f1d1d', fontStyle: 'italic', fontSize: 13 },
  reasonInput: {
    borderWidth: 1,
    borderColor: '#d5dded',
    borderRadius: 8,
    padding: 12,
    minHeight: 72,
    backgroundColor: '#fff',
    fontSize: 14,
    color: '#102243',
    textAlignVertical: 'top',
  },
  button: { backgroundColor: '#7f1d1d', borderRadius: 16, padding: 14, alignItems: 'center' },
  buttonDisabled: { backgroundColor: '#c4a0a0' },
  buttonText: { color: '#fff', fontWeight: '700' },
});