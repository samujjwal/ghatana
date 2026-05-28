import React from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';
import { requestMobileEmergencyAccess } from '../services/phrMobileApi';
import type { MobileEmergencyData, MobileSession } from '../types';

interface EmergencyAccessScreenProps {
  onAuthenticate: () => Promise<boolean>;
  session: MobileSession;
}

type EmergencyState = 'locked' | 'verifying' | 'server_approval' | 'authorized' | 'denied';

export function EmergencyAccessScreen({ onAuthenticate, session }: EmergencyAccessScreenProps): React.ReactElement {
  const [state, setState] = React.useState<EmergencyState>('locked');
  const [patientId, setPatientId] = React.useState('');
  const [reason, setReason] = React.useState('');
  const [emergencyData, setEmergencyData] = React.useState<MobileEmergencyData | null>(null);

  const requestEmergencyAccess = async (): Promise<void> => {
    try {
      const data = await requestMobileEmergencyAccess(patientId, reason, session);
      setEmergencyData(data);
      setState('authorized');
    } catch {
      setState('denied');
    }
  };

  const handleVerify = async (): Promise<void> => {
    setState('verifying');
    const granted = await onAuthenticate();
    
    if (granted) {
      setState('server_approval');
      await requestEmergencyAccess();
    } else {
      setState('denied');
    }
  };

  if (state === 'verifying' || state === 'server_approval') {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color="#7f1d1d" />
        <Text style={styles.summary}>
          {state === 'verifying' ? t('emergency.requesting') : t('emergency.serverApproval')}
        </Text>
      </View>
    );
  }

  if (state === 'authorized' && emergencyData) {
    return (
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.title}>{t('emergency.authorized')}</Text>
        <Text style={styles.summary}>
          {t('emergency.reasonLabel')}: {reason || t('emergency.reasonPlaceholder')}
        </Text>
        <Text style={styles.auditNote}>{t('emergency.auditWarning')}</Text>

        <View style={styles.dataCard}>
          <Text style={styles.dataTitle}>{t('emergency.emergencyData')}</Text>
          
          <View style={styles.dataRow}>
            <Text style={styles.dataLabel}>{t('login.title')}</Text>
            <Text style={styles.dataValue}>{emergencyData.patientName}</Text>
          </View>

          <View style={styles.dataRow}>
            <Text style={styles.dataLabel}>{t('emergency.bloodType')}</Text>
            <Text style={styles.dataValue}>{emergencyData.bloodType}</Text>
          </View>

          <View style={styles.dataSection}>
            <Text style={styles.dataLabel}>{t('emergency.allergies')}</Text>
            {emergencyData.allergies.length > 0 ? (
              emergencyData.allergies.map((allergy, index) => (
                <Text key={index} style={styles.dataValue}>{allergy}</Text>
              ))
            ) : (
              <Text style={styles.dataValue}>{t('emergency.noneReported')}</Text>
            )}
          </View>

          <View style={styles.dataSection}>
            <Text style={styles.dataLabel}>{t('emergency.medications')}</Text>
            {emergencyData.medications.length > 0 ? (
              emergencyData.medications.map((med, index) => (
                <Text key={index} style={styles.dataValue}>{med}</Text>
              ))
            ) : (
              <Text style={styles.dataValue}>{t('emergency.noneReported')}</Text>
            )}
          </View>

          <View style={styles.dataRow}>
            <Text style={styles.dataLabel}>{t('emergency.emergencyContact')}</Text>
            <Text style={styles.dataValue}>{emergencyData.emergencyContact}</Text>
          </View>
        </View>
      </ScrollView>
    );
  }

  if (state === 'denied') {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>{t('emergency.denied')}</Text>
        <Text style={styles.summary}>{t('emergency.error')}</Text>
        <Pressable
          onPress={() => setState('locked')}
          style={styles.button}
          accessibilityRole="button"
          accessibilityLabel={t('common.retry')}
        >
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
      <Text style={styles.label}>{t('emergency.patientIdLabel')}</Text>
      <TextInput
        style={styles.reasonInput}
        placeholder={t('emergency.patientIdPlaceholder')}
        placeholderTextColor="#9ca3af"
        value={patientId}
        onChangeText={setPatientId}
        accessibilityLabel={t('emergency.patientIdLabel')}
        accessibilityHint={t('emergency.patientIdPlaceholder')}
      />
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
        onPress={() => { if (patientId.trim() && reason.trim()) { void handleVerify(); } }}
        style={[styles.button, (!patientId.trim() || !reason.trim()) && styles.buttonDisabled]}
        accessibilityRole="button"
        accessibilityState={{ disabled: !patientId.trim() || !reason.trim() }}
        accessibilityLabel={t('emergency.requestButton')}
      >
        <Text style={styles.buttonText}>{t('emergency.requestButton')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fef2f2', padding: 18, gap: 14 },
  content: { gap: 16 },
  title: { fontWeight: '700', fontSize: 22, color: '#7f1d1d' },
  label: { fontWeight: '600', fontSize: 14, color: '#4b5c77' },
  summary: { color: '#4b5c77' },
  auditNote: { color: '#7f1d1d', fontStyle: 'italic', fontSize: 13, backgroundColor: '#fef2f2', padding: 12, borderRadius: 8, borderWidth: 1, borderColor: '#fecaca' },
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
  dataCard: { backgroundColor: '#fff', borderRadius: 16, padding: 16, borderWidth: 1, borderColor: '#fecaca', gap: 12 },
  dataTitle: { fontWeight: '700', fontSize: 18, color: '#7f1d1d', marginBottom: 8 },
  dataRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 4 },
  dataSection: { gap: 4, paddingVertical: 4 },
  dataLabel: { fontWeight: '600', fontSize: 14, color: '#4b5c77' },
  dataValue: { color: '#0b1b35', fontSize: 15 },
});
