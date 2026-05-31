import React, { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { t } from "../i18n/phrMobileI18n";
import type {
  MobileOfflineCacheStatus,
  MobileRecord,
  MobileSession,
} from "../types";
import { RecordDetailScreen } from "./RecordDetailScreen";

interface RecordsScreenProps {
  records?: MobileRecord[];
  session?: MobileSession | null;
  offlineCacheStatus?: MobileOfflineCacheStatus;
}

export function RecordsScreen({
  records = [],
  session = null,
  offlineCacheStatus,
}: RecordsScreenProps): React.ReactElement {
  const [selectedRecord, setSelectedRecord] = useState<MobileRecord | null>(
    null,
  );

  if (selectedRecord) {
    return (
      <RecordDetailScreen
        record={selectedRecord}
        onBack={() => setSelectedRecord(null)}
        session={session}
      />
    );
  }

  return (
    <ScrollView
      contentContainerStyle={styles.container}
      accessibilityLabel={t("records.title")}
    >
      {offlineCacheStatus ? (
        <View style={styles.cacheStatus}>
          <Text style={styles.cacheText}>
            {t("dashboard.lastSync", {
              time: offlineCacheStatus.lastSyncAt
                ? new Date(offlineCacheStatus.lastSyncAt).toLocaleString()
                : t("settings.never"),
            })}
          </Text>
          {offlineCacheStatus.isOffline ? (
            <Text
              style={[
                styles.cacheText,
                offlineCacheStatus.isStale ? styles.stale : styles.fresh,
              ]}
            >
              {offlineCacheStatus.isStale
                ? t("offline.cacheStaleWarning")
                : t("offline.cacheAvailable")}
            </Text>
          ) : null}
        </View>
      ) : null}
      {records.map((record) => (
        <Pressable
          key={record.id}
          style={styles.card}
          onPress={() => setSelectedRecord(record)}
          accessibilityRole="button"
          accessibilityLabel={`${record.title}. ${t("common.tapToView")}`}
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
  card: {
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: "#d5dded",
  },
  title: { fontWeight: "700", fontSize: 16, color: "#102243" },
  summary: { color: "#4b5c77", marginTop: 6 },
  preview: { color: "#173b7a", marginTop: 10, fontFamily: "Courier" },
  cacheStatus: {
    backgroundColor: "#fff",
    borderColor: "#d5dded",
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
    gap: 4,
  },
  cacheText: { color: "#4b5c77", fontSize: 13 },
  fresh: { color: "#166534", fontWeight: "700" },
  stale: { color: "#b91c1c", fontWeight: "700" },
});
