import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';
import type { MobileDashboard } from '../types';

function newCorrelationId(): string {
  return crypto.randomUUID();
}


interface DashboardScreenProps {
  dashboard: MobileDashboard;
}

export function DashboardScreen({ dashboard }: DashboardScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <Text style={styles.title} accessibilityLabel={`${t('dashboard.patientName')}: ${dashboard.patient.name}`}>
        {dashboard.patient.name}
      </Text>
      <Text style={styles.subtitle} accessibilityLabel={`${t('dashboard.location')}: ${dashboard.patient.district}, ${t('dashboard.bloodType')}: ${dashboard.patient.bloodType}`}>
        {dashboard.patient.district} · {t('dashboard.bloodType')} {dashboard.patient.bloodType}
      </Text>
      <View style={styles.metrics}>
        <View style={styles.metricCard} accessibilityLabel={`${t('dashboard.records')}: ${dashboard.records.length}`}>
          <Text style={styles.metricValue}>{dashboard.records.length}</Text>
          <Text>{t('dashboard.records')}</Text>
        </View>
        <View style={styles.metricCard} accessibilityLabel={`${t('dashboard.consents')}: ${dashboard.consents.length}`}>
          <Text style={styles.metricValue}>{dashboard.consents.length}</Text>
          <Text>{t('dashboard.consents')}</Text>
        </View>
        <View style={styles.metricCard} accessibilityLabel={`${t('dashboard.alerts')}: ${dashboard.notifications.length}`}>
          <Text style={styles.metricValue}>{dashboard.notifications.length}</Text>
          <Text>{t('dashboard.alerts')}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 26, fontWeight: '700', color: '#0b1b35' },
  subtitle: { color: '#4b5c77' },
  metrics: { flexDirection: 'row', gap: 10, flexWrap: 'wrap' },
  metricCard: { backgroundColor: '#edf4ff', padding: 14, borderRadius: 16, minWidth: 100 },
  metricValue: { fontSize: 28, fontWeight: '700', color: '#123c84' },
});