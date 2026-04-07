import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import type { MobileRecord } from '../types';

interface RecordsScreenProps {
  records: MobileRecord[];
}

export function RecordsScreen({ records }: RecordsScreenProps): React.ReactElement {
  return (
    <ScrollView contentContainerStyle={styles.container}>
      {records.map((record) => (
        <View key={record.id} style={styles.card}>
          <Text style={styles.title}>{record.title}</Text>
          <Text style={styles.summary}>{record.summary}</Text>
          <Text style={styles.preview}>{record.fhirPreview}</Text>
        </View>
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