import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { MobileDashboard } from '../types';

interface DashboardScreenProps {
  dashboard: MobileDashboard;
}

export function DashboardScreen({ dashboard }: DashboardScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{dashboard.patient.name}</Text>
      <Text style={styles.subtitle}>{dashboard.patient.district} · Blood group {dashboard.patient.bloodType}</Text>
      <View style={styles.metrics}>
        <View style={styles.metricCard}><Text style={styles.metricValue}>{dashboard.records.length}</Text><Text>Records</Text></View>
        <View style={styles.metricCard}><Text style={styles.metricValue}>{dashboard.consents.length}</Text><Text>Consents</Text></View>
        <View style={styles.metricCard}><Text style={styles.metricValue}>{dashboard.notifications.length}</Text><Text>Alerts</Text></View>
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