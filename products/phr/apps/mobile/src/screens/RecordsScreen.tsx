import React, { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';
import type { MobileRecord } from '../types';
import { RecordDetailScreen } from './RecordDetailScreen';

interface RecordsScreenProps {
  records: MobileRecord[];
}

export function RecordsScreen({ records }: RecordsScreenProps): React.ReactElement {
  const [selectedRecord, setSelectedRecord] = useState<MobileRecord | null>(null);

  if (selectedRecord) {
    return (
      <RecordDetailScreen
        record={selectedRecord}
        onBack={() => setSelectedRecord(null)}
      />
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      {records.map((record) => (
        <Pressable
          key={record.id}
          style={styles.card}
          onPress={() => setSelectedRecord(record)}
          accessibilityRole="button"
          accessibilityLabel={`${record.title}. ${t('common.tapToView')}`}
        >
          <Text style={styles.title}>{record.title}</Text>
          <Text style={styles.summary}>{record.summary}</Text>
          <Text style={styles.preview}>{record.fhirPreview}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 14, borderWidth: 1, borderColor: '#d5dded' },
  title: { fontWeight: '700', fontSize: 16, color: '#102243' },
  summary: { color: '#4b5c77', marginTop: 6 },
  preview: { color: '#173b7a', marginTop: 10, fontFamily: 'Courier' },
});