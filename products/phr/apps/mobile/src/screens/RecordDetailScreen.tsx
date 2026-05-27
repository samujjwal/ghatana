import React from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';
import type { MobileRecord } from '../types';

interface RecordDetailScreenProps {
  record: MobileRecord;
  onBack: () => void;
}

/**
 * Displays the full detail of a single health record.
 */
export function RecordDetailScreen({ record, onBack }: RecordDetailScreenProps): React.ReactElement {
  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <TouchableOpacity onPress={onBack} style={styles.backButton} accessibilityLabel={t('common.back')}>
        <Text style={styles.backText}>{'← '}{t('common.back')}</Text>
      </TouchableOpacity>

      <Text style={styles.title}>{t('records.detail')}</Text>

      <View style={styles.fieldRow}>
        <Text style={styles.label} accessibilityLabel={t('records.type')}>{t('records.type')}</Text>
        <Text style={styles.value} accessibilityLabel={`${t('records.type')}: ${record.title}`}>{record.title}</Text>
      </View>

      <View style={styles.fieldRow}>
        <Text style={styles.label} accessibilityLabel={t('records.recordId')}>{t('records.recordId')}</Text>
        <Text style={styles.value} accessibilityLabel={`${t('records.recordId')}: ${record.id}`}>{record.id}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle} accessibilityLabel={t('records.summary')}>Summary</Text>
        <Text style={styles.body} accessibilityLabel={t('records.summary')}>{record.summary}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle} accessibilityLabel={t('records.fhirResource')}>{t('records.fhirResource')}</Text>
        <Text style={styles.code} accessibilityLabel={`${t('records.fhirResource')}: ${record.fhirPreview}`}>{record.fhirPreview}</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f8ff' },
  content: { padding: 20, gap: 16 },
  backButton: { paddingVertical: 8 },
  backText: { color: '#123c84', fontSize: 16 },
  title: { fontSize: 22, fontWeight: '700', color: '#0b1b35', marginBottom: 12 },
  fieldRow: { flexDirection: 'row', gap: 8, alignItems: 'flex-start' },
  label: { width: 80, color: '#4b5c77', fontWeight: '600' },
  value: { flex: 1, color: '#0b1b35' },
  section: { marginTop: 8, gap: 4 },
  sectionTitle: { fontSize: 14, fontWeight: '700', color: '#4b5c77', textTransform: 'uppercase', letterSpacing: 0.5 },
  body: { color: '#0b1b35', lineHeight: 22 },
  code: { fontFamily: 'monospace', fontSize: 12, color: '#2d4060', backgroundColor: '#e8edf8', padding: 12, borderRadius: 8 },
});
