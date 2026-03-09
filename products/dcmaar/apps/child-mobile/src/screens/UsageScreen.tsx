import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useChildUsage } from '@/hooks/useChildUsage';
import { useChildUsageStats } from '@/hooks/useChildUsageStats';
import { formatDuration } from '@/utils/format';

const UsageScreen: React.FC = () => {
    const { data: devices, isLoading: devicesLoading } = useChildUsage();
    const { totalScreenTimeMinutes, deviceCount, isLoading: statsLoading } = useChildUsageStats();

    const isLoading = devicesLoading || statsLoading;

    if (isLoading) {
        return (
            <View style={styles.centered}>
                <Text style={styles.loadingText}>Loading usage...</Text>
            </View>
        );
    }

    return (
        <ScrollView style={styles.container}>
            <View style={styles.section}>
                <Text style={styles.sectionTitle}>Today&apos;s Summary</Text>
                <View style={styles.card}>
                    <Text style={styles.deviceName}>{formatDuration(totalScreenTimeMinutes)}</Text>
                    <Text style={styles.metaText}>
                        {deviceCount > 0
                            ? `Across ${deviceCount} device${deviceCount > 1 ? 's' : ''}`
                            : 'No activity recorded yet'}
                    </Text>
                </View>
            </View>

            <View style={styles.section}>
                <Text style={styles.sectionTitle}>Usage Overview</Text>
                {devices?.map((device) => (
                    <View key={device.id} style={styles.card}>
                        <Text style={styles.deviceName}>{device.name}</Text>
                        <Text style={styles.metaText}>Child: {device.childName}</Text>
                    </View>
                ))}
            </View>
        </ScrollView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#f3f4f6',
    },
    centered: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
    },
    loadingText: {
        fontSize: 18,
        color: '#6b7280',
    },
    section: {
        padding: 16,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: '600',
        color: '#1f2937',
        marginBottom: 12,
    },
    card: {
        backgroundColor: '#fff',
        padding: 16,
        borderRadius: 8,
        marginBottom: 12,
    },
    deviceName: {
        fontSize: 16,
        fontWeight: '600',
        color: '#1f2937',
    },
    metaText: {
        fontSize: 12,
        color: '#6b7280',
        marginTop: 4,
    },
});

export default UsageScreen;
